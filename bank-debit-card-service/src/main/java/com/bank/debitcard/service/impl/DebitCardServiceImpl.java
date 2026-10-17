package com.bank.debitcard.service.impl;

import com.bank.debitcard.adapter.ReactorRxAdapter;
import com.bank.debitcard.config.DebitCardProperties;
import com.bank.debitcard.dto.DebitCardRequest;
import com.bank.debitcard.dto.DebitCardResponse;
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
import com.bank.debitcard.service.DebitCardService;
import com.bank.debitcard.service.OverdueDebtService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Implementacion de las reglas de las tarjetas de debito.
 *
 * <p>Un fallo al publicar el snapshot no revierte la operacion: se registra
 * con ERROR (lo hace el publicador).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DebitCardServiceImpl implements DebitCardService {

    /** Largo del numero de tarjeta. */
    private static final int CARD_NUMBER_LENGTH = 16;

    private static final Random RANDOM = new SecureRandom();

    private final DebitCardRepository debitCardRepository;
    private final CustomerReplicaRepository customerReplicaRepository;
    private final AccountReplicaRepository accountReplicaRepository;
    private final OverdueDebtService overdueDebtService;
    private final DebitCardEventPublisher debitCardEventPublisher;
    private final DebitCardMapper debitCardMapper;
    private final DebitCardProperties debitCardProperties;

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<DebitCardResponse> create(DebitCardRequest request) {
        log.info("Issuing a debit card for customer {} with accounts {}", request.getCustomerId(),
                request.getAccountIds());
        return overdueDebtService.ensureNoOverdueDebt(request.getCustomerId())
                .andThen(validateAccounts(request.getCustomerId(), request.getAccountIds(),
                        request.getMainAccountId()))
                .andThen(Single.defer(this::generateCardNumber))
                .map(cardNumber -> DebitCard.builder()
                        .cardNumber(cardNumber)
                        .customerId(request.getCustomerId())
                        .accountIds(new ArrayList<>(request.getAccountIds()))
                        .mainAccountId(request.getMainAccountId())
                        .status(CardStatus.ACTIVE)
                        .createdAt(LocalDateTime.now())
                        .build())
                .flatMap(this::saveAndPublish)
                .map(debitCardMapper::toResponse)
                .doOnSuccess(card -> log.info("Debit card {} created with id={}", card.getCardNumber(), card.getId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flowable<DebitCardResponse> findAll(String customerId) {
        Flux<DebitCard> source = customerId == null || customerId.isBlank()
                ? debitCardRepository.findAll()
                : debitCardRepository.findByCustomerId(customerId);
        return ReactorRxAdapter.fluxToFlowable(source).map(debitCardMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<DebitCardResponse> findById(String id) {
        return findOrFail(id).map(debitCardMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<DebitCardResponse> update(String id, DebitCardRequest request) {
        log.info("Updating debit card id={}", id);
        return findOrFail(id)
                .flatMap(card -> {
                    if (!card.getCustomerId().equals(request.getCustomerId())) {
                        return Single.error(new BusinessRuleException("customerId cannot be modified"));
                    }
                    return validateAccounts(card.getCustomerId(), request.getAccountIds(), request.getMainAccountId())
                            .andThen(Single.fromCallable(() -> {
                                card.setAccountIds(new ArrayList<>(request.getAccountIds()));
                                card.setMainAccountId(request.getMainAccountId());
                                Optional.ofNullable(request.getStatus()).ifPresent(card::setStatus);
                                return card;
                            }));
                })
                .flatMap(this::saveAndPublish)
                .map(debitCardMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable delete(String id) {
        log.info("Deleting debit card id={}", id);
        return findOrFail(id).flatMapCompletable(card ->
                ReactorRxAdapter.monoToCompletable(debitCardRepository.delete(card))
                        .andThen(debitCardEventPublisher.publishDeleted(card).onErrorComplete()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Si la tarjeta estaba inactiva por no tener cuentas, la cuenta nueva
     * pasa a ser la principal y la tarjeta vuelve a {@code ACTIVE}.</p>
     */
    @Override
    public Single<DebitCardResponse> addAccount(String id, String accountId) {
        log.info("Linking account {} to debit card id={}", accountId, id);
        return findOrFail(id)
                .flatMap(card -> {
                    if (card.getAccountIds().contains(accountId)) {
                        return Single.error(new BusinessRuleException(
                                "Account " + accountId + " is already linked to this card"));
                    }
                    return accountOfCustomer(card.getCustomerId(), accountId).map(account -> {
                        card.getAccountIds().add(accountId);
                        if (card.getMainAccountId() == null) {
                            card.setMainAccountId(accountId);
                            card.setStatus(CardStatus.ACTIVE);
                        }
                        return card;
                    });
                })
                .flatMap(this::saveAndPublish)
                .map(debitCardMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<DebitCardResponse> changeMainAccount(String id, String accountId) {
        log.info("Setting account {} as main account of debit card id={}", accountId, id);
        return findOrFail(id)
                .flatMap(card -> {
                    if (!card.getAccountIds().contains(accountId)) {
                        return Single.error(new BusinessRuleException(
                                "Account " + accountId + " is not linked to this card"));
                    }
                    card.setMainAccountId(accountId);
                    return saveAndPublish(card);
                })
                .map(debitCardMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable removeDeletedAccount(String accountId) {
        return ReactorRxAdapter.fluxToFlowable(debitCardRepository.findByAccountIdsContaining(accountId))
                .concatMapCompletable(card -> {
                    card.getAccountIds().remove(accountId);
                    if (accountId.equals(card.getMainAccountId())) {
                        card.setMainAccountId(card.getAccountIds().isEmpty() ? null : card.getAccountIds().get(0));
                    }
                    if (card.getAccountIds().isEmpty()) {
                        card.setStatus(CardStatus.INACTIVE);
                    }
                    log.info("Account {} removed from debit card {} (main={}, status={})", accountId, card.getId(),
                            card.getMainAccountId(), card.getStatus());
                    return saveAndPublish(card).ignoreElement();
                });
    }

    /**
     * Valida las cuentas de una tarjeta contra las copias locales.
     *
     * @param customerId    cliente titular
     * @param accountIds    cuentas
     * @param mainAccountId cuenta principal
     * @return completable que falla con 404 o 409 si alguna regla no se cumple
     */
    private Completable validateAccounts(String customerId, List<String> accountIds, String mainAccountId) {
        if (accountIds.stream().distinct().count() != accountIds.size()) {
            return Completable.error(new BusinessRuleException("accountIds must not contain duplicates"));
        }
        if (!accountIds.contains(mainAccountId)) {
            return Completable.error(new BusinessRuleException("mainAccountId must be one of accountIds"));
        }
        return ReactorRxAdapter.monoToSingle(customerReplicaRepository.existsById(customerId))
                .flatMapCompletable(exists -> Boolean.TRUE.equals(exists)
                        ? Flowable.fromIterable(accountIds)
                                .concatMapSingle(accountId -> accountOfCustomer(customerId, accountId))
                                .ignoreElements()
                        : Completable.error(new ResourceNotFoundException("Customer not found with id " + customerId)));
    }

    /**
     * Busca una cuenta en la copia local y verifica que sea del cliente.
     *
     * @param customerId cliente titular
     * @param accountId  cuenta
     * @return la copia de la cuenta
     */
    private Single<AccountReplica> accountOfCustomer(String customerId, String accountId) {
        return ReactorRxAdapter.monoToMaybe(accountReplicaRepository.findById(accountId))
                .switchIfEmpty(Single.error(() -> new ResourceNotFoundException("Account not found with id "
                        + accountId)))
                .flatMap(account -> customerId.equals(account.getCustomerId())
                        ? Single.just(account)
                        : Single.error(new BusinessRuleException("Account " + accountId
                                + " does not belong to customer " + customerId)));
    }

    /**
     * Guarda la tarjeta y publica su snapshot.
     *
     * @param card tarjeta
     * @return tarjeta guardada
     */
    private Single<DebitCard> saveAndPublish(DebitCard card) {
        return ReactorRxAdapter.monoToSingle(debitCardRepository.save(card))
                .flatMap(saved -> debitCardEventPublisher.publishUpserted(saved).onErrorComplete()
                        .andThen(Single.just(saved)));
    }

    /**
     * Recupera una tarjeta o falla con 404.
     *
     * @param id identificador
     * @return la tarjeta
     */
    private Single<DebitCard> findOrFail(String id) {
        return ReactorRxAdapter.monoToMaybe(debitCardRepository.findById(id))
                .switchIfEmpty(Single.error(() -> new ResourceNotFoundException("Debit card not found with id " + id)));
    }

    /**
     * Genera un numero de tarjeta libre con el prefijo configurado.
     *
     * @return numero de {@value #CARD_NUMBER_LENGTH} digitos
     */
    private Single<String> generateCardNumber() {
        return Single.defer(() -> {
            String prefix = debitCardProperties.getCardNumberPrefix();
            String candidate = prefix + RANDOM.ints(CARD_NUMBER_LENGTH - prefix.length(), 0, 10)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining());
            return ReactorRxAdapter.monoToSingle(debitCardRepository.existsByCardNumber(candidate))
                    .flatMap(exists -> Boolean.TRUE.equals(exists) ? generateCardNumber() : Single.just(candidate));
        });
    }
}
