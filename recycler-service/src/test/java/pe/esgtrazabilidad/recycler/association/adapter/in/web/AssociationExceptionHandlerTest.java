package pe.esgtrazabilidad.recycler.association.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import pe.esgtrazabilidad.recycler.association.port.in.CreateAssociationUseCase;
import pe.esgtrazabilidad.recycler.association.port.in.GetAssociationUseCase;
import pe.esgtrazabilidad.recycler.association.port.in.ListAssociationsUseCase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A concurrent duplicate-RUC request can slip past AssociationService's
 * findByRuc() pre-check and hit the database's UNIQUE constraint instead
 * (findByRuc + save is not atomic). This simulates that race by having the
 * use case throw the same exception Postgres would, without needing real
 * concurrency or a database.
 */
@WebMvcTest(AssociationController.class)
class AssociationExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateAssociationUseCase createAssociationUseCase;

    @MockBean
    private GetAssociationUseCase getAssociationUseCase;

    @MockBean
    private ListAssociationsUseCase listAssociationsUseCase;

    @MockBean
    private AssociationMapper mapper;

    private static final String VALID_REQUEST = """
            {
              "name": "Asociación",
              "ruc": "20123456789",
              "registrationNumber": "REG-001",
              "address": "Dirección",
              "contactEmail": "a@b.pe",
              "contactPhone": "999999999"
            }
            """;

    @Test
    void translatesADataIntegrityViolationFromARaceConditionIntoConflict() throws Exception {
        when(createAssociationUseCase.create(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"association_ruc_key\""));

        mockMvc.perform(post("/associations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASO-002"));
    }
}
