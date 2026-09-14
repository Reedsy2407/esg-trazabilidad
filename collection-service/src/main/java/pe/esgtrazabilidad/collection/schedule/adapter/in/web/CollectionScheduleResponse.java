package pe.esgtrazabilidad.collection.schedule.adapter.in.web;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import pe.esgtrazabilidad.collection.schedule.domain.CollectionScheduleStatus;

public record CollectionScheduleResponse(
        UUID id, UUID neighborId, DayOfWeek dayOfWeek, LocalTime time, CollectionScheduleStatus status) {
}
