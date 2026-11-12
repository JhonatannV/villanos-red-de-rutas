package com.villanos.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class GatewayConfig {

    @Value("${services.ruta-service.timeout-ms:2000}")
    private int rutaTimeoutMs;

    @Value("${services.grafo-service.timeout-ms:5000}")
    private int grafoTimeoutMs;

    @Bean(name = "rutaRestTemplate")
    public RestTemplate rutaRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(rutaTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(rutaTimeoutMs));
        return new RestTemplate(factory);
    }

    @Bean(name = "grafoRestTemplate")
    public RestTemplate grafoRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(grafoTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(grafoTimeoutMs));
        return new RestTemplate(factory);
    }
}
