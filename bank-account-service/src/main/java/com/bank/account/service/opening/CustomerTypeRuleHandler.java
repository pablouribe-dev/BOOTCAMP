package com.bank.account.service.opening;

import com.bank.account.adapter.ReactorRxAdapter;
import com.bank.account.dto.AccountRequest;
import com.bank.account.exception.BusinessRuleException;
import com.bank.account.exception.ServiceUnavailableException;
import com.bank.account.model.AccountType;
import com.bank.account.repository.AccountRepository;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Eslabon 1: reglas de la Parte I segun el tipo de cliente.
 *
 * <ul>
 *   <li>PERSONAL: como maximo una cuenta de ahorro y una corriente; plazo fijo
 *       sin limite.</li>
 *   <li>BUSINESS: solo cuentas corrientes, con al menos un titular.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerTypeRuleHandler extends AccountOpeningHandler {

    private final AccountRepository accountRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    protected Completable check(AccountOpeningContext context) {
        if (context.getCustomer().getType() == null) {
            return Completable.error(new ServiceUnavailableException(
                    "bank-customer-service did not return the type of customer "
                            + context.getRequest().getCustomerId()));
        }
        return switch (context.getCustomer().getType()) {
            case PERSONAL -> checkPersonal(context.getRequest());
            case BUSINESS -> checkBusiness(context.getRequest());
        };
    }

    /**
     * Un cliente personal puede tener una sola cuenta de ahorro y una sola
     * corriente.
     *
     * @param request datos de apertura
     * @return completable que falla si ya tiene una cuenta de ese tipo
     */
    private Completable checkPersonal(AccountRequest request) {
        if (request.getAccountType() == AccountType.FIXED_TERM) {
            return Completable.complete();
        }
        return ReactorRxAdapter.monoToSingle(accountRepository.countByCustomerIdAndAccountType(
                        request.getCustomerId(), request.getAccountType()))
                .flatMapCompletable(count -> {
                    if (count > 0) {
                        log.warn("Customer {} already holds a {} account",
                                request.getCustomerId(), request.getAccountType());
                        return Completable.error(new BusinessRuleException(
                                "A personal customer can only hold one " + request.getAccountType() + " account"));
                    }
                    return Completable.complete();
                });
    }

    /**
     * Un cliente empresarial solo abre cuentas corrientes y con titulares.
     *
     * @param request datos de apertura
     * @return completable que falla si el tipo o los titulares no son validos
     */
    private Completable checkBusiness(AccountRequest request) {
        if (request.getAccountType() != AccountType.CURRENT) {
            log.warn("Business customer {} requested a {} account", request.getCustomerId(), request.getAccountType());
            return Completable.error(new BusinessRuleException("A business customer can only hold CURRENT accounts"));
        }
        if (request.getHolders() == null || request.getHolders().isEmpty()) {
            log.warn("Business account for customer {} came without holders", request.getCustomerId());
            return Completable.error(new BusinessRuleException("A business account requires at least one holder"));
        }
        return Completable.complete();
    }
}
