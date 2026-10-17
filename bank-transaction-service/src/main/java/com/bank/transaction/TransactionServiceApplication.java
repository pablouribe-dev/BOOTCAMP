package com.bank.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del microservicio de movimientos.
 *
 * <p>Es el dueno de los movimientos y de las reglas sobre ellos. Orquesta
 * depositos, retiros, pagos y consumos: valida las restricciones del producto,
 * pide el cambio de saldo al servicio dueno del producto y solo entonces
 * registra el movimiento.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TransactionServiceApplication {

    /**
     * Arranca el microservicio.
     *
     * @param args argumentos de linea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
