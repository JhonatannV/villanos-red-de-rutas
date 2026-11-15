package com.villanos.grafo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.villanos.grafo.model.Parada;
import com.villanos.grafo.model.RedGrafo;
import com.villanos.grafo.model.Tramo;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GrafoService {

    private final ObjectMapper objectMapper;
    private final Map<Integer, RedGrafo> redes = new ConcurrentHashMap<>();

    @Value("${app.datos.ruta-json:data/grafo_40_paradas.json}")
    private String rutaJsonConfig;

    public GrafoService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void inicializar() {
        cargarRedPorDefecto();
    }

    public synchronized void cargarRedPorDefecto() {
        cargarArchivoRed(rutaJsonConfig, 1);
        cargarArchivoRed("grafo_40_paradas.json", 1);
        cargarArchivoRed("grafo_400_paradas.json", 2);

        if (!redes.containsKey(1)) {
            RedGrafo redFallback = new RedGrafo(1, "Red Base Fallback", new ArrayList<>(), new ArrayList<>());
            redes.put(1, redFallback);
        }
    }

    private void cargarArchivoRed(String nombreArchivo, int idFallback) {
        if (nombreArchivo == null || nombreArchivo.isBlank()) return;
        List<Path> candidatos = Arrays.asList(
                Paths.get(nombreArchivo),
                Paths.get("data", nombreArchivo),
                Paths.get("backend", "grafo-service", "data", nombreArchivo),
                Paths.get("..", "grafo-service", "data", nombreArchivo)
        );

        for (Path p : candidatos) {
            File f = p.toFile();
            if (f.exists() && f.isFile()) {
                try {
                    RedGrafo red = objectMapper.readValue(f, RedGrafo.class);
                    redes.put(red.getRedId(), red);
                    return;
                } catch (IOException e) {
                    System.err.println("Error leyendo JSON de red " + nombreArchivo + ": " + e.getMessage());
                }
            }
        }
    }

    public Optional<RedGrafo> obtenerRed(int redId) {
        return Optional.ofNullable(redes.get(redId));
    }

    public List<Parada> obtenerParadas(int redId) {
        return obtenerRed(redId).map(RedGrafo::getParadas).orElseGet(Collections::emptyList);
    }

    public List<Tramo> obtenerTramos(int redId) {
        return obtenerRed(redId).map(RedGrafo::getTramos).orElseGet(Collections::emptyList);
    }

    public synchronized Parada agregarParada(int redId, Parada nuevaParada) {
        RedGrafo red = redes.computeIfAbsent(redId, id -> new RedGrafo(id, "Red " + id, new ArrayList<>(), new ArrayList<>()));
        int maxId = red.getParadas().stream().mapToInt(Parada::getId).max().orElse(-1);
        if (nuevaParada.getId() <= maxId) {
            nuevaParada.setId(maxId + 1);
        }
        red.getParadas().add(nuevaParada);
        return nuevaParada;
    }

    public synchronized Tramo agregarTramo(int redId, Tramo nuevoTramo) {
        RedGrafo red = redes.computeIfAbsent(redId, id -> new RedGrafo(id, "Red " + id, new ArrayList<>(), new ArrayList<>()));
        int maxId = red.getTramos().stream().mapToInt(Tramo::getId).max().orElse(-1);
        if (nuevoTramo.getId() <= maxId) {
            nuevoTramo.setId(maxId + 1);
        }
        red.getTramos().add(nuevoTramo);
        return nuevoTramo;
    }

    public synchronized Optional<Tramo> cambiarEstadoTramo(int redId, int tramoId, boolean abierto) {
        Optional<RedGrafo> opt = obtenerRed(redId);
        if (opt.isEmpty()) return Optional.empty();

        for (Tramo t : opt.get().getTramos()) {
            if (t.getId() == tramoId) {
                t.setAbierto(abierto);
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
