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

import pe.esgtrazabilidad.auth.recycleraccount.domain.DeviceSummary;
import pe.esgtrazabilidad.auth.recycleraccount.domain.RecyclerDevice;
import pe.esgtrazabilidad.auth.recycleraccount.port.out.RecyclerDeviceStore;

@Component
class JdbcRecyclerDeviceStore implements RecyclerDeviceStore {

    private static final RowMapper<RecyclerDevice> DEVICE_ROW = (rs, row) -> new RecyclerDevice(
            rs.getObject("id", UUID.class),
            rs.getObject("account_id", UUID.class),
            rs.getString("refresh_hash"),
            rs.getInt("refresh_generation"),
            rs.getString("previous_hash"),
            instant(rs, "previous_valid_until"),
            instant(rs, "last_seen_at"),
            instant(rs, "absolute_expires_at"),
            instant(rs, "revoked_at"));

    private final JdbcTemplate jdbc;

    JdbcRecyclerDeviceStore(JdbcTemplate jdbc) {
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
    public void insert(
            UUID deviceId,
            UUID accountId,
            String label,
            String installId,
            String refreshHash,
            Instant now,
            Instant absoluteExpiresAt) {
        jdbc.update(
                "INSERT INTO recycler_device (id, account_id, label, install_id, refresh_hash, created_at, last_seen_at,"
                        + " absolute_expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                deviceId,
                accountId,
                label,
                installId,
                refreshHash,
                utc(now),
                utc(now),
                utc(absoluteExpiresAt));
    }

    @Override
    public Optional<RecyclerDevice> findByTokenHashForUpdate(String tokenHash) {
        return jdbc.query(
                        "SELECT id, account_id, refresh_hash, refresh_generation, previous_hash, previous_valid_until,"
                                + " last_seen_at, absolute_expires_at, revoked_at FROM recycler_device"
                                + " WHERE refresh_hash = ? OR previous_hash = ? FOR UPDATE",
                        DEVICE_ROW,
                        tokenHash,
                        tokenHash)
                .stream()
                .findFirst();
    }

    @Override
    public void rotate(
            UUID deviceId, String newHash, int newGeneration, String previousHash, Instant previousValidUntil, Instant now) {
        jdbc.update(
                "UPDATE recycler_device SET refresh_hash = ?, refresh_generation = ?, previous_hash = ?,"
                        + " previous_valid_until = ?, last_seen_at = ? WHERE id = ?",
                newHash,
                newGeneration,
                previousHash,
                utc(previousValidUntil),
                utc(now),
                deviceId);
    }

    @Override
    public void touch(UUID deviceId, Instant now) {
        jdbc.update("UPDATE recycler_device SET last_seen_at = ? WHERE id = ?", utc(now), deviceId);
    }

    @Override
    public void revoke(UUID deviceId, Instant now) {
        jdbc.update(
                "UPDATE recycler_device SET revoked_at = COALESCE(revoked_at, ?) WHERE id = ?", utc(now), deviceId);
    }

    @Override
    public List<DeviceSummary> listByAccount(UUID accountId) {
        return jdbc.query(
                "SELECT id, label, created_at, last_seen_at, revoked_at FROM recycler_device"
                        + " WHERE account_id = ? ORDER BY created_at, id",
                (rs, row) -> new DeviceSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("label"),
                        instant(rs, "created_at"),
                        instant(rs, "last_seen_at"),
                        instant(rs, "revoked_at")),
                accountId);
    }

    @Override
    public boolean revokeOwned(UUID accountId, UUID deviceId, Instant now) {
        return jdbc.update(
                        "UPDATE recycler_device SET revoked_at = COALESCE(revoked_at, ?) WHERE id = ? AND account_id = ?",
                        utc(now),
                        deviceId,
                        accountId)
                > 0;
    }
}
