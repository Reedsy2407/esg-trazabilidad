package pe.esgtrazabilidad.collection.events.consume;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.esgtrazabilidad.collection.retention.RetentionSettingsCopy;

/**
 * Feeds the local copy of the retention periods. No ledger table: the copy itself decides, by applying an
 * event only if its {@code occurredAt} is newer than the stored one. A malformed or out-of-range
 * announcement is discarded (it can never become valid, and a period of zero days must never reach the
 * job that deletes data).
 */
@Component
class RetentionConfigEventListener {

    private static final Logger log = LoggerFactory.getLogger(RetentionConfigEventListener.class);

    private final RetentionSettingsCopy settings;
    private final ObjectMapper objectMapper;

    RetentionConfigEventListener(RetentionSettingsCopy settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RetentionConfigQueueConfig.QUEUE_NAME)
    void handle(Message message) {
        RetentionConfigChangedEvent event;
        try {
            event = objectMapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8), RetentionConfigChangedEvent.class);
        } catch (JsonProcessingException exception) {
            log.warn("event=retention_config_discarded reason=unreadable_payload");
            return;
        }
        if (event.occurredAt() == null || event.gpsDays() == null || event.photoMonths() == null || event.consentVersion() == null) {
            log.warn("event=retention_config_discarded reason=missing_fields");
            return;
        }
        if (!RetentionSettingsCopy.inRange(event.gpsDays(), event.photoMonths())) {
            log.warn("event=retention_config_discarded reason=out_of_range");
            return;
        }
        boolean applied = settings.applyIfNewer(event.gpsDays(), event.photoMonths(), event.consentVersion(), event.occurredAt());
        log.info("event=retention_config_received applied={} gpsDays={} photoMonths={}", applied, event.gpsDays(), event.photoMonths());
    }
}
