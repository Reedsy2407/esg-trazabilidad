package pe.esgtrazabilidad.auth.recycleraccount.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.auth.recycleraccount.domain.AccountStatus;
import pe.esgtrazabilidad.auth.recycleraccount.domain.AccountSummary;
import pe.esgtrazabilidad.auth.recycleraccount.domain.RecyclerAccount;
import pe.esgtrazabilidad.auth.recycleraccount.port.out.RecyclerAccountStore;

/** Plain SQL, like the password recovery store: a few conditional statements read better than entities. */
@Component
class JdbcRecyclerAccountStore implements RecyclerAccountStore {

    private static final String ACCOUNT_COLUMNS =
            "id, recycler_id, association_id, status, created_at, activated_at, consent_version, location_consent";

    private static final RowMapper<RecyclerAccount> ACCOUNT_ROW = (rs, row) -> new RecyclerAccount(
            rs.getObject("id", UUID.class),
            rs.getObject("recycler_id", UUID.class),
            rs.getObject("association_id", UUID.class),
            AccountStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            instant(rs, "activated_at"),
            rs.getObject("consent_version", Integer.class),
            rs.getBoolean("location_consent"));

    private final JdbcTemplate jdbc;

    JdbcRecyclerAccountStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    @Override
    public Optional<RecyclerAccount> findById(UUID accountId) {
        return jdbc.query("SELECT " + ACCOUNT_COLUMNS + " FROM recycler_account WHERE id = ?", ACCOUNT_ROW, accountId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<RecyclerAccount> findByIdForUpdate(UUID accountId) {
        return jdbc.query(
                        "SELECT " + ACCOUNT_COLUMNS + " FROM recycler_account WHERE id = ? FOR UPDATE",
                        ACCOUNT_ROW,
                        accountId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<RecyclerAccount> findByRecyclerId(UUID recyclerId) {
        return jdbc.query(
                        "SELECT " + ACCOUNT_COLUMNS + " FROM recycler_account WHERE recycler_id = ?",
                        ACCOUNT_ROW,
                        recyclerId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean insertIfAbsent(RecyclerAccount account) {
        return jdbc.update(
                        "INSERT INTO recycler_account (id, recycler_id, association_id, status, created_at)"
                                + " VALUES (?, ?, ?, ?, ?) ON CONFLICT (recycler_id) DO NOTHING",
                        account.id(),
                        account.recyclerId(),
                        account.associationId(),
                        account.status().name(),
                        utc(account.createdAt()))
                > 0;
    }

    @Override
    public void replaceCode(UUID accountId, UUID codeId, String codeHmac, Instant now, Instant expiresAt) {
        jdbc.queryForList("SELECT id FROM recycler_account WHERE id = ? FOR UPDATE", accountId);
        jdbc.update(
                "UPDATE activation_code SET replaced_at = ? WHERE account_id = ? AND used_at IS NULL AND replaced_at IS NULL",
                utc(now),
                accountId);
        jdbc.update(
                "INSERT INTO activation_code (id, account_id, hmac, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                codeId,
                accountId,
                codeHmac,
                utc(now),
                utc(expiresAt));
    }

    @Override
    public Optional<UUID> lockLiveCode(String codeHmac, Instant now) {
        return jdbc.query(
                        "SELECT account_id FROM activation_code"
                                + " WHERE hmac = ? AND used_at IS NULL AND replaced_at IS NULL AND expires_at > ? FOR UPDATE",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        codeHmac,
                        utc(now))
                .stream()
                .findFirst();
    }

    @Override
    public void markCodeUsed(String codeHmac, Instant now) {
        jdbc.update("UPDATE activation_code SET used_at = ? WHERE hmac = ?", utc(now), codeHmac);
    }

    @Override
    public void markActivated(UUID accountId, Instant now) {
        jdbc.update(
                "UPDATE recycler_account SET status = 'ACTIVE', activated_at = COALESCE(activated_at, ?) WHERE id = ?",
                utc(now),
                accountId);
    }

    @Override
    public void recordConsent(UUID accountId, Integer consentVersion, boolean locationConsent, Instant now) {
        jdbc.update(
                "UPDATE recycler_account SET consent_version = ?, consent_accepted_at = ?, location_consent = ? WHERE id = ?",
                consentVersion,
                utc(now),
                consentVersion != null && locationConsent,
                accountId);
    }

    @Override
    public int liveDeviceCount(UUID accountId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM recycler_device WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class,
                accountId);
        return count == null ? 0 : count;
    }

    @Override
    public List<AccountSummary> list(UUID associationId) {
        String sql = """
                SELECT a.id, a.recycler_id,
                       CASE WHEN c.status IS DISTINCT FROM 'ACTIVE' THEN 'INACTIVE' ELSE a.status END AS status,
                       a.activated_at,
                       (SELECT count(*) FROM recycler_device d WHERE d.account_id = a.id AND d.revoked_at IS NULL) AS devices
                FROM recycler_account a
                LEFT JOIN recycler_status_copy_auth c ON c.recycler_id = a.recycler_id
                """
                + (associationId == null ? "" : "WHERE a.association_id = ?\n")
                + "ORDER BY a.created_at, a.id";
        RowMapper<AccountSummary> mapper = (rs, row) -> new AccountSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("recycler_id", UUID.class),
                AccountStatus.valueOf(rs.getString("status")),
                instant(rs, "activated_at"),
                rs.getInt("devices"));
        return associationId == null ? jdbc.query(sql, mapper) : jdbc.query(sql, mapper, associationId);
    }
}
