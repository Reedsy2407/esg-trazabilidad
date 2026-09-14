package pe.esgtrazabilidad.collection.schedule.port.in;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record CreateCollectionScheduleCommand(UUID neighborId, DayOfWeek dayOfWeek, LocalTime time) {
}
