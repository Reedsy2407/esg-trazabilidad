package pe.esgtrazabilidad.collection.evidence;

import java.time.Duration;
import java.util.Optional;

/**
 * Where the field photos live (a private bucket). The rest of the service talks only to this port, so
 * the integration tests never need the internet: they use an in-memory double, and the real adapter has
 * its own test against a local stub server. The bucket is private: no method here can produce a public URL.
 */
public interface EvidenceStorage {

    /** What a bucket reports about one object. */
    record StoredObject(long sizeBytes) {
    }

    /**
     * A signed URL to upload exactly one object: the signature covers the key, the size, the content
     * type and the SHA-256, so the bucket refuses anything else. Computed locally; no network.
     */
    String presignUpload(String key, long sizeBytes, String sha256Hex, Duration validFor);

    /** A signed URL to read one object, short lived. Computed locally; no network. */
    String presignRead(String key, Duration validFor);

    /** The object's metadata, or empty when it does not exist. Network call: never inside a transaction. */
    Optional<StoredObject> head(String key);

    /** Deletes the object; deleting one that is already gone is not an error. Network call. */
    void delete(String key);
}
