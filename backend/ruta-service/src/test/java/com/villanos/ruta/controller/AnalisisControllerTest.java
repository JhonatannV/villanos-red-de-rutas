package com.villanos.ruta.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalisisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor consultor() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_consultor"));
    }

    @Test
    void ciclosDeLaRed40() throws Exception {
        mockMvc.perform(get("/api/rutas/ciclos").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneCiclos").value(true))
                .andExpect(jsonPath("$.numeroCiclos").value(51))
                .andExpect(jsonPath("$.ciclos", hasSize(51)))
                .andExpect(jsonPath("$.ciclos[0].longitud").value(3))
                .andExpect(jsonPath("$.ciclos[0].nombres").isArray());
    }

    @Test
    void ciclosConLimite() throws Exception {
        mockMvc.perform(get("/api/rutas/ciclos?limite=5").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroCiclos").value(51))
                .andExpect(jsonPath("$.ciclos", hasSize(5)));
        mockMvc.perform(get("/api/rutas/ciclos?limite=-1").with(consultor()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memoriaListaContraMatriz() throws Exception {
        mockMvc.perform(get("/api/rutas/memoria").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redActual.paradas").value(40))
                .andExpect(jsonPath("$.redActual.tramos").value(90))
                .andExpect(jsonPath("$.redActual.bytesListaAdyacencia").value(1604))
                .andExpect(jsonPath("$.redActual.bytesMatrizAdyacencia").value(6400))
                .andExpect(jsonPath("$.redGrande.bytesListaAdyacencia").value(19204))
                .andExpect(jsonPath("$.redGrande.bytesMatrizAdyacencia").value(640000))
                .andExpect(jsonPath("$.formulas.lista").exists());
    }
}
