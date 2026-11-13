package com.villanos.ruta.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecorridoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor consultor() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_consultor"));
    }

    @Test
    void anchuraDesdeTerminalVisitaLas40Paradas() throws Exception {
        mockMvc.perform(get("/api/rutas/recorrido?desde=0&modo=anchura").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("anchura"))
                .andExpect(jsonPath("$.visitadas").value(40))
                .andExpect(jsonPath("$.orden[0]").value(0))
                .andExpect(jsonPath("$.ordenNombres[0]").value("Terminal de Transportes"))
                .andExpect(jsonPath("$.pasos[0].nivel").value(0))
                .andExpect(jsonPath("$.pasos[0].cola").isArray());
    }

    @Test
    void profundidadDevuelvePilaYProfundidad() throws Exception {
        mockMvc.perform(get("/api/rutas/recorrido?desde=0&modo=profundidad").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("profundidad"))
                .andExpect(jsonPath("$.visitadas").value(40))
                .andExpect(jsonPath("$.pasos[1].profundidad").value(1))
                .andExpect(jsonPath("$.pasos[0].pila").isArray());
    }

    @Test
    void conTramoCerradoInformaParadasNoAlcanzadas() throws Exception {
        mockMvc.perform(get("/api/rutas/recorrido?desde=0&modo=anchura&sin=90").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitadas").value(37))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void paradaInexistenteOModoInvalidoResponden400() throws Exception {
        mockMvc.perform(get("/api/rutas/recorrido?desde=99&modo=anchura").with(consultor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
        mockMvc.perform(get("/api/rutas/recorrido?desde=0&modo=diagonal").with(consultor()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calcularConParadaInexistenteResponde400() throws Exception {
        mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=99").with(consultor()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/rutas/recorrido?desde=0&modo=anchura"))
                .andExpect(status().isUnauthorized());
    }
}
