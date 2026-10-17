package com.bank.transaction.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.transaction.client.AccountClient;
import com.bank.transaction.client.AccountDto;
import com.bank.transaction.client.AccountOperationRequest;
import com.bank.transaction.client.AccountOperationType;
import com.bank.transaction.client.CreditCardClient;
import com.bank.transaction.client.CreditCardDto;
import com.bank.transaction.client.CreditClient;
import com.bank.transaction.client.CustomerClient;
import com.bank.transaction.client.CustomerDto;
import com.bank.transaction.client.CreditDto;
import com.bank.transaction.dto.AccountTransactionRequest;
import com.bank.transaction.dto.ChargeRequest;
import com.bank.transaction.dto.PaymentRequest;
import com.bank.transaction.dto.TransactionUpdateRequest;
import com.bank.transaction.exception.BusinessRuleException;
import com.bank.transaction.exception.ResourceNotFoundException;
import com.bank.transaction.mapper.TransactionMapper;
import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.TransactionRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private CreditClient creditClient;

    @Mock
    private CreditCardClient creditCardClient;

    @Mock
    private AccountMovementRules accountMovementRules;

    @Mock
    private CustomerClient customerClient;

    private TransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TransactionServiceImpl(transactionRepository, new TransactionMapper(), accountClient,
                creditClient, creditCardClient, customerClient, accountMovementRules);
    }

    private void saveReturnsArgument() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            saved.setId("t1");
            return Mono.just(saved);
        });
    }

    private AccountTransactionRequest movement(String amount) {
        return AccountTransactionRequest.builder().accountId("a1").amount(new BigDecimal(amount)).description("d")
                .build();
    }

    @Test
    void depositChargesTheFeeAndRecordsItInTheTransaction() {
        AccountDto account = AccountDto.builder().id("a1").customerId("c1").build();
        when(accountClient.findById("a1")).thenReturn(Single.just(account));
        when(accountMovementRules.validateHardRules(account)).thenReturn(Completable.complete());
        when(accountMovementRules.calculateFee(account)).thenReturn(Single.just(new BigDecimal("1.50")));
        when(accountClient.applyBalanceOperation(eq("a1"), any())).thenReturn(Completable.complete());
        saveReturnsArgument();

        service.deposit(movement("10")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getTransactionType() == TransactionType.DEPOSIT
                        && r.getFeeCharged().compareTo(new BigDecimal("1.50")) == 0
                        && "c1".equals(r.getCustomerId()));

        ArgumentCaptor<AccountOperationRequest> operation = ArgumentCaptor.forClass(AccountOperationRequest.class);
        verify(accountClient).applyBalanceOperation(eq("a1"), operation.capture());
        assertThat(operation.getValue().getOperation()).isEqualTo(AccountOperationType.DEPOSIT);
        assertThat(operation.getValue().getFee()).isEqualByComparingTo("1.50");
    }

    @Test
    void withdrawalIsNotRecordedWhenTheBalanceOperationFails() {
        AccountDto account = AccountDto.builder().id("a1").customerId("c1").build();
        when(accountClient.findById("a1")).thenReturn(Single.just(account));
        when(accountMovementRules.validateHardRules(account)).thenReturn(Completable.complete());
        when(accountMovementRules.calculateFee(account)).thenReturn(Single.just(BigDecimal.ZERO));
        when(accountClient.applyBalanceOperation(eq("a1"), any()))
                .thenReturn(Completable.error(new BusinessRuleException("Insufficient funds")));

        service.withdrawal(movement("999")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void hardRuleViolationStopsBeforeMovingMoney() {
        AccountDto account = AccountDto.builder().id("a1").build();
        when(accountClient.findById("a1")).thenReturn(Single.just(account));
        when(accountMovementRules.validateHardRules(account))
                .thenReturn(Completable.error(new BusinessRuleException("monthly movement limit")));
        when(accountMovementRules.calculateFee(account)).thenReturn(Single.just(BigDecimal.ZERO));

        service.deposit(movement("10")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
        verify(accountClient, never()).applyBalanceOperation(any(), any());
    }

    @Test
    void paymentsOfCreditsAndCardsAndRejectionForAccounts() {
        when(creditClient.findById("cr1")).thenReturn(Single.just(CreditDto.builder().id("cr1").customerId("c1")
                .build()));
        when(creditClient.applyBalanceOperation(eq("cr1"), any())).thenReturn(Completable.complete());
        when(creditCardClient.findById("k1")).thenReturn(Single.just(CreditCardDto.builder().id("k1")
                .customerId("c1").build()));
        when(creditCardClient.applyBalanceOperation(eq("k1"), any())).thenReturn(Completable.complete());
        saveReturnsArgument();

        service.payment(PaymentRequest.builder().productType(ProductType.CREDIT).productId("cr1")
                        .amount(BigDecimal.TEN).build()).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getProductType() == ProductType.CREDIT
                        && r.getTransactionType() == TransactionType.PAYMENT);
        service.payment(PaymentRequest.builder().productType(ProductType.CREDIT_CARD).productId("k1")
                        .amount(BigDecimal.TEN).build()).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getProductType() == ProductType.CREDIT_CARD);
        service.payment(PaymentRequest.builder().productType(ProductType.ACCOUNT).productId("a1")
                        .amount(BigDecimal.TEN).build()).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("only apply to CREDIT"));
    }

    @Test
    void chargeOnCreditCard() {
        when(creditCardClient.findById("k1")).thenReturn(Single.just(CreditCardDto.builder().id("k1")
                .customerId("c1").build()));
        when(creditCardClient.applyBalanceOperation(eq("k1"), any())).thenReturn(Completable.complete());
        saveReturnsArgument();

        service.charge(ChargeRequest.builder().creditCardId("k1").amount(BigDecimal.ONE).build()).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> r.getTransactionType() == TransactionType.CHARGE
                        && r.getFeeCharged().signum() == 0);
    }

    @Test
    void queriesUpdateAndDelete() {
        Transaction transaction = Transaction.builder().id("t1").productId("a1").amount(BigDecimal.ONE)
                .description("old").build();
        when(transactionRepository.findByProductId("a1")).thenReturn(Flux.just(transaction));
        when(transactionRepository.findAll()).thenReturn(Flux.just(transaction, transaction));
        when(transactionRepository.findById("t1")).thenReturn(Mono.just(transaction));
        when(transactionRepository.findById("x")).thenReturn(Mono.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionRepository.delete(transaction)).thenReturn(Mono.empty());

        service.findAll("a1").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findAll(null).test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(2);
        service.findById("t1").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findById("x").test().awaitDone(1, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
        service.update("t1", TransactionUpdateRequest.builder().description("new").build()).test()
                .awaitDone(1, TimeUnit.SECONDS).assertValue(r -> "new".equals(r.getDescription()));
        service.delete("t1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
    }

    // --- Parte III: pago de terceros ------------------------------------------

    @Test
    void ownerPaymentRecordsThePayerWithoutThirdPartyFlag() {
        when(creditClient.findById("cr1")).thenReturn(Single.just(CreditDto.builder().id("cr1").customerId("c1")
                .build()));
        when(creditClient.applyBalanceOperation(eq("cr1"), any())).thenReturn(Completable.complete());
        saveReturnsArgument();

        service.payment(PaymentRequest.builder().productType(ProductType.CREDIT).productId("cr1")
                        .amount(BigDecimal.TEN).build()).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> "c1".equals(r.getPayerCustomerId()) && !r.getThirdParty()
                        && "c1".equals(r.getCustomerId()));
        verify(customerClient, never()).findById(any());
    }

    @Test
    void thirdPartyPaysACreditCardAfterValidatingThePayer() {
        when(creditCardClient.findById("k1")).thenReturn(Single.just(CreditCardDto.builder().id("k1")
                .customerId("c1").build()));
        when(customerClient.findById("c2")).thenReturn(Single.just(CustomerDto.builder().id("c2").build()));
        when(creditCardClient.applyBalanceOperation(eq("k1"), any())).thenReturn(Completable.complete());
        saveReturnsArgument();

        service.payment(PaymentRequest.builder().productType(ProductType.CREDIT_CARD).productId("k1")
                        .amount(BigDecimal.TEN).payerCustomerId("c2").build()).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(r -> "c2".equals(r.getPayerCustomerId()) && r.getThirdParty()
                        && "c1".equals(r.getCustomerId()));
    }

    @Test
    void unknownPayerIs404AndNothingIsCharged() {
        when(creditClient.findById("cr1")).thenReturn(Single.just(CreditDto.builder().id("cr1").customerId("c1")
                .build()));
        when(customerClient.findById("ghost"))
                .thenReturn(Single.error(new ResourceNotFoundException("Customer not found with id ghost")));

        service.payment(PaymentRequest.builder().productType(ProductType.CREDIT).productId("cr1")
                        .amount(BigDecimal.TEN).payerCustomerId("ghost").build()).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(ResourceNotFoundException.class);
        verify(creditClient, never()).applyBalanceOperation(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void debitCardProductsCannotBePaidThroughThisEndpoint() {
        service.payment(PaymentRequest.builder().productType(ProductType.DEBIT_CARD).productId("d1")
                        .amount(BigDecimal.TEN).build()).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
    }
}
