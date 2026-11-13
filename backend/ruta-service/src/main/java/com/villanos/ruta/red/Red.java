package com.villanos.ruta.red;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Red de transporte en la forma que usan los algoritmos: paradas numeradas de
 * 0 a numParadas - 1 y tramos abiertos como arreglos paralelos.
 * El tramo i une origen[i] con destino[i], cuesta peso[i] y su id en grafo-service es tramoId[i].
 */
public final class Red {

    private final int numParadas;
    private final String[] nombres;
    private final int[] tramoId;
    private final int[] origen;
    private final int[] destino;
    private final int[] peso;

    public Red(String[] nombres, int[] tramoId, int[] origen, int[] destino, int[] peso) {
        if (tramoId.length != origen.length || origen.length != destino.length || destino.length != peso.length) {
            throw new IllegalArgumentException("Los arreglos de tramos deben tener el mismo tamaño");
        }
        this.numParadas = nombres.length;
        this.nombres = nombres;
        this.tramoId = tramoId;
        this.origen = origen;
        this.destino = destino;
        this.peso = peso;
    }

    /** Copia de la red sin los tramos indicados (simulación de cierre, parámetro ?sin=). */
    public Red sinTramos(Collection<Integer> cerrados) {
        if (cerrados == null || cerrados.isEmpty()) {
            return this;
        }
        List<Integer> quedan = new ArrayList<>();
        for (int i = 0; i < tramoId.length; i++) {
            if (!cerrados.contains(tramoId[i])) {
                quedan.add(i);
            }
        }
        int m = quedan.size();
        int[] id = new int[m], o = new int[m], d = new int[m], p = new int[m];
        for (int k = 0; k < m; k++) {
            int i = quedan.get(k);
            id[k] = tramoId[i];
            o[k] = origen[i];
            d[k] = destino[i];
            p[k] = peso[i];
        }
        return new Red(nombres, id, o, d, p);
    }

    /** true si existe un tramo con ese id en esta red. */
    public boolean tieneTramo(int id) {
        return Arrays.stream(tramoId).anyMatch(t -> t == id);
    }

    /**
     * Vecinos de cada parada en orden ascendente de número de parada.
     * Los tramos se recorren en los dos sentidos.
     */
    public List<List<Integer>> vecinos() {
        List<List<Integer>> adj = new ArrayList<>(numParadas);
        for (int i = 0; i < numParadas; i++) {
            adj.add(new ArrayList<>());
        }
        for (int i = 0; i < origen.length; i++) {
            adj.get(origen[i]).add(destino[i]);
            if (origen[i] != destino[i]) {
                adj.get(destino[i]).add(origen[i]);
            }
        }
        adj.forEach(l -> l.sort(Integer::compare));
        return adj;
    }

    public void validarParada(int parada, String nombreParametro) {
        if (parada < 0 || parada >= numParadas) {
            throw new IllegalArgumentException(
                    "La parada '" + nombreParametro + "' = " + parada + " no existe; la red tiene paradas de 0 a " + (numParadas - 1));
        }
    }

    public int numParadas() { return numParadas; }
    public int numTramos() { return origen.length; }
    public String nombre(int parada) { return nombres[parada]; }
    public int[] tramoId() { return tramoId; }
    public int[] origen() { return origen; }
    public int[] destino() { return destino; }
    public int[] peso() { return peso; }

    public List<String> nombres(Collection<Integer> paradas) {
        List<String> r = new ArrayList<>(paradas.size());
        for (int p : paradas) {
            r.add(nombres[p]);
        }
        return r;
    }
}
