package com.bank.yanki.controller;

import com.bank.yanki.dto.YankiPaymentRequest;
import com.bank.yanki.dto.YankiPaymentResponse;
import com.bank.yanki.service.YankiPaymentService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de pagos Yanki.
 */
@RestController
@RequestMapping("/yanki/payments")
@RequiredArgsConstructor
public class YankiPaymentController {

    private final YankiPaymentService yankiPaymentService;

    /**
     * Envia un pago por numero de celular. Responde 202: el estado final se
     * consulta con el GET del pago.
     *
     * @param request celulares, importe y descripcion
     * @return el pago aceptado, con estado 202
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Single<YankiPaymentResponse> send(@Valid @RequestBody YankiPaymentRequest request) {
        return yankiPaymentService.send(request);
    }

    /**
     * Consulta un pago.
     *
     * @param id identificador
     * @return el pago
     */
    @GetMapping("/{id}")
    public Single<YankiPaymentResponse> findById(@PathVariable String id) {
        return yankiPaymentService.findById(id);
    }

    /**
     * Historial de pagos.
     *
     * @param phoneNumber filtro opcional por celular
     * @return pagos
     */
    @GetMapping
    public Flowable<YankiPaymentResponse> findAll(@RequestParam(required = false) String phoneNumber) {
        return yankiPaymentService.findAll(phoneNumber);
    }
}
