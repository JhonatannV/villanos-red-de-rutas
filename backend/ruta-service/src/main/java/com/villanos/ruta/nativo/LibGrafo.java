package com.villanos.ruta.nativo;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface LibGrafo extends Library {

    // Carga la biblioteca compilada (libgrafo.so en Linux/Docker o grafo.dll en Windows)
    static LibGrafo cargar() {
        return Native.load("grafo", LibGrafo.class);
    }

    // Firma identica a backend/ruta-service/native/grafo.h
    int dijkstra(int numParadas,
                 int numTramos,
                 int[] origen,
                 int[] destino,
                 int[] peso,
                 int paradaInicio,
                 int paradaFin,
                 int[] caminoSalida,
                 int[] costoSalida);
}