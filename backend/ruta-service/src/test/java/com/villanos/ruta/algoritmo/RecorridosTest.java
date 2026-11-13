package com.villanos.ruta.algoritmo;

import com.villanos.ruta.red.ClienteRed;
import com.villanos.ruta.red.Red;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecorridosTest {

    /*
     * Red pequeña, calculada a mano:
     *
     *   0 - 1
     *   |   |
     *   2 - 3 - 4
     *
     * BFS desde 0: 0, 1, 2, 3, 4   (niveles 0, 1, 1, 2, 3)
     * DFS desde 0: 0, 1, 3, 2, 4   (baja por 1 -> 3 -> 2, regresa y toma 4)
     */
    private static final Red CUADRO = new Red(
            new String[]{"A", "B", "C", "D", "E"},
            new int[]{1, 2, 3, 4, 5},
            new int[]{0, 0, 1, 2, 3},
            new int[]{1, 2, 3, 3, 4},
            new int[]{1, 1, 1, 1, 1});

    static Red red40() throws IOException {
        return ClienteRed.desdeJson(Files.readString(
                Path.of("..", "grafo-service", "data", "grafo_40_paradas.json"), StandardCharsets.UTF_8));
    }

    @Test
    void anchuraEnLaRedPequenaCoincideConElCalculoAMano() {
        Recorridos.Resultado r = Recorridos.recorrer(CUADRO.vecinos(), 0, Recorridos.Modo.ANCHURA);
        assertEquals(List.of(0, 1, 2, 3, 4), r.orden);
        assertEquals(List.of(0, 1, 1, 2, 3), r.pasos.stream().map(p -> p.nivel).toList());
        assertEquals(List.of(1, 2), r.pasos.get(0).descubiertas);
        assertEquals(List.of(1, 2), r.pasos.get(0).pendientes);
    }

    @Test
    void profundidadEnLaRedPequenaCoincideConElCalculoAMano() {
        Recorridos.Resultado r = Recorridos.recorrer(CUADRO.vecinos(), 0, Recorridos.Modo.PROFUNDIDAD);
        assertEquals(List.of(0, 1, 3, 2, 4), r.orden);
        assertEquals(List.of(0, 1, 2, 3, 3), r.pasos.stream().map(p -> p.nivel).toList());
        assertEquals(List.of(-1, 0, 1, 3, 3), r.pasos.stream().map(p -> p.desde).toList());
    }

    @Test
    void enLaRed40BfsYDfsVisitanLas40ParadasEnOrdenDistinto() throws IOException {
        Red red = red40();
        Recorridos.Resultado bfs = Recorridos.recorrer(red.vecinos(), 0, Recorridos.Modo.ANCHURA);
        Recorridos.Resultado dfs = Recorridos.recorrer(red.vecinos(), 0, Recorridos.Modo.PROFUNDIDAD);

        assertEquals(40, bfs.orden.size());
        assertEquals(40, dfs.orden.size());
        assertEquals(40, new HashSet<>(bfs.orden).size(), "BFS no repite paradas");
        assertEquals(40, new HashSet<>(dfs.orden).size(), "DFS no repite paradas");
        assertNotEquals(bfs.orden, dfs.orden);
        assertEquals(0, bfs.orden.get(0));
        assertEquals(0, dfs.orden.get(0));
    }

    @Test
    void enBfsLosNivelesNuncaBajan() throws IOException {
        Recorridos.Resultado bfs = Recorridos.recorrer(red40().vecinos(), 0, Recorridos.Modo.ANCHURA);
        for (int i = 1; i < bfs.pasos.size(); i++) {
            assertTrue(bfs.pasos.get(i).nivel >= bfs.pasos.get(i - 1).nivel);
        }
    }

    @Test
    void conElTramo90CerradoNoSeAlcanzanLasParadasAisladas() throws IOException {
        Red red = red40().sinTramos(List.of(90));
        Recorridos.Resultado bfs = Recorridos.recorrer(red.vecinos(), 0, Recorridos.Modo.ANCHURA);
        assertEquals(37, bfs.orden.size());
        assertTrue(bfs.orden.stream().noneMatch(p -> p >= 37));

        Recorridos.Resultado desdeLaIsla = Recorridos.recorrer(red.vecinos(), 38, Recorridos.Modo.PROFUNDIDAD);
        assertEquals(List.of(38, 37, 39), desdeLaIsla.orden);
    }

    @Test
    void modoYParadaInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> Recorridos.Modo.desdeTexto("diagonal"));
        assertThrows(IllegalArgumentException.class,
                () -> Recorridos.recorrer(CUADRO.vecinos(), 9, Recorridos.Modo.ANCHURA));
        assertEquals(Recorridos.Modo.ANCHURA, Recorridos.Modo.desdeTexto("BFS"));
        assertEquals(Recorridos.Modo.PROFUNDIDAD, Recorridos.Modo.desdeTexto(" profundidad "));
    }
}
