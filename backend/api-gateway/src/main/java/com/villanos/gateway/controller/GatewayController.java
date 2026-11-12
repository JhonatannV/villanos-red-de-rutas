package com.villanos.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class GatewayController {

    private final RestTemplate grafoRestTemplate;
    private final RestTemplate rutaRestTemplate;

    @Value("${services.grafo-service.url:http://localhost:8081}")
    private String grafoServiceUrl;

    @Value("${services.ruta-service.url:http://localhost:8082}")
    private String rutaServiceUrl;

    public GatewayController(@Qualifier("grafoRestTemplate") RestTemplate grafoRestTemplate,
                             @Qualifier("rutaRestTemplate") RestTemplate rutaRestTemplate) {
        this.grafoRestTemplate = grafoRestTemplate;
        this.rutaRestTemplate = rutaRestTemplate;
    }

    public String getRutaServiceUrl() {
        return rutaServiceUrl;
    }

    public void setRutaServiceUrl(String rutaServiceUrl) {
        this.rutaServiceUrl = rutaServiceUrl;
    }

    public String getGrafoServiceUrl() {
        return grafoServiceUrl;
    }

    public void setGrafoServiceUrl(String grafoServiceUrl) {
        this.grafoServiceUrl = grafoServiceUrl;
    }

    @RequestMapping(value = "/api/redes/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<?> proxyGrafo(HttpServletRequest request,
                                        @RequestBody(required = false) byte[] body) {
        return reenviarPeticion(request, body, grafoServiceUrl, grafoRestTemplate, "grafo-service");
    }

    @RequestMapping(value = "/api/rutas/**", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> proxyRuta(HttpServletRequest request,
                                       @RequestBody(required = false) byte[] body) {
        return reenviarPeticion(request, body, rutaServiceUrl, rutaRestTemplate, "ruta-service");
    }

    private ResponseEntity<?> reenviarPeticion(HttpServletRequest request,
                                               byte[] body,
                                               String targetBaseUrl,
                                               RestTemplate restTemplate,
                                               String serviceName) {
        try {
            String path = request.getRequestURI();
            String query = request.getQueryString();
            String targetUrl = targetBaseUrl + path + (query != null ? "?" + query : "");

            HttpHeaders headers = new HttpHeaders();
            Collections.list(request.getHeaderNames()).forEach(headerName -> {
                if (!headerName.equalsIgnoreCase(HttpHeaders.HOST)) {
                    headers.put(headerName, Collections.list(request.getHeaders(headerName)));
                }
            });

            HttpMethod method = HttpMethod.valueOf(request.getMethod());
            HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);

            return restTemplate.exchange(URI.create(targetUrl), method, entity, byte[].class);

        } catch (ResourceAccessException e) {
            // RQ-01: El servicio de atras no responde o excedio el timeout de 2s -> HTTP 503 explicativo
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("timestamp", Instant.now().toString());
            errorBody.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
            errorBody.put("error", "Service Unavailable");
            errorBody.put("servicio", serviceName);

            boolean esTimeout = (e.getCause() instanceof java.net.SocketTimeoutException)
                    || (e.getMessage() != null && e.getMessage().toLowerCase().contains("timed out"));

            if (esTimeout) {
                errorBody.put("motivo", "TIMEOUT");
                errorBody.put("mensaje", "El microservicio " + serviceName + " excedio el tiempo limite de espera (timeout 2s).");
            } else {
                errorBody.put("motivo", "CONEXION_RECHAZADA");
                errorBody.put("mensaje", "El microservicio " + serviceName + " no esta disponible (servicio apagado o conexion rechazada).");
            }
            errorBody.put("detalle", e.getMessage());

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorBody);

        } catch (HttpStatusCodeException e) {
            // Propagar el codigo HTTP que devolvio el servicio de atras (ej. 400, 404, etc.)
            return ResponseEntity.status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("timestamp", Instant.now().toString());
            errorBody.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            errorBody.put("error", "Gateway Error");
            errorBody.put("mensaje", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorBody);
        }
    }
}
