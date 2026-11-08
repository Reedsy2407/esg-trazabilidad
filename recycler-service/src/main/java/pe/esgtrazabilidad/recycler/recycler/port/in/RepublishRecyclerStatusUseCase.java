package pe.esgtrazabilidad.recycler.recycler.port.in;

public interface RepublishRecyclerStatusUseCase {

    /**
     * Writes one RecyclerStatusChanged to the outbox per existing recycler, with
     * its current status. Idempotent for consumers; safe to repeat.
     *
     * @return how many events were written
     */
    int republishAll();
}
