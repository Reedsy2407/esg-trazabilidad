package pe.esgtrazabilidad.collection.schedule.port.in;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;

public interface ListCollectionSchedulesUseCase {

    Page<CollectionSchedule> list(UUID neighborId, Pageable pageable);
}
