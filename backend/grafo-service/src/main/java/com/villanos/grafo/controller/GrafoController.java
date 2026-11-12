package com.villanos.grafo.controller;

import com.villanos.grafo.model.Parada;
import com.villanos.grafo.model.RedGrafo;
import com.villanos.grafo.model.Tramo;
import com.villanos.grafo.service.GrafoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/redes")
@CrossOrigin(origins = "*")
public class GrafoController {

    private final GrafoService grafoService;

    public GrafoController(GrafoService grafoService) {
        this.grafoService = grafoService;
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
    public ResponseEntity<Parada> agregarParada(@PathVariable int id, @RequestBody Parada parada) {
        Parada creada = grafoService.agregarParada(id, parada);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}/tramos/{tramoId}/estado")
    public ResponseEntity<Tramo> cambiarEstadoTramo(@PathVariable int id,
                                                    @PathVariable int tramoId,
                                                    @RequestParam boolean abierto) {
        return grafoService.cambiarEstadoTramo(id, tramoId, abierto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
