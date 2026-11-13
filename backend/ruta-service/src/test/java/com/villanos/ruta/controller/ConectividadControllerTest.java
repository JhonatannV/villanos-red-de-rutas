package com.villanos.ruta.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConectividadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor consultor() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_consultor"));
    }

    @Test
    void conTodoAbiertoHayUnaComponenteYElPuenteEsElTramo90() throws Exception {
        mockMvc.perform(get("/api/rutas/conectividad").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.componentes").value(1))
                .andExpect(jsonPath("$.aisladas").isEmpty())
                .andExpect(jsonPath("$.puentes[0].id").value(90))
                .andExpect(jsonPath("$.puentes[0].nombre").value("Salida Sur - Peaje Aeropuerto"));
    }

    @Test
    void alCerrarElTramo90PasaDe1a2ComponentesYNombraLasAisladas() throws Exception {
        mockMvc.perform(get("/api/rutas/conectividad?sin=90").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.componentesConTodoAbierto").value(1))
                .andExpect(jsonPath("$.componentes").value(2))
                .andExpect(jsonPath("$.grupos[0].tamano").value(37))
                .andExpect(jsonPath("$.aisladas[0]").value(37))
                .andExpect(jsonPath("$.aisladas[2]").value(39))
                .andExpect(jsonPath("$.aisladasNombres[1]").value("Terminal Aérea"))
                .andExpect(jsonPath("$.mensaje", containsString("pasó de 1 a 2")));
    }

    @Test
    void cerrarUnTramoQueNoEsPuenteNoAislaNada() throws Exception {
        mockMvc.perform(get("/api/rutas/conectividad?sin=1").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.componentes").value(1))
                .andExpect(jsonPath("$.mensaje", containsString("ninguna parada queda incomunicada")));
    }

    @Test
    void unTramoInexistenteResponde400() throws Exception {
        mockMvc.perform(get("/api/rutas/conectividad?sin=999").with(consultor()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calcularConElTramo90CerradoDiceQueNoHayRuta() throws Exception {
        mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=38&sin=90").with(consultor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costoTotal").value(-1))
                .andExpect(jsonPath("$.mensaje").exists());
    }
}
