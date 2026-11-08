package pe.esgtrazabilidad.auth.it;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.restassured.response.Response;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import pe.esgtrazabilidad.auth.it.support.RecyclerApiIT;
import pe.esgtrazabilidad.auth.recycleraccount.service.RecyclerCodeHasher;
import pe.esgtrazabilidad.auth.recyclerstatus.domain.RecyclerStatus;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/** CA-4, CA-5 and CA-22 (auth side): creating the account and its activation code. */
class RecyclerAccountCodeIT extends RecyclerApiIT {

    @Autowired
    private RecyclerCodeHasher hasher;

    private Response create(String token, UUID recyclerId) {
        return as(token).body(Map.of("recyclerId", recyclerId.toString())).post("/auth/recycler-accounts");
    }

    private int liveCodes(UUID accountId) {
        return jdbc.queryForObject(
                "select count(*) from activation_code where account_id = ? and used_at is null and replaced_at is null",
                Integer.class,
                accountId);
    }

    @Test
    void staffGetsATenCharacterCodeValidFor72HoursAndTheDatabaseHoldsOnlyItsHmac() {
        UUID recyclerId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        seedCopy(recyclerId, associationId, RecyclerStatus.ACTIVE);

        Response response = create(staffToken(StaffRole.STAFF), recyclerId);

        response.then().statusCode(201).header("Cache-Control", equalTo("no-store"));
        String code = response.path("activationCode");
        UUID accountId = UUID.fromString(response.path("accountId"));
        assertThat(code).matches("[A-HJ-NP-Z2-9]{10}");
        assertThat(Instant.parse(response.path("expiresAt"))).isEqualTo(START.plus(Duration.ofHours(72)));

        // The clear code is nowhere in the table: only its HMAC.
        Map<String, Object> row = jdbc.queryForMap("select * from activation_code where account_id = ?", accountId);
        assertThat(row.values().stream().map(String::valueOf)).doesNotContain(code);
        assertThat(row.get("hmac")).isEqualTo(hasher.code(code));
        assertThat(row.get("used_at")).isNull();
        // And not in any other text column of the account either.
        assertThat(jdbc.queryForMap("select * from recycler_account where id = ?", accountId)
                        .values()
                        .stream()
                        .map(String::valueOf))
                .doesNotContain(code);
    }

    @Test
    void anAdminCanCreateToo() {
        UUID recyclerId = UUID.randomUUID();
        seedCopy(recyclerId, UUID.randomUUID(), RecyclerStatus.ACTIVE);

        create(staffToken(StaffRole.ADMIN), recyclerId).then().statusCode(201);
    }

    @Test
    void theListNeverShowsTheCodeAndCarriesTheAccountDevicesAndStatus() {
        UUID recyclerId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        seedCopy(recyclerId, associationId, RecyclerStatus.ACTIVE);
        String token = staffToken(StaffRole.STAFF);
        Response created = create(token, recyclerId);
        String code = created.path("activationCode");

        Response list = as(token).queryParam("associationId", associationId).get("/auth/recycler-accounts");

        list.then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].accountId", equalTo(created.path("accountId")))
                .body("[0].recyclerId", equalTo(recyclerId.toString()))
                .body("[0].status", equalTo("PENDING_ACTIVATION"))
                .body("[0].devices", equalTo(0));
        assertThat(list.asString()).doesNotContain(code).doesNotContain("activationCode");
        // Asking again, once more: still no code anywhere.
        assertThat(as(token).get("/auth/recycler-accounts").asString()).doesNotContain(code);
    }

    @Test
    void theListFiltersByAssociationAndShowsInactiveWhenTheRecyclerWasTurnedOff() {
        UUID association = UUID.randomUUID();
        UUID active = UUID.randomUUID();
        UUID turnedOff = UUID.randomUUID();
        UUID elsewhere = UUID.randomUUID();
        seedCopy(active, association, RecyclerStatus.ACTIVE);
        seedCopy(turnedOff, association, RecyclerStatus.ACTIVE);
        seedCopy(elsewhere, UUID.randomUUID(), RecyclerStatus.ACTIVE);
        String token = staffToken(StaffRole.STAFF);
        create(token, active).then().statusCode(201);
        create(token, turnedOff).then().statusCode(201);
        create(token, elsewhere).then().statusCode(201);
        setCopyStatus(turnedOff, association, RecyclerStatus.INACTIVE, Instant.now().plusSeconds(30));

        List<Map<String, Object>> rows =
                as(token).queryParam("associationId", association).get("/auth/recycler-accounts").jsonPath().getList("$");

        assertThat(rows).extracting(r -> r.get("recyclerId")).containsExactlyInAnyOrder(active.toString(), turnedOff.toString());
        assertThat(rows)
                .filteredOn(r -> r.get("recyclerId").equals(turnedOff.toString()))
                .extracting(r -> r.get("status"))
                .containsExactly("INACTIVE");
        assertThat(rows)
                .filteredOn(r -> r.get("recyclerId").equals(active.toString()))
                .extracting(r -> r.get("status"))
                .containsExactly("PENDING_ACTIVATION");
    }

    @Test
    void askingAgainForAnAccountNotYetActivatedReplacesTheLiveCodeWithinTheSameAccount() {
        UUID recyclerId = UUID.randomUUID();
        seedCopy(recyclerId, UUID.randomUUID(), RecyclerStatus.ACTIVE);
        String token = staffToken(StaffRole.STAFF);

        Response first = create(token, recyclerId);
        clock.advance(Duration.ofHours(1));
        Response second = create(token, recyclerId);

        second.then().statusCode(201);
        assertThat(second.<String>path("accountId")).isEqualTo(first.path("accountId"));
        assertThat(second.<String>path("activationCode")).isNotEqualTo(first.path("activationCode"));
        UUID accountId = UUID.fromString(first.path("accountId"));
        assertThat(liveCodes(accountId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from activation_code where account_id = ?", Integer.class, accountId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from recycler_account where recycler_id = ?", Integer.class, recyclerId))
                .isEqualTo(1);
    }

    @Test
    void aRecyclerWithAnActiveAccountAnswers409AndTheReissueRouteNeedsEveryDeviceRevoked() {
        UUID recyclerId = UUID.randomUUID();
        seedCopy(recyclerId, UUID.randomUUID(), RecyclerStatus.ACTIVE);
        String token = staffToken(StaffRole.STAFF);
        UUID accountId = UUID.fromString(create(token, recyclerId).path("accountId"));
        jdbc.update("update recycler_account set status = 'ACTIVE', activated_at = now() where id = ?", accountId);
        UUID deviceId = UUID.randomUUID();
        jdbc.update(
                "insert into recycler_device (id, account_id, label, install_id, refresh_hash, created_at, last_seen_at,"
                        + " absolute_expires_at) values (?, ?, 'telefono', 'install-1', ?, now(), now(), now() + interval '90 days')",
                deviceId,
                accountId,
                UUID.randomUUID().toString().replace("-", ""));

        create(token, recyclerId).then().statusCode(409).body("code", equalTo("DATA_CONFLICT"));
        as(token).post("/auth/recycler-accounts/{id}/activation-code", accountId).then().statusCode(409);

        jdbc.update("update recycler_device set revoked_at = now() where id = ?", deviceId);
        Response reissued = as(token).post("/auth/recycler-accounts/{id}/activation-code", accountId);
        reissued.then().statusCode(201).header("Cache-Control", equalTo("no-store"));
        assertThat(reissued.<String>path("activationCode")).matches("[A-HJ-NP-Z2-9]{10}");
        assertThat(liveCodes(accountId)).isEqualTo(1);
    }

    @Test
    void reissueFollowsTheSameLiveStatusRulesAsCreation() {
        UUID recyclerId = UUID.randomUUID();
        UUID association = UUID.randomUUID();
        seedCopy(recyclerId, association, RecyclerStatus.ACTIVE);
        String token = staffToken(StaffRole.STAFF);
        UUID accountId = UUID.fromString(create(token, recyclerId).path("accountId"));

        setCopyStatus(recyclerId, association, RecyclerStatus.INACTIVE, Instant.now().plusSeconds(30));
        as(token).post("/auth/recycler-accounts/{id}/activation-code", accountId).then().statusCode(403).body("code", equalTo("AUTH-018"));

        // An account whose recycler has no copy at all (never happens in practice) fails closed too.
        jdbc.update("delete from recycler_status_copy_auth where recycler_id = ?", recyclerId);
        as(token).post("/auth/recycler-accounts/{id}/activation-code", accountId).then().statusCode(409).body("code", equalTo("AUTH-020"));
    }

    @Test
    void reissueOnAnUnknownAccountIs404() {
        as(staffToken(StaffRole.STAFF))
                .post("/auth/recycler-accounts/{id}/activation-code", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void ca22ARecyclerWithNoCopyIsAuth020ThenAfterTheEventTheAssociationComesFromTheCopyNotTheClient() {
        UUID recyclerId = UUID.randomUUID();
        UUID realAssociation = UUID.randomUUID();
        UUID claimedByClient = UUID.randomUUID();
        String token = staffToken(StaffRole.STAFF);
        Map<String, Object> body = Map.of("recyclerId", recyclerId.toString(), "associationId", claimedByClient.toString());

        as(token).body(body).post("/auth/recycler-accounts").then().statusCode(409).body("code", equalTo("AUTH-020"));
        assertThat(jdbc.queryForObject("select count(*) from recycler_account where recycler_id = ?", Integer.class, recyclerId))
                .as("nothing is created without a copy")
                .isZero();

        seedCopy(recyclerId, realAssociation, RecyclerStatus.ACTIVE); // what `republish` does through the consumer
        as(token).body(body).post("/auth/recycler-accounts").then().statusCode(201);

        UUID stored = jdbc.queryForObject(
                "select association_id from recycler_account where recycler_id = ?", UUID.class, recyclerId);
        assertThat(stored).isEqualTo(realAssociation).isNotEqualTo(claimedByClient);
    }

    @Test
    void aRecyclerTurnedOffIsAuth018AndGetsNoAccount() {
        UUID recyclerId = UUID.randomUUID();
        seedCopy(recyclerId, UUID.randomUUID(), RecyclerStatus.INACTIVE);

        create(staffToken(StaffRole.STAFF), recyclerId).then().statusCode(403).body("code", equalTo("AUTH-018"));

        assertThat(jdbc.queryForObject("select count(*) from recycler_account where recycler_id = ?", Integer.class, recyclerId))
                .isZero();
    }

    @Test
    void aRecyclerTokenGets403Auth000AndNoTokenGets401OnEveryRoute() {
        String recycler = recyclerToken(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID someId = UUID.randomUUID();

        as(recycler).body(Map.of("recyclerId", someId.toString())).post("/auth/recycler-accounts").then().statusCode(403).body("code", equalTo("AUTH-000"));
        as(recycler).get("/auth/recycler-accounts").then().statusCode(403).body("code", equalTo("AUTH-000"));
        as(recycler).post("/auth/recycler-accounts/{id}/activation-code", someId).then().statusCode(403).body("code", equalTo("AUTH-000"));

        given().contentType("application/json").body(Map.of("recyclerId", someId.toString())).post("/auth/recycler-accounts").then().statusCode(401);
        given().get("/auth/recycler-accounts").then().statusCode(401);
    }

    @Test
    void aMissingOrMalformedRecyclerIdIs400() {
        String token = staffToken(StaffRole.STAFF);

        as(token).body("{}").post("/auth/recycler-accounts").then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        as(token).body("{\"recyclerId\":\"no-es-uuid\"}").post("/auth/recycler-accounts").then().statusCode(400);
    }
}
