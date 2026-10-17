package com.bank.debitcard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.debitcard.dto.DebitPaymentRequest;
import com.bank.debitcard.event.AccountOperationResultPayload;
import com.bank.debitcard.event.DebitCardEventPublisher;
import com.bank.debitcard.exception.BusinessRuleException;
import com.bank.debitcard.exception.ResourceNotFoundException;
import com.bank.debitcard.exception.ServiceUnavailableException;
import com.bank.debitcard.mapper.DebitCardMapper;
import com.bank.debitcard.model.CardStatus;
import com.bank.debitcard.model.DebitCard;
import com.bank.debitcard.model.DebitPayment;
import com.bank.debitcard.model.PaymentStatus;
import com.bank.debitcard.repository.DebitCardRepository;
import com.bank.debitcard.repository.DebitPaymentRepository;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class DebitPaymentServiceImplTest {

    private static final String COMPLETED = AccountOperationResultPayload.ACCOUNT_OPERATION_COMPLETED;
    private static final String REJECTED = AccountOperationResultPayload.ACCOUNT_OPERATION_REJECTED;

    @Mock
    private DebitCardRepository debitCardRepository;

    @Mock
    private DebitPaymentRepository debitPaymentRepository;

    @Mock
    private DebitCardEventPublisher debitCardEventPublisher;

    private DebitPaymentServiceImpl service;

    private final DebitCard card = DebitCard.builder().id("d1").customerId("c1")
            .accountIds(new ArrayList<>(List.of("a1", "a2", "a3"))).mainAccountId("a2").status(CardStatus.ACTIVE)
            .build();

    @BeforeEach
    void setUp() {
        service = new DebitPaymentServiceImpl(debitCardRepository, debitPaymentRepository, debitCardEventPublisher,
                new DebitCardMapper());
        lenient().when(debitPaymentRepository.save(any(DebitPayment.class))).thenAnswer(invocation -> {
            DebitPayment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId("p1");
            }
            return Mono.just(payment);
        });
        lenient().when(debitCardEventPublisher.requestWithdrawal(anyString(), anyString(), any(), anyString()))
                .thenReturn(Completable.complete());
        lenient().when(debitCardRepository.findById("d1")).thenReturn(Mono.just(card));
    }

    private DebitPayment pending(String operationId, String... attempted) {
        return DebitPayment.builder().id("p1").cardId("d1").amount(new BigDecimal("80"))
                .status(PaymentStatus.PENDING).currentOperationId(operationId)
                .attemptedAccountIds(new ArrayList<>(List.of(attempted))).build();
    }

    private AccountOperationResultPayload result(String operationId, String accountId, String reason) {
        return AccountOperationResultPayload.builder().operationId(operationId).accountId(accountId).reason(reason)
                .build();
    }

    @Test
    void payCreatesAPendingPaymentAndChargesTheMainAccountFirst() {
        service.pay("d1", DebitPaymentRequest.builder().amount(new BigDecimal("80")).description("cafe").build())
                .test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(payment -> payment.getStatus() == PaymentStatus.PENDING
                        && "p1".equals(payment.getPaymentId())
                        && payment.getAttemptedAccountIds().equals(List.of("a2")));
        verify(debitCardEventPublisher).requestWithdrawal(anyString(), eq("a2"), eq(new BigDecimal("80")), eq("d1"));
    }

    @Test
    void inactiveOrMissingCardsCannotPay() {
        card.setStatus(CardStatus.INACTIVE);
        when(debitCardRepository.findById("x")).thenReturn(Mono.empty());

        service.pay("d1", DebitPaymentRequest.builder().amount(BigDecimal.TEN).build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertError(BusinessRuleException.class);
        service.pay("x", DebitPaymentRequest.builder().amount(BigDecimal.TEN).build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
    }

    @Test
    void kafkaDownMarksThePaymentFailedAndAnswers503() {
        when(debitCardEventPublisher.requestWithdrawal(anyString(), anyString(), any(), anyString()))
                .thenReturn(Completable.error(new IllegalStateException("broker down")));

        service.pay("d1", DebitPaymentRequest.builder().amount(BigDecimal.TEN).build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertError(ServiceUnavailableException.class);
    }

    @Test
    void completedResultFinishesThePayment() {
        DebitPayment payment = pending("op-1", "a2");
        when(debitPaymentRepository.findByCurrentOperationId("op-1")).thenReturn(Mono.just(payment));

        service.handleOperationResult(COMPLETED, result("op-1", "a2", null))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getChargedAccountId()).isEqualTo("a2");
    }

    @Test
    void insufficientFundsFallsBackToTheNextLinkedAccountInOrder() {
        DebitPayment payment = pending("op-1", "a2");
        when(debitPaymentRepository.findByCurrentOperationId("op-1")).thenReturn(Mono.just(payment));

        service.handleOperationResult(REJECTED, result("op-1", "a2", "INSUFFICIENT_FUNDS"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAttemptedAccountIds()).containsExactly("a2", "a1");
        assertThat(payment.getCurrentOperationId()).isNotEqualTo("op-1");
        verify(debitCardEventPublisher).requestWithdrawal(payment.getCurrentOperationId(), "a1",
                new BigDecimal("80"), "d1");
    }

    @Test
    void paymentFailsWhenEveryLinkedAccountWasTried() {
        DebitPayment payment = pending("op-3", "a2", "a1", "a3");
        when(debitPaymentRepository.findByCurrentOperationId("op-3")).thenReturn(Mono.just(payment));

        service.handleOperationResult(REJECTED, result("op-3", "a3", "INSUFFICIENT_FUNDS"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        verify(debitCardEventPublisher, never()).requestWithdrawal(anyString(), anyString(), any(), anyString());
    }

    @Test
    void paymentFailsWhenTheCardNoLongerExists() {
        DebitPayment payment = pending("op-1", "a2");
        when(debitPaymentRepository.findByCurrentOperationId("op-1")).thenReturn(Mono.just(payment));
        when(debitCardRepository.findById("d1")).thenReturn(Mono.empty());

        service.handleOperationResult(REJECTED, result("op-1", "a2", "ACCOUNT_NOT_FOUND"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void resultsOfOtherServicesOrFinishedPaymentsAreIgnored() {
        when(debitPaymentRepository.findByCurrentOperationId("yanki-op")).thenReturn(Mono.empty());
        DebitPayment done = pending("op-1", "a2");
        done.setStatus(PaymentStatus.COMPLETED);
        when(debitPaymentRepository.findByCurrentOperationId("op-1")).thenReturn(Mono.just(done));

        service.handleOperationResult(COMPLETED, result("yanki-op", "a9", null))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        service.handleOperationResult(REJECTED, result("op-1", "a2", "INSUFFICIENT_FUNDS"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        verify(debitPaymentRepository, never()).save(any());
    }

    @Test
    void listAndFindPayments() {
        when(debitPaymentRepository.findByCardIdOrderByCreatedAtDesc("d1")).thenReturn(Flux.just(pending("op", "a2")));
        when(debitPaymentRepository.findByIdAndCardId("p1", "d1")).thenReturn(Mono.just(pending("op", "a2")));
        when(debitPaymentRepository.findByIdAndCardId("x", "d1")).thenReturn(Mono.empty());

        service.findByCard("d1").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findById("d1", "p1").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findById("d1", "x").test().awaitDone(1, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
    }

    @Test
    void nextAccountSkipsTheAttemptedOnes() {
        assertThat(DebitPaymentServiceImpl.nextAccount(card, List.of())).contains("a2");
        assertThat(DebitPaymentServiceImpl.nextAccount(card, List.of("a2"))).contains("a1");
        assertThat(DebitPaymentServiceImpl.nextAccount(card, List.of("a2", "a1"))).contains("a3");
        assertThat(DebitPaymentServiceImpl.nextAccount(card, List.of("a1", "a2", "a3"))).isEmpty();
    }
}
