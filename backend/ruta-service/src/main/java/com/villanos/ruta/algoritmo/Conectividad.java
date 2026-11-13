package com.villanos.ruta.algoritmo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Componentes conexas y tramos puente de una red no dirigida [RF-06].
 *
 * <p>Una componente conexa es un grupo de paradas comunicadas entre sí. Un tramo
 * puente es uno cuyo cierre aumenta el número de componentes, es decir, deja
 * paradas incomunicadas.
 */
public final class Conectividad {

    private Conectividad() {
    }

    /**
     * Grupos de paradas comunicadas, del más grande al más pequeño; a igual
     * tamaño, el que contiene la parada de menor número va primero. Cada grupo
     * viene ordenado de forma ascendente.
     */
    public static List<List<Integer>> componentes(int numParadas, int[] origen, int[] destino) {
        List<List<Integer>> vecinos = vecinos(numParadas, origen, destino);
        boolean[] visto = new boolean[numParadas];
        List<List<Integer>> grupos = new ArrayList<>();
        for (int s = 0; s < numParadas; s++) {
            if (visto[s]) {
                continue;
            }
            List<Integer> grupo = new ArrayList<>();
            Deque<Integer> cola = new ArrayDeque<>();
            visto[s] = true;
            cola.add(s);
            while (!cola.isEmpty()) {
                int u = cola.poll();
                grupo.add(u);
                for (int v : vecinos.get(u)) {
                    if (!visto[v]) {
                        visto[v] = true;
                        cola.add(v);
                    }
                }
            }
            Collections.sort(grupo);
            grupos.add(Collections.unmodifiableList(grupo));
        }
        grupos.sort(Comparator.<List<Integer>>comparingInt(List::size).reversed()
                .thenComparingInt(g -> g.get(0)));
        return Collections.unmodifiableList(grupos);
    }

    /**
     * Índices (en los arreglos de tramos) de los tramos puente, con el algoritmo
     * de Tarjan: el tramo u-v del árbol DFS es puente si desde el subárbol de v
     * no hay otro camino que vuelva a u o más arriba (low[v] > tin[u]).
     * Dos tramos entre las mismas paradas nunca son puente.
     */
    public static List<Integer> puentes(int numParadas, int[] origen, int[] destino) {
        List<List<int[]>> adj = new ArrayList<>(numParadas);
        for (int i = 0; i < numParadas; i++) {
            adj.add(new ArrayList<>());
        }
        for (int i = 0; i < origen.length; i++) {
            if (origen[i] == destino[i]) {
                continue;   // un tramo que vuelve a la misma parada no conecta nada
            }
            adj.get(origen[i]).add(new int[]{destino[i], i});
            adj.get(destino[i]).add(new int[]{origen[i], i});
        }
        int[] tin = new int[numParadas];
        int[] low = new int[numParadas];
        Arrays.fill(tin, -1);
        int[] reloj = {0};
        List<Integer> resultado = new ArrayList<>();
        for (int s = 0; s < numParadas; s++) {
            if (tin[s] == -1) {
                buscarPuentes(s, -1, adj, tin, low, reloj, resultado);
            }
        }
        Collections.sort(resultado);
        return Collections.unmodifiableList(resultado);
    }

    private static void buscarPuentes(int u, int tramoPadre, List<List<int[]>> adj, int[] tin, int[] low,
                                      int[] reloj, List<Integer> resultado) {
        tin[u] = low[u] = reloj[0]++;
        for (int[] arista : adj.get(u)) {
            int v = arista[0], tramo = arista[1];
            if (tramo == tramoPadre) {
                continue;
            }
            if (tin[v] != -1) {
                low[u] = Math.min(low[u], tin[v]);
            } else {
                buscarPuentes(v, tramo, adj, tin, low, reloj, resultado);
                low[u] = Math.min(low[u], low[v]);
                if (low[v] > tin[u]) {
                    resultado.add(tramo);
                }
            }
        }
    }

    private static List<List<Integer>> vecinos(int numParadas, int[] origen, int[] destino) {
        List<List<Integer>> adj = new ArrayList<>(numParadas);
        for (int i = 0; i < numParadas; i++) {
            adj.add(new ArrayList<>());
        }
        for (int i = 0; i < origen.length; i++) {
            adj.get(origen[i]).add(destino[i]);
            adj.get(destino[i]).add(origen[i]);
        }
        return adj;
    }
}
