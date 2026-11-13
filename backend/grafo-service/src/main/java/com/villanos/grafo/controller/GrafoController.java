package com.villanos.grafo.controller;

import com.villanos.grafo.model.Parada;
import com.villanos.grafo.model.RedGrafo;
import com.villanos.grafo.model.RegistroAuditoria;
import com.villanos.grafo.model.Tramo;
import com.villanos.grafo.service.AuditoriaService;
import com.villanos.grafo.service.GrafoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/redes")
@CrossOrigin(origins = "*")
public class GrafoController {

    private final GrafoService grafoService;
    private final AuditoriaService auditoriaService;

    public GrafoController(GrafoService grafoService, AuditoriaService auditoriaService) {
        this.grafoService = grafoService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RedGrafo> obtenerRed(@PathVariable int id) {
        return grafoService.obtenerRed(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/paradas")
    public ResponseEntity<List<Parada>> obtenerParadas(@PathVariable int id) {
        return ResponseEntity.ok(grafoService.obtenerParadas(id));
    }

    @GetMapping("/{id}/tramos")
    public ResponseEntity<List<Tramo>> obtenerTramos(@PathVariable int id) {
        return ResponseEntity.ok(grafoService.obtenerTramos(id));
    }

    @PostMapping("/{id}/paradas")
    public ResponseEntity<Parada> agregarParada(@PathVariable int id,
                                                @RequestBody Parada parada,
                                                Authentication authentication) {
        Parada creada = grafoService.agregarParada(id, parada);
        String sub = extraerSub(authentication);
        String username = extraerUsername(authentication);
        auditoriaService.registrarCambio(sub, username, "CREAR_PARADA",
                "Parada agregada: id=" + creada.getId() + ", nombre=" + creada.getNombre(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}/tramos/{tramoId}/estado")
    public ResponseEntity<Tramo> cambiarEstadoTramo(@PathVariable int id,
                                                    @PathVariable int tramoId,
                                                    @RequestParam boolean abierto,
                                                    Authentication authentication) {
        return grafoService.cambiarEstadoTramo(id, tramoId, abierto)
                .map(tramo -> {
                    String sub = extraerSub(authentication);
                    String username = extraerUsername(authentication);
                    auditoriaService.registrarCambio(sub, username, "CAMBIAR_ESTADO_TRAMO",
                            "Tramo id=" + tramoId + " cambiado a abierto=" + abierto, id);
                    return ResponseEntity.ok(tramo);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/auditoria")
    public ResponseEntity<List<RegistroAuditoria>> obtenerAuditoriaPorRed(@PathVariable int id) {
        return ResponseEntity.ok(auditoriaService.obtenerRegistrosPorRed(id));
    }

    @GetMapping("/auditoria")
    public ResponseEntity<List<RegistroAuditoria>> obtenerTodaAuditoria() {
        return ResponseEntity.ok(auditoriaService.obtenerTodos());
    }

    private String extraerSub(Authentication authentication) {
        if (authentication == null) {
            return "anonimo";
        }
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            String sub = jwtToken.getToken().getSubject();
            if (sub != null && !sub.isBlank()) {
                return sub;
            }
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                return sub;
            }
        }
        return authentication.getName();
    }

    private String extraerUsername(Authentication authentication) {
        if (authentication == null) {
            return "desconocido";
        }
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            String username = jwtToken.getToken().getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
        return authentication.getName();
    }
}
