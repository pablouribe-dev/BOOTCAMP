package com.bank.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class UserHeadersFilterTest {

    private final UserHeadersFilter filter = new UserHeadersFilter();

    private HttpHeaders forwardedHeaders(ServerWebExchange exchange) {
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = next -> {
            forwarded.set(next.getRequest().getHeaders());
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return forwarded.get();
    }

    private MockServerHttpRequest forgedRequest() {
        return MockServerHttpRequest.get("/accounts")
                .header("X-User-Id", "hacker")
                .header("x-user-roles", "ADMIN")
                .header("X-Customer-Id", "someone-else")
                .header("X-Other", "kept")
                .build();
    }

    @Test
    void stripsClientIdentityHeadersAndAddsTheOnesFromTheToken() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("u1")
                .claim("roles", List.of("USER", "ADMIN")).claim("customerId", "c1").claim("phoneNumber", "999")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        Principal principal = new JwtAuthenticationToken(jwt);
        ServerWebExchange exchange = MockServerWebExchange.from(forgedRequest()).mutate()
                .principal(Mono.just(principal)).build();

        HttpHeaders headers = forwardedHeaders(exchange);

        assertThat(headers.getFirst("X-User-Id")).isEqualTo("u1");
        assertThat(headers.getFirst("X-User-Roles")).isEqualTo("USER,ADMIN");
        assertThat(headers.getFirst("X-Customer-Id")).isEqualTo("c1");
        assertThat(headers.getFirst("X-User-Phone")).isEqualTo("999");
        assertThat(headers.getFirst("X-Other")).isEqualTo("kept");
    }

    @Test
    void publicRequestsOnlyGetTheForgedHeadersRemoved() {
        HttpHeaders headers = forwardedHeaders(MockServerWebExchange.from(forgedRequest()));

        assertThat(headers.containsKey("X-User-Id")).isFalse();
        assertThat(headers.containsKey("X-User-Roles")).isFalse();
        assertThat(headers.containsKey("X-Customer-Id")).isFalse();
        assertThat(headers.getFirst("X-Other")).isEqualTo("kept");
    }

    @Test
    void runsRightAfterTheInternalEndpointBlocker() {
        assertThat(filter.getOrder()).isGreaterThan(new InternalEndpointBlockingFilter(null).getOrder());
    }
}
