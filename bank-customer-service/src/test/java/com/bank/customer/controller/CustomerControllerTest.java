package com.bank.customer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.customer.dto.CustomerRequest;
import com.bank.customer.dto.CustomerResponse;
import com.bank.customer.exception.BusinessRuleException;
import com.bank.customer.exception.ResourceNotFoundException;
import com.bank.customer.model.CustomerProfile;
import com.bank.customer.model.CustomerType;
import com.bank.customer.service.CustomerService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CustomerService customerService;

    private static final String VALID_BODY = """
            {"type":"PERSONAL","documentType":"DNI","documentNumber":"45678912","name":"Ana Torres"}""";

    @Test
    void createReturns201() {
        when(customerService.create(any(CustomerRequest.class))).thenReturn(Single.just(
                CustomerResponse.builder().id("c1").type(CustomerType.PERSONAL).profile(CustomerProfile.STANDARD)
                        .build()));

        webTestClient.post().uri("/customers").contentType(MediaType.APPLICATION_JSON).bodyValue(VALID_BODY)
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.id").isEqualTo("c1").jsonPath("$.profile").isEqualTo("STANDARD");
    }

    @Test
    void createWithMissingFieldsReturns400WithUniformBody() {
        webTestClient.post().uri("/customers").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Sin tipo\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.path").isEqualTo("/customers")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void businessRuleViolationReturns409() {
        when(customerService.create(any(CustomerRequest.class)))
                .thenReturn(Single.error(new BusinessRuleException("Profile VIP is not allowed")));

        webTestClient.post().uri("/customers").contentType(MediaType.APPLICATION_JSON).bodyValue(VALID_BODY)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.message").isEqualTo("Profile VIP is not allowed");
    }

    @Test
    void findByIdNotFoundReturns404() {
        when(customerService.findById("x")).thenReturn(Single.error(new ResourceNotFoundException("missing")));

        webTestClient.get().uri("/customers/x").exchange().expectStatus().isNotFound();
    }

    @Test
    void listUpdateAndDelete() {
        when(customerService.findAll()).thenReturn(Flowable.just(CustomerResponse.builder().id("c1").build()));
        when(customerService.update(eq("c1"), any(CustomerRequest.class)))
                .thenReturn(Single.just(CustomerResponse.builder().id("c1").build()));
        when(customerService.delete("c1")).thenReturn(Completable.complete());

        webTestClient.get().uri("/customers").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].id").isEqualTo("c1");
        webTestClient.put().uri("/customers/c1").contentType(MediaType.APPLICATION_JSON).bodyValue(VALID_BODY)
                .exchange().expectStatus().isOk();
        webTestClient.delete().uri("/customers/c1").exchange().expectStatus().isNoContent();
    }

    @Test
    void listFilteredByDocumentNumberUsesTheCachedLookup() {
        when(customerService.findByDocumentNumber("77"))
                .thenReturn(io.reactivex.rxjava3.core.Maybe.just(CustomerResponse.builder().id("c7").build()));

        webTestClient.get().uri("/customers?documentNumber=77").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].id").isEqualTo("c7");
    }
}
