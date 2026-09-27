package pe.esgtrazabilidad.auth.ratelimit;

import java.util.Collections;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The only class that knows which proxy header carries the real client IP.
 * It is the rate limit's key, so it must never trust anything the caller can
 * set. Each proxy appends the address it received from to the right of the
 * forwarding header, which leaves the leftmost entries fully caller-controlled.
 * Only the entry added by the outermost proxy we trust is reliable: counted
 * {@code trustedProxyHops} from the right.
 *
 * <p>PROVISIONAL: the header name and hop count used in production are
 * unconfirmed until they are read from a real request on Render (SPEC-deployment.md
 * Open Questions; Task 82). Anything missing or malformed falls back to the
 * socket address, never to a caller-supplied value.
 */
public class ClientIpResolver {

    // Both literal checks are syntax-only: never resolved through DNS, unlike
    // InetAddress.getByName on arbitrary input.
    private static final Pattern IPV4 =
            Pattern.compile("((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    private static final Pattern HEX_GROUP = Pattern.compile("[0-9a-fA-F]{1,4}");

    private final String headerName;
    private final int trustedProxyHops;

    public ClientIpResolver(String headerName, int trustedProxyHops) {
        if (trustedProxyHops < 1) {
            throw new IllegalArgumentException("trustedProxyHops must be at least 1");
        }
        this.headerName = headerName;
        this.trustedProxyHops = trustedProxyHops;
    }

    public String resolve(HttpServletRequest request) {
        // All lines, not getHeader()'s first one: a proxy may append its own
        // header line instead of concatenating (RFC 9110 §5.3), and the first
        // line is then the caller's.
        String header = String.join(",", Collections.list(request.getHeaders(headerName)));
        if (header.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] entries = header.split(",", -1);
        int index = entries.length - trustedProxyHops;
        if (index < 0) {
            return request.getRemoteAddr();
        }
        String candidate = entries[index].trim();
        return isIpLiteral(candidate) ? candidate : request.getRemoteAddr();
    }

    private static boolean isIpLiteral(String value) {
        return IPV4.matcher(value).matches() || isIpv6(value);
    }

    /** RFC 4291 text form: 8 hex groups, one optional "::", optional trailing IPv4. */
    private static boolean isIpv6(String value) {
        if (!value.contains(":")) {
            return false;
        }
        boolean compressed = value.contains("::");
        if (compressed && value.indexOf("::") != value.lastIndexOf("::")) {
            return false; // two "::", or ":::"
        }
        if ((value.startsWith(":") && !value.startsWith("::")) || (value.endsWith(":") && !value.endsWith("::"))) {
            return false;
        }
        String[] parts = value.split(":", -1);
        int groups = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue; // only possible inside the single "::"
            }
            if (i == parts.length - 1 && part.contains(".")) {
                if (!IPV4.matcher(part).matches()) {
                    return false;
                }
                groups += 2;
            } else if (HEX_GROUP.matcher(part).matches()) {
                groups++;
            } else {
                return false;
            }
        }
        return compressed ? groups <= 7 : groups == 8;
    }
}
