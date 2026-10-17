package com.bank.account.service.impl;

import com.bank.account.adapter.ReactorRxAdapter;
import com.bank.account.dto.BalanceOperationRequest;
import com.bank.account.event.AccountEventPublisher;
import com.bank.account.event.AccountOperationRequestedPayload;
import com.bank.account.event.AccountOperationResultPayload;
import com.bank.account.exception.BusinessRuleException;
import com.bank.account.exception.ResourceNotFoundException;
import com.bank.account.model.ProcessedOperation;
import com.bank.account.repository.ProcessedOperationRepository;
import com.bank.account.service.AccountOperationService;
import com.bank.account.service.AccountService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion de las operaciones de cuenta pedidas por Kafka.
 *
 * <p>Reutiliza {@link AccountService#applyBalanceOperation} (misma logica que
 * el endpoint interno, incluido el saldo diario) con comision cero. Los
 * rechazos de negocio no son errores: se publican como
 * {@code ACCOUNT_OPERATION_REJECTED}. Cualquier otro fallo se propaga para que
 * Kafka reintente.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountOperationServiceImpl implements AccountOperationService {

    private final AccountService accountService;
    private final ProcessedOperationRepository processedOperationRepository;
    private final AccountEventPublisher accountEventPublisher;

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable process(AccountOperationRequestedPayload request) {
        return ReactorRxAdapter.monoToMaybe(processedOperationRepository.findById(request.getOperationId()))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMapCompletable(previous -> previous
                        .map(this::republish)
                        .orElseGet(() -> applyAndPublish(request)));
    }

    /**
     * Operacion repetida: vuelve a publicar el resultado guardado.
     *
     * @param previous resultado de la primera vez
     * @return publicacion del mismo resultado
     */
    private Completable republish(ProcessedOperation previous) {
        log.info("Operation {} was already processed ({}): republishing the same result",
                previous.getOperationId(), previous.getResultType());
        return accountEventPublisher.publishOperationResult(previous.getResultType(), previous.getResult());
    }

    /**
     * Aplica la operacion, guarda el resultado y lo publica. Se guarda antes de
     * publicar: si la publicacion falla, el reintento encuentra el resultado y
     * lo republica sin volver a mover el saldo.
     *
     * @param request operacion pedida
     * @return completable que termina cuando el resultado fue publicado
     */
    private Completable applyAndPublish(AccountOperationRequestedPayload request) {
        log.info("Applying {} of {} on account {} requested by {} ({})", request.getOperation(),
                request.getAmount(), request.getAccountId(), request.getOrigin(), request.getReference());
        return apply(request)
                .flatMap(processed -> ReactorRxAdapter.monoToSingle(processedOperationRepository.save(processed)))
                .flatMapCompletable(saved ->
                        accountEventPublisher.publishOperationResult(saved.getResultType(), saved.getResult()));
    }

    /**
     * Aplica la operacion y traduce el resultado (o el rechazo de negocio).
     *
     * @param request operacion pedida
     * @return resultado listo para guardar
     */
    private Single<ProcessedOperation> apply(AccountOperationRequestedPayload request) {
        BalanceOperationRequest operation = BalanceOperationRequest.builder()
                .operation(request.getOperation())
                .amount(request.getAmount())
                .fee(BigDecimal.ZERO)
                .build();
        return accountService.applyBalanceOperation(request.getAccountId(), operation)
                .map(balance -> processed(request, AccountOperationResultPayload.ACCOUNT_OPERATION_COMPLETED,
                        baseResult(request)
                                .customerId(balance.getCustomerId())
                                .operation(request.getOperation())
                                .amount(request.getAmount())
                                .balanceAfter(balance.getBalance())
                                .build()))
                .onErrorResumeNext(error -> rejection(request, error));
    }

    /**
     * Convierte los errores de negocio en un rechazo; el resto se propaga.
     *
     * @param request operacion pedida
     * @param error   error de la operacion
     * @return rechazo, o el mismo error si no es de negocio
     */
    private Single<ProcessedOperation> rejection(AccountOperationRequestedPayload request, Throwable error) {
        String reason;
        if (error instanceof ResourceNotFoundException) {
            reason = AccountOperationResultPayload.ACCOUNT_NOT_FOUND;
        } else if (error instanceof BusinessRuleException) {
            reason = AccountOperationResultPayload.INSUFFICIENT_FUNDS;
        } else {
            return Single.error(error);
        }
        log.warn("Operation {} on account {} rejected: {}", request.getOperationId(), request.getAccountId(), reason);
        return Single.just(processed(request, AccountOperationResultPayload.ACCOUNT_OPERATION_REJECTED,
                baseResult(request).reason(reason).build()));
    }

    /**
     * Campos comunes a completados y rechazos.
     *
     * @param request operacion pedida
     * @return builder del resultado
     */
    private AccountOperationResultPayload.AccountOperationResultPayloadBuilder baseResult(
            AccountOperationRequestedPayload request) {
        return AccountOperationResultPayload.builder()
                .operationId(request.getOperationId())
                .accountId(request.getAccountId())
                .origin(request.getOrigin())
                .reference(request.getReference());
    }

    /**
     * Arma el registro de operacion procesada.
     *
     * @param request    operacion pedida
     * @param resultType tipo de evento del resultado
     * @param result     resultado
     * @return registro a guardar
     */
    private ProcessedOperation processed(AccountOperationRequestedPayload request, String resultType,
                                         AccountOperationResultPayload result) {
        return ProcessedOperation.builder()
                .operationId(request.getOperationId())
                .resultType(resultType)
                .result(result)
                .processedAt(Instant.now())
                .build();
    }
}
