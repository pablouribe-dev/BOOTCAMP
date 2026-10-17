package com.bank.gateway.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.web.reactive.function.server.RequestPredicates.all;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.bank.gateway.config.JwtProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.ServerResponse;

class SecurityConfigTest {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        SecurityConfig config = new SecurityConfig();
        JwtProperties properties = new JwtProperties();
        properties.setSecret(TestTokens.SECRET);
        SecurityWebFilterChain chain = config.securityWebFilterChain(ServerHttpSecurity.http(),
                config.jwtDecoder(properties));
        client = WebTestClient.bindToRouterFunction(route(all(), request -> ServerResponse.ok().bodyValue("ok")))
                .webFilter(new WebFilterChainProxy(chain))
                .build();
    }

    private WebTestClient.ResponseSpec call(String method, String path, String token) {
        WebTestClient.RequestBodySpec spec = client.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(path);
        if (token != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return spec.exchange();
    }

    @Test
    void loginRegisterAndHealthArePublic() {
        call("POST", "/auth/login", null).expectStatus().isOk();
        call("POST", "/auth/register", null).expectStatus().isOk();
        call("GET", "/actuator/health", null).expectStatus().isOk();
    }

    @Test
    void missingInvalidOrExpiredTokenIs401WithTheUniformBody() throws Exception {
        call("GET", "/customers", null).expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.status").isEqualTo(401).jsonPath("$.path").isEqualTo("/customers");
        call("GET", "/customers", "not-a-jwt").expectStatus().isUnauthorized();
        call("GET", "/customers", TestTokens.token("another-secret-with-at-least-32-bytes", Duration.ofMinutes(5),
                "ADMIN")).expectStatus().isUnauthorized();
        call("GET", "/customers", TestTokens.token(TestTokens.SECRET, Duration.ofMinutes(-5), "ADMIN"))
                .expectStatus().isUnauthorized();
    }

    @Test
    void userCanReadAndCreateButNotUpdateOrDelete() throws Exception {
        String user = TestTokens.valid("USER");

        call("GET", "/accounts", user).expectStatus().isOk();
        call("POST", "/debit-cards", user).expectStatus().isOk();
        call("PUT", "/accounts/a1", user).expectStatus().isForbidden()
                .expectBody().jsonPath("$.message").isEqualTo("Insufficient role for this operation");
        call("DELETE", "/accounts/a1", user).expectStatus().isForbidden();
    }

    @Test
    void adminOnlyRoutesRejectUsers() throws Exception {
        String user = TestTokens.valid("USER");
        String admin = TestTokens.valid("ADMIN");

        call("GET", "/auth/users", user).expectStatus().isForbidden();
        call("POST", "/admin/customers/events/republish", user).expectStatus().isForbidden();
        call("GET", "/actuator/gateway/routes", user).expectStatus().isForbidden();
        call("GET", "/auth/users", admin).expectStatus().isOk();
        call("POST", "/admin/customers/events/republish", admin).expectStatus().isOk();
        call("PUT", "/accounts/a1", admin).expectStatus().isOk();
        call("DELETE", "/accounts/a1", admin).expectStatus().isOk();
    }

    @Test
    void otherMethodsAreDenied() throws Exception {
        call("PATCH", "/accounts/a1", TestTokens.valid("ADMIN")).expectStatus().isForbidden();
    }

    @Test
    void refusesAShortSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("short");

        assertThatThrownBy(() -> new SecurityConfig().jwtDecoder(properties))
                .isInstanceOf(IllegalStateException.class);
    }
}
