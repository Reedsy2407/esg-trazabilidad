package pe.esgtrazabilidad.kernel.events;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * Not a JPA {@code @Entity} -- each service owns its own JPA entity +
 * Liquibase table and mirrors this shape. Crosses the {@link OutboxRepository}
 * port the same way a domain object crosses any other repository port in
 * this codebase; each service's adapter translates it to/from its own
 * entity internally.
 */
public record OutboxEntry(
        UUID id, String eventType, String routingKey, String payloadJson, OutboxStatus status, Instant createdAt) {

    public static OutboxEntry create(String eventType, String routingKey, String payloadJson) {
        return new OutboxEntry(IdGenerator.generate(), eventType, routingKey, payloadJson, OutboxStatus.NEW, Instant.now());
    }
}
