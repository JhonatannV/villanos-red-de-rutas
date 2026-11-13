package com.villanos.ruta.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villanos.ruta.algoritmo.DijkstraJava.ResultadoRuta;
import com.villanos.ruta.calculo.CalculadorRutas;
import com.villanos.ruta.calculo.ModoAlgoritmo;
import com.villanos.ruta.calculo.SelectorAlgoritmo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/rutas")
@CrossOrigin(origins = "*")
public class RutaController {

    private final CalculadorRutas calculadorRutas;
    private final RestTemplate restTemplate;

    @Value("${services.grafo-service.url:http://localhost:8081}")
    private String grafoServiceUrl;

    /** El selector lo crea AlgoritmoConfig a partir de app.algorithm.mode. */
    @Autowired
    public RutaController(CalculadorRutas calculadorRutas) {
        this(calculadorRutas, new RestTemplate());
    }

    public RutaController(CalculadorRutas calculadorRutas, RestTemplate restTemplate) {
        this.calculadorRutas = calculadorRutas;
        this.restTemplate = restTemplate;
    }

    @GetMapping("/calcular")
    public ResponseEntity<?> calcularRuta(@RequestParam int desde,
                                          @RequestParam int hasta,
                                          @RequestParam(defaultValue = "1") int redId,
                                          @RequestParam(required = false) Integer sin,
                                          @RequestParam(required = false) String modo,
                                          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacion) {
        CalculadorRutas calculador = this.calculadorRutas;
        if (modo != null && !modo.isBlank()) {
            calculador = new SelectorAlgoritmo(ModoAlgoritmo.desdeTexto(modo));
        }

        RedEstructura red = obtenerRed(redId, sin, autorizacion);
        if (red == null || red.numParadas <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo obtener la red " + redId));
        }

        ResultadoRuta resultado = calculador.calcular(
                red.numParadas,
                red.origen,
                red.destino,
                red.peso,
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

        if (resultado.costoTotal == -1) {
            respuesta.put("mensaje", "No existe camino alcanzable entre las paradas indicadas.");
        }

        return ResponseEntity.ok(respuesta);
    }

    private static class RedEstructura {
        final int numParadas;
        final int[] origen;
        final int[] destino;
        final int[] peso;

        RedEstructura(int numParadas, int[] origen, int[] destino, int[] peso) {
            this.numParadas = numParadas;
            this.origen = origen;
            this.destino = destino;
            this.peso = peso;
        }
    }

    private RedEstructura obtenerRed(int redId, Integer tramoCerradoId, String autorizacion) {
        // 1. Intenta consultar grafo-service por HTTP, reenviando el token del usuario:
        //    grafo-service es resource server y sin token responde 401
        try {
            String url = grafoServiceUrl + "/api/redes/" + redId;
            HttpHeaders cabeceras = new HttpHeaders();
            if (autorizacion != null && !autorizacion.isBlank()) {
                cabeceras.set(HttpHeaders.AUTHORIZATION, autorizacion);
            }
            String jsonResp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(cabeceras), String.class).getBody();
            if (jsonResp != null && !jsonResp.isBlank()) {
                return parsearRedJson(jsonResp, tramoCerradoId);
            }
        } catch (Exception ignored) {
            // grafo-service no respondio; pasa al fallback local
        }

        // 2. Fallback local al archivo de 40 paradas
        List<Path> candidatos = Arrays.asList(
                Paths.get("data/grafo_40_paradas.json"),
                Paths.get("../grafo-service/data/grafo_40_paradas.json"),
                Paths.get("backend/grafo-service/data/grafo_40_paradas.json")
        );

        for (Path p : candidatos) {
            File f = p.toFile();
            if (f.exists() && f.isFile()) {
                try {
                    String contenido = Files.readString(f.toPath());
                    return parsearRedJson(contenido, tramoCerradoId);
                } catch (IOException ignored) {}
            }
        }

        return null;
    }

    private RedEstructura parsearRedJson(String json, Integer tramoCerradoId) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int numParadas = root.getAsJsonArray("paradas").size();
        JsonArray tramosJson = root.getAsJsonArray("tramos");

        List<Integer> listaOrigen = new ArrayList<>();
        List<Integer> listaDestino = new ArrayList<>();
        List<Integer> listaPeso = new ArrayList<>();

        for (JsonElement elem : tramosJson) {
            JsonObject tramo = elem.getAsJsonObject();
            int id = tramo.get("id").getAsInt();
            boolean abierto = tramo.has("abierto") ? tramo.get("abierto").getAsBoolean() : true;

            if (abierto && (tramoCerradoId == null || id != tramoCerradoId)) {
                listaOrigen.add(tramo.get("origen").getAsInt());
                listaDestino.add(tramo.get("destino").getAsInt());
                listaPeso.add(tramo.get("peso").getAsInt());
            }
        }

        int n = listaOrigen.size();
        int[] origen = new int[n];
        int[] destino = new int[n];
        int[] peso = new int[n];
        for (int i = 0; i < n; i++) {
            origen[i] = listaOrigen.get(i);
            destino[i] = listaDestino.get(i);
            peso[i] = listaPeso.get(i);
        }

        return new RedEstructura(numParadas, origen, destino, peso);
    }
}
