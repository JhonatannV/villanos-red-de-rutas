package com.villanos.ruta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RutaServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        assertNotNull(mockMvc);
    }

    @Test
    void actuatorHealthRetornaUpSinToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void peticionSinTokenRetorna401Unauthorized() throws Exception {
        // Criterio de aceptacion SCRUM-20: Peticion sin token responde 401
        mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=11&redId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void peticionConTokenAlteradoRetorna401Unauthorized() throws Exception {
        // Criterio de aceptacion SCRUM-20: Peticion con token alterado en su firma responde 401
        mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=11&redId=1")
                        .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.tokenFalso.firmaAlterada"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointCalcularRutaDevuelveCasoManualEsperadoConTokenValido() throws Exception {
        // Caso 1: 0 -> 11 en red 1 (esperado costo 10, camino [0, 9, 10, 11])
        mockMvc.perform(get("/api/rutas/calcular?desde=0&hasta=11&redId=1")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_consultor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costoTotal").value(10))
                .andExpect(jsonPath("$.camino[0]").value(0))
                .andExpect(jsonPath("$.camino[1]").value(9))
                .andExpect(jsonPath("$.camino[2]").value(10))
                .andExpect(jsonPath("$.camino[3]").value(11))
                .andExpect(jsonPath("$.modoEfectivo").exists());
    }
}
