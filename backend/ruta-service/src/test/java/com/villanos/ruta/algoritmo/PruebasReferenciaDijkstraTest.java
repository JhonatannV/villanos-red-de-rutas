package com.villanos.ruta.algoritmo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PruebasReferenciaDijkstraTest {

    @Test
    void test01_grafoGuiaCincoParadas() {
        int[] origen  = {0, 0, 1, 1, 2, 2, 3};
        int[] destino = {1, 2, 2, 3, 3, 4, 4};
        int[] peso    = {4, 2, 3, 5, 8, 15, 2};

        DijkstraJava.ResultadoRuta res = DijkstraJava.calcular(5, origen.length, origen, destino, peso, 0, 4);

        assertEquals(11, res.costoTotal);
        assertEquals(Arrays.asList(0, 1, 3, 4), res.camino);
    }

    @Test
    void test02_grafoSieteParadas() {
        int[] origen  = {0, 0, 1, 2, 3, 3, 4, 4, 5};
        int[] destino = {1, 2, 3, 3, 4, 5, 5, 6, 6};
        int[] peso    = {2, 6, 5, 8, 10, 15, 6, 2, 6};

        DijkstraJava.ResultadoRuta res = DijkstraJava.calcular(7, origen.length, origen, destino, peso, 0, 6);

        assertEquals(19, res.costoTotal);
        assertEquals(Arrays.asList(0, 1, 3, 4, 6), res.camino);
    }

    @Test
    void test03_empateDeCostoEligeMenosParadas() {
        // Camino A: 0->1->2->4 cuesta 2+2+6 = 10 (4 paradas)
        // Camino B: 0->3->4    cuesta 5+5   = 10 (3 paradas) -> debe ganar este segun RN-06
        int[] origen  = {0, 1, 2, 0, 3};
        int[] destino = {1, 2, 4, 3, 4};
        int[] peso    = {2, 2, 6, 5, 5};

        DijkstraJava.ResultadoRuta res = DijkstraJava.calcular(5, origen.length, origen, destino, peso, 0, 4);

        assertEquals(10, res.costoTotal);
        assertEquals(Arrays.asList(0, 3, 4), res.camino);
    }

    @Test
    void test04_grafoDosComponentesInalcanzable() {
        // Componente 1: {0, 1, 2} | Componente 2: {3, 4, 5}
        int[] origen  = {0, 1, 3, 4};
        int[] destino = {1, 2, 4, 5};
        int[] peso    = {3, 4, 2, 5};

        DijkstraJava.ResultadoRuta res = DijkstraJava.calcular(6, origen.length, origen, destino, peso, 0, 5);

        assertEquals(-1, res.costoTotal);
        assertTrue(res.camino.isEmpty());
    }

    @Test
    void test05_origenIgualADestino() {
        int[] origen  = {0, 1};
        int[] destino = {1, 2};
        int[] peso    = {4, 5};

        DijkstraJava.ResultadoRuta res = DijkstraJava.calcular(3, origen.length, origen, destino, peso, 2, 2);

        assertEquals(0, res.costoTotal);
        assertEquals(Collections.singletonList(2), res.camino);
    }

    @Test
    void test06_grafoUnaSolaParada() {
        int[] vacio = new int[0];

        DijkstraJava.ResultadoRuta res = DijkstraJava.calcular(1, 0, vacio, vacio, vacio, 0, 0);

        assertEquals(0, res.costoTotal);
        assertEquals(Collections.singletonList(0), res.camino);
    }

    @Test
    void test07_tramosConPesoCero() {
        int[] origen  = {0, 1, 2, 0};
        int[] destino = {1, 2, 3, 3};
        int[] peso    = {0, 0, 5, 8};

        DijkstraJava.ResultadoRuta res = DijkstraJava.calcular(4, origen.length, origen, destino, peso, 0, 3);

        assertEquals(5, res.costoTotal);
        assertEquals(Arrays.asList(0, 1, 2, 3), res.camino);
    }

    @Test
    void test08_indicesFueraDeRangoNoExplota() {
        int[] origen  = {0, 1};
        int[] destino = {1, 2};
        int[] peso    = {4, 5};

        DijkstraJava.ResultadoRuta resDestinoInvalido = DijkstraJava.calcular(3, 2, origen, destino, peso, 0, 99);
        DijkstraJava.ResultadoRuta resOrigenNegativo  = DijkstraJava.calcular(3, 2, origen, destino, peso, -1, 2);

        assertEquals(-1, resDestinoInvalido.costoTotal);
        assertEquals(-1, resOrigenNegativo.costoTotal);
    }

    @Test
    void test09_red40ParadasCasoManual1() throws IOException {
        RedCargada red = cargarRedDesdeJson(-1);

        // Caso 1 documentado en docs/casos_prueba_rf03.md: 0 -> 11
        DijkstraJava.ResultadoRuta res = DijkstraJava.calcular(
                red.numParadas, red.numTramos, red.origen, red.destino, red.peso, 0, 11
        );

        assertEquals(10, res.costoTotal);
        assertEquals(Arrays.asList(0, 9, 10, 11), res.camino);
    }

    @Test
    void test10_red40ParadasCasoManual2() throws IOException {
        RedCargada red = cargarRedDesdeJson(-1);

        // Caso 2 documentado en docs/casos_prueba_rf03.md: 4 -> 22
        DijkstraJava.ResultadoRuta res = DijkstraJava.calcular(
                red.numParadas, red.numTramos, red.origen, red.destino, red.peso, 4, 22
        );

        assertEquals(11, res.costoTotal);
        assertEquals(Arrays.asList(4, 5, 13, 21, 22), res.camino);
    }

    @Test
    void test11_red40ParadasCierrePuenteDejaAisladaZonaRural() throws IOException {
        // Simulamos ?sin=90 cerrando el tramo puente 90 (Salida Sur <-> Peaje Aeropuerto)
        RedCargada redSinPuente = cargarRedDesdeJson(90);

        DijkstraJava.ResultadoRuta res = DijkstraJava.calcular(
                redSinPuente.numParadas, redSinPuente.numTramos,
                redSinPuente.origen, redSinPuente.destino, redSinPuente.peso, 0, 39
        );

        assertEquals(-1, res.costoTotal);
        assertTrue(res.camino.isEmpty());
    }

    // --- Ayudante para leer grafo_40_paradas.json y convertirlo en arreglos paralelos ---
    private static class RedCargada {
        final int numParadas;
        final int numTramos;
        final int[] origen;
        final int[] destino;
        final int[] peso;

        RedCargada(int numParadas, int numTramos, int[] origen, int[] destino, int[] peso) {
            this.numParadas = numParadas;
            this.numTramos = numTramos;
            this.origen = origen;
            this.destino = destino;
            this.peso = peso;
        }
    }

    private RedCargada cargarRedDesdeJson(int tramoCerradoId) throws IOException {
        Path rutaJson = Paths.get("../grafo-service/data/grafo_40_paradas.json");
        if (!Files.exists(rutaJson)) {
            rutaJson = Paths.get("backend/grafo-service/data/grafo_40_paradas.json");
        }

        String contenido = Files.readString(rutaJson);
        JsonObject raiz = JsonParser.parseString(contenido).getAsJsonObject();

        int numParadas = raiz.getAsJsonArray("paradas").size();
        JsonArray tramosJson = raiz.getAsJsonArray("tramos");

        List<Integer> listaOrigen = new ArrayList<>();
        List<Integer> listaDestino = new ArrayList<>();
        List<Integer> listaPeso = new ArrayList<>();

        for (JsonElement elem : tramosJson) {
            JsonObject tramo = elem.getAsJsonObject();
            int id = tramo.get("id").getAsInt();
            boolean abierto = tramo.get("abierto").getAsBoolean();

            if (abierto && id != tramoCerradoId) {
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

        return new RedCargada(numParadas, n, origen, destino, peso);
    }
}