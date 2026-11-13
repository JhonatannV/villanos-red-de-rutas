package com.villanos.grafo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.villanos.grafo.model.Parada;
import com.villanos.grafo.model.RedGrafo;
import com.villanos.grafo.service.GrafoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GrafoServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GrafoService grafoService;

    @Autowired
    private ObjectMapper objectMapper;

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
        mockMvc.perform(get("/api/redes/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void peticionConTokenAlteradoRetorna401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/redes/1")
                        .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.tokenAlterado.firmaInvalida"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consultorConsultandoRedRecibe200Ok() throws Exception {
        // Rol consultor tiene permiso de lectura
        mockMvc.perform(get("/api/redes/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_consultor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redId").value(1))
                .andExpect(jsonPath("$.paradas").isArray());
    }

    @Test
    void consultorIntentandoCrearParadaRecibe403Forbidden() throws Exception {
        // Criterio de aceptacion SCRUM-21 [RA-05]:
        // Un consultor intentando crear una parada o tramo recibe HTTP 403 Forbidden
        Parada nueva = new Parada(999, "Estacion Prohibida", 4.08, -76.19);
        String json = objectMapper.writeValueAsString(nueva);

        mockMvc.perform(post("/api/redes/1/paradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_consultor"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultorIntentandoCambiarEstadoTramoRecibe403Forbidden() throws Exception {
        // Criterio de aceptacion SCRUM-21 [RA-05]: Modificar tramo con rol consultor devuelve 403
        mockMvc.perform(put("/api/redes/1/tramos/90/estado?abierto=false")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_consultor"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void planificadorIntentandoCrearParadaRecibe201Created() throws Exception {
        // Rol planificador SI tiene permiso de creacion/modificacion
        Parada nueva = new Parada(100, "Estacion Permitida", 4.085, -76.195);
        String json = objectMapper.writeValueAsString(nueva);

        mockMvc.perform(post("/api/redes/1/paradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_planificador"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Estacion Permitida"));
    }

    @Test
    void planificadorIntentandoCambiarEstadoTramoRecibe200Ok() throws Exception {
        // Rol planificador SI tiene permiso de modificar estado de tramo
        mockMvc.perform(put("/api/redes/1/tramos/90/estado?abierto=false")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_planificador"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abierto").value(false));

        // Restaurar a true
        grafoService.cambiarEstadoTramo(1, 90, true);
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
        var opt = grafoService.cambiarEstadoTramo(1, 90, false);
        assertTrue(opt.isPresent());
        assertFalse(opt.get().isAbierto(), "El tramo 90 debe quedar cerrado");

        grafoService.cambiarEstadoTramo(1, 90, true);
    }
}
