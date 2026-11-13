package com.villanos.ruta.controller;

import com.villanos.ruta.algoritmo.Recorridos;
import com.villanos.ruta.red.ClienteRed;
import com.villanos.ruta.red.Red;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** RF-05: recorridos BFS y DFS desde una parada. */
@RestController
@RequestMapping("/api/rutas")
@CrossOrigin(origins = "*")
public class RecorridoController {

    private final ClienteRed clienteRed;

    public RecorridoController(ClienteRed clienteRed) {
        this.clienteRed = clienteRed;
    }

    /**
     * GET /api/rutas/recorrido?desde=0&modo=anchura|profundidad[&redId=1][&sin=90]
     */
    @GetMapping("/recorrido")
    public ResponseEntity<?> recorrido(@RequestParam int desde,
                                       @RequestParam String modo,
                                       @RequestParam(defaultValue = "1") int redId,
                                       @RequestParam(required = false) List<Integer> sin,
                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacion) {
        Recorridos.Modo m = Recorridos.Modo.desdeTexto(modo);
        Red red = clienteRed.obtener(redId, autorizacion)
                .orElseThrow(() -> new IllegalArgumentException("No se pudo obtener la red " + redId))
                .sinTramos(sin);
        red.validarParada(desde, "desde");

        Recorridos.Resultado r = Recorridos.recorrer(red.vecinos(), desde, m);

        List<Map<String, Object>> pasos = new ArrayList<>();
        for (Recorridos.Paso p : r.pasos) {
            Map<String, Object> paso = new LinkedHashMap<>();
            paso.put("visita", p.visita);
            paso.put("parada", p.parada);
            paso.put("nombre", red.nombre(p.parada));
            paso.put("desde", p.desde);
            paso.put(m == Recorridos.Modo.ANCHURA ? "nivel" : "profundidad", p.nivel);
            paso.put("descubiertas", p.descubiertas);
            paso.put(m == Recorridos.Modo.ANCHURA ? "cola" : "pila", p.pendientes);
            pasos.add(paso);
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("redId", redId);
        respuesta.put("desde", desde);
        respuesta.put("modo", m == Recorridos.Modo.ANCHURA ? "anchura" : "profundidad");
        respuesta.put("tramosCerrados", sin == null ? List.of() : sin);
        respuesta.put("visitadas", r.orden.size());
        respuesta.put("orden", r.orden);
        respuesta.put("ordenNombres", red.nombres(r.orden));
        respuesta.put("pasos", pasos);
        if (r.orden.size() < red.numParadas()) {
            respuesta.put("mensaje", (red.numParadas() - r.orden.size())
                    + " paradas no se alcanzan desde la parada de inicio.");
        }
        return ResponseEntity.ok(respuesta);
    }
}
