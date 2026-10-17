package com.bank.customer.service.impl;

import com.bank.customer.adapter.ReactorRxAdapter;
import com.bank.customer.cache.CustomerCache;
import com.bank.customer.dto.CustomerRequest;
import com.bank.customer.dto.CustomerResponse;
import com.bank.customer.event.CustomerEventPublisher;
import com.bank.customer.exception.BusinessRuleException;
import com.bank.customer.exception.ResourceNotFoundException;
import com.bank.customer.mapper.CustomerMapper;
import com.bank.customer.model.Customer;
import com.bank.customer.model.CustomerProfile;
import com.bank.customer.model.CustomerType;
import com.bank.customer.repository.CustomerRepository;
import com.bank.customer.service.CustomerService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion de las reglas de negocio de clientes.
 *
 * <p>Parte III: las lecturas por id y por documento pasan por la cache Redis
 * ({@link CustomerCache}), y cada alta, cambio o baja se publica en
 * {@code bank.customer.events}. Un fallo al publicar no revierte la operacion:
 * se registra con ERROR y se recupera con {@code POST /admin/events/republish}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final CustomerCache customerCache;
    private final CustomerEventPublisher customerEventPublisher;

    /**
     * {@inheritDoc}
     *
     * <p>Rechaza el registro si el perfil no corresponde al tipo de cliente o si
     * el numero de documento ya existe.</p>
     */
    @Override
    public Single<CustomerResponse> create(CustomerRequest request) {
        log.info("Creating customer with documentNumber={}", request.getDocumentNumber());
        CustomerProfile profile = Optional.ofNullable(request.getProfile()).orElse(CustomerProfile.STANDARD);
        return ensureProfileMatchesType(request.getType(), profile)
                .andThen(Single.defer(() -> ReactorRxAdapter.monoToSingle(
                        customerRepository.existsByDocumentNumber(request.getDocumentNumber()))))
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.warn("documentNumber={} is already registered", request.getDocumentNumber());
                        return Single.error(new BusinessRuleException(
                                "A customer with documentNumber " + request.getDocumentNumber() + " already exists"));
                    }
                    return ReactorRxAdapter.monoToSingle(
                            customerRepository.save(customerMapper.toDocument(request)));
                })
                .flatMap(saved -> customerCache.put(saved)
                        .andThen(publishQuietly(customerEventPublisher.publishUpserted(saved)))
                        .andThen(Single.just(saved)))
                .map(customerMapper::toResponse)
                .doOnSuccess(response -> log.info("Customer created with id={}", response.getId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flowable<CustomerResponse> findAll() {
        log.debug("Listing all customers");
        return ReactorRxAdapter.fluxToFlowable(customerRepository.findAll())
                .map(customerMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<CustomerResponse> findById(String id) {
        log.debug("Looking for customer with id={}", id);
        return customerCache.findById(id)
                .switchIfEmpty(Single.defer(() -> findOrFail(id)
                        .flatMap(customer -> customerCache.put(customer).andThen(Single.just(customer)))))
                .map(customerMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Si cambia el numero de documento, verifica que el nuevo valor no este
     * en uso por otro cliente. El perfil se valida sobre el resultado final,
     * por si cambia el tipo de cliente. Despues de guardar se invalidan las
     * claves de cache (la del documento anterior incluida) y se publica el
     * snapshot.</p>
     */
    @Override
    public Single<CustomerResponse> update(String id, CustomerRequest request) {
        log.info("Updating customer with id={}", id);
        return findOrFail(id)
                .flatMap(existing -> ensureDocumentNumberIsFree(existing, request).andThen(Single.just(existing)))
                .flatMap(existing -> {
                    String previousDocument = existing.getDocumentNumber();
                    Customer updated = customerMapper.merge(existing, request);
                    return ensureProfileMatchesType(updated.getType(), updated.getProfile())
                            .andThen(Single.defer(() -> ReactorRxAdapter.monoToSingle(
                                    customerRepository.save(updated))))
                            .flatMap(saved -> customerCache.evict(id, previousDocument, saved.getDocumentNumber())
                                    .andThen(publishQuietly(customerEventPublisher.publishUpserted(saved)))
                                    .andThen(Single.just(saved)));
                })
                .map(customerMapper::toResponse)
                .doOnSuccess(response -> log.info("Customer with id={} updated", response.getId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable delete(String id) {
        log.info("Deleting customer with id={}", id);
        return findOrFail(id)
                .flatMapCompletable(customer ->
                        ReactorRxAdapter.monoToCompletable(customerRepository.delete(customer))
                                .andThen(customerCache.evict(id, customer.getDocumentNumber()))
                                .andThen(publishQuietly(customerEventPublisher.publishDeleted(id))))
                .doOnComplete(() -> log.info("Customer with id={} deleted", id));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Consulta primero la cache por documento; si no esta, va a MongoDB y
     * guarda el resultado.</p>
     */
    @Override
    public Maybe<CustomerResponse> findByDocumentNumber(String documentNumber) {
        log.debug("Looking for customer with documentNumber={}", documentNumber);
        return customerCache.findByDocumentNumber(documentNumber)
                .switchIfEmpty(Maybe.defer(() -> ReactorRxAdapter.monoToMaybe(
                                customerRepository.findByDocumentNumber(documentNumber))
                        .flatMap(customer -> customerCache.put(customer).andThen(Maybe.just(customer)))))
                .map(customerMapper::toResponse);
    }

    /**
     * Una operacion ya guardada no se revierte si Kafka falla: el error queda
     * registrado por el publicador y se recupera con el endpoint de
     * republicacion.
     *
     * @param publication publicacion del evento
     * @return completable que siempre termina bien
     */
    private Completable publishQuietly(Completable publication) {
        return publication.onErrorComplete();
    }

    /**
     * Recupera un cliente o falla con 404 si no existe.
     *
     * @param id identificador del cliente
     * @return el documento encontrado
     */
    private Single<Customer> findOrFail(String id) {
        return ReactorRxAdapter.monoToMaybe(customerRepository.findById(id))
                .switchIfEmpty(Single.error(() ->
                        new ResourceNotFoundException("Customer not found with id " + id)));
    }

    /**
     * Comprueba que el numero de documento de la peticion siga disponible.
     *
     * <p>Si el numero no cambio respecto al documento actual no hay nada que
     * validar.</p>
     *
     * @param existing cliente almacenado
     * @param request  datos nuevos
     * @return completable que falla con {@link BusinessRuleException} si el
     *         numero ya pertenece a otro cliente
     */
    private Completable ensureDocumentNumberIsFree(Customer existing, CustomerRequest request) {
        if (existing.getDocumentNumber().equals(request.getDocumentNumber())) {
            return Completable.complete();
        }
        return ReactorRxAdapter.monoToSingle(customerRepository.existsByDocumentNumber(request.getDocumentNumber()))
                .flatMapCompletable(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.warn("Cannot reassign documentNumber={}: already in use", request.getDocumentNumber());
                        return Completable.error(new BusinessRuleException(
                                "A customer with documentNumber " + request.getDocumentNumber() + " already exists"));
                    }
                    return Completable.complete();
                });
    }

    /**
     * Valida que el perfil sea compatible con el tipo de cliente: {@code VIP}
     * solo para {@code PERSONAL} y {@code PYME} solo para {@code BUSINESS}.
     *
     * @param type    tipo de cliente
     * @param profile perfil solicitado
     * @return completable que falla con {@link BusinessRuleException} si la
     *         combinacion no es valida
     */
    private Completable ensureProfileMatchesType(CustomerType type, CustomerProfile profile) {
        if (profile.isAllowedFor(type)) {
            return Completable.complete();
        }
        log.warn("Profile {} is not allowed for a {} customer", profile, type);
        return Completable.error(new BusinessRuleException(
                "Profile " + profile + " is not allowed for a " + type + " customer"));
    }
}
