package com.bank.transaction.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bank.transaction.client.AccountClient;
import com.bank.transaction.client.AccountDto;
import com.bank.transaction.client.CreditCardClient;
import com.bank.transaction.client.CreditCardDto;
import com.bank.transaction.client.CreditClient;
import com.bank.transaction.client.CreditDto;
import com.bank.transaction.config.ReportProperties;
import com.bank.transaction.dto.GeneralReportResponse;
import com.bank.transaction.dto.ProductReportResponse;
import com.bank.transaction.dto.ProductTypeSummary;
import com.bank.transaction.exception.InvalidRequestException;
import com.bank.transaction.mapper.TransactionMapper;
import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.TransactionRepository;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 16);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private CreditClient creditClient;

    @Mock
    private CreditCardClient creditCardClient;

    private ReportServiceImpl service;

    private final ReportProperties reportProperties = new ReportProperties();

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(transactionRepository, new TransactionMapper(), accountClient, creditClient,
                creditCardClient, reportProperties);
    }

    private Transaction tx(ProductType productType, String productId, TransactionType type, String amount,
                           String fee) {
        return Transaction.builder().productType(productType).productId(productId).transactionType(type)
                .amount(new BigDecimal(amount)).feeCharged(fee == null ? null : new BigDecimal(fee))
                .createdAt(DAY.atTime(10, 0)).build();
    }

    @Test
    void productReportOfAnAccountGroupsTotalsByTypeAndSumsFees() {
        when(accountClient.findById("a1")).thenReturn(Single.just(AccountDto.builder().id("a1").customerId("c1")
                .balance(new BigDecimal("1000")).build()));
        when(transactionRepository.findByProductIdAndCreatedAtBetweenOrderByCreatedAtAsc(eq("a1"), any(), any()))
                .thenReturn(Flux.just(
                        tx(ProductType.ACCOUNT, "a1", TransactionType.DEPOSIT, "10", "0"),
                        tx(ProductType.ACCOUNT, "a1", TransactionType.DEPOSIT, "10", "1.50"),
                        tx(ProductType.ACCOUNT, "a1", TransactionType.WITHDRAWAL, "20", "1.50"),
                        tx(ProductType.ACCOUNT, "a1", TransactionType.TRANSFER_OUT, "87", null)));

        ProductReportResponse report = service.productReport(ProductType.ACCOUNT, "a1", DAY, DAY).blockingGet();

        assertThat(report.getMovementCount()).isEqualTo(4);
        assertThat(report.getCurrentBalance()).isEqualByComparingTo("1000");
        assertThat(report.getCustomerId()).isEqualTo("c1");
        assertThat(report.getTotalFees()).isEqualByComparingTo("3.00");
        assertThat(report.getTotalsByType().get(TransactionType.DEPOSIT)).isEqualByComparingTo("20");
        assertThat(report.getTotalsByType().get(TransactionType.WITHDRAWAL)).isEqualByComparingTo("20");
        assertThat(report.getTotalsByType().get(TransactionType.TRANSFER_OUT)).isEqualByComparingTo("87");
        assertThat(report.getMovements()).hasSize(4);
    }

    @Test
    void productReportUsesTheRightBalanceForCreditsAndCards() {
        when(creditClient.findById("cr1")).thenReturn(Single.just(CreditDto.builder().id("cr1")
                .outstandingBalance(new BigDecimal("4500")).build()));
        when(creditCardClient.findById("k1")).thenReturn(Single.just(CreditCardDto.builder().id("k1")
                .availableBalance(new BigDecimal("950")).build()));
        when(transactionRepository.findByProductIdAndCreatedAtBetweenOrderByCreatedAtAsc(any(), any(), any()))
                .thenReturn(Flux.empty());

        service.productReport(ProductType.CREDIT, "cr1", DAY, DAY).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getCurrentBalance().compareTo(new BigDecimal("4500")) == 0
                        && r.getMovementCount() == 0 && r.getTotalFees().signum() == 0);
        service.productReport(ProductType.CREDIT_CARD, "k1", DAY, DAY).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getCurrentBalance().compareTo(new BigDecimal("950")) == 0);
    }

    @Test
    void invertedRangeIs400() {
        service.productReport(ProductType.ACCOUNT, "a1", DAY, DAY.minusDays(1)).test()
                .awaitDone(1, TimeUnit.SECONDS).assertError(InvalidRequestException.class);
        service.generalReport(DAY, DAY.minusDays(1)).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(InvalidRequestException.class);
        verifyNoInteractions(accountClient, transactionRepository);
    }

    @Test
    void generalReportCountsDistinctProductsAndMovementsPerProductType() {
        when(transactionRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Flux.just(
                        tx(ProductType.ACCOUNT, "a1", TransactionType.DEPOSIT, "10", "1"),
                        tx(ProductType.ACCOUNT, "a1", TransactionType.WITHDRAWAL, "5", null),
                        tx(ProductType.ACCOUNT, "a2", TransactionType.DEPOSIT, "7", "0"),
                        tx(ProductType.CREDIT_CARD, "k1", TransactionType.CHARGE, "3", "0")));

        GeneralReportResponse report = service.generalReport(DAY, DAY).blockingGet();

        assertThat(report.getTotalMovements()).isEqualTo(4);
        assertThat(report.getByProductType()).extracting(ProductTypeSummary::getProductType)
                .containsExactly(ProductType.ACCOUNT, ProductType.CREDIT_CARD);
        ProductTypeSummary accounts = report.getByProductType().get(0);
        assertThat(accounts.getProductsWithMovements()).isEqualTo(2);
        assertThat(accounts.getMovementCount()).isEqualTo(3);
        assertThat(accounts.getTotalAmount()).isEqualByComparingTo("22");
        assertThat(accounts.getTotalFees()).isEqualByComparingTo("1");
        assertThat(accounts.getTotalsByType().get(TransactionType.DEPOSIT)).isEqualByComparingTo("17");
    }

    @Test
    void lastTenMovementsOfACreditCardMostRecentFirst() {
        when(creditCardClient.findById("k1")).thenReturn(Single.just(CreditCardDto.builder().id("k1").build()));
        when(transactionRepository.findTop10ByProductTypeAndProductIdOrderByCreatedAtDesc(ProductType.CREDIT_CARD,
                "k1")).thenReturn(Flux.fromStream(IntStream.iterate(12, i -> i - 1).limit(10)
                .mapToObj(i -> tx(ProductType.CREDIT_CARD, "k1", TransactionType.CHARGE, String.valueOf(i), "0"))));

        service.lastMovements(ProductType.CREDIT_CARD, "k1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValueCount(10)
                .assertValueAt(0, r -> r.getAmount().compareTo(new BigDecimal("12")) == 0)
                .assertValueAt(9, r -> r.getAmount().compareTo(new BigDecimal("3")) == 0);
    }

    @Test
    void lastMovementsOfAnUnsupportedProductTypeIs400() {
        service.lastMovements(ProductType.ACCOUNT, "a1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error instanceof InvalidRequestException
                        && error.getMessage().contains("CREDIT_CARD"));
        verifyNoInteractions(accountClient, transactionRepository);
    }

    // --- Parte III: tarjeta de debito -----------------------------------------

    @Test
    void lastMovementsOfADebitCardReadTheLocalMovementsWithoutRest() {
        when(transactionRepository.findTop10ByProductTypeAndProductIdOrderByCreatedAtDesc(ProductType.DEBIT_CARD,
                "d1")).thenReturn(Flux.just(tx(ProductType.DEBIT_CARD, "d1", TransactionType.DEBIT_PAYMENT, "80", "0")));
        reportProperties.setLastMovementsProductTypes(java.util.EnumSet.of(ProductType.CREDIT_CARD,
                ProductType.DEBIT_CARD));

        service.lastMovements(ProductType.DEBIT_CARD, "d1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(movement -> movement.getTransactionType() == TransactionType.DEBIT_PAYMENT);
    }

    @Test
    void productReportIsNotAvailableForDebitCards() {
        service.productReport(ProductType.DEBIT_CARD, "d1", DAY, DAY).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(com.bank.transaction.exception.InvalidRequestException.class);
    }
}
