package com.villanos.ruta.algoritmo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class DijkstraJava {

    // Orden en que se cierran las paradas (resumen de la traza)
    public static class PasoAlgoritmo {
        public final int nodo;
        public final int distancia;

        public PasoAlgoritmo(int nodo, int distancia) {
            this.nodo = nodo;
            this.distancia = distancia;
        }

        @Override
        public String toString() {
            return "{nodo=" + nodo + ", dist=" + distancia + "}";
        }
    }

    /**
     * Revisión de un vecino al cerrar una parada (RF-04).
     * {@code anterior} es null cuando el vecino aún tenía distancia infinita.
     */
    public static class Relajacion {
        public final int vecino;
        public final int peso;
        public final Integer anterior;
        public final int propuesta;
        public final boolean mejora;

        public Relajacion(int vecino, int peso, Integer anterior, int propuesta, boolean mejora) {
            this.vecino = vecino;
            this.peso = peso;
            this.anterior = anterior;
            this.propuesta = propuesta;
            this.mejora = mejora;
        }
    }

    /**
     * Un paso del algoritmo: la parada que sale de la cola y se cierra, desde cuál se
     * llegó y qué pasó con cada vecino todavía abierto. Con la lista de pasos el
     * reproductor reconstruye el estado completo en cualquier instante.
     */
    public static class PasoTraza {
        public final int paso;
        public final int actual;
        public final int distancia;
        public final int desde;
        public final List<Relajacion> relajaciones;

        public PasoTraza(int paso, int actual, int distancia, int desde, List<Relajacion> relajaciones) {
            this.paso = paso;
            this.actual = actual;
            this.distancia = distancia;
            this.desde = desde;
            this.relajaciones = relajaciones;
        }
    }

    // Empaqueta la respuesta completa de una consulta de ruta
    public static class ResultadoRuta {
        public final int costoTotal;
        public final List<Integer> camino;
        public final List<PasoAlgoritmo> nodosCerrados;
        /** Traza detallada para el reproductor; vacía cuando el cálculo lo hizo C. */
        public final List<PasoTraza> pasos;

        public ResultadoRuta(int costoTotal, List<Integer> camino, List<PasoAlgoritmo> nodosCerrados) {
            this(costoTotal, camino, nodosCerrados, Collections.emptyList());
        }

        public ResultadoRuta(int costoTotal, List<Integer> camino, List<PasoAlgoritmo> nodosCerrados,
                             List<PasoTraza> pasos) {
            this.costoTotal = costoTotal;
            this.camino = camino;
            this.nodosCerrados = nodosCerrados;
            this.pasos = pasos;
        }
    }

    // Representa una calle saliente hacia un vecino con su costo en minutos
    private static class Arista {
        final int destino;
        final int peso;

        Arista(int destino, int peso) {
            this.destino = destino;
            this.peso = peso;
        }
    }

    // Elemento que entra a la Cola de Prioridad (Min-Heap)
    private static class EstadoNodo {
        final int nodo;
        final int distanciaAcumulada;
        final int saltos;

        EstadoNodo(int nodo, int distanciaAcumulada, int saltos) {
            this.nodo = nodo;
            this.distanciaAcumulada = distanciaAcumulada;
            this.saltos = saltos;
        }
    }

    /**
     * Calcula la ruta mas corta recibiendo la red como 3 arreglos paralelos,
     * usando exactamente la misma firma de datos que recibira C mediante JNA.
     */
    public static ResultadoRuta calcular(int numParadas,
                                         int numTramos,
                                         int[] origen,
                                         int[] destino,
                                         int[] peso,
                                         int paradaInicio,
                                         int paradaFin) {
        // 1. Validar que los datos y los indices no vengan rotos o fuera de rango
        if (numParadas <= 0 || numTramos < 0 || origen == null || destino == null || peso == null) {
            return new ResultadoRuta(-1, Collections.emptyList(), Collections.emptyList());
        }
        if (origen.length < numTramos || destino.length < numTramos || peso.length < numTramos) {
            return new ResultadoRuta(-1, Collections.emptyList(), Collections.emptyList());
        }
        if (paradaInicio < 0 || paradaInicio >= numParadas || paradaFin < 0 || paradaFin >= numParadas) {
            return new ResultadoRuta(-1, Collections.emptyList(), Collections.emptyList());
        }

        // 2. Caso degenerado: si el inicio y el fin son la misma parada, costo es 0
        if (paradaInicio == paradaFin) {
            List<Integer> caminoUnico = Collections.singletonList(paradaInicio);
            List<PasoAlgoritmo> pasoUnico = Collections.singletonList(new PasoAlgoritmo(paradaInicio, 0));
            List<PasoTraza> trazaUnica = Collections.singletonList(
                    new PasoTraza(1, paradaInicio, 0, -1, Collections.emptyList()));
            return new ResultadoRuta(0, caminoUnico, pasoUnico, trazaUnica);
        }

        // 3. Construir la Lista de Adyacencia a partir de los 3 arreglos paralelos
        List<List<Arista>> grafo = new ArrayList<>(numParadas);
        for (int i = 0; i < numParadas; i++) {
            grafo.add(new ArrayList<>());
        }

        for (int i = 0; i < numTramos; i++) {
            int u = origen[i];
            int v = destino[i];
            int w = peso[i];
            if (u >= 0 && u < numParadas && v >= 0 && v < numParadas && w >= 0) {
                grafo.get(u).add(new Arista(v, w));
                grafo.get(v).add(new Arista(u, w));
            }
        }

        // 4. Inicializar arreglos de estado para Dijkstra
        int[] distancias = new int[numParadas];
        int[] cantidadParadas = new int[numParadas];
        int[] previos = new int[numParadas];
        boolean[] cerrados = new boolean[numParadas];

        Arrays.fill(distancias, Integer.MAX_VALUE);
        Arrays.fill(cantidadParadas, Integer.MAX_VALUE);
        Arrays.fill(previos, -1);

        distancias[paradaInicio] = 0;
        cantidadParadas[paradaInicio] = 1;

        // Cola de prioridad: primero menor distancia; si empatan, menos paradas (RN-06)
        PriorityQueue<EstadoNodo> cola = new PriorityQueue<>(
                Comparator.comparingInt((EstadoNodo e) -> e.distanciaAcumulada)
                        .thenComparingInt(e -> e.saltos)
        );
        cola.add(new EstadoNodo(paradaInicio, 0, 1));

        List<PasoAlgoritmo> nodosCerrados = new ArrayList<>();
        List<PasoTraza> pasos = new ArrayList<>();

        // 5. Bucle principal de exploracion
        while (!cola.isEmpty()) {
            EstadoNodo actual = cola.poll();
            int u = actual.nodo;

            if (cerrados[u]) {
                continue;
            }

            cerrados[u] = true;
            nodosCerrados.add(new PasoAlgoritmo(u, distancias[u]));

            if (u == paradaFin) {
                pasos.add(new PasoTraza(pasos.size() + 1, u, distancias[u], previos[u], Collections.emptyList()));
                break;
            }

            // 6. Relajacion de vecinos
            List<Relajacion> relajaciones = new ArrayList<>();
            for (Arista arista : grafo.get(u)) {
                int v = arista.destino;
                if (cerrados[v]) {
                    continue;
                }

                int nuevaDistancia = distancias[u] + arista.peso;
                int nuevasParadas = cantidadParadas[u] + 1;
                Integer anterior = distancias[v] == Integer.MAX_VALUE ? null : distancias[v];

                boolean mejora = nuevaDistancia < distancias[v]
                        || (nuevaDistancia == distancias[v] && nuevasParadas < cantidadParadas[v]);
                if (mejora) {
                    distancias[v] = nuevaDistancia;
                    cantidadParadas[v] = nuevasParadas;
                    previos[v] = u;
                    cola.add(new EstadoNodo(v, nuevaDistancia, nuevasParadas));
                }
                relajaciones.add(new Relajacion(v, arista.peso, anterior, nuevaDistancia, mejora));
            }
            pasos.add(new PasoTraza(pasos.size() + 1, u, distancias[u], previos[u],
                    Collections.unmodifiableList(relajaciones)));
        }

        // 7. Si paradaFin quedo en infinito, significa que es inalcanzable (-1)
        if (distancias[paradaFin] == Integer.MAX_VALUE) {
            return new ResultadoRuta(-1, Collections.emptyList(), nodosCerrados, pasos);
        }

        // 8. Reconstruir el camino caminando hacia atras usando el arreglo previos
        List<Integer> camino = new ArrayList<>();
        for (int actual = paradaFin; actual != -1; actual = previos[actual]) {
            camino.add(actual);
        }
        Collections.reverse(camino);

        return new ResultadoRuta(distancias[paradaFin], camino, nodosCerrados, pasos);
    }

    // Prueba rapida en consola con el grafo guia (A=0, B=1, C=2, D=3, E=4)
    public static void main(String[] args) {
        int numParadas = 5;
        int[] origen  = {0, 0, 1, 1, 2, 2, 3};
        int[] destino = {1, 2, 2, 3, 3, 4, 4};
        int[] peso    = {4, 2, 3, 5, 8, 15, 2};
        int numTramos = origen.length;

        ResultadoRuta resultado = calcular(numParadas, numTramos, origen, destino, peso, 0, 4);

        System.out.println("Costo total (esperado 11): " + resultado.costoTotal);
        System.out.println("Camino (esperado [0, 1, 3, 4]): " + resultado.camino);
        System.out.println("Orden de nodos cerrados: " + resultado.nodosCerrados);
    }
}