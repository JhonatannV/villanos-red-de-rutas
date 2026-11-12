package com.villanos.gateway;

import com.sun.net.httpserver.HttpServer;
import com.villanos.gateway.controller.GatewayController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;
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
    void actuatorHealthRetornaUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void rutaServiceApagadoResponde503EnMenosDeDosSegundos() throws Exception {
        long tInicio = System.currentTimeMillis();
        mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=11"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.servicio").value("ruta-service"))
                .andExpect(jsonPath("$.motivo").value("CONEXION_RECHAZADA"));
        long duracionMs = System.currentTimeMillis() - tInicio;

        // Criterio de aceptacion SCRUM-17: HTTP 503 en menos de 2s sin dejar conexiones colgadas
        assertTrue(duracionMs < 2000, "Debe responder en menos de 2s cuando esta apagado. Tardo: " + duracionMs + " ms");
    }

    @Test
    void rutaServiceLentoCortaExactamenteEnTimeoutDosSegundos() throws Exception {
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
            mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=11"))
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
