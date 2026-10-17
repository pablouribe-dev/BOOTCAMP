package com.bank.transaction.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Beans de {@link WebClient} usados para hablar con los servicios duenos de
 * cada producto.
 *
 * <p>El builder es {@link LoadBalanced}: las URLs base son nombres de Eureka
 * (por ejemplo {@code http://bank-account-service}) y Spring Cloud LoadBalancer
 * las resuelve a una instancia registrada.</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final ServiceUrlProperties serviceUrlProperties;

    /**
     * Builder de WebClient balanceado por nombre de servicio.
     *
     * @return builder con el filtro de Spring Cloud LoadBalancer
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    /**
     * Crea el cliente HTTP apuntado a {@code bank-account-service}.
     *
     * @param loadBalancedWebClientBuilder builder balanceado
     * @return cliente con la URL base del servicio de cuentas
     */
    @Bean
    public WebClient accountWebClient(WebClient.Builder loadBalancedWebClientBuilder) {
        return loadBalancedWebClientBuilder.clone().baseUrl(serviceUrlProperties.getAccountServiceUrl()).build();
    }

    /**
     * Crea el cliente HTTP apuntado a {@code bank-credit-service}.
     *
     * @param loadBalancedWebClientBuilder builder balanceado
     * @return cliente con la URL base del servicio de creditos
     */
    @Bean
    public WebClient creditWebClient(WebClient.Builder loadBalancedWebClientBuilder) {
        return loadBalancedWebClientBuilder.clone().baseUrl(serviceUrlProperties.getCreditServiceUrl()).build();
    }

    /**
     * Crea el cliente HTTP apuntado a {@code bank-credit-card-service}.
     *
     * @param loadBalancedWebClientBuilder builder balanceado
     * @return cliente con la URL base del servicio de tarjetas de credito
     */
    @Bean
    public WebClient creditCardWebClient(WebClient.Builder loadBalancedWebClientBuilder) {
        return loadBalancedWebClientBuilder.clone().baseUrl(serviceUrlProperties.getCreditCardServiceUrl()).build();
    }

    /**
     * Parte III: crea el cliente HTTP apuntado a {@code bank-customer-service}.
     *
     * @param loadBalancedWebClientBuilder builder balanceado
     * @return cliente con la URL base del servicio de clientes
     */
    @Bean
    public WebClient customerWebClient(WebClient.Builder loadBalancedWebClientBuilder) {
        return loadBalancedWebClientBuilder.clone().baseUrl(serviceUrlProperties.getCustomerServiceUrl()).build();
    }
}
