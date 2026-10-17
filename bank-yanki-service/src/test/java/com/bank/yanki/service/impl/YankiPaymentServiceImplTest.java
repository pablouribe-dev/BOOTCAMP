package com.bank.yanki.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.yanki.config.YankiProperties;
import com.bank.yanki.dto.YankiPaymentRequest;
import com.bank.yanki.event.AccountOperationResultPayload;
import com.bank.yanki.event.YankiEventPublisher;
import com.bank.yanki.exception.BusinessRuleException;
import com.bank.yanki.exception.ResourceNotFoundException;
import com.bank.yanki.exception.ServiceUnavailableException;
import com.bank.yanki.mapper.YankiMapper;
import com.bank.yanki.model.DebitCardReplica;
import com.bank.yanki.model.PaymentStatus;
import com.bank.yanki.model.PaymentStep;
import com.bank.yanki.model.Wallet;
import com.bank.yanki.model.YankiPayment;
import com.bank.yanki.repository.DebitCardReplicaRepository;
import com.bank.yanki.repository.WalletRepository;
import com.bank.yanki.repository.YankiPaymentRepository;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class YankiPaymentServiceImplTest {

    private static final String COMPLETED = AccountOperationResultPayload.ACCOUNT_OPERATION_COMPLETED;
    private static final String REJECTED = AccountOperationResultPayload.ACCOUNT_OPERATION_REJECTED;

    @Mock
    private YankiPaymentRepository yankiPaymentRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private DebitCardReplicaRepository debitCardReplicaRepository;

    @Mock
    private YankiEventPublisher yankiEventPublisher;

    private YankiPaymentServiceImpl service;

    private Wallet sender;
    private Wallet receiver;

    @BeforeEach
    void setUp() {
        YankiProperties properties = new YankiProperties();
        properties.setPaymentTimeout(Duration.ofMinutes(2));
        Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
        service = new YankiPaymentServiceImpl(yankiPaymentRepository, walletRepository, debitCardReplicaRepository,
                yankiEventPublisher, new YankiMapper(), properties, clock);
        sender = Wallet.builder().id("w1").phoneNumber("911111111").balance(new BigDecimal("100")).build();
        receiver = Wallet.builder().id("w2").phoneNumber("922222222").balance(new BigDecimal("5")).build();
        lenient().when(walletRepository.findByPhoneNumber("911111111")).thenAnswer(i -> Mono.just(sender));
        lenient().when(walletRepository.findByPhoneNumber("922222222")).thenAnswer(i -> Mono.just(receiver));
        lenient().when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        lenient().when(yankiPaymentRepository.save(any(YankiPayment.class))).thenAnswer(i -> {
            YankiPayment payment = i.getArgument(0);
            if (payment.getId() == null) {
                payment.setId("p1");
            }
            return Mono.just(payment);
        });
        lenient().when(yankiEventPublisher.requestOperation(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Completable.complete());
        lenient().when(yankiEventPublisher.publishOutcome(any())).thenReturn(Completable.complete());
        lenient().when(debitCardReplicaRepository.findByCardNumber("CARD-S")).thenReturn(Mono.just(card("acc-s")));
        lenient().when(debitCardReplicaRepository.findByCardNumber("CARD-R")).thenReturn(Mono.just(card("acc-r")));
    }

    private DebitCardReplica card(String mainAccountId) {
        return DebitCardReplica.builder().cardId("c-" + mainAccountId).mainAccountId(mainAccountId).status("ACTIVE")
                .build();
    }

    private YankiPaymentRequest request(String amount) {
        return YankiPaymentRequest.builder().fromPhoneNumber("911111111").toPhoneNumber("922222222")
                .amount(new BigDecimal(amount)).build();
    }

    private AccountOperationResultPayload result(String operationId, String reason) {
        return AccountOperationResultPayload.builder().operationId(operationId).reason(reason).build();
    }

    private void paymentForOperation(YankiPayment payment, String operationId) {
        when(yankiPaymentRepository.findFirstByDebitOperationIdOrCreditOperationIdOrCompensationOperationId(
                operationId, operationId, operationId)).thenReturn(Mono.just(payment));
    }

    // --- Validaciones -----------------------------------------------------------

    @Test
    void sameOrUnknownPhonesAreRejected() {
        service.send(YankiPaymentRequest.builder().fromPhoneNumber("911111111").toPhoneNumber("911111111")
                        .amount(BigDecimal.ONE).build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertError(BusinessRuleException.class);
        when(walletRepository.findByPhoneNumber("999")).thenReturn(Mono.empty());
        service.send(YankiPaymentRequest.builder().fromPhoneNumber("911111111").toPhoneNumber("999")
                        .amount(BigDecimal.ONE).build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
    }

    // --- Sin tarjeta -------------------------------------------------------------

    @Test
    void walletToWalletMovesTheOwnBalancesAndCompletesAtOnce() {
        service.send(request("30")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(payment -> payment.getStatus() == PaymentStatus.COMPLETED);

        assertThat(sender.getBalance()).isEqualByComparingTo("70");
        assertThat(receiver.getBalance()).isEqualByComparingTo("35");
        verify(yankiEventPublisher).publishOutcome(any());
        verify(yankiEventPublisher, never()).requestOperation(anyString(), anyString(), anyString(), any(),
                anyString());
    }

    @Test
    void walletWithoutEnoughBalanceIsRejected() {
        service.send(request("500")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("Insufficient wallet balance"));
        verify(yankiPaymentRepository, never()).save(any());
    }

    // --- Con tarjeta --------------------------------------------------------------

    @Test
    void senderWithCardRequestsAWithdrawalFromTheMainAccountAndWaits() {
        sender.setDebitCardNumber("CARD-S");

        service.send(request("40")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(payment -> payment.getStatus() == PaymentStatus.PENDING
                        && payment.getStep() == PaymentStep.DEBIT_REQUESTED);

        verify(yankiEventPublisher).requestOperation(anyString(), eq("acc-s"), eq("WITHDRAWAL"),
                eq(new BigDecimal("40")), eq("p1"));
        assertThat(sender.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    void inactiveLinkedCardIsRejected() {
        sender.setDebitCardNumber("CARD-OFF");
        when(debitCardReplicaRepository.findByCardNumber("CARD-OFF"))
                .thenReturn(Mono.just(DebitCardReplica.builder().status("INACTIVE").build()));

        service.send(request("40")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("is not active"));
    }

    @Test
    void kafkaDownWhenStartingIsFailedAnd503() {
        sender.setDebitCardNumber("CARD-S");
        when(yankiEventPublisher.requestOperation(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Completable.error(new IllegalStateException("broker down")));

        service.send(request("40")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(ServiceUnavailableException.class);
    }

    @Test
    void debitCompletedThenCreditToAWalletCompletesThePayment() {
        YankiPayment payment = YankiPayment.builder().id("p1").fromPhoneNumber("911111111")
                .toPhoneNumber("922222222").amount(new BigDecimal("40")).status(PaymentStatus.PENDING)
                .step(PaymentStep.DEBIT_REQUESTED).senderAccountId("acc-s").debitOperationId("op-d").build();
        paymentForOperation(payment, "op-d");

        service.handleOperationResult(COMPLETED, result("op-d", null)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(receiver.getBalance()).isEqualByComparingTo("45");
    }

    @Test
    void debitRejectedFailsWithoutCompensation() {
        YankiPayment payment = YankiPayment.builder().id("p1").fromPhoneNumber("911111111")
                .toPhoneNumber("922222222").amount(new BigDecimal("40")).status(PaymentStatus.PENDING)
                .step(PaymentStep.DEBIT_REQUESTED).debitOperationId("op-d").build();
        paymentForOperation(payment, "op-d");

        service.handleOperationResult(REJECTED, result("op-d", "INSUFFICIENT_FUNDS"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        verify(yankiEventPublisher, never()).requestOperation(anyString(), anyString(), anyString(), any(),
                anyString());
    }

    @Test
    void receiverWithCardGetsADepositOnItsMainAccount() {
        receiver.setDebitCardNumber("CARD-R");

        service.send(request("30")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(payment -> payment.getStatus() == PaymentStatus.PENDING
                        && payment.getStep() == PaymentStep.CREDIT_REQUESTED);

        assertThat(sender.getBalance()).isEqualByComparingTo("70");
        verify(yankiEventPublisher).requestOperation(anyString(), eq("acc-r"), eq("DEPOSIT"),
                eq(new BigDecimal("30")), eq("p1"));
    }

    @Test
    void creditCompletedFinishesThePayment() {
        YankiPayment payment = YankiPayment.builder().id("p1").amount(BigDecimal.TEN).status(PaymentStatus.PENDING)
                .step(PaymentStep.CREDIT_REQUESTED).debitCompleted(true).creditOperationId("op-c").build();
        paymentForOperation(payment, "op-c");

        service.handleOperationResult(COMPLETED, result("op-c", null)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void creditRejectedRefundsAWalletSenderAtOnce() {
        YankiPayment payment = YankiPayment.builder().id("p1").fromPhoneNumber("911111111")
                .amount(new BigDecimal("30")).status(PaymentStatus.PENDING).step(PaymentStep.CREDIT_REQUESTED)
                .debitCompleted(true).creditOperationId("op-c").build();
        paymentForOperation(payment, "op-c");

        service.handleOperationResult(REJECTED, result("op-c", "ACCOUNT_NOT_FOUND"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(sender.getBalance()).isEqualByComparingTo("130");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void creditRejectedCompensatesACardSenderWithADeposit() {
        YankiPayment payment = YankiPayment.builder().id("p1").amount(new BigDecimal("40"))
                .status(PaymentStatus.PENDING).step(PaymentStep.CREDIT_REQUESTED).senderAccountId("acc-s")
                .debitCompleted(true).creditOperationId("op-c").build();
        paymentForOperation(payment, "op-c");

        service.handleOperationResult(REJECTED, result("op-c", "ACCOUNT_NOT_FOUND"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(payment.getStep()).isEqualTo(PaymentStep.COMPENSATING);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(yankiEventPublisher).requestOperation(eq(payment.getCompensationOperationId()), eq("acc-s"),
                eq("DEPOSIT"), eq(new BigDecimal("40")), eq("p1"));
    }

    @Test
    void compensationResultClosesThePaymentAsFailed() {
        YankiPayment payment = YankiPayment.builder().id("p1").status(PaymentStatus.PENDING)
                .step(PaymentStep.COMPENSATING).failureReason("ACCOUNT_NOT_FOUND").compensationOperationId("op-x")
                .build();
        paymentForOperation(payment, "op-x");

        service.handleOperationResult(COMPLETED, result("op-x", null)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void rejectedCompensationIsFlaggedForManualReview() {
        YankiPayment payment = YankiPayment.builder().id("p1").status(PaymentStatus.PENDING)
                .step(PaymentStep.COMPENSATING).failureReason("X").compensationOperationId("op-x").build();
        paymentForOperation(payment, "op-x");

        service.handleOperationResult(REJECTED, result("op-x", "ACCOUNT_NOT_FOUND"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(payment.getFailureReason()).isEqualTo("X/COMPENSATION_REJECTED");
    }

    @Test
    void receiverCardNotActiveTriggersCompensation() {
        receiver.setDebitCardNumber("CARD-OFF");
        when(debitCardReplicaRepository.findByCardNumber("CARD-OFF")).thenReturn(Mono.empty());

        service.send(request("30")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(payment -> payment.getStatus() == PaymentStatus.FAILED
                        && "RECEIVER_CARD_NOT_ACTIVE".equals(payment.getFailureReason()));
        assertThat(sender.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    void unrelatedOrFinishedResultsAreIgnored() {
        when(yankiPaymentRepository.findFirstByDebitOperationIdOrCreditOperationIdOrCompensationOperationId(
                "debit-card-op", "debit-card-op", "debit-card-op")).thenReturn(Mono.empty());
        YankiPayment done = YankiPayment.builder().id("p1").status(PaymentStatus.COMPLETED).step(PaymentStep.DONE)
                .creditOperationId("op-c").build();
        paymentForOperation(done, "op-c");

        service.handleOperationResult(COMPLETED, result("debit-card-op", null)).test()
                .awaitDone(1, TimeUnit.SECONDS).assertComplete();
        service.handleOperationResult(COMPLETED, result("op-c", null)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();

        verify(yankiPaymentRepository, never()).save(any());
    }

    // --- Pagos colgados -----------------------------------------------------------

    @Test
    void stalePaymentsAreExpiredCompensatingWhenTheDebitWasDone() {
        YankiPayment waitingDebit = YankiPayment.builder().id("p1").status(PaymentStatus.PENDING)
                .step(PaymentStep.DEBIT_REQUESTED).build();
        YankiPayment waitingCredit = YankiPayment.builder().id("p2").fromPhoneNumber("911111111")
                .amount(new BigDecimal("20")).status(PaymentStatus.PENDING).step(PaymentStep.CREDIT_REQUESTED)
                .debitCompleted(true).build();
        YankiPayment waitingRefund = YankiPayment.builder().id("p3").status(PaymentStatus.PENDING)
                .step(PaymentStep.COMPENSATING).failureReason("TIMEOUT").build();
        when(yankiPaymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING,
                LocalDateTime.of(2026, 10, 16, 11, 58))).thenReturn(Flux.just(waitingDebit, waitingCredit,
                waitingRefund));

        service.expireStalePayments().test().awaitDone(1, TimeUnit.SECONDS).assertValue(3L);

        assertThat(waitingDebit.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(waitingDebit.getFailureReason()).isEqualTo("TIMEOUT");
        assertThat(waitingCredit.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(sender.getBalance()).isEqualByComparingTo("120");
        assertThat(waitingRefund.getFailureReason()).isEqualTo("TIMEOUT/COMPENSATION_TIMEOUT");
    }

    @Test
    void lateDebitOfAnExpiredPaymentIsRefunded() {
        YankiPayment expired = YankiPayment.builder().id("p1").amount(BigDecimal.TEN).status(PaymentStatus.FAILED)
                .step(PaymentStep.DONE).senderAccountId("acc-s").debitOperationId("op-d").build();
        paymentForOperation(expired, "op-d");

        service.handleOperationResult(COMPLETED, result("op-d", null)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();

        verify(yankiEventPublisher).requestOperation(eq(expired.getCompensationOperationId()), eq("acc-s"),
                eq("DEPOSIT"), eq(BigDecimal.TEN), eq("p1"));
    }

    @Test
    void historyAndLookup() {
        YankiPayment payment = YankiPayment.builder().id("p1").status(PaymentStatus.COMPLETED).build();
        when(yankiPaymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Flux.just(payment));
        when(yankiPaymentRepository.findByFromPhoneNumberOrToPhoneNumberOrderByCreatedAtDesc("911111111",
                "911111111")).thenReturn(Flux.just(payment));
        when(yankiPaymentRepository.findById("p1")).thenReturn(Mono.just(payment));
        when(yankiPaymentRepository.findById("x")).thenReturn(Mono.empty());

        service.findAll(null).test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findAll("911111111").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findById("p1").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findById("x").test().awaitDone(1, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
    }
}
