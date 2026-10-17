package com.bank.transaction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bank.transaction.dto.GeneralReportResponse;
import com.bank.transaction.dto.ProductReportResponse;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.exception.InvalidRequestException;
import com.bank.transaction.model.ProductType;
import com.bank.transaction.service.ReportService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReportService reportService;

    @Test
    void productReport() {
        when(reportService.productReport(ProductType.ACCOUNT, "a1", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31))).thenReturn(Single.just(ProductReportResponse.builder()
                .productType(ProductType.ACCOUNT).productId("a1").movementCount(3).build()));

        webTestClient.get().uri("/reports/products/ACCOUNT/a1?from=2026-10-01&to=2026-10-31").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.movementCount").isEqualTo(3);
    }

    @Test
    void generalReportAndInvalidRange() {
        when(reportService.generalReport(any(), any()))
                .thenReturn(Single.just(GeneralReportResponse.builder().totalMovements(7).build()))
                .thenReturn(Single.error(new InvalidRequestException("'from' must not be after 'to'")));

        webTestClient.get().uri("/reports/products?from=2026-10-01&to=2026-10-31").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.totalMovements").isEqualTo(7);
        webTestClient.get().uri("/reports/products?from=2026-10-31&to=2026-10-01").exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void lastMovementsAndBadParameters() {
        when(reportService.lastMovements(ProductType.CREDIT_CARD, "k1"))
                .thenReturn(Flowable.just(TransactionResponse.builder().id("t1").build()));

        webTestClient.get().uri("/reports/last-movements?productType=CREDIT_CARD&productId=k1").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$[0].id").isEqualTo("t1");
        webTestClient.get().uri("/reports/last-movements?productType=NOPE&productId=k1").exchange()
                .expectStatus().isBadRequest();
        webTestClient.get().uri("/reports/products?from=ayer&to=hoy").exchange().expectStatus().isBadRequest();
    }
}
