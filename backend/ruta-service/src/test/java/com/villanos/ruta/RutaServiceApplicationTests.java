package com.villanos.ruta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RutaServiceApplicationTests {

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
    void endpointCalcularRutaDevuelveCasoManualEsperado() {
        // Caso 1: 0 -> 11 en red 1 (esperado costo 10, camino [0, 9, 10, 11])
        String url = "http://localhost:" + port + "/api/rutas/calcular?desde=0&hasta=11&redId=1";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        assertEquals(10, ((Number) body.get("costoTotal")).intValue());
        assertEquals(List.of(0, 9, 10, 11), body.get("camino"));
        assertNotNull(body.get("modoEfectivo"));
    }
}
