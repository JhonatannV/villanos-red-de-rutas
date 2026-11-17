package com.villanos.ruta.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RF-04: /api/rutas/calcular entrega la traza paso a paso cuando se pide con traza=true. */
@SpringBootTest
@AutoConfigureMockMvc
class RutaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor consultor() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_consultor"));
    }

    @Test
    void sinTrazaNoAgregaPasos() throws Exception {
        mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=5").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camino").isArray())
                .andExpect(jsonPath("$.pasos").doesNotExist());
    }

    @Test
    void conTrazaEntregaPasosQueTerminanEnElDestino() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=5&traza=true").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trazaCalculadaCon").value("JAVA"))
                .andExpect(jsonPath("$.pasos[0].paso").value(1))
                .andExpect(jsonPath("$.pasos[0].actual").value(0))
                .andExpect(jsonPath("$.pasos[0].distancia").value(0))
                .andExpect(jsonPath("$.pasos[0].desde").value(-1))
                .andExpect(jsonPath("$.pasos[0].relajaciones[0].vecino").isNumber())
                .andExpect(jsonPath("$.pasos[0].relajaciones[0].mejora").value(true))
                .andReturn();

        String json = res.getResponse().getContentAsString();
        List<Integer> actuales = JsonPath.read(json, "$.pasos[*].actual");
        List<Integer> distancias = JsonPath.read(json, "$.pasos[*].distancia");
        int costo = JsonPath.read(json, "$.costoTotal");

        assertEquals(5, actuales.get(actuales.size() - 1), "el último paso cierra el destino");
        assertEquals(costo, distancias.get(distancias.size() - 1), "la distancia final es el costo de la ruta");
        for (int i = 1; i < distancias.size(); i++) {
            // Dijkstra cierra las paradas en orden de distancia no decreciente
            assertEquals(true, distancias.get(i) >= distancias.get(i - 1));
        }
    }

    @Test
    void trazaRespetaElTramoCerrado() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=5&traza=true&sin=1").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tramoCerrado").value(1))
                .andReturn();
        String json = res.getResponse().getContentAsString();
        int costo = JsonPath.read(json, "$.costoTotal");
        List<Integer> distancias = JsonPath.read(json, "$.pasos[*].distancia");
        assertEquals(costo, distancias.get(distancias.size() - 1));
    }
}
