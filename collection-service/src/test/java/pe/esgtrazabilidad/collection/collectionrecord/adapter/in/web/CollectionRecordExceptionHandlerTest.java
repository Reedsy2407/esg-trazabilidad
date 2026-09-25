package pe.esgtrazabilidad.collection.collectionrecord.adapter.in.web;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import pe.esgtrazabilidad.collection.collectionrecord.port.in.CreateCollectionRecordUseCase;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.GetCollectionRecordUseCase;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.ListCollectionRecordsUseCase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CollectionRecord has two FK constraints (neighbor, schedule) that can
 * surface as the same DataIntegrityViolationException type, so
 * CollectionRecordExceptionHandler disambiguates by constraint name -- built
 * in from the start this time, after the same gap was caught for
 * ScheduleExceptionHandler.
 */
// addFilters = false: collection-service now has a real SecurityConfig requiring
// a token on every request -- this test is only about exception translation,
// not auth, so the servlet filter chain (including security) is disabled here
// rather than faking a JWT for a concern this class doesn't test.
@WebMvcTest(CollectionRecordController.class)
@AutoConfigureMockMvc(addFilters = false)
class CollectionRecordExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateCollectionRecordUseCase createCollectionRecordUseCase;

    @MockBean
    private GetCollectionRecordUseCase getCollectionRecordUseCase;

    @MockBean
    private ListCollectionRecordsUseCase listCollectionRecordsUseCase;

    @MockBean
    private CollectionRecordMapper mapper;

    private static final String NEIGHBOR_ID = "00000000-0000-0000-0000-000000000000";

    private static final String VALID_REQUEST = """
            {
              "associationId": "00000000-0000-0000-0000-000000000001",
              "collectionDate": "2026-01-01",
              "weightKg": 10
            }
            """;

    private static DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException(
                "constraint violation", new ConstraintViolationException("constraint violated", null, constraintName));
    }

    @Test
    void mapsTheNeighborForeignKeyConstraintToNeighborNotFound() throws Exception {
        when(createCollectionRecordUseCase.create(any())).thenThrow(violation("fk_collection_record_neighbor"));

        mockMvc.perform(post("/neighbors/{neighborId}/collection-records", NEIGHBOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COL-001"));
    }

    @Test
    void mapsTheScheduleForeignKeyConstraintToScheduleNotFound() throws Exception {
        when(createCollectionRecordUseCase.create(any())).thenThrow(violation("fk_collection_record_schedule"));

        mockMvc.perform(post("/neighbors/{neighborId}/collection-records", NEIGHBOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COL-006"));
    }

    @Test
    void fallsBackToAGenericConflictForAnUnrecognizedConstraint() throws Exception {
        when(createCollectionRecordUseCase.create(any())).thenThrow(violation("some_other_constraint"));

        mockMvc.perform(post("/neighbors/{neighborId}/collection-records", NEIGHBOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
    }
}
