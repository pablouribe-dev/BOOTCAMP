package com.bank.debitcard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.debitcard.config.DebitCardProperties;
import com.bank.debitcard.dto.DebitCardRequest;
import com.bank.debitcard.event.DebitCardEventPublisher;
import com.bank.debitcard.exception.BusinessRuleException;
import com.bank.debitcard.exception.ResourceNotFoundException;
import com.bank.debitcard.mapper.DebitCardMapper;
import com.bank.debitcard.model.AccountReplica;
import com.bank.debitcard.model.CardStatus;
import com.bank.debitcard.model.DebitCard;
import com.bank.debitcard.repository.AccountReplicaRepository;
import com.bank.debitcard.repository.CustomerReplicaRepository;
import com.bank.debitcard.repository.DebitCardRepository;
import com.bank.debitcard.service.OverdueDebtService;
import io.reactivex.rxjava3.core.Completable;
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
class DebitCardServiceImplTest {

    @Mock
    private DebitCardRepository debitCardRepository;

    @Mock
    private CustomerReplicaRepository customerReplicaRepository;

    @Mock
    private AccountReplicaRepository accountReplicaRepository;

    @Mock
    private OverdueDebtService overdueDebtService;

    @Mock
    private DebitCardEventPublisher debitCardEventPublisher;

    private DebitCardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DebitCardServiceImpl(debitCardRepository, customerReplicaRepository, accountReplicaRepository,
                overdueDebtService, debitCardEventPublisher, new DebitCardMapper(), new DebitCardProperties());
        lenient().when(overdueDebtService.ensureNoOverdueDebt(any())).thenReturn(Completable.complete());
        lenient().when(debitCardEventPublisher.publishUpserted(any())).thenReturn(Completable.complete());
        lenient().when(debitCardEventPublisher.publishDeleted(any())).thenReturn(Completable.complete());
        lenient().when(customerReplicaRepository.existsById("c1")).thenReturn(Mono.just(true));
        lenient().when(accountReplicaRepository.findById("a1")).thenReturn(Mono.just(account("a1", "c1")));
        lenient().when(accountReplicaRepository.findById("a2")).thenReturn(Mono.just(account("a2", "c1")));
        lenient().when(accountReplicaRepository.findById("other")).thenReturn(Mono.just(account("other", "c2")));
        lenient().when(accountReplicaRepository.findById("ghost")).thenReturn(Mono.empty());
        lenient().when(debitCardRepository.existsByCardNumber(anyString())).thenReturn(Mono.just(false));
        lenient().when(debitCardRepository.save(any(DebitCard.class))).thenAnswer(invocation -> {
            DebitCard card = invocation.getArgument(0);
            if (card.getId() == null) {
                card.setId("d1");
            }
            return Mono.just(card);
        });
    }

    private AccountReplica account(String id, String customerId) {
        return AccountReplica.builder().accountId(id).customerId(customerId).build();
    }

    private DebitCardRequest request(List<String> accounts, String main) {
        return DebitCardRequest.builder().customerId("c1").accountIds(accounts).mainAccountId(main).build();
    }

    private DebitCard card(List<String> accounts, String main) {
        return DebitCard.builder().id("d1").cardNumber("5555000011112222").customerId("c1")
                .accountIds(new ArrayList<>(accounts)).mainAccountId(main).status(CardStatus.ACTIVE).build();
    }

    @Test
    void createsAnActiveCardWithAGeneratedNumberAndPublishesIt() {
        service.create(request(List.of("a1", "a2"), "a2")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(card -> card.getCardNumber().matches("5555\\d{12}") && card.getStatus() == CardStatus.ACTIVE
                        && "a2".equals(card.getMainAccountId()) && card.getAccountIds().equals(List.of("a1", "a2")));
        verify(debitCardEventPublisher).publishUpserted(any());
    }

    @Test
    void customerMustExistInTheLocalReplica() {
        when(customerReplicaRepository.existsById("c1")).thenReturn(Mono.just(false));

        service.create(request(List.of("a1"), "a1")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error instanceof ResourceNotFoundException
                        && error.getMessage().contains("Customer"));
    }

    @Test
    void accountsMustExistAndBelongToTheCustomer() {
        service.create(request(List.of("a1", "ghost"), "a1")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(ResourceNotFoundException.class);
        service.create(request(List.of("a1", "other"), "a1")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error instanceof BusinessRuleException
                        && error.getMessage().contains("does not belong"));
        verify(debitCardRepository, never()).save(any());
    }

    @Test
    void mainAccountMustBeLinkedAndAccountsCannotRepeat() {
        service.create(request(List.of("a1"), "a2")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("mainAccountId must be one of accountIds"));
        service.create(request(List.of("a1", "a1"), "a1")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("duplicates"));
    }

    @Test
    void customerWithOverdueDebtCannotGetACard() {
        when(overdueDebtService.ensureNoOverdueDebt("c1"))
                .thenReturn(Completable.error(new BusinessRuleException("overdue debt")));

        service.create(request(List.of("a1"), "a1")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
        verify(debitCardRepository, never()).save(any());
    }

    @Test
    void findAllByCustomerAndById() {
        when(debitCardRepository.findAll()).thenReturn(Flux.just(card(List.of("a1"), "a1")));
        when(debitCardRepository.findByCustomerId("c1")).thenReturn(Flux.just(card(List.of("a1"), "a1")));
        when(debitCardRepository.findById("d1")).thenReturn(Mono.just(card(List.of("a1"), "a1")));
        when(debitCardRepository.findById("x")).thenReturn(Mono.empty());

        service.findAll(null).test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findAll("c1").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findById("d1").test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findById("x").test().awaitDone(1, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesAccountsAndStatusButNotTheCustomer() {
        when(debitCardRepository.findById("d1")).thenReturn(Mono.just(card(List.of("a1"), "a1")));

        DebitCardRequest update = request(List.of("a1", "a2"), "a2");
        update.setStatus(CardStatus.INACTIVE);
        service.update("d1", update).test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(card -> card.getAccountIds().size() == 2 && card.getStatus() == CardStatus.INACTIVE);

        DebitCardRequest otherCustomer = request(List.of("a1"), "a1");
        otherCustomer.setCustomerId("c2");
        service.update("d1", otherCustomer).test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("customerId cannot be modified"));
    }

    @Test
    void deletePublishesTheDeletion() {
        DebitCard card = card(List.of("a1"), "a1");
        when(debitCardRepository.findById("d1")).thenReturn(Mono.just(card));
        when(debitCardRepository.delete(card)).thenReturn(Mono.empty());

        service.delete("d1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        verify(debitCardEventPublisher).publishDeleted(card);
    }

    @Test
    void addAccountAppendsItAndRejectsRepeatedOrForeignAccounts() {
        when(debitCardRepository.findById("d1")).thenReturn(Mono.just(card(List.of("a1"), "a1")));

        service.addAccount("d1", "a2").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(card -> card.getAccountIds().equals(List.of("a1", "a2"))
                        && "a1".equals(card.getMainAccountId()));
        service.addAccount("d1", "a1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("already linked"));
        service.addAccount("d1", "other").test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(BusinessRuleException.class);
    }

    @Test
    void addingAnAccountToAnInactiveCardReactivatesIt() {
        DebitCard inactive = card(List.of(), null);
        inactive.setStatus(CardStatus.INACTIVE);
        when(debitCardRepository.findById("d1")).thenReturn(Mono.just(inactive));

        service.addAccount("d1", "a1").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(card -> card.getStatus() == CardStatus.ACTIVE && "a1".equals(card.getMainAccountId()));
    }

    @Test
    void changeMainAccountOnlyToALinkedAccount() {
        when(debitCardRepository.findById("d1")).thenReturn(Mono.just(card(List.of("a1", "a2"), "a1")));

        service.changeMainAccount("d1", "a2").test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(card -> "a2".equals(card.getMainAccountId()));
        service.changeMainAccount("d1", "other").test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error.getMessage().contains("not linked"));
    }

    @Test
    void deletedMainAccountIsReplacedByTheFirstRemainingOne() {
        DebitCard card = card(List.of("a1", "a2"), "a1");
        when(debitCardRepository.findByAccountIdsContaining("a1")).thenReturn(Flux.just(card));

        service.removeDeletedAccount("a1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(card.getAccountIds()).containsExactly("a2");
        assertThat(card.getMainAccountId()).isEqualTo("a2");
        assertThat(card.getStatus()).isEqualTo(CardStatus.ACTIVE);
        verify(debitCardEventPublisher).publishUpserted(card);
    }

    @Test
    void cardWithoutAccountsBecomesInactive() {
        DebitCard card = card(List.of("a1"), "a1");
        when(debitCardRepository.findByAccountIdsContaining("a1")).thenReturn(Flux.just(card));

        service.removeDeletedAccount("a1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(card.getAccountIds()).isEmpty();
        assertThat(card.getMainAccountId()).isNull();
        assertThat(card.getStatus()).isEqualTo(CardStatus.INACTIVE);
    }

    @Test
    void removingANonMainAccountKeepsTheMainOne() {
        DebitCard card = card(List.of("a1", "a2"), "a1");
        when(debitCardRepository.findByAccountIdsContaining("a2")).thenReturn(Flux.just(card));

        service.removeDeletedAccount("a2").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        assertThat(card.getMainAccountId()).isEqualTo("a1");
    }
}
