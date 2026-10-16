package pe.esgtrazabilidad.reporting.certificate;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolOrigin;

import static org.assertj.core.api.Assertions.assertThat;

class HierarchyDeclarationTest {

    private static final String TAIL = "; no es una cifra oficial de SIGERSOL ni la verifica esta plataforma.";

    @Test
    void withAProofItNamesTheOriginTheAuthorAndTheProof() {
        assertThat(new HierarchyDeclaration(SigersolOrigin.REFERENCED_PROOF, "ana@asociacion.pe", "factura F001-123").note())
                .isEqualTo("El cumplimiento de jerarquía es un dato declarado por el operador de la asociación "
                        + "(origen: con comprobante referenciado, registrado por ana@asociacion.pe), "
                        + "respaldado por el comprobante «factura F001-123»" + TAIL);
    }

    @Test
    void declaredByTheOperatorHasNoProofPart() {
        assertThat(new HierarchyDeclaration(SigersolOrigin.DECLARED_BY_OPERATOR, "ana@asociacion.pe", null).note())
                .isEqualTo("El cumplimiento de jerarquía es un dato declarado por el operador de la asociación "
                        + "(origen: declarado por el operador, registrado por ana@asociacion.pe)" + TAIL);
    }

    @Test
    void anOldRecordSaysItsOriginWasNotRecordedAndNamesNobody() {
        assertThat(HierarchyDeclaration.NOT_RECORDED.note())
                .isEqualTo("El cumplimiento de jerarquía es un dato declarado por el operador de la asociación "
                        + "(origen: no registrado)" + TAIL);
    }

    @Test
    void aTokenWithoutAnEmailLeavesOutTheAuthorAndBlankPartsAreIgnored() {
        assertThat(new HierarchyDeclaration(SigersolOrigin.REFERENCED_PROOF, "  ", " ").note())
                .isEqualTo("El cumplimiento de jerarquía es un dato declarado por el operador de la asociación "
                        + "(origen: con comprobante referenciado)" + TAIL);
    }

    @Test
    void neverClaimsSigersolOrThePlatformConfirmedTheFigure() {
        for (HierarchyDeclaration declaration : new HierarchyDeclaration[] {
            HierarchyDeclaration.NOT_RECORDED,
            new HierarchyDeclaration(SigersolOrigin.REFERENCED_PROOF, "a@b.pe", "x"),
        }) {
            assertThat(declaration.note()).contains("ni la verifica esta plataforma").doesNotContain("verificado por SIGERSOL");
        }
    }
}
