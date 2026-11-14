package pe.esgtrazabilidad.collection.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.signer.AwsS3V4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4PresignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

/**
 * Pins the signature that scripts/smoke-b2.mjs must reproduce. The script has its own small SigV4 signer
 * (no dependencies); its offline self-test compares against the value asserted here, which the AWS SDK's own
 * S3 signer produced for fixed inputs (fixed clock, made-up credentials). If this value ever changes, the
 * script's self-test constant must change with it.
 */
class SigV4CrossCheckTest {

    /** Shared with scripts/smoke-b2.mjs (--selftest). */
    static final String EXPECTED_SIGNATURE = "0b2b4a38d0d2245d2f0a513f8799b0803f77ccee1f2f48e6400692b238ed99af";

    @Test
    void theSdkSignerProducesTheValueTheScriptPins() {
        SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.GET)
                .protocol("https")
                .host("bucket.example.test")
                .encodedPath("/test-bucket/smoke-test/object.jpg")
                .build();
        Aws4PresignerParams params = Aws4PresignerParams.builder()
                .signingName("s3")
                .signingRegion(Region.of("us-east-005"))
                .awsCredentials(AwsBasicCredentials.create("stub-id", "stub-secret"))
                .signingClockOverride(Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC))
                .expirationTime(Instant.parse("2026-01-02T03:14:05Z"))
                .build();

        SdkHttpFullRequest signed = AwsS3V4Signer.create().presign(request, params);

        Map<String, List<String>> query = signed.rawQueryParameters();
        assertThat(query.get("X-Amz-Expires")).containsExactly("600");
        assertThat(query.get("X-Amz-SignedHeaders")).containsExactly("host");
        assertThat(query.get("X-Amz-Signature")).containsExactly(EXPECTED_SIGNATURE);
    }

    /** Shared with scripts/smoke-b2.mjs (--selftest): a PUT whose signature covers type, size and hash. */
    static final String EXPECTED_PUT_SIGNATURE = "49f7f698e3fc67c7fc25bd4414e46524ba898f6bb19580103eb75ebb797b6c24";

    @Test
    void theSdkSignerProducesTheValueTheScriptPinsForAPutWithSignedHeaders() {
        SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.PUT)
                .protocol("https")
                .host("bucket.example.test")
                .encodedPath("/test-bucket/smoke-test/object.jpg")
                .putHeader("content-type", "image/jpeg")
                .putHeader("content-length", "1234")
                .putHeader("x-amz-checksum-sha256", "q83vEjRWeJA=")
                .build();
        Aws4PresignerParams params = Aws4PresignerParams.builder()
                .signingName("s3")
                .signingRegion(Region.of("us-east-005"))
                .awsCredentials(AwsBasicCredentials.create("stub-id", "stub-secret"))
                .signingClockOverride(Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC))
                .expirationTime(Instant.parse("2026-01-02T03:14:05Z"))
                .build();

        SdkHttpFullRequest signed = AwsS3V4Signer.create().presign(request, params);

        Map<String, List<String>> query = signed.rawQueryParameters();
        assertThat(query.get("X-Amz-SignedHeaders")).containsExactly("content-length;content-type;host;x-amz-checksum-sha256");
        assertThat(query.get("X-Amz-Signature")).containsExactly(EXPECTED_PUT_SIGNATURE);
    }
}
