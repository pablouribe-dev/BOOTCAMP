package com.bank.transaction.client;

import com.bank.transaction.exception.BusinessRuleException;
import com.bank.transaction.exception.ServiceUnavailableException;
import com.bank.transaction.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

/**
 * Traduce los errores HTTP de los servicios remotos a excepciones locales.
 *
 * <p>Conserva la semantica de punta a punta: un 404 remoto sigue siendo 404 y
 * un 409 remoto (fondos insuficientes, limite excedido) sigue siendo 409.
 * Cualquier otro error se reporta como servicio no disponible, sin inventar
 * datos.</p>
 */
@Slf4j
public final class RemoteErrors {

    /**
     * Clase de utilidad: no se instancia.
     */
    private RemoteErrors() {
    }

    /**
     * Convierte la respuesta de error remota en la excepcion local equivalente.
     *
     * @param response     respuesta recibida del servicio remoto
     * @param productLabel nombre legible del producto consultado, por ejemplo
     *                     {@code "Account"}
     * @param productId    identificador del producto consultado
     * @param serviceName  nombre del servicio remoto, usado en los mensajes
     * @return mono con la excepcion a emitir
     */
    public static Mono<Throwable> translate(ClientResponse response,
                                            String productLabel,
                                            String productId,
                                            String serviceName) {
        int status = response.statusCode().value();
        if (status == HttpStatus.NOT_FOUND.value()) {
            log.warn("{} answered 404 for {} id={}", serviceName, productLabel, productId);
            return response.releaseBody()
                    .then(Mono.just(new ResourceNotFoundException(
                            productLabel + " not found with id " + productId)));
        }
        if (status == HttpStatus.CONFLICT.value()) {
            log.warn("{} rejected the operation on {} id={}", serviceName, productLabel, productId);
            return response.bodyToMono(RemoteErrorResponse.class)
                    .map(body -> (Throwable) new BusinessRuleException(
                            body.getMessage() != null
                                    ? body.getMessage()
                                    : serviceName + " rejected the operation"))
                    .defaultIfEmpty(new BusinessRuleException(serviceName + " rejected the operation"));
        }
        log.error("{} answered {} for {} id={}", serviceName, status, productLabel, productId);
        return response.releaseBody()
                .then(Mono.just(new ServiceUnavailableException(
                        serviceName + " answered an unexpected status " + status)));
    }
}
