package pe.esgtrazabilidad.kernel.events;

/**
 * Which transport publishes domain events. RABBITMQ is the only real
 * transport -- Docker Compose locally, CloudAMQP in prod, both managed
 * RabbitMQ, so there's exactly one publishing/consuming code path to
 * write and test. MOCK is for tests that need to assert publishing
 * happened without a broker.
 *
 * Originally {@code GCP_PUB_SUB, MOCK, SPRING_EVENTS}. GCP_PUB_SUB was
 * removed: the architecture guide flags it as a paid option to avoid,
 * and nothing ever implemented it. SPRING_EVENTS was removed: plain
 * {@code ApplicationEventPublisher} is in-process only and can never
 * cross the recycler-service/collection-service JVM boundary this
 * project actually needs to cross -- see SPEC-cross-service-events.md's
 * Resolved Decisions for the full reasoning.
 */
public enum EventPublishingStrategy {
    RABBITMQ,
    MOCK
}
