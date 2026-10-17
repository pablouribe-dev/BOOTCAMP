package com.bank.account.controller;

import static org.mockito.Mockito.when;

import com.bank.account.dto.RepublishResponse;
import com.bank.account.service.EventRepublishService;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private EventRepublishService eventRepublishService;

    @Test
    void republishReturnsTheCount() {
        when(eventRepublishService.republishAll()).thenReturn(Single.just(
                RepublishResponse.builder().eventType("ACCOUNT_UPSERTED").republished(3).build()));

        webTestClient.post().uri("/admin/events/republish").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.republished").isEqualTo(3);
    }
}
