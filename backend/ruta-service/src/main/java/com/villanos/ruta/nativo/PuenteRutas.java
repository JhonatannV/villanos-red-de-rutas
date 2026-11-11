package com.villanos.ruta.nativo;

import com.villanos.ruta.algoritmo.DijkstraJava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PuenteRutas {

    private static LibGrafo instanciaNativa;
    private static boolean intentoCargaRealizado = false;

    // Intenta cargar el binario C una sola vez sin tumbar el programa si aun no existe
    public static synchronized boolean estaDisponibleNativo() {
        if (!intentoCargaRealizado) {
            intentoCargaRealizado = true;
            try {
                instanciaNativa = LibGrafo.cargar();
            } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
                instanciaNativa = null;
            }
        }
        return instanciaNativa != null;
    }

    // Ejecuta por C si esta disponible y activado; si no, usa el respaldo Java puro
    public static DijkstraJava.ResultadoRuta calcularConContrato(int numParadas,
                                                                 int numTramos,
                                                                 int[] origen,
                                                                 int[] destino,
                                                                 int[] peso,
                                                                 int paradaInicio,
                                                                 int paradaFin,
                                                                 boolean usarNativo) {
        if (usarNativo && estaDisponibleNativo()) {
            // Java reserva la memoria antes de cruzar la frontera hacia C
            int[] caminoSalida = new int[numParadas];
            int[] costoSalida = new int[1];

            int cantidadParadas = instanciaNativa.dijkstra(
                    numParadas, numTramos, origen, destino, peso,
                    paradaInicio, paradaFin, caminoSalida, costoSalida
            );

            if (cantidadParadas < 0) {
                return new DijkstraJava.ResultadoRuta(-1, Collections.emptyList(), Collections.emptyList());
            }

            List<Integer> camino = new ArrayList<>(cantidadParadas);
            for (int i = 0; i < cantidadParadas; i++) {
                camino.add(caminoSalida[i]);
            }
            return new DijkstraJava.ResultadoRuta(costoSalida[0], camino, Collections.emptyList());
        }

        // Respaldo transparente en Java puro
        return DijkstraJava.calcular(numParadas, numTramos, origen, destino, peso, paradaInicio, paradaFin);
    }
}