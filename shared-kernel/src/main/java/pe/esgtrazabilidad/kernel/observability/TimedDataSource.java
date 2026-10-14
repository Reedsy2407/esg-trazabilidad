package pe.esgtrazabilidad.kernel.observability;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Set;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

/**
 * Times what the service asks of the database without changing it: how long {@code getConnection}
 * waited on the pool, and every statement execution. Time goes to the request's {@link RequestTimings}
 * and a statement over the threshold is logged once as
 * {@code event=slow_query service=… durationMs=… sql="…"}.
 *
 * <p>Only the statement's shape is logged: literals and numbers are replaced by {@code ?}, the text
 * is cut at 160 characters, and bind values are never read. Plain JDK proxies, so every interface
 * of the pool (including {@code Closeable}) is kept.
 */
final class TimedDataSource {

    private static final Logger log = LoggerFactory.getLogger("esg.db");
    private static final Set<String> EXECUTES =
            Set.of("execute", "executeQuery", "executeUpdate", "executeBatch", "executeLargeUpdate", "executeLargeBatch");

    private TimedDataSource() {}

    static boolean isWrapped(DataSource dataSource) {
        return Proxy.isProxyClass(dataSource.getClass());
    }

    static DataSource wrap(DataSource target, String service, long slowMillis) {
        Class<?>[] interfaces = ClassUtils.getAllInterfaces(target);
        return (DataSource) Proxy.newProxyInstance(
                TimedDataSource.class.getClassLoader(), interfaces, (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        long start = System.nanoTime();
                        try {
                            return connection((Connection) invoke(target, method, args), service, slowMillis);
                        } finally {
                            RequestTimings.addPoolWait(System.nanoTime() - start);
                        }
                    }
                    return invoke(target, method, args);
                });
    }

    private static Connection connection(Connection target, String service, long slowMillis) {
        return (Connection) Proxy.newProxyInstance(
                TimedDataSource.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    Object result = invoke(target, method, args);
                    String name = method.getName();
                    if (result instanceof PreparedStatement prepared && name.equals("prepareStatement")) {
                        return statement(prepared, PreparedStatement.class, (String) args[0], service, slowMillis);
                    }
                    if (result instanceof Statement plain && name.equals("createStatement")) {
                        return statement(plain, Statement.class, null, service, slowMillis);
                    }
                    return result;
                });
    }

    private static Object statement(Object target, Class<?> type, String sql, String service, long slowMillis) {
        return Proxy.newProxyInstance(TimedDataSource.class.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            if (!EXECUTES.contains(method.getName())) {
                return invoke(target, method, args);
            }
            long start = System.nanoTime();
            try {
                return invoke(target, method, args);
            } finally {
                long nanos = System.nanoTime() - start;
                RequestTimings.addQuery(nanos);
                long millis = nanos / 1_000_000;
                if (millis >= slowMillis) {
                    String text = sql != null ? sql : args != null && args.length > 0 && args[0] instanceof String s ? s : "?";
                    log.info("event=slow_query service={} durationMs={} sql=\"{}\"", service, millis, shape(text));
                }
            }
        });
    }

    /** Literals and numbers out, whitespace squeezed, at most 160 characters. */
    static String shape(String sql) {
        String flat = sql.replaceAll("'(?:[^']|'')*'", "?")
                .replaceAll("\\b\\d+(?:\\.\\d+)?\\b", "?")
                .replaceAll("\\s+", " ")
                .trim();
        return flat.length() > 160 ? flat.substring(0, 160) + "..." : flat;
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
