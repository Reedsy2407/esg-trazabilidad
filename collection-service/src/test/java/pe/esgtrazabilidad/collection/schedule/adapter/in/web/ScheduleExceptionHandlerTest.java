package pe.esgtrazabilidad.collection.schedule.adapter.in.web;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import pe.esgtrazabilidad.collection.schedule.port.in.CancelCollectionScheduleUseCase;
import pe.esgtrazabilidad.collection.schedule.port.in.CreateCollectionScheduleUseCase;
import pe.esgtrazabilidad.collection.schedule.port.in.GetCollectionScheduleUseCase;
import pe.esgtrazabilidad.collection.schedule.port.in.ListCollectionSchedulesUseCase;
import pe.esgtrazabilidad.collection.schedule.port.in.PauseCollectionScheduleUseCase;
import pe.esgtrazabilidad.collection.schedule.port.in.ReactivateCollectionScheduleUseCase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CollectionSchedule has two real constraints that can surface as the same
 * DataIntegrityViolationException type (the partial unique index for COL-002,
 * the FK to neighbor for COL-001), so ScheduleExceptionHandler disambiguates
 * by constraint name -- a wrong or swapped name in that mapping wouldn't be
 * caught by anything else. This simulates each race the way Postgres would
 * report it, without needing real concurrency or a database.
 */
@WebMvcTest(CollectionScheduleController.class)
class ScheduleExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateCollectionScheduleUseCase createCollectionScheduleUseCase;

    @MockBean
    private GetCollectionScheduleUseCase getCollectionScheduleUseCase;

    @MockBean
    private ListCollectionSchedulesUseCase listCollectionSchedulesUseCase;

    @MockBean
    private PauseCollectionScheduleUseCase pauseCollectionScheduleUseCase;

    @MockBean
    private CancelCollectionScheduleUseCase cancelCollectionScheduleUseCase;

    @MockBean
    private ReactivateCollectionScheduleUseCase reactivateCollectionScheduleUseCase;

    @MockBean
    private CollectionScheduleMapper mapper;

    private static final String NEIGHBOR_ID = "00000000-0000-0000-0000-000000000000";

    private static final String VALID_REQUEST = """
            {
              "dayOfWeek": "MONDAY",
              "time": "09:00:00"
            }
            """;

    private static DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException(
                "constraint violation", new ConstraintViolationException("constraint violated", null, constraintName));
    }

    @Test
    void mapsTheActiveConflictIndexToScheduleConflict() throws Exception {
        when(createCollectionScheduleUseCase.create(any()))
                .thenThrow(violation("ux_collection_schedule_neighbor_day_active"));

        mockMvc.perform(post("/neighbors/{neighborId}/schedules", NEIGHBOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COL-002"));
    }

    @Test
    void mapsTheNeighborForeignKeyConstraintToNeighborNotFound() throws Exception {
        when(createCollectionScheduleUseCase.create(any())).thenThrow(violation("fk_collection_schedule_neighbor"));

        mockMvc.perform(post("/neighbors/{neighborId}/schedules", NEIGHBOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COL-001"));
    }

    @Test
    void fallsBackToAGenericConflictForAnUnrecognizedConstraint() throws Exception {
        when(createCollectionScheduleUseCase.create(any())).thenThrow(violation("some_other_constraint"));

        mockMvc.perform(post("/neighbors/{neighborId}/schedules", NEIGHBOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
    }
}
