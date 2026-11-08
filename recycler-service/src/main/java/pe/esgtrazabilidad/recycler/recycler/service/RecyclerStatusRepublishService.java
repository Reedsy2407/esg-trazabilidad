package pe.esgtrazabilidad.recycler.recycler.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.recycler.events.publish.RecyclerStatusChangedEventPublisher;
import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.events.RecyclerStatusChangedEvent;
import pe.esgtrazabilidad.recycler.recycler.port.in.RepublishRecyclerStatusUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.out.RecyclerRepository;

/**
 * The initial backfill of the status copies in auth-service and
 * collection-service (A.4). One transaction: all events or none, so a repeat
 * after a failure never leaves a half-published set behind.
 *
 * <p>Known window: each event is stamped with the time it is read. If a
 * deactivation commits while this runs, a stale ACTIVE could carry a later
 * {@code occurredAt} than the INACTIVE. It is a one-off at deployment; run it
 * without write traffic, or simply run it again (it is safe to repeat).
 */
@Service
class RecyclerStatusRepublishService implements RepublishRecyclerStatusUseCase {

    private static final int PAGE_SIZE = 500;

    private final RecyclerRepository recyclerRepository;
    private final RecyclerStatusChangedEventPublisher eventPublisher;

    RecyclerStatusRepublishService(
            RecyclerRepository recyclerRepository, RecyclerStatusChangedEventPublisher eventPublisher) {
        this.recyclerRepository = recyclerRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public int republishAll() {
        int published = 0;
        Page<Recycler> page;
        int pageNumber = 0;
        do {
            page = recyclerRepository.findAll(PageRequest.of(pageNumber++, PAGE_SIZE, Sort.by("id")));
            for (Recycler recycler : page) {
                eventPublisher.publish(RecyclerStatusChangedEvent.from(recycler));
                published++;
            }
        } while (page.hasNext());
        return published;
    }
}
