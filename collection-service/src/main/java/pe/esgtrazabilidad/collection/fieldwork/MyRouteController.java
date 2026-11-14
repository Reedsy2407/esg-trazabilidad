package pe.esgtrazabilidad.collection.fieldwork;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.kernel.security.RecyclerClaims;

/** Field route of the recycler's device: identity and association only from the validated token. */
@RestController
class MyRouteController {

    private final MyRouteService service;

    MyRouteController(MyRouteService service) {
        this.service = service;
    }

    @GetMapping("/me/route")
    List<MyRouteService.RouteStop> route(@AuthenticationPrincipal Jwt jwt) {
        RecyclerClaims claims = RecyclerClaims.from(jwt);
        return service.routeOf(claims.recyclerId(), claims.associationId());
    }
}
