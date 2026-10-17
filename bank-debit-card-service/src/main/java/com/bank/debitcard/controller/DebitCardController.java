package com.bank.debitcard.controller;

import com.bank.debitcard.dto.AccountLinkRequest;
import com.bank.debitcard.dto.DebitCardRequest;
import com.bank.debitcard.dto.DebitCardResponse;
import com.bank.debitcard.dto.DebitPaymentRequest;
import com.bank.debitcard.dto.DebitPaymentResponse;
import com.bank.debitcard.service.DebitCardService;
import com.bank.debitcard.service.DebitPaymentService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de tarjetas de debito y sus pagos.
 */
@RestController
@RequestMapping("/debit-cards")
@RequiredArgsConstructor
public class DebitCardController {

    private final DebitCardService debitCardService;
    private final DebitPaymentService debitPaymentService;

    /**
     * Emite una tarjeta.
     *
     * @param request datos de la tarjeta
     * @return la tarjeta creada, con estado 201
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Single<DebitCardResponse> create(@Valid @RequestBody DebitCardRequest request) {
        return debitCardService.create(request);
    }

    /**
     * Lista tarjetas.
     *
     * @param customerId filtro opcional por cliente
     * @return flujo de tarjetas
     */
    @GetMapping
    public Flowable<DebitCardResponse> findAll(@RequestParam(required = false) String customerId) {
        return debitCardService.findAll(customerId);
    }

    /**
     * Busca una tarjeta.
     *
     * @param id identificador
     * @return la tarjeta
     */
    @GetMapping("/{id}")
    public Single<DebitCardResponse> findById(@PathVariable String id) {
        return debitCardService.findById(id);
    }

    /**
     * Actualiza una tarjeta.
     *
     * @param id      identificador
     * @param request datos nuevos
     * @return la tarjeta actualizada
     */
    @PutMapping("/{id}")
    public Single<DebitCardResponse> update(@PathVariable String id, @Valid @RequestBody DebitCardRequest request) {
        return debitCardService.update(id, request);
    }

    /**
     * Elimina una tarjeta.
     *
     * @param id identificador
     * @return completable vacio, con estado 204
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Completable delete(@PathVariable String id) {
        return debitCardService.delete(id);
    }

    /**
     * Asocia otra cuenta a la tarjeta.
     *
     * @param id      identificador de la tarjeta
     * @param request cuenta a asociar
     * @return la tarjeta actualizada
     */
    @PostMapping("/{id}/accounts")
    public Single<DebitCardResponse> addAccount(@PathVariable String id,
                                                @Valid @RequestBody AccountLinkRequest request) {
        return debitCardService.addAccount(id, request.getAccountId());
    }

    /**
     * Cambia la cuenta principal.
     *
     * @param id      identificador de la tarjeta
     * @param request nueva cuenta principal
     * @return la tarjeta actualizada
     */
    @PutMapping("/{id}/main-account")
    public Single<DebitCardResponse> changeMainAccount(@PathVariable String id,
                                                       @Valid @RequestBody AccountLinkRequest request) {
        return debitCardService.changeMainAccount(id, request.getAccountId());
    }

    /**
     * Paga con la tarjeta. Es asincrono: responde 202 con el pago en
     * {@code PENDING}; el estado final se consulta con el GET del pago.
     *
     * @param id      identificador de la tarjeta
     * @param request importe y descripcion
     * @return el pago aceptado, con estado 202
     */
    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Single<DebitPaymentResponse> pay(@PathVariable String id, @Valid @RequestBody DebitPaymentRequest request) {
        return debitPaymentService.pay(id, request);
    }

    /**
     * Lista los pagos de la tarjeta.
     *
     * @param id identificador de la tarjeta
     * @return pagos
     */
    @GetMapping("/{id}/payments")
    public Flowable<DebitPaymentResponse> payments(@PathVariable String id) {
        return debitPaymentService.findByCard(id);
    }

    /**
     * Consulta el estado de un pago.
     *
     * @param id        identificador de la tarjeta
     * @param paymentId identificador del pago
     * @return el pago
     */
    @GetMapping("/{id}/payments/{paymentId}")
    public Single<DebitPaymentResponse> payment(@PathVariable String id, @PathVariable String paymentId) {
        return debitPaymentService.findById(id, paymentId);
    }
}
