package com.bank.account.service.opening;

import com.bank.account.client.CustomerDto;
import com.bank.account.dto.AccountRequest;
import com.bank.account.model.AccountProfile;
import com.bank.account.service.rule.AccountRule;
import lombok.Builder;
import lombok.Getter;

/**
 * Datos que recorren la cadena de validaciones de apertura de cuenta.
 */
@Getter
@Builder
public class AccountOpeningContext {

    /** Datos de apertura enviados por el cliente de la API. */
    private final AccountRequest request;

    /** Cliente devuelto por {@code bank-customer-service}. */
    private final CustomerDto customer;

    /** Perfil efectivo del cliente ({@code STANDARD} si no tiene). */
    private final AccountProfile profile;

    /** Estrategia del tipo de cuenta y perfil, elegida por la fabrica. */
    private final AccountRule rule;
}
