package pe.esgtrazabilidad.collection.evidence;

import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * {@link EvidenceStorage} over an S3-compatible API (Backblaze B2). The signed upload URL carries the
 * object's content type, size and SHA-256 in its signed headers, so a PUT that differs in any of them
 * is rejected by the bucket. Never logs a key, a URL or a credential.
 */
class S3EvidenceStorage implements EvidenceStorage {

    static final String JPEG = "image/jpeg";

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    S3EvidenceStorage(S3Client client, S3Presigner presigner, String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public String presignUpload(String key, long sizeBytes, String sha256Hex, Duration validFor) {
        String sha256Base64 = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(sha256Hex));
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(JPEG)
                .contentLength(sizeBytes)
                .checksumSHA256(sha256Base64)
                .build();
        return presigner
                .presignPutObject(request -> request.signatureDuration(validFor).putObjectRequest(put))
                .url()
                .toString();
    }

    @Override
    public String presignRead(String key, Duration validFor) {
        return presigner
                .presignGetObject(request -> request
                        .signatureDuration(validFor)
                        .getObjectRequest(get -> get.bucket(bucket).key(key)))
                .url()
                .toString();
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            long size = client.headObject(request -> request.bucket(bucket).key(key)).contentLength();
            return Optional.of(new StoredObject(size));
        } catch (NoSuchKeyException notFound) {
            return Optional.empty();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(request -> request.bucket(bucket).key(key));
    }
}
