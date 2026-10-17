package com.bank.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.gateway.config.BankGatewayProperties;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class InternalEndpointBlockingFilterTest {

    private InternalEndpointBlockingFilter filter() {
        BankGatewayProperties properties = new BankGatewayProperties();
        properties.setBlockedPaths(List.of("/**/balance-operations"));
        return new InternalEndpointBlockingFilter(properties);
    }

    @Test
    void blocksInternalEndpointsWith403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/accounts/a1/balance-operations").build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter().filter(exchange, next -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"status\":403")
                .contains("Internal endpoint not exposed by the gateway");
    }

    @Test
    void letsEverythingElseThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/accounts").build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter().filter(exchange, next -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
    }
}
