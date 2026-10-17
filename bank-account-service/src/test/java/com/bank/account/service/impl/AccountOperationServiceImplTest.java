package com.bank.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.account.dto.BalanceOperationRequest;
import com.bank.account.dto.BalanceResponse;
import com.bank.account.event.AccountEventPublisher;
import com.bank.account.event.AccountOperationRequestedPayload;
import com.bank.account.event.AccountOperationResultPayload;
import com.bank.account.exception.BusinessRuleException;
import com.bank.account.exception.ResourceNotFoundException;
import com.bank.account.model.BalanceOperationType;
import com.bank.account.model.ProcessedOperation;
import com.bank.account.repository.ProcessedOperationRepository;
import com.bank.account.service.AccountService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AccountOperationServiceImplTest {

    @Mock
    private AccountService accountService;

    @Mock
    private ProcessedOperationRepository processedOperationRepository;

    @Mock
    private AccountEventPublisher accountEventPublisher;

    @InjectMocks
    private AccountOperationServiceImpl service;

    private final AccountOperationRequestedPayload withdrawal = AccountOperationRequestedPayload.builder()
            .operationId("op-1").accountId("a1").operation(BalanceOperationType.WITHDRAWAL)
            .amount(new BigDecimal("40")).origin("DEBIT_CARD").reference("card-1").build();

    private void newOperation() {
        when(processedOperationRepository.findById("op-1")).thenReturn(Mono.empty());
        when(processedOperationRepository.save(any(ProcessedOperation.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(accountEventPublisher.publishOperationResult(anyString(), any())).thenReturn(Completable.complete());
    }

    @Test
    void appliesTheOperationWithoutFeeAndPublishesCompleted() {
        newOperation();
        when(accountService.applyBalanceOperation(eq("a1"), any())).thenReturn(Single.just(BalanceResponse.builder()
                .accountId("a1").customerId("c1").balance(new BigDecimal("60")).build()));

        service.process(withdrawal).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        ArgumentCaptor<BalanceOperationRequest> operation = ArgumentCaptor.forClass(BalanceOperationRequest.class);
        verify(accountService).applyBalanceOperation(eq("a1"), operation.capture());
        assertThat(operation.getValue().getFee()).isEqualByComparingTo("0");
        ArgumentCaptor<AccountOperationResultPayload> result =
                ArgumentCaptor.forClass(AccountOperationResultPayload.class);
        verify(accountEventPublisher).publishOperationResult(eq("ACCOUNT_OPERATION_COMPLETED"), result.capture());
        assertThat(result.getValue().getBalanceAfter()).isEqualByComparingTo("60");
        assertThat(result.getValue().getCustomerId()).isEqualTo("c1");
        assertThat(result.getValue().getReference()).isEqualTo("card-1");
    }

    @Test
    void insufficientFundsIsPublishedAsRejected() {
        newOperation();
        when(accountService.applyBalanceOperation(eq("a1"), any()))
                .thenReturn(Single.error(new BusinessRuleException("Insufficient funds")));

        service.process(withdrawal).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        ArgumentCaptor<AccountOperationResultPayload> result =
                ArgumentCaptor.forClass(AccountOperationResultPayload.class);
        verify(accountEventPublisher).publishOperationResult(eq("ACCOUNT_OPERATION_REJECTED"), result.capture());
        assertThat(result.getValue().getReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void missingAccountIsPublishedAsRejected() {
        newOperation();
        when(accountService.applyBalanceOperation(eq("a1"), any()))
                .thenReturn(Single.error(new ResourceNotFoundException("Account not found")));

        service.process(withdrawal).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        ArgumentCaptor<AccountOperationResultPayload> result =
                ArgumentCaptor.forClass(AccountOperationResultPayload.class);
        verify(accountEventPublisher).publishOperationResult(eq("ACCOUNT_OPERATION_REJECTED"), result.capture());
        assertThat(result.getValue().getReason()).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void unexpectedErrorsPropagateSoKafkaRetries() {
        when(processedOperationRepository.findById("op-1")).thenReturn(Mono.empty());
        when(accountService.applyBalanceOperation(eq("a1"), any()))
                .thenReturn(Single.error(new IllegalStateException("mongo down")));

        service.process(withdrawal).test().awaitDone(1, TimeUnit.SECONDS).assertError(IllegalStateException.class);
        verify(processedOperationRepository, never()).save(any());
    }

    @Test
    void repeatedOperationRepublishesTheStoredResultWithoutMovingTheBalanceAgain() {
        AccountOperationResultPayload stored = AccountOperationResultPayload.builder().operationId("op-1")
                .accountId("a1").balanceAfter(new BigDecimal("60")).build();
        when(processedOperationRepository.findById("op-1")).thenReturn(Mono.just(ProcessedOperation.builder()
                .operationId("op-1").resultType("ACCOUNT_OPERATION_COMPLETED").result(stored).build()));
        when(accountEventPublisher.publishOperationResult("ACCOUNT_OPERATION_COMPLETED", stored))
                .thenReturn(Completable.complete());

        service.process(withdrawal).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        verify(accountService, never()).applyBalanceOperation(any(), any());
        verify(accountEventPublisher).publishOperationResult("ACCOUNT_OPERATION_COMPLETED", stored);
    }
}
