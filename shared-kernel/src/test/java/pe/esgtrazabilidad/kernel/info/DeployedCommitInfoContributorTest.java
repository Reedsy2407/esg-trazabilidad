package pe.esgtrazabilidad.kernel.info;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DeployedCommitInfoContributorTest {

    private static Info infoWith(MockEnvironment environment) {
        Info.Builder builder = new Info.Builder();
        new DeployedCommitInfoContributor(environment).contribute(builder);
        return builder.build();
    }

    @Test
    void exposesTheDeployedCommitAsItsOnlyDetail() {
        Info info = infoWith(new MockEnvironment()
                .withProperty("RENDER_GIT_COMMIT", "409B730F60B5A7F8B710E538D6F0DF877AD89F28"));

        assertThat(info.getDetails()).containsOnlyKeys("commit");
        assertThat(info.get("commit")).isEqualTo("409b730f60b5a7f8b710e538d6f0df877ad89f28");
    }

    @Test
    void contributesNothingWhenTheVariableIsAbsent() {
        assertThat(infoWith(new MockEnvironment()).getDetails()).isEmpty();
    }

    @Test
    void neverEchoesAValueThatIsNotACommitSha() {
        assertThat(infoWith(new MockEnvironment().withProperty("RENDER_GIT_COMMIT", "not-a-sha")).getDetails())
                .isEmpty();
        assertThat(infoWith(new MockEnvironment().withProperty("RENDER_GIT_COMMIT", "abc12")).getDetails())
                .isEmpty();
        assertThat(infoWith(new MockEnvironment().withProperty("RENDER_GIT_COMMIT", "409b730 ; secret")).getDetails())
                .isEmpty();
    }
}
