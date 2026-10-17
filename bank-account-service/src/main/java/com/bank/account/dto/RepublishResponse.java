package com.bank.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de {@code POST /admin/events/republish}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepublishResponse {

    /** Tipo de evento republicado. */
    private String eventType;

    /** Cantidad de eventos publicados. */
    private long republished;
}
