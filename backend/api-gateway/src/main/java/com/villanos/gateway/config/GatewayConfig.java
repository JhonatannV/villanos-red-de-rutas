package com.villanos.gateway.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
public class GatewayConfig {

    @Value("${services.ruta-service.timeout-ms:2000}")
    private int rutaTimeoutMs;

    @Value("${services.grafo-service.timeout-ms:5000}")
    private int grafoTimeoutMs;

    @Bean(name = "rutaRestTemplate")
    public RestTemplate rutaRestTemplate() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(rutaTimeoutMs, TimeUnit.MILLISECONDS))
                .setSocketTimeout(Timeout.of(rutaTimeoutMs, TimeUnit.MILLISECONDS))
                .build());

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(rutaTimeoutMs, TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(rutaTimeoutMs, TimeUnit.MILLISECONDS))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }

    @Bean(name = "grafoRestTemplate")
    public RestTemplate grafoRestTemplate() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(grafoTimeoutMs, TimeUnit.MILLISECONDS))
                .setSocketTimeout(Timeout.of(grafoTimeoutMs, TimeUnit.MILLISECONDS))
                .build());

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(grafoTimeoutMs, TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(grafoTimeoutMs, TimeUnit.MILLISECONDS))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }
}
