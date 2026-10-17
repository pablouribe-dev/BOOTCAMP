package com.bank.transaction.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.transaction.event.AccountOperationResultPayload;
import com.bank.transaction.mapper.TransactionMapper;
import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionOrigin;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class EventMovementServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    private EventMovementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EventMovementServiceImpl(transactionRepository, new TransactionMapper());
    }

    private AccountOperationResultPayload result(String origin, String operation) {
        return AccountOperationResultPayload.builder().operationId("op-1").accountId("a1").customerId("c1")
                .operation(operation).amount(new BigDecimal("40")).origin(origin).reference("ref-1").build();
    }

    private Transaction saved() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private void newOperation() {
        when(transactionRepository.existsByOperationId("op-1")).thenReturn(Mono.just(false));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void debitCardPaymentIsRecordedOnTheCard() {
        newOperation();

        service.recordCompletedOperation("e-1", result("DEBIT_CARD", "WITHDRAWAL"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        Transaction transaction = saved();
        assertThat(transaction.getProductType()).isEqualTo(ProductType.DEBIT_CARD);
        assertThat(transaction.getProductId()).isEqualTo("ref-1");
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.DEBIT_PAYMENT);
        assertThat(transaction.getAccountId()).isEqualTo("a1");
        assertThat(transaction.getOrigin()).isEqualTo(TransactionOrigin.DEBIT_CARD);
        assertThat(transaction.getEventId()).isEqualTo("e-1");
    }

    @Test
    void yankiMovementIsRecordedOnTheAccount() {
        newOperation();

        service.recordCompletedOperation("e-1", result("YANKI", "DEPOSIT"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        Transaction transaction = saved();
        assertThat(transaction.getProductType()).isEqualTo(ProductType.ACCOUNT);
        assertThat(transaction.getProductId()).isEqualTo("a1");
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transaction.getOrigin()).isEqualTo(TransactionOrigin.YANKI);
    }

    @Test
    void republishedResultOfTheSameOperationIsNotDuplicated() {
        when(transactionRepository.existsByOperationId("op-1")).thenReturn(Mono.just(true));

        service.recordCompletedOperation("e-2", result("YANKI", "DEPOSIT"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void unknownOrApiOriginsAreIgnored() {
        service.recordCompletedOperation("e-1", result("SOMETHING", "DEPOSIT"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        service.recordCompletedOperation("e-1", result("API", "DEPOSIT"))
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        verify(transactionRepository, never()).save(any());
    }
}
