package com.bank.account.service.impl;

import com.bank.account.adapter.ReactorRxAdapter;
import com.bank.account.client.CustomerClient;
import com.bank.account.client.CustomerDto;
import com.bank.account.client.CustomerType;
import com.bank.account.dto.AccountRequest;
import com.bank.account.dto.AccountResponse;
import com.bank.account.dto.AverageDailyBalanceResponse;
import com.bank.account.dto.BalanceOperationRequest;
import com.bank.account.dto.BalanceResponse;
import com.bank.account.event.AccountEventPublisher;
import com.bank.account.exception.BusinessRuleException;
import com.bank.account.exception.ResourceNotFoundException;
import com.bank.account.mapper.AccountMapper;
import com.bank.account.model.Account;
import com.bank.account.model.AccountProfile;
import com.bank.account.model.BalanceOperationType;
import com.bank.account.model.Holder;
import com.bank.account.repository.AccountRepository;
import com.bank.account.service.AccountService;
import com.bank.account.service.DailyBalanceService;
import com.bank.account.service.opening.AccountOpeningChain;
import com.bank.account.service.opening.AccountOpeningContext;
import com.bank.account.service.rule.AccountRuleFactory;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion de las reglas de negocio de las cuentas bancarias.
 *
 * <p>La apertura delega en dos patrones: {@link AccountRuleFactory} elige la
 * estrategia del tipo de cuenta y perfil, y {@link AccountOpeningChain} recorre
 * la cadena de validaciones.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    /** Cantidad de digitos del numero de cuenta generado. */
    private static final int ACCOUNT_NUMBER_LENGTH = 14;

    private static final Random RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final CustomerClient customerClient;
    private final AccountRuleFactory accountRuleFactory;
    private final AccountOpeningChain accountOpeningChain;
    private final DailyBalanceService dailyBalanceService;
    private final AccountEventPublisher accountEventPublisher;

    /**
     * {@inheritDoc}
     *
     * <p>Consulta el cliente, elige la estrategia segun tipo de cuenta y perfil,
     * recorre la cadena de validaciones y recien entonces guarda la cuenta y su
     * primer saldo diario.</p>
     */
    @Override
    public Single<AccountResponse> create(AccountRequest request) {
        log.info("Opening a {} account for customerId={}", request.getAccountType(), request.getCustomerId());
        return customerClient.findById(request.getCustomerId())
                .map(customer -> openingContext(request, customer))
                .flatMap(context -> accountOpeningChain.validate(context).andThen(buildAccount(context)))
                .flatMap(account -> ReactorRxAdapter.monoToSingle(accountRepository.save(account)))
                .flatMap(saved -> dailyBalanceService.recordClosingBalance(saved.getId(), saved.getBalance())
                        .andThen(publishQuietly(accountEventPublisher.publishUpserted(saved)))
                        .andThen(Single.just(saved)))
                .map(accountMapper::toResponse)
                .doOnSuccess(response -> log.info("{} {} account {} created with id={}", response.getProfile(),
                        response.getAccountType(), response.getAccountNumber(), response.getId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flowable<AccountResponse> findAll(String customerId, String accountNumber) {
        if (hasText(accountNumber)) {
            log.debug("Looking for account number={}", accountNumber);
            return ReactorRxAdapter.monoToMaybe(accountRepository.findByAccountNumber(accountNumber))
                    .toFlowable()
                    .map(accountMapper::toResponse);
        }
        if (hasText(customerId)) {
            log.debug("Listing accounts for customerId={}", customerId);
            return ReactorRxAdapter.fluxToFlowable(accountRepository.findByCustomerId(customerId))
                    .map(accountMapper::toResponse);
        }
        log.debug("Listing all accounts");
        return ReactorRxAdapter.fluxToFlowable(accountRepository.findAll()).map(accountMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<AccountResponse> findById(String id) {
        log.debug("Looking for account with id={}", id);
        return findOrFail(id).map(accountMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     *
     * <p>El tipo de cuenta y el cliente son inmutables: si la peticion intenta
     * cambiarlos se rechaza la operacion.</p>
     */
    @Override
    public Single<AccountResponse> update(String id, AccountRequest request) {
        log.info("Updating account with id={}", id);
        return findOrFail(id)
                .flatMap(existing -> ensureImmutableFields(existing, request).andThen(Single.just(existing)))
                .map(existing -> accountMapper.merge(existing, request))
                .flatMap(updated -> ReactorRxAdapter.monoToSingle(accountRepository.save(updated)))
                .flatMap(saved -> publishQuietly(accountEventPublisher.publishUpserted(saved))
                        .andThen(Single.just(saved)))
                .map(accountMapper::toResponse)
                .doOnSuccess(response -> log.info("Account with id={} updated", response.getId()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Tambien elimina el historial de saldos diarios de la cuenta.</p>
     */
    @Override
    public Completable delete(String id) {
        log.info("Deleting account with id={}", id);
        return findOrFail(id)
                .flatMapCompletable(account -> ReactorRxAdapter.monoToCompletable(accountRepository.delete(account))
                        .andThen(dailyBalanceService.deleteHistory(account.getId()))
                        .andThen(publishQuietly(accountEventPublisher.publishDeleted(account))))
                .doOnComplete(() -> log.info("Account with id={} deleted", id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<BalanceResponse> getBalance(String id) {
        log.debug("Checking balance of account with id={}", id);
        return findOrFail(id).map(accountMapper::toBalanceResponse);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Aplica la comision opcional y actualiza el saldo diario.</p>
     */
    @Override
    public Single<BalanceResponse> applyBalanceOperation(String id, BalanceOperationRequest request) {
        log.info("Applying {} of {} (fee {}) to account with id={}",
                request.getOperation(), request.getAmount(), request.getFee(), id);
        return findOrFail(id)
                .map(account -> applyOperation(account, request))
                .flatMap(account -> ReactorRxAdapter.monoToSingle(accountRepository.save(account)))
                .flatMap(saved -> dailyBalanceService.recordClosingBalance(saved.getId(), saved.getBalance())
                        .andThen(Single.just(saved)))
                .map(accountMapper::toBalanceResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<AverageDailyBalanceResponse> getAverageDailyBalance(String id, YearMonth month) {
        log.debug("Calculating average daily balance of account {} for {}", id, month);
        return findOrFail(id)
                .flatMap(account -> dailyBalanceService
                        .averageForMonth(account.getId(), account.getCreatedAt().toLocalDate(), month)
                        .map(average -> AverageDailyBalanceResponse.builder()
                                .accountId(account.getId())
                                .month(month.toString())
                                .averageDailyBalance(average)
                                .minimumDailyAverage(account.getMinimumDailyAverage())
                                .meetsMinimum(Optional.ofNullable(account.getMinimumDailyAverage())
                                        .map(minimum -> average.compareTo(minimum) >= 0)
                                        .orElse(null))
                                .build()));
    }

    /**
     * Recupera una cuenta o falla con 404 si no existe.
     *
     * @param id identificador de la cuenta
     * @return el documento encontrado
     */
    private Single<Account> findOrFail(String id) {
        return ReactorRxAdapter.monoToMaybe(accountRepository.findById(id))
                .switchIfEmpty(Single.error(() -> new ResourceNotFoundException("Account not found with id " + id)));
    }

    /**
     * Arma el contexto de la cadena: perfil efectivo del cliente y estrategia
     * elegida por la fabrica.
     *
     * @param request  datos de apertura
     * @param customer cliente devuelto por {@code bank-customer-service}
     * @return contexto de apertura
     */
    private AccountOpeningContext openingContext(AccountRequest request, CustomerDto customer) {
        AccountProfile profile = Optional.ofNullable(customer.getProfile()).orElse(AccountProfile.STANDARD);
        return AccountOpeningContext.builder()
                .request(request)
                .customer(customer)
                .profile(profile)
                .rule(accountRuleFactory.getRule(request.getAccountType(), profile))
                .build();
    }

    /**
     * Comprueba que la actualizacion no intente cambiar el tipo de cuenta ni el
     * cliente dueno.
     *
     * @param existing cuenta almacenada
     * @param request  datos nuevos
     * @return completable que falla si se intenta cambiar un campo inmutable
     */
    private Completable ensureImmutableFields(Account existing, AccountRequest request) {
        if (existing.getAccountType() != request.getAccountType()) {
            return Completable.error(new BusinessRuleException("accountType cannot be modified"));
        }
        if (!existing.getCustomerId().equals(request.getCustomerId())) {
            return Completable.error(new BusinessRuleException("customerId cannot be modified"));
        }
        return Completable.complete();
    }

    /**
     * Completa el documento de cuenta: numero generado, monto de apertura como
     * saldo, perfil, titulares y los valores que aporta la estrategia.
     *
     * @param context contexto de apertura ya validado
     * @return cuenta lista para persistir
     */
    private Single<Account> buildAccount(AccountOpeningContext context) {
        AccountRequest request = context.getRequest();
        BigDecimal openingAmount = Optional.ofNullable(request.getOpeningAmount()).orElse(BigDecimal.ZERO);
        return generateAccountNumber().map(accountNumber -> {
            Account account = accountMapper.toDocument(request);
            account.setAccountNumber(accountNumber);
            account.setOpeningAmount(openingAmount);
            account.setBalance(openingAmount);
            account.setProfile(context.getProfile());
            account.setCreatedAt(LocalDateTime.now());
            account.setHolders(resolveHolders(request, context.getCustomer()));
            account.setAuthorizedSigners(accountMapper.toHolders(request.getAuthorizedSigners()));
            context.getRule().applyDefaults(account, request);
            return account;
        });
    }

    /**
     * En una cuenta personal el titular es el propio cliente; en una
     * empresarial se toman los titulares enviados.
     *
     * @param request  datos de apertura
     * @param customer cliente dueno de la cuenta
     * @return lista de titulares
     */
    private List<Holder> resolveHolders(AccountRequest request, CustomerDto customer) {
        if (customer.getType() == CustomerType.PERSONAL) {
            Holder owner = Holder.builder()
                    .documentType(customer.getDocumentType())
                    .documentNumber(customer.getDocumentNumber())
                    .fullName(customer.getName())
                    .build();
            return new ArrayList<>(List.of(owner));
        }
        return accountMapper.toHolders(request.getHolders());
    }

    /**
     * Genera un numero de cuenta libre; si el sorteado ya existe, reintenta.
     *
     * @return numero de cuenta de {@value #ACCOUNT_NUMBER_LENGTH} digitos
     */
    private Single<String> generateAccountNumber() {
        return Single.defer(() -> {
            String candidate = RANDOM.ints(ACCOUNT_NUMBER_LENGTH, 0, 10)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining());
            return ReactorRxAdapter.monoToSingle(accountRepository.existsByAccountNumber(candidate))
                    .flatMap(exists -> Boolean.TRUE.equals(exists) ? generateAccountNumber() : Single.just(candidate));
        });
    }

    /**
     * Aplica el deposito o el retiro, con su comision, sobre el saldo en memoria.
     *
     * <p>Deposito: {@code balance += amount - fee}. Retiro:
     * {@code balance -= amount + fee}. Ninguno puede dejar el saldo negativo.</p>
     *
     * @param account cuenta recuperada
     * @param request operacion, importe y comision
     * @return la misma cuenta con el saldo actualizado
     * @throws BusinessRuleException si el saldo resultante seria negativo
     */
    private Account applyOperation(Account account, BalanceOperationRequest request) {
        BigDecimal fee = Optional.ofNullable(request.getFee()).orElse(BigDecimal.ZERO);
        BigDecimal delta = request.getOperation() == BalanceOperationType.DEPOSIT
                ? request.getAmount().subtract(fee)
                : request.getAmount().add(fee).negate();
        BigDecimal newBalance = account.getBalance().add(delta);
        if (newBalance.signum() < 0) {
            log.warn("Insufficient funds in account {}: balance={}, amount={}, fee={}",
                    account.getAccountNumber(), account.getBalance(), request.getAmount(), fee);
            throw new BusinessRuleException("Insufficient funds in account " + account.getAccountNumber());
        }
        account.setBalance(newBalance);
        log.info("{} of {} (fee {}) on account {}, balance is now {}", request.getOperation(),
                request.getAmount(), fee, account.getAccountNumber(), newBalance);
        return account;
    }

    /**
     * Una cuenta ya guardada no se revierte si Kafka falla: el publicador
     * registra el error y se recupera con {@code POST /admin/events/republish}.
     *
     * @param publication publicacion del evento
     * @return completable que siempre termina bien
     */
    private Completable publishQuietly(Completable publication) {
        return publication.onErrorComplete();
    }

    /**
     * Indica si el texto tiene contenido.
     *
     * @param value texto a evaluar
     * @return {@code true} si no es nulo ni esta en blanco
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
