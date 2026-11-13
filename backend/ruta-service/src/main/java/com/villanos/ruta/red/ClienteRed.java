package com.villanos.ruta.red;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Obtiene la red desde grafo-service (ruta-service no guarda la red).
 * Reenvía el token del usuario, porque grafo-service es resource server.
 * Si grafo-service no responde, usa el archivo local de 40 paradas cuando está disponible.
 */
@Component
public class ClienteRed {

    private static final List<Path> ARCHIVOS_LOCALES = List.of(
            Path.of("data/grafo_40_paradas.json"),
            Path.of("../grafo-service/data/grafo_40_paradas.json"),
            Path.of("backend/grafo-service/data/grafo_40_paradas.json"));

    private final RestTemplate restTemplate;
    private final String grafoServiceUrl;

    @Autowired
    public ClienteRed(@Value("${services.grafo-service.url:http://localhost:8081}") String grafoServiceUrl) {
        this(new RestTemplate(), grafoServiceUrl);
    }

    public ClienteRed(RestTemplate restTemplate, String grafoServiceUrl) {
        this.restTemplate = restTemplate;
        this.grafoServiceUrl = grafoServiceUrl;
    }

    /** La red con sus tramos abiertos, o vacío si no se pudo obtener. */
    public Optional<Red> obtener(int redId, String autorizacion) {
        try {
            HttpHeaders cabeceras = new HttpHeaders();
            if (autorizacion != null && !autorizacion.isBlank()) {
                cabeceras.set(HttpHeaders.AUTHORIZATION, autorizacion);
            }
            String json = restTemplate.exchange(grafoServiceUrl + "/api/redes/" + redId, HttpMethod.GET,
                    new HttpEntity<>(cabeceras), String.class).getBody();
            if (json != null && !json.isBlank()) {
                Red red = desdeJson(json);
                if (red.numParadas() > 0) {
                    return Optional.of(red);
                }
            }
        } catch (Exception e) {
            // grafo-service no respondió: se intenta con el archivo local
        }
        for (Path p : ARCHIVOS_LOCALES) {
            if (Files.isRegularFile(p)) {
                try {
                    return Optional.of(desdeJson(Files.readString(p, StandardCharsets.UTF_8)));
                } catch (IOException ignored) {
                    // se prueba el siguiente
                }
            }
        }
        return Optional.empty();
    }

    /** Convierte el JSON de grafo-service (paradas y tramos) en una Red con sus tramos abiertos. */
    public static Red desdeJson(String json) {
        JsonObject raiz = JsonParser.parseString(json).getAsJsonObject();
        JsonArray paradas = raiz.getAsJsonArray("paradas");
        String[] nombres = new String[paradas.size()];
        for (JsonElement e : paradas) {
            JsonObject p = e.getAsJsonObject();
            int id = p.get("id").getAsInt();
            if (id < 0 || id >= nombres.length) {
                throw new IllegalStateException("Parada con id fuera de secuencia: " + id);
            }
            nombres[id] = p.has("nombre") ? p.get("nombre").getAsString() : "Parada " + id;
        }

        List<int[]> tramos = new ArrayList<>();
        for (JsonElement e : raiz.getAsJsonArray("tramos")) {
            JsonObject t = e.getAsJsonObject();
            boolean abierto = !t.has("abierto") || t.get("abierto").getAsBoolean();
            if (abierto) {
                tramos.add(new int[]{t.get("id").getAsInt(), t.get("origen").getAsInt(),
                        t.get("destino").getAsInt(), t.get("peso").getAsInt()});
            }
        }
        int m = tramos.size();
        int[] id = new int[m], o = new int[m], d = new int[m], w = new int[m];
        for (int i = 0; i < m; i++) {
            int[] t = tramos.get(i);
            id[i] = t[0];
            o[i] = t[1];
            d[i] = t[2];
            w[i] = t[3];
        }
        return new Red(nombres, id, o, d, w);
    }
}
