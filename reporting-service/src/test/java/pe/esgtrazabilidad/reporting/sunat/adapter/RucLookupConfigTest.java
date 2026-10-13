package pe.esgtrazabilidad.reporting.sunat.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import pe.esgtrazabilidad.reporting.sunat.RucLookup;

import static org.assertj.core.api.Assertions.assertThat;

/** Startup rules for the provider switch (D2c): no default, no "none" on Render, no Decolecta without a token. */
class RucLookupConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(RucLookupConfig.class);

    @Test
    void failsToStartWithoutAProvider() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsToStartWithAnUnknownProvider() {
        runner.withPropertyValues("RUC_LOOKUP_PROVIDER=sunat-direct").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void noneIsAllowedOutsideRender() {
        runner.withPropertyValues("RUC_LOOKUP_PROVIDER=none").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RucLookup.class)).isInstanceOf(UnavailableRucLookup.class);
        });
    }

    @Test
    void noneIsRefusedWhenRenderIsTrue() {
        runner.withPropertyValues("RUC_LOOKUP_PROVIDER=none", "RENDER=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "RUC_LOOKUP_PROVIDER=none is for development and tests only: refused when RENDER=true");
        });
    }

    @Test
    void decolectaFailsToStartWithoutAToken() {
        runner.withPropertyValues("RUC_LOOKUP_PROVIDER=decolecta").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void decolectaStartsWithATokenUnderEitherName() {
        runner.withPropertyValues("RUC_LOOKUP_PROVIDER=decolecta", "RUC_LOOKUP_TOKEN=x").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RucLookup.class)).isInstanceOf(DecolectaRucLookup.class);
        });
        runner.withPropertyValues("RUC_LOOKUP_PROVIDER=decolecta", "DECOLECTA_TOKEN=x")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
