package com.villanos.ruta.calculo;

import com.villanos.ruta.algoritmo.DijkstraJava.ResultadoRuta;

/**
 * Cálculo de la ruta de menor peso sobre una red dada como arreglos paralelos
 * (el tramo i une origen[i] con destino[i] y cuesta peso[i]).
 *
 * <p>Si no hay camino, el resultado tiene {@code costoTotal = -1} y el camino vacío.
 */
public interface CalculadorRutas {

    ResultadoRuta calcular(int numParadas, int[] origen, int[] destino, int[] peso,
                           int paradaInicio, int paradaFin);

    /** Implementación activa en este momento. */
    ModoAlgoritmo modoEfectivo();
}
