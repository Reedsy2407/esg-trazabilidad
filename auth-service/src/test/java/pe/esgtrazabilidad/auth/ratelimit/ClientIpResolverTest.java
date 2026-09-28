package pe.esgtrazabilidad.auth.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the parsing rule, plus Render's real header layout as observed in
 * Task 82 (documentation addresses stand in for the real ones).
 */
class ClientIpResolverTest {

    private static final String REMOTE_ADDR = "10.0.0.1";

    private final ClientIpResolver oneTrustedHop = new ClientIpResolver("X-Forwarded-For", 1);

    private MockHttpServletRequest requestWith(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(REMOTE_ADDR);
        if (headerValue != null) {
            request.addHeader("X-Forwarded-For", headerValue);
        }
        return request;
    }

    @Test
    void theProductionRuleKeysOnTheAddressRendersEdgeAppendedNotOnCallerSentHeaders() {
        // The layout Render's Cloudflare edge produced for a caller that sent
        // its own X-Forwarded-For: the caller's value stays on the left, the
        // edge appends the real address on the right, and remoteAddr is an
        // edge node. Caller-sent CF-Connecting-IP / True-Client-IP are ignored.
        ClientIpResolver production =
                new ClientIpResolver(RateLimitConfig.CLIENT_IP_HEADER, RateLimitConfig.TRUSTED_PROXY_HOPS);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.87");
        request.addHeader("X-Forwarded-For", "192.0.2.4,203.0.113.27");
        request.addHeader("CF-Connecting-IP", "192.0.2.8");
        request.addHeader("True-Client-IP", "192.0.2.8");

        assertThat(production.resolve(request)).isEqualTo("203.0.113.27");
    }

    @Test
    void withNoForwardingHeaderItFallsBackToTheSocketAddress() {
        assertThat(oneTrustedHop.resolve(requestWith(null))).isEqualTo(REMOTE_ADDR);
    }

    @Test
    void withASingleEntryItReturnsThatEntry() {
        assertThat(oneTrustedHop.resolve(requestWith("203.0.113.7"))).isEqualTo("203.0.113.7");
    }

    @Test
    void withMultipleHopsItTakesTheEntryAddedByTheTrustedProxyNotTheCallerControlledLeftmostOne() {
        // A caller can send any X-Forwarded-For it likes; each proxy appends to
        // the right. With one trusted hop, only the rightmost entry is trustworthy.
        assertThat(oneTrustedHop.resolve(requestWith("1.2.3.4, 198.51.100.9, 203.0.113.7")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    void withTwoTrustedHopsItTakesTheSecondEntryFromTheRight() {
        ClientIpResolver twoTrustedHops = new ClientIpResolver("X-Forwarded-For", 2);

        assertThat(twoTrustedHops.resolve(requestWith("1.2.3.4, 198.51.100.9, 203.0.113.7")))
                .isEqualTo("198.51.100.9");
    }

    @Test
    void withFewerEntriesThanTrustedHopsItFallsBackToTheSocketAddress() {
        ClientIpResolver twoTrustedHops = new ClientIpResolver("X-Forwarded-For", 2);

        assertThat(twoTrustedHops.resolve(requestWith("203.0.113.7"))).isEqualTo(REMOTE_ADDR);
    }

    @Test
    void aRepeatedHeaderLineIsReadAsOneListSoACallerSuppliedFirstLineCannotWin() {
        // RFC 9110 lets a proxy append its own header line instead of
        // concatenating; getHeader() would return only the caller's first line.
        MockHttpServletRequest request = requestWith(null);
        request.addHeader("X-Forwarded-For", "6.6.6.6");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertThat(oneTrustedHop.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void wellFormedIpv6EntriesAreAccepted() {
        assertThat(oneTrustedHop.resolve(requestWith("2001:db8::1"))).isEqualTo("2001:db8::1");
        assertThat(oneTrustedHop.resolve(requestWith("::1"))).isEqualTo("::1");
        assertThat(oneTrustedHop.resolve(requestWith("2001:db8:0:0:0:0:0:1"))).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(oneTrustedHop.resolve(requestWith("::ffff:192.0.2.1"))).isEqualTo("::ffff:192.0.2.1");
    }

    @Test
    void malformedIpv6EntriesFallBackToTheSocketAddress() {
        assertThat(oneTrustedHop.resolve(requestWith("::::::"))).isEqualTo(REMOTE_ADDR);
        assertThat(oneTrustedHop.resolve(requestWith("1:"))).isEqualTo(REMOTE_ADDR);
        assertThat(oneTrustedHop.resolve(requestWith(":1"))).isEqualTo(REMOTE_ADDR);
        assertThat(oneTrustedHop.resolve(requestWith("1::2::3"))).isEqualTo(REMOTE_ADDR);
        assertThat(oneTrustedHop.resolve(requestWith("12345::1"))).isEqualTo(REMOTE_ADDR);
        assertThat(oneTrustedHop.resolve(requestWith("1:2:3:4:5:6:7"))).isEqualTo(REMOTE_ADDR);
        assertThat(oneTrustedHop.resolve(requestWith("1:2:3:4:5:6:7:8:9"))).isEqualTo(REMOTE_ADDR);
        assertThat(oneTrustedHop.resolve(requestWith("::ffff:999.0.2.1"))).isEqualTo(REMOTE_ADDR);
        assertThat(oneTrustedHop.resolve(requestWith("gggg::1"))).isEqualTo(REMOTE_ADDR);
    }

    @Test
    void aMalformedEntryFallsBackToTheSocketAddress() {
        assertThat(oneTrustedHop.resolve(requestWith("not-an-ip"))).isEqualTo(REMOTE_ADDR);
        assertThat(oneTrustedHop.resolve(requestWith("999.1.1.1"))).isEqualTo(REMOTE_ADDR);
        assertThat(oneTrustedHop.resolve(requestWith("1.2.3.4, "))).isEqualTo(REMOTE_ADDR);
        assertThat(oneTrustedHop.resolve(requestWith(""))).isEqualTo(REMOTE_ADDR);
    }
}
