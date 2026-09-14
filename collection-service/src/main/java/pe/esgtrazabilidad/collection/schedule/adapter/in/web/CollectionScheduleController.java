package pe.esgtrazabilidad.collection.schedule.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;
import pe.esgtrazabilidad.collection.schedule.port.in.CreateCollectionScheduleUseCase;
import pe.esgtrazabilidad.collection.schedule.port.in.GetCollectionScheduleUseCase;
import pe.esgtrazabilidad.collection.schedule.port.in.ListCollectionSchedulesUseCase;
import pe.esgtrazabilidad.kernel.web.PageResponse;

@RestController
@RequestMapping("/neighbors/{neighborId}/schedules")
class CollectionScheduleController {

    private final CreateCollectionScheduleUseCase createCollectionScheduleUseCase;
    private final GetCollectionScheduleUseCase getCollectionScheduleUseCase;
    private final ListCollectionSchedulesUseCase listCollectionSchedulesUseCase;
    private final CollectionScheduleMapper mapper;

    CollectionScheduleController(
            CreateCollectionScheduleUseCase createCollectionScheduleUseCase,
            GetCollectionScheduleUseCase getCollectionScheduleUseCase,
            ListCollectionSchedulesUseCase listCollectionSchedulesUseCase,
            CollectionScheduleMapper mapper) {
        this.createCollectionScheduleUseCase = createCollectionScheduleUseCase;
        this.getCollectionScheduleUseCase = getCollectionScheduleUseCase;
        this.listCollectionSchedulesUseCase = listCollectionSchedulesUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<CollectionScheduleResponse> create(
            @PathVariable UUID neighborId, @Valid @RequestBody CreateCollectionScheduleRequest request) {
        CollectionSchedule schedule = createCollectionScheduleUseCase.create(mapper.toCommand(neighborId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(schedule));
    }

    @GetMapping("/{id}")
    CollectionScheduleResponse getById(@PathVariable UUID neighborId, @PathVariable UUID id) {
        return mapper.toResponse(getCollectionScheduleUseCase.getById(neighborId, id));
    }

    @GetMapping
    PageResponse<CollectionScheduleResponse> list(
            @PathVariable UUID neighborId,
            @PageableDefault(size = 20, sort = "dayOfWeek", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<CollectionScheduleResponse> page =
                listCollectionSchedulesUseCase.list(neighborId, pageable).map(mapper::toResponse);
        return PageResponse.from(page);
    }
}
