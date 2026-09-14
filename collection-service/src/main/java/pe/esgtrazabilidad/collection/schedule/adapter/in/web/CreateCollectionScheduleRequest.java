package pe.esgtrazabilidad.collection.schedule.adapter.in.web;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

public record CreateCollectionScheduleRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime time) {
}
