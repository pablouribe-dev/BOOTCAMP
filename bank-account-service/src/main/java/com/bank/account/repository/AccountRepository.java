package com.bank.account.repository;

import com.bank.account.model.Account;
import com.bank.account.model.AccountType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de cuentas bancarias.
 *
 * <p>Solo consultas derivadas del nombre del metodo: no se usa {@code @Query}
 * ni criterios armados a mano.</p>
 */
@Repository
public interface AccountRepository extends ReactiveMongoRepository<Account, String> {

    /**
     * Lista las cuentas de un cliente.
     *
     * @param customerId identificador del cliente
     * @return flujo de cuentas del cliente
     */
    Flux<Account> findByCustomerId(String customerId);

    /**
     * Busca una cuenta por su numero.
     *
     * @param accountNumber numero de cuenta
     * @return la cuenta, o vacio si no existe
     */
    Mono<Account> findByAccountNumber(String accountNumber);

    /**
     * Cuenta cuantas cuentas de un tipo tiene un cliente.
     *
     * <p>Base de la regla que limita a una cuenta de ahorro y una corriente por
     * cliente personal.</p>
     *
     * @param customerId  identificador del cliente
     * @param accountType tipo de cuenta
     * @return cantidad de cuentas encontradas
     */
    Mono<Long> countByCustomerIdAndAccountType(String customerId, AccountType accountType);

    /**
     * Indica si un numero de cuenta ya esta en uso.
     *
     * @param accountNumber numero de cuenta generado
     * @return {@code true} si el numero ya existe
     */
    Mono<Boolean> existsByAccountNumber(String accountNumber);
}
