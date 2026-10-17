package com.bank.credit.controller;

import static org.mockito.Mockito.when;

import com.bank.credit.dto.OverdueScanResponse;
import com.bank.credit.service.OverdueScanService;
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
    private OverdueScanService overdueScanService;

    @Test
    void scanReturnsTheDetectedCount() {
        when(overdueScanService.scan()).thenReturn(Single.just(
                OverdueScanResponse.builder().productType("CREDIT").detected(2).build()));

        webTestClient.post().uri("/admin/overdue-debts/scan").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.detected").isEqualTo(2);
    }
}
