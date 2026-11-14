package pe.esgtrazabilidad.collection.evidence;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * The bucket client. Credentials come from the environment (B2_KEY_ID, B2_APPLICATION_KEY) and have no
 * default: without them the service does not start. Every outgoing call has the three timeouts the
 * dependency sheet asks for (connect, read, wait for a pooled connection) plus a ceiling for the whole call.
 */
@Configuration
class EvidenceConfig {

    private static final class Settings {
        final URI endpoint;
        final Region region;
        final AwsBasicCredentials credentials;

        Settings(String endpoint, String region, String keyId, String applicationKey) {
            this.endpoint = URI.create(endpoint);
            this.region = Region.of(region);
            this.credentials = AwsBasicCredentials.create(keyId, applicationKey);
        }
    }

    @Bean
    EvidenceStorage evidenceStorage(
            @Value("${esg.evidence.endpoint}") String endpoint,
            @Value("${esg.evidence.region}") String region,
            @Value("${esg.evidence.bucket}") String bucket,
            @Value("${esg.evidence.key-id}") String keyId,
            @Value("${esg.evidence.application-key}") String applicationKey,
            @Value("${esg.evidence.connect-timeout-seconds}") long connectTimeoutSeconds,
            @Value("${esg.evidence.read-timeout-seconds}") long readTimeoutSeconds,
            @Value("${esg.evidence.pool-wait-timeout-seconds}") long poolWaitTimeoutSeconds,
            @Value("${esg.evidence.call-timeout-seconds}") long callTimeoutSeconds) {
        Settings settings = new Settings(endpoint, region, keyId, applicationKey);
        S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        S3Client client = S3Client.builder()
                .endpointOverride(settings.endpoint)
                .region(settings.region)
                .credentialsProvider(StaticCredentialsProvider.create(settings.credentials))
                .serviceConfiguration(pathStyle)
                // S3-compatible stores do not all accept the SDK's default extra checksums.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                        .socketTimeout(Duration.ofSeconds(readTimeoutSeconds))
                        .connectionAcquisitionTimeout(Duration.ofSeconds(poolWaitTimeoutSeconds)))
                .overrideConfiguration(override -> override
                        .apiCallTimeout(Duration.ofSeconds(callTimeoutSeconds))
                        .apiCallAttemptTimeout(Duration.ofSeconds(readTimeoutSeconds + connectTimeoutSeconds)))
                .build();
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(settings.endpoint)
                .region(settings.region)
                .credentialsProvider(StaticCredentialsProvider.create(settings.credentials))
                .serviceConfiguration(pathStyle)
                .build();
        return new S3EvidenceStorage(client, presigner, bucket);
    }
}
