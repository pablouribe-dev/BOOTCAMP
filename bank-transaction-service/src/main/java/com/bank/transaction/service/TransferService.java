package com.bank.transaction.service;

import com.bank.transaction.dto.TransferRequest;
import com.bank.transaction.dto.TransferResponse;
import io.reactivex.rxjava3.core.Single;

/**
 * Transferencias entre cuentas del mismo banco.
 */
public interface TransferService {

    /**
     * Transfiere dinero de una cuenta a otra, propia o de un tercero.
     *
     * <p>No hay transaccion distribuida: si el deposito en el destino falla, se
     * compensa devolviendo el dinero al origen y no se guarda ningun
     * movimiento.</p>
     *
     * @param request origen, numero de cuenta destino, importe y descripcion
     * @return los dos movimientos registrados
     */
    Single<TransferResponse> transfer(TransferRequest request);
}
