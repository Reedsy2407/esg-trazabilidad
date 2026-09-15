package pe.esgtrazabilidad.collection.collectionrecord.adapter.in.web;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.CreateCollectionRecordUseCase;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.GetCollectionRecordUseCase;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.ListCollectionRecordsUseCase;
import pe.esgtrazabilidad.kernel.web.PageResponse;

@RestController
@RequestMapping("/neighbors/{neighborId}/collection-records")
class CollectionRecordController {

    private final CreateCollectionRecordUseCase createCollectionRecordUseCase;
    private final GetCollectionRecordUseCase getCollectionRecordUseCase;
    private final ListCollectionRecordsUseCase listCollectionRecordsUseCase;
    private final CollectionRecordMapper mapper;

    CollectionRecordController(
            CreateCollectionRecordUseCase createCollectionRecordUseCase,
            GetCollectionRecordUseCase getCollectionRecordUseCase,
            ListCollectionRecordsUseCase listCollectionRecordsUseCase,
            CollectionRecordMapper mapper) {
        this.createCollectionRecordUseCase = createCollectionRecordUseCase;
        this.getCollectionRecordUseCase = getCollectionRecordUseCase;
        this.listCollectionRecordsUseCase = listCollectionRecordsUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<CollectionRecordResponse> create(
            @PathVariable UUID neighborId, @Valid @RequestBody CreateCollectionRecordRequest request) {
        CollectionRecord record = createCollectionRecordUseCase.create(mapper.toCommand(neighborId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(record));
    }

    @GetMapping("/{id}")
    CollectionRecordResponse getById(@PathVariable UUID neighborId, @PathVariable UUID id) {
        return mapper.toResponse(getCollectionRecordUseCase.getById(neighborId, id));
    }

    @GetMapping
    PageResponse<CollectionRecordResponse> list(
            @PathVariable UUID neighborId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "collectionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CollectionRecordResponse> page =
                listCollectionRecordsUseCase.list(neighborId, from, to, pageable).map(mapper::toResponse);
        return PageResponse.from(page);
    }
}
