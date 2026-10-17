package com.bank.transaction.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.transaction.client.AccountClient;
import com.bank.transaction.client.AccountDto;
import com.bank.transaction.client.AccountOperationRequest;
import com.bank.transaction.client.AccountOperationType;
import com.bank.transaction.dto.TransferRequest;
import com.bank.transaction.exception.BusinessRuleException;
import com.bank.transaction.exception.ResourceNotFoundException;
import com.bank.transaction.exception.ServiceUnavailableException;
import com.bank.transaction.mapper.TransactionMapper;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.model.TransferType;
import com.bank.transaction.repository.TransactionRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    private static final BigDecimal AMOUNT = new BigDecimal("100");

    @Mock
    private AccountClient accountClient;

    @Mock
    private AccountMovementRules accountMovementRules;

    @Mock
    private TransactionRepository transactionRepository;

    private TransferServiceImpl service;

    private final AccountDto source = AccountDto.builder().id("src").accountNumber("111").customerId("c1").build();

    @BeforeEach
    void setUp() {
        service = new TransferServiceImpl(accountClient, accountMovementRules, transactionRepository,
                new TransactionMapper());
    }

    private TransferRequest request(String destinationNumber) {
        return TransferRequest.builder().sourceAccountId("src").destinationAccountNumber(destinationNumber)
                .amount(AMOUNT).description("pago").build();
    }

    private void accounts(AccountDto destination) {
        when(accountClient.findById("src")).thenReturn(Single.just(source));
        when(accountClient.findByAccountNumber(destination.getAccountNumber())).thenReturn(Single.just(destination));
        when(accountMovementRules.validateHardRules(any())).thenReturn(Completable.complete());
    }

    private static AccountOperationRequest op(AccountOperationType type) {
        return argThat(request -> request != null && request.getOperation() == type
                && request.getAmount().compareTo(AMOUNT) == 0 && request.getFee().signum() == 0);
    }

    @SuppressWarnings("unchecked")
    private void saveAllReturnsArgument() {
        when(transactionRepository.saveAll(any(Publisher.class)))
                .thenAnswer(invocation -> Flux.from(invocation.<Publisher<Transaction>>getArgument(0)));
    }

    @Test
    void thirdPartyTransferMovesMoneyAndSavesTwoLinkedMovements() {
        accounts(AccountDto.builder().id("dst").accountNumber("222").customerId("c2").build());
        when(accountClient.applyBalanceOperation(eq("src"), op(AccountOperationType.WITHDRAWAL)))
                .thenReturn(Completable.complete());
        when(accountClient.applyBalanceOperation(eq("dst"), op(AccountOperationType.DEPOSIT)))
                .thenReturn(Completable.complete());
        saveAllReturnsArgument();

        service.transfer(request("222")).test().awaitDone(1, TimeUnit.SECONDS).assertValue(response ->
                response.getTransferType() == TransferType.THIRD_PARTY
                        && response.getOutgoing().getTransactionType() == TransactionType.TRANSFER_OUT
                        && "src".equals(response.getOutgoing().getProductId())
                        && response.getIncoming().getTransactionType() == TransactionType.TRANSFER_IN
                        && "dst".equals(response.getIncoming().getProductId())
                        && response.getTransferId().equals(response.getOutgoing().getTransferId())
                        && response.getTransferId().equals(response.getIncoming().getTransferId())
                        && response.getOutgoing().getFeeCharged().signum() == 0);
    }

    @Test
    void ownTransferBetweenAccountsOfTheSameCustomer() {
        accounts(AccountDto.builder().id("dst").accountNumber("222").customerId("c1").build());
        when(accountClient.applyBalanceOperation(any(), any())).thenReturn(Completable.complete());
        saveAllReturnsArgument();

        service.transfer(request("222")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> response.getTransferType() == TransferType.OWN);
    }

    @Test
    void sameSourceAndDestinationIsRejected() {
        when(accountClient.findById("src")).thenReturn(Single.just(source));
        when(accountClient.findByAccountNumber("111")).thenReturn(Single.just(source));

        service.transfer(request("111")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("must be different"));
        verify(accountClient, never()).applyBalanceOperation(any(), any());
    }

    @Test
    void unknownDestinationIs404() {
        when(accountClient.findById("src")).thenReturn(Single.just(source));
        when(accountClient.findByAccountNumber("999"))
                .thenReturn(Single.error(new ResourceNotFoundException("Account not found with number 999")));

        service.transfer(request("999")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(ResourceNotFoundException.class);
    }

    @Test
    void insufficientFundsStopsBeforeTheDepositAndSavesNothing() {
        accounts(AccountDto.builder().id("dst").accountNumber("222").customerId("c2").build());
        when(accountClient.applyBalanceOperation(eq("src"), op(AccountOperationType.WITHDRAWAL)))
                .thenReturn(Completable.error(new BusinessRuleException("Insufficient funds in account 111")));

        service.transfer(request("222")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("Insufficient funds"));
        verify(accountClient, never()).applyBalanceOperation(eq("dst"), any());
        verify(transactionRepository, never()).saveAll(any(Publisher.class));
    }

    @Test
    void failedDepositIsCompensatedAndNothingIsSaved() {
        accounts(AccountDto.builder().id("dst").accountNumber("222").customerId("c2").build());
        when(accountClient.applyBalanceOperation(eq("src"), op(AccountOperationType.WITHDRAWAL)))
                .thenReturn(Completable.complete());
        when(accountClient.applyBalanceOperation(eq("dst"), op(AccountOperationType.DEPOSIT)))
                .thenReturn(Completable.error(new ServiceUnavailableException("bank-account-service is not available")));
        when(accountClient.applyBalanceOperation(eq("src"), op(AccountOperationType.DEPOSIT)))
                .thenReturn(Completable.complete());

        service.transfer(request("222")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(ServiceUnavailableException.class);
        verify(accountClient).applyBalanceOperation(eq("src"), op(AccountOperationType.DEPOSIT));
        verify(transactionRepository, never()).saveAll(any(Publisher.class));
    }

    @Test
    void failedCompensationStillReturnsTheOriginalError() {
        accounts(AccountDto.builder().id("dst").accountNumber("222").customerId("c2").build());
        when(accountClient.applyBalanceOperation(eq("src"), op(AccountOperationType.WITHDRAWAL)))
                .thenReturn(Completable.complete());
        when(accountClient.applyBalanceOperation(eq("dst"), op(AccountOperationType.DEPOSIT)))
                .thenReturn(Completable.error(new BusinessRuleException("deposit rejected")));
        when(accountClient.applyBalanceOperation(eq("src"), op(AccountOperationType.DEPOSIT)))
                .thenReturn(Completable.error(new ServiceUnavailableException("down")));

        service.transfer(request("222")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error instanceof BusinessRuleException
                        && error.getMessage().equals("deposit rejected"));
        verify(transactionRepository, never()).saveAll(any(Publisher.class));
    }

    @Test
    void hardRulesApplyToTransfers() {
        when(accountClient.findById("src")).thenReturn(Single.just(source));
        when(accountClient.findByAccountNumber("222"))
                .thenReturn(Single.just(AccountDto.builder().id("dst").accountNumber("222").customerId("c2").build()));
        when(accountMovementRules.validateHardRules(any()))
                .thenReturn(Completable.error(new BusinessRuleException("monthly movement limit")));

        service.transfer(request("222")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
        verify(accountClient, never()).applyBalanceOperation(any(), any());
    }
}
