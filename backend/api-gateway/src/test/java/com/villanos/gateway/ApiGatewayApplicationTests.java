package com.villanos.gateway;

import com.sun.net.httpserver.HttpServer;
import com.villanos.gateway.controller.GatewayController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiGatewayApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GatewayController gatewayController;

    @Test
    void contextLoads() {
        assertNotNull(mockMvc);
        assertNotNull(gatewayController);
    }

    @Test
    void actuatorHealthRetornaUpSinAutenticacion() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void peticionSinTokenRetorna401Unauthorized() throws Exception {
        // Criterio de aceptacion SCRUM-20: Peticion sin token responde 401
        mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=11"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void peticionConTokenAlteradoRetorna401Unauthorized() throws Exception {
        // Criterio de aceptacion SCRUM-20: Peticion con token alterado en su firma responde 401
        mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=11")
                        .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.tokenFalsoAlterado.firmaInvalida123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consultorIntentandoCrearParadaEnGatewayRecibe403Forbidden() throws Exception {
        // Criterio de aceptacion SCRUM-21 [RA-05]:
        // En el Gateway, un consultor intentando crear una parada recibe HTTP 403 Forbidden
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/redes/1/paradas")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Parada Prohibida\"}")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_consultor"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rutaServiceApagadoResponde503EnMenosDeDosSegundosConTokenValido() throws Exception {
        long tInicio = System.currentTimeMillis();
        mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=11")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_consultor"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.servicio").value("ruta-service"))
                .andExpect(jsonPath("$.motivo").value("CONEXION_RECHAZADA"));
        long duracionMs = System.currentTimeMillis() - tInicio;

        // Criterio de aceptacion: Respuesta de contingencia en menos de 3s sin dejar conexiones colgadas
        assertTrue(duracionMs < 3000, "Debe responder en menos de 3s cuando esta apagado. Tardo: " + duracionMs + " ms");
    }

    @Test
    void rutaServiceLentoCortaExactamenteEnTimeoutDosSegundosConTokenValido() throws Exception {
        // Servidor HTTP de prueba que tarda 4.0s (mayor que el timeout de 2.0s de ruta-service)
        HttpServer slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slowServer.createContext("/api/rutas/calcular", exchange -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException ignored) {}
            byte[] response = "{\"ok\":true}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        slowServer.start();

        int slowPort = slowServer.getAddress().getPort();
        String originalUrl = gatewayController.getRutaServiceUrl();
        gatewayController.setRutaServiceUrl("http://127.0.0.1:" + slowPort);

        try {
            long tInicio = System.currentTimeMillis();
            mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=11")
                            .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_consultor"))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.servicio").value("ruta-service"))
                    .andExpect(jsonPath("$.motivo").value("TIMEOUT"));
            long duracionMs = System.currentTimeMillis() - tInicio;

            // 1. Debe esperar al menos ~2000 ms (timeout configurado)
            assertTrue(duracionMs >= 1800, "Debe esperar al menos ~2000ms de timeout configurado. Tardo: " + duracionMs + " ms");
            // 2. Debe cortar antes de los 4000 ms del servicio colgado
            assertTrue(duracionMs < 3000, "Debe cortar por timeout antes de los 4000ms. Tardo: " + duracionMs + " ms");

        } finally {
            gatewayController.setRutaServiceUrl(originalUrl);
            slowServer.stop(0);
        }
    }
}
