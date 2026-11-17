package com.villanos.ruta.controller;

import com.villanos.ruta.algoritmo.DijkstraJava;
import com.villanos.ruta.algoritmo.DijkstraJava.ResultadoRuta;
import com.villanos.ruta.calculo.CalculadorRutas;
import com.villanos.ruta.calculo.ModoAlgoritmo;
import com.villanos.ruta.calculo.SelectorAlgoritmo;
import com.villanos.ruta.red.ClienteRed;
import com.villanos.ruta.red.Red;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/rutas")
@CrossOrigin(origins = "*")
public class RutaController {

    private final CalculadorRutas calculadorRutas;
    private final ClienteRed clienteRed;

    /** El selector lo crea AlgoritmoConfig a partir de app.algorithm.mode. */
    public RutaController(CalculadorRutas calculadorRutas, ClienteRed clienteRed) {
        this.calculadorRutas = calculadorRutas;
        this.clienteRed = clienteRed;
    }

    @GetMapping("/calcular")
    public ResponseEntity<?> calcularRuta(@RequestParam int desde,
                                          @RequestParam int hasta,
                                          @RequestParam(defaultValue = "1") int redId,
                                          @RequestParam(required = false) Integer sin,
                                          @RequestParam(required = false) String modo,
                                          @RequestParam(defaultValue = "false") boolean traza,
                                          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacion) {
        CalculadorRutas calculador = this.calculadorRutas;
        if (modo != null && !modo.isBlank()) {
            calculador = new SelectorAlgoritmo(ModoAlgoritmo.desdeTexto(modo));
        }

        Optional<Red> obtenida = clienteRed.obtener(redId, autorizacion);
        if (obtenida.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo obtener la red " + redId));
        }
        Red red = sin == null ? obtenida.get() : obtenida.get().sinTramos(List.of(sin));

        ResultadoRuta resultado = calculador.calcular(
                red.numParadas(),
                red.origen(),
                red.destino(),
                red.peso(),
                desde,
                hasta
        );

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("redId", redId);
        respuesta.put("desde", desde);
        respuesta.put("hasta", hasta);
        respuesta.put("costoTotal", resultado.costoTotal);
        respuesta.put("camino", resultado.camino);
        respuesta.put("nodosCerrados", resultado.nodosCerrados);
        respuesta.put("modoEfectivo", calculador.modoEfectivo().name());
        respuesta.put("tramoCerrado", sin);

        if (traza) {
            // RF-04: libgrafo solo devuelve camino y costo; la traza paso a paso la arma
            // la versión Java sobre la misma red (misma regla de desempate, D-06).
            List<DijkstraJava.PasoTraza> pasos = resultado.pasos;
            if (pasos.isEmpty()) {
                pasos = DijkstraJava.calcular(red.numParadas(), red.numTramos(), red.origen(), red.destino(),
                        red.peso(), desde, hasta).pasos;
            }
            respuesta.put("trazaCalculadaCon", "JAVA");
            respuesta.put("pasos", pasos);
        }

        if (resultado.costoTotal == -1) {
            respuesta.put("mensaje", "No existe camino alcanzable entre las paradas indicadas.");
        }

        return ResponseEntity.ok(respuesta);
    }
}
