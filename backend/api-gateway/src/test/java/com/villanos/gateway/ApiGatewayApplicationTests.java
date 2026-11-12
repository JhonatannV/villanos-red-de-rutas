package com.villanos.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        assertNotNull(restTemplate);
    }

    @Test
    void actuatorHealthRetornaUp() {
        ResponseEntity<Map> response = restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    void rutaServiceApagadoDevuelve503ConDetalleExplicativo() {
        // Peticion hacia ruta-service cuando este no esta corriendo
        ResponseEntity<Map> response = restTemplate.getForEntity("http://localhost:" + port + "/api/rutas/calcular?desde=0&hasta=11", Map.class);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode(), "Debe responder HTTP 503 segun RQ-01");

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(503, body.get("status"));
        assertEquals("ruta-service", body.get("servicio"));
        assertTrue(body.get("mensaje").toString().contains("no responde"));
    }
}
