package com.bank.account.controller;

import com.bank.account.dto.RepublishResponse;
import com.bank.account.service.EventRepublishService;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de administracion. En el gateway se exponen bajo
 * {@code /admin/accounts/**} y exigen rol {@code ADMIN}.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final EventRepublishService eventRepublishService;

    /**
     * Vuelve a publicar un {@code ACCOUNT_UPSERTED} por cada cuenta guardada.
     *
     * @return cantidad de eventos publicados, con estado 200
     */
    @PostMapping("/events/republish")
    public Single<RepublishResponse> republish() {
        return eventRepublishService.republishAll();
    }
}
