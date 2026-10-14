package pe.esgtrazabilidad.reporting.warmup;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.kernel.warmup.WarmupTask;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.ListTrackedCompaniesUseCase;

/** The company list's first page, so Hibernate's first query and the mapping are done before a user asks. */
@Component
class ReportingWarmupTask implements WarmupTask {

    private final ListTrackedCompaniesUseCase companies;

    ReportingWarmupTask(ListTrackedCompaniesUseCase companies) {
        this.companies = companies;
    }

    @Override
    public String name() {
        return "tracked-company-list";
    }

    @Override
    public void run() {
        companies.list(PageRequest.of(0, 1));
    }
}
