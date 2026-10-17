package com.bank.account.mapper;

import com.bank.account.dto.AccountRequest;
import com.bank.account.dto.AccountResponse;
import com.bank.account.dto.BalanceResponse;
import com.bank.account.dto.HolderDto;
import com.bank.account.model.Account;
import com.bank.account.model.Holder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Conversiones entre el documento {@link Account} y sus DTOs.
 */
@Component
public class AccountMapper {

    /**
     * Construye un documento con los campos planos de la peticion.
     *
     * <p>El numero de cuenta, el saldo, la comision, los limites y los
     * titulares los completa la capa de servicio, que es la que conoce las
     * reglas y la configuracion.</p>
     *
     * @param request datos enviados por el cliente de la API
     * @return documento parcialmente construido
     */
    public Account toDocument(AccountRequest request) {
        return Account.builder()
                .accountType(request.getAccountType())
                .customerId(request.getCustomerId())
                .build();
    }

    /**
     * Copia sobre una cuenta existente los campos que admiten actualizacion.
     *
     * <p>El saldo, el numero de cuenta, el tipo y el cliente no se modifican:
     * el saldo solo cambia con los movimientos.</p>
     *
     * @param existing cuenta recuperada de la base de datos
     * @param request  datos nuevos
     * @return la misma cuenta con los valores actualizados
     */
    public Account merge(Account existing, AccountRequest request) {
        Optional.ofNullable(request.getMaintenanceFee()).ifPresent(existing::setMaintenanceFee);
        Optional.ofNullable(request.getMonthlyMovementLimit()).ifPresent(existing::setMonthlyMovementLimit);
        Optional.ofNullable(request.getMovementDay()).ifPresent(existing::setMovementDay);
        Optional.ofNullable(request.getHolders())
                .ifPresent(holders -> existing.setHolders(toHolders(holders)));
        Optional.ofNullable(request.getAuthorizedSigners())
                .ifPresent(signers -> existing.setAuthorizedSigners(toHolders(signers)));
        return existing;
    }

    /**
     * Convierte una lista de titulares del DTO al modelo embebido.
     *
     * @param holders lista enviada por la API, puede ser nula
     * @return lista de titulares, vacia si la entrada era nula
     */
    public List<Holder> toHolders(List<HolderDto> holders) {
        return Optional.ofNullable(holders).orElse(Collections.emptyList()).stream()
                .map(dto -> Holder.builder()
                        .documentType(dto.getDocumentType())
                        .documentNumber(dto.getDocumentNumber())
                        .fullName(dto.getFullName())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Convierte una lista de titulares del modelo embebido al DTO.
     *
     * @param holders lista almacenada, puede ser nula
     * @return lista de DTOs, vacia si la entrada era nula
     */
    public List<HolderDto> toHolderDtos(List<Holder> holders) {
        return Optional.ofNullable(holders).orElse(Collections.emptyList()).stream()
                .map(holder -> HolderDto.builder()
                        .documentType(holder.getDocumentType())
                        .documentNumber(holder.getDocumentNumber())
                        .fullName(holder.getFullName())
                        .build())
                .toList();
    }

    /**
     * Convierte el documento en la respuesta expuesta por la API.
     *
     * @param account documento de cuenta
     * @return DTO de respuesta
     */
    public AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .customerId(account.getCustomerId())
                .balance(account.getBalance())
                .maintenanceFee(account.getMaintenanceFee())
                .monthlyMovementLimit(account.getMonthlyMovementLimit())
                .movementDay(account.getMovementDay())
                .holders(toHolderDtos(account.getHolders()))
                .authorizedSigners(toHolderDtos(account.getAuthorizedSigners()))
                .profile(account.getProfile())
                .openingAmount(account.getOpeningAmount())
                .minimumDailyAverage(account.getMinimumDailyAverage())
                .freeTransactionLimit(account.getFreeTransactionLimit())
                .transactionFee(account.getTransactionFee())
                .createdAt(account.getCreatedAt())
                .build();
    }

    /**
     * Construye la respuesta de consulta de saldo.
     *
     * @param account documento de cuenta
     * @return DTO con el saldo disponible
     */
    public BalanceResponse toBalanceResponse(Account account) {
        return BalanceResponse.builder()
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .customerId(account.getCustomerId())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .build();
    }
}
