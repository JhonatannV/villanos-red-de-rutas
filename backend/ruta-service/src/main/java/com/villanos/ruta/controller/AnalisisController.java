package com.villanos.ruta.controller;

import com.villanos.ruta.algoritmo.AnalisisCiclos;
import com.villanos.ruta.algoritmo.MemoriaRepresentacion;
import com.villanos.ruta.red.ClienteRed;
import com.villanos.ruta.red.Red;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** RF-07: detección de ciclos y memoria de la representación usada. */
@RestController
@RequestMapping("/api/rutas")
@CrossOrigin(origins = "*")
public class AnalisisController {

    /** Tamaño de ciudad intermedia del escenario W-RQ-02, para comparar. */
    private static final int PARADAS_RED_GRANDE = 400;
    private static final int TRAMOS_RED_GRANDE = 1100;

    private final ClienteRed clienteRed;

    public AnalisisController(ClienteRed clienteRed) {
        this.clienteRed = clienteRed;
    }

    /** GET /api/rutas/ciclos[?redId=1][&sin=90][&limite=10] */
    @GetMapping("/ciclos")
    public ResponseEntity<?> ciclos(@RequestParam(defaultValue = "1") int redId,
                                    @RequestParam(required = false) List<Integer> sin,
                                    @RequestParam(required = false) Integer limite,
                                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacion) {
        if (limite != null && limite < 0) {
            throw new IllegalArgumentException("El límite no puede ser negativo");
        }
        Red red = obtener(redId, sin, autorizacion);
        AnalisisCiclos.Resultado r = AnalisisCiclos.analizar(red.numParadas(), red.tramoId(), red.origen(), red.destino());

        List<Map<String, Object>> ciclos = new ArrayList<>();
        for (AnalisisCiclos.Ciclo c : r.ciclos) {
            if (limite != null && ciclos.size() >= limite) {
                break;
            }
            Map<String, Object> ciclo = new LinkedHashMap<>();
            ciclo.put("longitud", c.paradas.size());
            ciclo.put("paradas", c.paradas);
            ciclo.put("nombres", red.nombres(c.paradas));
            ciclo.put("tramoQueCierra", c.tramoQueCierra);
            ciclos.add(ciclo);
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("redId", redId);
        respuesta.put("tramosCerrados", sin == null ? List.of() : sin);
        respuesta.put("paradas", red.numParadas());
        respuesta.put("tramos", red.numTramos());
        respuesta.put("componentes", r.componentes);
        respuesta.put("tieneCiclos", r.tieneCiclos());
        respuesta.put("numeroCiclos", r.numeroCiclomatico);
        respuesta.put("explicacion", "Ciclos independientes = tramos - paradas + componentes = "
                + red.numTramos() + " - " + red.numParadas() + " + " + r.componentes + " = " + r.numeroCiclomatico
                + ". Cada ciclo listado lo cierra un tramo fuera del árbol de recorrido.");
        respuesta.put("ciclos", ciclos);
        return ResponseEntity.ok(respuesta);
    }

    /** GET /api/rutas/memoria[?redId=1][&sin=90] */
    @GetMapping("/memoria")
    public ResponseEntity<?> memoria(@RequestParam(defaultValue = "1") int redId,
                                     @RequestParam(required = false) List<Integer> sin,
                                     @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacion) {
        Red red = obtener(redId, sin, autorizacion);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("redId", redId);
        respuesta.put("representacionUsada", "lista de adyacencia compacta (la misma de dijkstra.c)");
        respuesta.put("redActual", describir(new MemoriaRepresentacion(red.numParadas(), red.numTramos())));
        respuesta.put("redGrande", describir(new MemoriaRepresentacion(PARADAS_RED_GRANDE, TRAMOS_RED_GRANDE)));
        respuesta.put("formulas", Map.of(
                "lista", "4 · (V + 1 + 4E) bytes: inicio_vecinos[V+1], vecino[2E] y peso_vecino[2E]",
                "matriz", "4 · V² bytes: una casilla con el peso por cada par de paradas"));
        return ResponseEntity.ok(respuesta);
    }

    private Red obtener(int redId, List<Integer> sin, String autorizacion) {
        return clienteRed.obtener(redId, autorizacion)
                .orElseThrow(() -> new IllegalArgumentException("No se pudo obtener la red " + redId))
                .sinTramos(sin);
    }

    private static Map<String, Object> describir(MemoriaRepresentacion m) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("paradas", m.paradas);
        d.put("tramos", m.tramos);
        d.put("bytesListaAdyacencia", m.bytesLista);
        d.put("bytesMatrizAdyacencia", m.bytesMatriz);
        d.put("matrizOcupaVecesLaLista", Math.round(m.relacionMatrizSobreLista() * 10) / 10.0);
        d.put("densidad", Math.round(m.densidad * 1000) / 1000.0);
        d.put("casillasVaciasMatriz", Math.round(m.casillasVaciasMatriz * 1000) / 1000.0);
        d.put("tramosParaQueConvengaLaMatriz", m.tramosParaQueConvengaLaMatriz);
        return d;
    }
}
