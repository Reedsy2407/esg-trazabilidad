package pe.esgtrazabilidad.collection.it.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import pe.esgtrazabilidad.collection.evidence.EvidenceStorage;

/**
 * A bucket in memory for the integration tests: no network, no signing. It records whether a call
 * happened inside a database transaction (it must never), can fail the next calls on demand, and keeps
 * the arguments of the signed URLs it was asked for. {@code @Import} it in a test class to replace the real
 * storage with it.
 */
public class InMemoryEvidenceStorage implements EvidenceStorage {

    private final Map<String, Long> objects = new ConcurrentHashMap<>();
    private final List<String> deleted = new ArrayList<>();
    private final List<Boolean> callsInsideTransaction = new ArrayList<>();
    private final AtomicInteger failHeads = new AtomicInteger();
    private final AtomicInteger failDeletes = new AtomicInteger();
    private volatile Duration lastUploadValidity;
    private volatile Duration lastReadValidity;
    private volatile Long lastUploadSize;
    private volatile String lastUploadSha256;

    /** Puts an object in the bucket as if the device had uploaded it. */
    public void put(String key, long sizeBytes) {
        objects.put(key, sizeBytes);
    }

    public boolean contains(String key) {
        return objects.containsKey(key);
    }

    public void failNextHeads(int count) {
        failHeads.set(count);
    }

    public void failNextDeletes(int count) {
        failDeletes.set(count);
    }

    public synchronized List<String> deleted() {
        return List.copyOf(deleted);
    }

    public synchronized List<Boolean> callsInsideTransaction() {
        return List.copyOf(callsInsideTransaction);
    }

    public Duration lastUploadValidity() {
        return lastUploadValidity;
    }

    public Duration lastReadValidity() {
        return lastReadValidity;
    }

    public Long lastUploadSize() {
        return lastUploadSize;
    }

    public String lastUploadSha256() {
        return lastUploadSha256;
    }

    public synchronized void reset() {
        objects.clear();
        deleted.clear();
        callsInsideTransaction.clear();
        failHeads.set(0);
        failDeletes.set(0);
    }

    @Override
    public String presignUpload(String key, long sizeBytes, String sha256Hex, Duration validFor) {
        lastUploadValidity = validFor;
        lastUploadSize = sizeBytes;
        lastUploadSha256 = sha256Hex;
        return "https://bucket.invalid/" + key + "?signed-upload";
    }

    @Override
    public String presignRead(String key, Duration validFor) {
        record();
        lastReadValidity = validFor;
        return "https://bucket.invalid/" + key + "?signed-read";
    }

    @Override
    public Optional<StoredObject> head(String key) {
        record();
        if (failHeads.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new IllegalStateException("bucket unreachable (simulated)");
        }
        Long size = objects.get(key);
        return size == null ? Optional.empty() : Optional.of(new StoredObject(size));
    }

    @Override
    public void delete(String key) {
        record();
        if (failDeletes.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new IllegalStateException("bucket unreachable (simulated)");
        }
        objects.remove(key);
        synchronized (this) {
            deleted.add(key);
        }
    }

    private synchronized void record() {
        callsInsideTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
    }

    /** Import this in a test class to swap the real storage for a fresh in-memory one. */
    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public InMemoryEvidenceStorage inMemoryEvidenceStorage() {
            return new InMemoryEvidenceStorage();
        }
    }
}
