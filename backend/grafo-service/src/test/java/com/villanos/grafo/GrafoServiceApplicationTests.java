package com.villanos.grafo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.villanos.grafo.model.Parada;
import com.villanos.grafo.model.RedGrafo;
import com.villanos.grafo.model.RegistroAuditoria;
import com.villanos.grafo.repository.AuditoriaRepository;
import com.villanos.grafo.service.GrafoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
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
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        auditoriaRepository.deleteAll();
        grafoService.cargarRedPorDefecto();
    }

    @Test
    void contextLoads() {
        assertNotNull(grafoService);
        assertNotNull(mockMvc);
        assertNotNull(auditoriaRepository);
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

    @Test
    void operacionCrearParadaGuardaAuditoriaConClaimSubYFecha() throws Exception {
        // SCRUM-22 [RA-06]: Guardar en base de datos el ID unico del usuario (claim 'sub') y fecha
        String subEsperado = "keycloak-sub-uuid-planificador-1234";
        String usernameEsperado = "planificador.operaciones";

        Parada nueva = new Parada(150, "Terminal Sur Auditada", 4.09, -76.20);
        String json = objectMapper.writeValueAsString(nueva);

        mockMvc.perform(post("/api/redes/1/paradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(subEsperado).claim("preferred_username", usernameEsperado))
                                .authorities(new SimpleGrantedAuthority("ROLE_planificador"))))
                .andExpect(status().isCreated());

        List<RegistroAuditoria> registros = auditoriaRepository.findAll();
        assertEquals(1, registros.size(), "Debe haberse guardado un registro de auditoria");

        RegistroAuditoria registro = registros.get(0);
        assertEquals(subEsperado, registro.getUsuarioSub(), "El claim 'sub' debe coincidir con el del token");
        assertEquals(usernameEsperado, registro.getUsuarioUsername());
        assertEquals("CREAR_PARADA", registro.getOperacion());
        assertNotNull(registro.getFecha(), "La fecha de auditoria debe registrarse");
        assertEquals(1, registro.getRedId());
        assertTrue(registro.getDetalle().contains("Terminal Sur Auditada"));
    }

    @Test
    void dosUsuariosDistintosRegistranIdentificadoresSubEstablesDiferentes() throws Exception {
        // Criterio de aceptacion SCRUM-22 [RA-06]:
        // "Tras operar con dos usuarios, la base de datos registra dos identificadores estables distintos provenientes del token."
        String subUsuarioA = "11111111-aaaa-bbbb-cccc-111111111111";
        String subUsuarioB = "22222222-dddd-eeee-ffff-222222222222";

        // Usuario A crea una parada
        Parada paradaA = new Parada(160, "Estacion Usuario A", 4.092, -76.198);
        mockMvc.perform(post("/api/redes/1/paradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paradaA))
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(subUsuarioA).claim("preferred_username", "usuario_a"))
                                .authorities(new SimpleGrantedAuthority("ROLE_planificador"))))
                .andExpect(status().isCreated());

        // Usuario B modifica el estado de un tramo
        mockMvc.perform(put("/api/redes/1/tramos/85/estado?abierto=false")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(subUsuarioB).claim("preferred_username", "usuario_b"))
                                .authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isOk());

        // Consultar base de datos
        List<RegistroAuditoria> registros = auditoriaRepository.findAll();
        assertEquals(2, registros.size(), "Deben existir dos registros en base de datos");

        RegistroAuditoria regA = registros.stream()
                .filter(r -> "CREAR_PARADA".equals(r.getOperacion()))
                .findFirst().orElseThrow();

        RegistroAuditoria regB = registros.stream()
                .filter(r -> "CAMBIAR_ESTADO_TRAMO".equals(r.getOperacion()))
                .findFirst().orElseThrow();

        // Verificacion del criterio de aceptacion
        assertEquals(subUsuarioA, regA.getUsuarioSub(), "El registro A debe guardar el sub del usuario A");
        assertEquals(subUsuarioB, regB.getUsuarioSub(), "El registro B debe guardar el sub del usuario B");
        assertNotEquals(regA.getUsuarioSub(), regB.getUsuarioSub(), "Los identificadores sub deben ser distintos y estables");
        assertNotNull(regA.getFecha());
        assertNotNull(regB.getFecha());
    }

    @Test
    void consultarAuditoriaPorEndpointRetornaRegistrosGuardados() throws Exception {
        // Guardar un registro manual y consultar via endpoint REST
        auditoriaRepository.save(new RegistroAuditoria(
                "33333333-sub-test",
                "auditor_test",
                java.time.Instant.now(),
                "MODIFICACION_PRUEBA",
                "Detalle de prueba",
                1
        ));

        mockMvc.perform(get("/api/redes/1/auditoria")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_consultor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].usuarioSub").value("33333333-sub-test"))
                .andExpect(jsonPath("$[0].operacion").value("MODIFICACION_PRUEBA"));
    }
}
