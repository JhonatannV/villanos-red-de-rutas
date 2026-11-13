package com.villanos.ruta.algoritmo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Recorridos en anchura (BFS) y en profundidad (DFS) desde una parada [RF-05].
 *
 * <p>Los vecinos se visitan en orden ascendente de número de parada, para que el
 * resultado sea siempre el mismo y se pueda comprobar a mano. Cada visita queda
 * registrada como un paso que el reproductor del frontend puede animar.
 */
public final class Recorridos {

    public enum Modo {
        ANCHURA, PROFUNDIDAD;

        public static Modo desdeTexto(String valor) {
            if (valor == null) {
                throw new IllegalArgumentException("Falta el parámetro modo: use 'anchura' o 'profundidad'");
            }
            switch (valor.trim().toLowerCase(Locale.ROOT)) {
                case "anchura":
                case "bfs":
                    return ANCHURA;
                case "profundidad":
                case "dfs":
                    return PROFUNDIDAD;
                default:
                    throw new IllegalArgumentException(
                            "Modo de recorrido inválido: '" + valor + "'. Use 'anchura' o 'profundidad'");
            }
        }
    }

    /** Una visita: la parada visitada, desde cuál se llegó y qué quedó pendiente. */
    public static final class Paso {
        public final int visita;
        public final int parada;
        public final int desde;
        /** Nivel (BFS: número de tramos desde el inicio) o profundidad en el árbol (DFS). */
        public final int nivel;
        /** Vecinos que se agregaron a la cola o pila en esta visita. */
        public final List<Integer> descubiertas;
        /** Contenido de la cola (BFS) o de la pila (DFS, el tope al final) después de la visita. */
        public final List<Integer> pendientes;

        Paso(int visita, int parada, int desde, int nivel, List<Integer> descubiertas, List<Integer> pendientes) {
            this.visita = visita;
            this.parada = parada;
            this.desde = desde;
            this.nivel = nivel;
            this.descubiertas = descubiertas;
            this.pendientes = pendientes;
        }
    }

    public static final class Resultado {
        public final Modo modo;
        public final int inicio;
        /** Paradas en el orden en que se visitaron. */
        public final List<Integer> orden;
        public final List<Paso> pasos;

        Resultado(Modo modo, int inicio, List<Integer> orden, List<Paso> pasos) {
            this.modo = modo;
            this.inicio = inicio;
            this.orden = orden;
            this.pasos = pasos;
        }
    }

    private Recorridos() {
    }

    public static Resultado recorrer(List<List<Integer>> vecinos, int inicio, Modo modo) {
        if (inicio < 0 || inicio >= vecinos.size()) {
            throw new IllegalArgumentException("La parada de inicio " + inicio + " no existe");
        }
        return modo == Modo.ANCHURA ? anchura(vecinos, inicio) : profundidad(vecinos, inicio);
    }

    /** BFS con cola: una parada se marca al descubrirla, así entra a la cola una sola vez. */
    static Resultado anchura(List<List<Integer>> vecinos, int inicio) {
        int n = vecinos.size();
        int[] nivel = new int[n];
        int[] padre = new int[n];
        Arrays.fill(nivel, -1);
        Arrays.fill(padre, -1);
        Deque<Integer> cola = new ArrayDeque<>();
        List<Integer> orden = new ArrayList<>();
        List<Paso> pasos = new ArrayList<>();

        nivel[inicio] = 0;
        cola.addLast(inicio);
        while (!cola.isEmpty()) {
            int u = cola.pollFirst();
            orden.add(u);
            List<Integer> nuevas = new ArrayList<>();
            for (int v : vecinos.get(u)) {
                if (nivel[v] == -1) {
                    nivel[v] = nivel[u] + 1;
                    padre[v] = u;
                    cola.addLast(v);
                    nuevas.add(v);
                }
            }
            pasos.add(new Paso(orden.size(), u, padre[u], nivel[u],
                    Collections.unmodifiableList(nuevas), List.copyOf(cola)));
        }
        return new Resultado(Modo.ANCHURA, inicio, Collections.unmodifiableList(orden), Collections.unmodifiableList(pasos));
    }

    /**
     * DFS iterativo con pila. Los vecinos se apilan en orden inverso para que se
     * visite primero el de menor número, igual que la versión recursiva.
     * Una parada se marca al sacarla de la pila (puede estar apilada más de una vez).
     */
    static Resultado profundidad(List<List<Integer>> vecinos, int inicio) {
        int n = vecinos.size();
        boolean[] visitada = new boolean[n];
        int[] profundidad = new int[n];
        Deque<int[]> pila = new ArrayDeque<>();   // {parada, desde, profundidad}
        List<Integer> orden = new ArrayList<>();
        List<Paso> pasos = new ArrayList<>();

        pila.push(new int[]{inicio, -1, 0});
        while (!pila.isEmpty()) {
            int[] tope = pila.pop();
            int u = tope[0];
            if (visitada[u]) {
                continue;
            }
            visitada[u] = true;
            profundidad[u] = tope[2];
            orden.add(u);

            List<Integer> nuevas = new ArrayList<>();
            List<Integer> lista = vecinos.get(u);
            for (int k = lista.size() - 1; k >= 0; k--) {
                int v = lista.get(k);
                if (!visitada[v]) {
                    pila.push(new int[]{v, u, tope[2] + 1});
                    nuevas.add(0, v);
                }
            }
            List<Integer> pendientes = new ArrayList<>();
            pila.descendingIterator().forEachRemaining(e -> {
                if (!visitada[e[0]]) {
                    pendientes.add(e[0]);
                }
            });
            pasos.add(new Paso(orden.size(), u, tope[1], profundidad[u],
                    Collections.unmodifiableList(nuevas), Collections.unmodifiableList(pendientes)));
        }
        return new Resultado(Modo.PROFUNDIDAD, inicio, Collections.unmodifiableList(orden), Collections.unmodifiableList(pasos));
    }
}
