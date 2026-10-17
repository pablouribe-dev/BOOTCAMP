package com.bank.yanki.controller;

import com.bank.yanki.dto.CardLinkRequest;
import com.bank.yanki.dto.WalletRequest;
import com.bank.yanki.dto.WalletResponse;
import com.bank.yanki.service.WalletService;
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
 * Endpoints de monederos Yanki.
 */
@RestController
@RequestMapping("/yanki/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * Crea un monedero.
     *
     * @param request datos del monedero
     * @return el monedero creado, con estado 201
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Single<WalletResponse> create(@Valid @RequestBody WalletRequest request) {
        return walletService.create(request);
    }

    /**
     * Lista monederos.
     *
     * @param phoneNumber filtro opcional por celular
     * @return monederos
     */
    @GetMapping
    public Flowable<WalletResponse> findAll(@RequestParam(required = false) String phoneNumber) {
        return walletService.findAll(phoneNumber);
    }

    /**
     * Busca un monedero.
     *
     * @param id identificador
     * @return el monedero
     */
    @GetMapping("/{id}")
    public Single<WalletResponse> findById(@PathVariable String id) {
        return walletService.findById(id);
    }

    /**
     * Actualiza un monedero.
     *
     * @param id      identificador
     * @param request datos nuevos
     * @return el monedero actualizado
     */
    @PutMapping("/{id}")
    public Single<WalletResponse> update(@PathVariable String id, @Valid @RequestBody WalletRequest request) {
        return walletService.update(id, request);
    }

    /**
     * Elimina un monedero.
     *
     * @param id identificador
     * @return completable vacio, con estado 204
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Completable delete(@PathVariable String id) {
        return walletService.delete(id);
    }

    /**
     * Asocia una tarjeta de debito.
     *
     * @param id      monedero
     * @param request numero de tarjeta
     * @return el monedero actualizado
     */
    @PutMapping("/{id}/debit-card")
    public Single<WalletResponse> linkDebitCard(@PathVariable String id, @Valid @RequestBody CardLinkRequest request) {
        return walletService.linkDebitCard(id, request.getCardNumber());
    }

    /**
     * Quita la tarjeta asociada.
     *
     * @param id monedero
     * @return el monedero actualizado
     */
    @DeleteMapping("/{id}/debit-card")
    public Single<WalletResponse> unlinkDebitCard(@PathVariable String id) {
        return walletService.unlinkDebitCard(id);
    }
}
