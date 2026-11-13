package com.villanos.grafo;

import com.villanos.grafo.model.RedGrafo;
import com.villanos.grafo.service.GrafoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GrafoServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GrafoService grafoService;

    @Test
    void contextLoads() {
        assertNotNull(grafoService);
        assertNotNull(mockMvc);
    }

    @Test
    void actuatorHealthPublicoRetornaUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void peticionSinTokenRetorna401Unauthorized() throws Exception {
        // Criterio de aceptacion SCRUM-20: Peticion sin token responde 401
        mockMvc.perform(get("/api/redes/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void peticionConTokenAlteradoRetorna401Unauthorized() throws Exception {
        // Criterio de aceptacion SCRUM-20: Peticion con token alterado en su firma responde 401
        mockMvc.perform(get("/api/redes/1")
                        .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.tokenAlterado.firmaInvalida"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void peticionConTokenValidoRetorna200Ok() throws Exception {
        mockMvc.perform(get("/api/redes/1").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redId").value(1))
                .andExpect(jsonPath("$.paradas").isArray());
    }

    @Test
    void redInicialCargadaCon40ParadasY90Tramos() {
        Optional<RedGrafo> opt = grafoService.obtenerRed(1);
        assertTrue(opt.isPresent(), "La red 1 debe existir");
        RedGrafo red = opt.get();

        assertEquals(40, red.getParadas().size(), "Deben cargarse exactamente 40 paradas");
        assertEquals(90, red.getTramos().size(), "Deben cargarse exactamente 90 tramos");
    }

    @Test
    void puedeCambiarEstadoDeTramo() {
        // Cerrar tramo 90 (puente)
        var opt = grafoService.cambiarEstadoTramo(1, 90, false);
        assertTrue(opt.isPresent());
        assertFalse(opt.get().isAbierto(), "El tramo 90 debe quedar cerrado");

        // Restaurar estado abierto
        grafoService.cambiarEstadoTramo(1, 90, true);
    }
}
