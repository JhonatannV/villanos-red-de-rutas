package com.villanos.ruta.controller;

import com.villanos.ruta.algoritmo.Conectividad;
import com.villanos.ruta.red.ClienteRed;
import com.villanos.ruta.red.Red;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** RF-06: componentes conexas y efecto de cerrar tramos. */
@RestController
@RequestMapping("/api/rutas")
@CrossOrigin(origins = "*")
public class ConectividadController {

    private final ClienteRed clienteRed;

    public ConectividadController(ClienteRed clienteRed) {
        this.clienteRed = clienteRed;
    }

    /**
     * GET /api/rutas/conectividad[?redId=1][&sin=90][&sin=12]
     * Compara la red con todos sus tramos abiertos contra la red con los tramos
     * de {@code sin} cerrados, y nombra las paradas que quedan incomunicadas.
     */
    @GetMapping("/conectividad")
    public ResponseEntity<?> conectividad(@RequestParam(defaultValue = "1") int redId,
                                          @RequestParam(required = false) List<Integer> sin,
                                          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacion) {
        Red completa = clienteRed.obtener(redId, autorizacion)
                .orElseThrow(() -> new IllegalArgumentException("No se pudo obtener la red " + redId));
        List<Integer> cerrados = sin == null ? List.of() : sin;
        for (int id : cerrados) {
            if (!completa.tieneTramo(id)) {
                throw new IllegalArgumentException("El tramo " + id + " no existe o ya está cerrado en la red " + redId);
            }
        }
        Red red = completa.sinTramos(cerrados);

        List<List<Integer>> antes = Conectividad.componentes(completa.numParadas(), completa.origen(), completa.destino());
        List<List<Integer>> despues = Conectividad.componentes(red.numParadas(), red.origen(), red.destino());
        List<Integer> aisladas = despues.stream().skip(1).flatMap(List::stream).sorted().collect(Collectors.toList());

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("redId", redId);
        respuesta.put("tramosCerrados", describirTramos(completa, cerrados));
        respuesta.put("componentesConTodoAbierto", antes.size());
        respuesta.put("componentes", despues.size());
        respuesta.put("grupos", despues.stream().map(g -> grupo(red, g)).collect(Collectors.toList()));
        respuesta.put("aisladas", aisladas);
        respuesta.put("aisladasNombres", red.nombres(aisladas));
        respuesta.put("puentes", describirTramos(red, puentesPorId(red)));
        respuesta.put("mensaje", mensaje(cerrados, antes.size(), despues.size(), red.nombres(aisladas)));
        return ResponseEntity.ok(respuesta);
    }

    private static List<Integer> puentesPorId(Red red) {
        List<Integer> ids = new ArrayList<>();
        for (int i : Conectividad.puentes(red.numParadas(), red.origen(), red.destino())) {
            ids.add(red.tramoId()[i]);
        }
        return ids;
    }

    private static Map<String, Object> grupo(Red red, List<Integer> paradas) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("tamano", paradas.size());
        g.put("paradas", paradas);
        g.put("nombres", red.nombres(paradas));
        return g;
    }

    private static List<Map<String, Object>> describirTramos(Red red, List<Integer> ids) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (int id : ids) {
            for (int i = 0; i < red.numTramos(); i++) {
                if (red.tramoId()[i] == id) {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("id", id);
                    t.put("origen", red.origen()[i]);
                    t.put("destino", red.destino()[i]);
                    t.put("nombre", red.nombre(red.origen()[i]) + " - " + red.nombre(red.destino()[i]));
                    lista.add(t);
                    break;
                }
            }
        }
        return lista;
    }

    private static String mensaje(List<Integer> cerrados, int antes, int despues, List<String> aisladas) {
        if (cerrados.isEmpty()) {
            return despues == 1 ? "La red está completamente comunicada."
                    : "La red tiene " + despues + " zonas incomunicadas entre sí.";
        }
        String tramos = cerrados.size() == 1 ? "el tramo " + cerrados.get(0) : "los tramos " + cerrados;
        if (despues == antes) {
            return "Al cerrar " + tramos + " la red sigue con " + despues + (despues == 1 ? " componente" : " componentes")
                    + ": ninguna parada queda incomunicada.";
        }
        return "Al cerrar " + tramos + " la red pasó de " + antes + " a " + despues
                + " componentes. Quedaron aisladas: " + String.join(", ", aisladas) + ".";
    }
}
