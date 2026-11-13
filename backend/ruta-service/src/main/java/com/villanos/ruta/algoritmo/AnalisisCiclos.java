package com.villanos.ruta.algoritmo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Detección de ciclos en una red no dirigida [RF-07].
 *
 * <p>Se arma un árbol de recorrido (BFS) por cada componente. Cada tramo que no
 * entra al árbol cierra exactamente un ciclo: el camino por el árbol entre sus
 * dos paradas más el propio tramo. Esos ciclos forman una base de ciclos
 * (ciclos fundamentales). Su cantidad es el número ciclomático E - V + C, y
 * cualquier otro ciclo de la red se obtiene combinándolos.
 */
public final class AnalisisCiclos {

    /** Un ciclo fundamental: las paradas en orden y el tramo que lo cierra. */
    public static final class Ciclo {
        public final List<Integer> paradas;
        public final int tramoQueCierra;

        Ciclo(List<Integer> paradas, int tramoQueCierra) {
            this.paradas = paradas;
            this.tramoQueCierra = tramoQueCierra;
        }
    }

    public static final class Resultado {
        /** E - V + C: cuántos ciclos independientes tiene la red. */
        public final int numeroCiclomatico;
        public final int componentes;
        /** Ciclos fundamentales, del más corto al más largo. */
        public final List<Ciclo> ciclos;

        Resultado(int numeroCiclomatico, int componentes, List<Ciclo> ciclos) {
            this.numeroCiclomatico = numeroCiclomatico;
            this.componentes = componentes;
            this.ciclos = ciclos;
        }

        public boolean tieneCiclos() {
            return numeroCiclomatico > 0;
        }
    }

    private AnalisisCiclos() {
    }

    /** @param tramoId identificador de cada tramo, en el mismo orden que origen y destino */
    public static Resultado analizar(int numParadas, int[] tramoId, int[] origen, int[] destino) {
        List<List<int[]>> adj = new ArrayList<>(numParadas);
        for (int i = 0; i < numParadas; i++) {
            adj.add(new ArrayList<>());
        }
        for (int i = 0; i < origen.length; i++) {
            adj.get(origen[i]).add(new int[]{destino[i], i});
            if (origen[i] != destino[i]) {
                adj.get(destino[i]).add(new int[]{origen[i], i});
            }
        }

        int[] padre = new int[numParadas];
        int[] profundidad = new int[numParadas];
        boolean[] enArbol = new boolean[origen.length];
        Arrays.fill(padre, -2);   // -2: sin visitar; -1: raíz
        int componentes = 0;
        for (int s = 0; s < numParadas; s++) {
            if (padre[s] != -2) {
                continue;
            }
            componentes++;
            padre[s] = -1;
            Deque<Integer> cola = new ArrayDeque<>();
            cola.add(s);
            while (!cola.isEmpty()) {
                int u = cola.poll();
                for (int[] a : adj.get(u)) {
                    int v = a[0];
                    if (padre[v] == -2) {
                        padre[v] = u;
                        profundidad[v] = profundidad[u] + 1;
                        enArbol[a[1]] = true;
                        cola.add(v);
                    }
                }
            }
        }

        List<Ciclo> ciclos = new ArrayList<>();
        for (int i = 0; i < origen.length; i++) {
            if (!enArbol[i]) {
                ciclos.add(new Ciclo(Collections.unmodifiableList(cerrarCiclo(origen[i], destino[i], padre, profundidad)),
                        tramoId[i]));
            }
        }
        ciclos.sort(Comparator.comparingInt((Ciclo c) -> c.paradas.size()).thenComparingInt(c -> c.tramoQueCierra));
        int ciclomatico = origen.length - numParadas + componentes;
        return new Resultado(ciclomatico, componentes, Collections.unmodifiableList(ciclos));
    }

    /** Camino por el árbol de u a v pasando por su ancestro común: u ... ancestro ... v. */
    private static List<Integer> cerrarCiclo(int u, int v, int[] padre, int[] profundidad) {
        if (u == v) {
            return List.of(u);
        }
        List<Integer> desdeU = new ArrayList<>();
        List<Integer> desdeV = new ArrayList<>();
        int a = u, b = v;
        while (profundidad[a] > profundidad[b]) {
            desdeU.add(a);
            a = padre[a];
        }
        while (profundidad[b] > profundidad[a]) {
            desdeV.add(b);
            b = padre[b];
        }
        while (a != b) {
            desdeU.add(a);
            desdeV.add(b);
            a = padre[a];
            b = padre[b];
        }
        desdeU.add(a);
        Collections.reverse(desdeV);
        desdeU.addAll(desdeV);
        return desdeU;
    }
}
