package com.bank.yanki.service.impl;

import com.bank.yanki.adapter.ReactorRxAdapter;
import com.bank.yanki.dto.WalletRequest;
import com.bank.yanki.dto.WalletResponse;
import com.bank.yanki.event.DebitCardEventPayload;
import com.bank.yanki.exception.BusinessRuleException;
import com.bank.yanki.exception.ResourceNotFoundException;
import com.bank.yanki.mapper.YankiMapper;
import com.bank.yanki.model.DebitCardReplica;
import com.bank.yanki.model.Wallet;
import com.bank.yanki.repository.DebitCardReplicaRepository;
import com.bank.yanki.repository.WalletRepository;
import com.bank.yanki.service.WalletService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion de los monederos Yanki.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    /** Estado de tarjeta operativa. */
    static final String ACTIVE = "ACTIVE";

    private final WalletRepository walletRepository;
    private final DebitCardReplicaRepository debitCardReplicaRepository;
    private final YankiMapper yankiMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<WalletResponse> create(WalletRequest request) {
        log.info("Creating Yanki wallet for phone {}", request.getPhoneNumber());
        return ensureUnique(request.getDocumentNumber(), request.getPhoneNumber())
                .andThen(Single.defer(() -> ReactorRxAdapter.monoToSingle(
                        walletRepository.save(yankiMapper.toDocument(request)))))
                .map(yankiMapper::toResponse)
                .doOnSuccess(wallet -> log.info("Wallet {} created for phone {}", wallet.getId(),
                        wallet.getPhoneNumber()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flowable<WalletResponse> findAll(String phoneNumber) {
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            return ReactorRxAdapter.monoToMaybe(walletRepository.findByPhoneNumber(phoneNumber)).toFlowable()
                    .map(yankiMapper::toResponse);
        }
        return ReactorRxAdapter.fluxToFlowable(walletRepository.findAll()).map(yankiMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<WalletResponse> findById(String id) {
        return findOrFail(id).map(yankiMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<WalletResponse> update(String id, WalletRequest request) {
        return findOrFail(id)
                .flatMap(wallet -> ensureUnique(
                        wallet.getDocumentNumber().equals(request.getDocumentNumber()) ? null
                                : request.getDocumentNumber(),
                        wallet.getPhoneNumber().equals(request.getPhoneNumber()) ? null : request.getPhoneNumber())
                        .andThen(Single.just(wallet)))
                .map(wallet -> yankiMapper.merge(wallet, request))
                .flatMap(wallet -> ReactorRxAdapter.monoToSingle(walletRepository.save(wallet)))
                .map(yankiMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable delete(String id) {
        return findOrFail(id).flatMapCompletable(wallet ->
                ReactorRxAdapter.monoToCompletable(walletRepository.delete(wallet)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<WalletResponse> linkDebitCard(String id, String cardNumber) {
        log.info("Linking debit card {} to wallet {}", cardNumber, id);
        return findOrFail(id)
                .flatMap(wallet -> ReactorRxAdapter.monoToMaybe(debitCardReplicaRepository.findByCardNumber(cardNumber))
                        .switchIfEmpty(Single.error(() -> new ResourceNotFoundException(
                                "Debit card not found with number " + cardNumber)))
                        .flatMap(card -> {
                            if (!ACTIVE.equals(card.getStatus())) {
                                return Single.error(new BusinessRuleException(
                                        "Debit card " + cardNumber + " is not active"));
                            }
                            wallet.setDebitCardNumber(cardNumber);
                            return ReactorRxAdapter.monoToSingle(walletRepository.save(wallet));
                        }))
                .map(yankiMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<WalletResponse> unlinkDebitCard(String id) {
        return findOrFail(id)
                .flatMap(wallet -> {
                    wallet.setDebitCardNumber(null);
                    return ReactorRxAdapter.monoToSingle(walletRepository.save(wallet));
                })
                .map(yankiMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable upsertDebitCard(DebitCardEventPayload payload) {
        DebitCardReplica replica = DebitCardReplica.builder()
                .cardId(payload.getCardId())
                .cardNumber(payload.getCardNumber())
                .customerId(payload.getCustomerId())
                .mainAccountId(payload.getMainAccountId())
                .status(payload.getStatus())
                .updatedAt(Instant.now())
                .build();
        return ReactorRxAdapter.monoToCompletable(debitCardReplicaRepository.save(replica))
                .doOnComplete(() -> log.info("Debit card replica {} updated (main={}, status={})",
                        payload.getCardNumber(), payload.getMainAccountId(), payload.getStatus()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable deleteDebitCard(String cardId) {
        return ReactorRxAdapter.monoToCompletable(debitCardReplicaRepository.deleteById(cardId))
                .doOnComplete(() -> log.info("Debit card replica {} deleted", cardId));
    }

    /**
     * Verifica que documento y celular no esten en uso. Un valor nulo no se
     * verifica (no cambio).
     *
     * @param documentNumber documento a verificar, o {@code null}
     * @param phoneNumber    celular a verificar, o {@code null}
     * @return completable que falla con 409 si alguno ya existe
     */
    private Completable ensureUnique(String documentNumber, String phoneNumber) {
        Completable document = documentNumber == null ? Completable.complete()
                : ReactorRxAdapter.monoToSingle(walletRepository.existsByDocumentNumber(documentNumber))
                        .flatMapCompletable(exists -> Boolean.TRUE.equals(exists)
                                ? Completable.error(new BusinessRuleException("A wallet with documentNumber "
                                        + documentNumber + " already exists"))
                                : Completable.complete());
        Completable phone = phoneNumber == null ? Completable.complete()
                : ReactorRxAdapter.monoToSingle(walletRepository.existsByPhoneNumber(phoneNumber))
                        .flatMapCompletable(exists -> Boolean.TRUE.equals(exists)
                                ? Completable.error(new BusinessRuleException("A wallet with phoneNumber "
                                        + phoneNumber + " already exists"))
                                : Completable.complete());
        return document.andThen(phone);
    }

    /**
     * Recupera un monedero o falla con 404.
     *
     * @param id identificador
     * @return el monedero
     */
    private Single<Wallet> findOrFail(String id) {
        return ReactorRxAdapter.monoToMaybe(walletRepository.findById(id))
                .switchIfEmpty(Single.error(() -> new ResourceNotFoundException("Wallet not found with id " + id)));
    }
}
