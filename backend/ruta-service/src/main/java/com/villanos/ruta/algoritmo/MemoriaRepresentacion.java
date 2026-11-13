package com.villanos.ruta.algoritmo;

/**
 * Ocupación teórica de memoria de las dos representaciones de la red [RF-07],
 * con enteros de 4 bytes (int de C y de Java).
 *
 * <ul>
 *   <li><b>Lista de adyacencia compacta</b>, la que usa dijkstra.c:
 *       inicio_vecinos[V + 1] + vecino[2E] + peso_vecino[2E]
 *       = 4·(V + 1 + 4E) bytes. Cada tramo aparece dos veces porque se recorre en los dos sentidos.</li>
 *   <li><b>Matriz de adyacencia</b> V × V con el peso de cada tramo (0 = sin tramo)
 *       = 4·V² bytes.</li>
 * </ul>
 */
public final class MemoriaRepresentacion {

    public static final int BYTES_POR_ENTERO = 4;

    public final int paradas;
    public final int tramos;
    public final long bytesLista;
    public final long bytesMatriz;
    /** Fracción de pares de paradas que tienen tramo: E / (V·(V-1)/2). */
    public final double densidad;
    /** Fracción de casillas de la matriz que quedan en 0. */
    public final double casillasVaciasMatriz;
    /** Con cuántos tramos la matriz empezaría a ocupar menos que la lista. */
    public final long tramosParaQueConvengaLaMatriz;

    public MemoriaRepresentacion(int paradas, int tramos) {
        if (paradas <= 0 || tramos < 0) {
            throw new IllegalArgumentException("La red debe tener al menos una parada y tramos no negativos");
        }
        this.paradas = paradas;
        this.tramos = tramos;
        this.bytesLista = (long) BYTES_POR_ENTERO * (paradas + 1L + 4L * tramos);
        this.bytesMatriz = (long) BYTES_POR_ENTERO * paradas * paradas;
        long pares = (long) paradas * (paradas - 1) / 2;
        this.densidad = pares == 0 ? 0 : (double) tramos / pares;
        this.casillasVaciasMatriz = 1.0 - Math.min(1.0, 2.0 * tramos / ((double) paradas * paradas));
        // 4(V + 1 + 4E) > 4V²  <=>  E > (V² - V - 1) / 4
        this.tramosParaQueConvengaLaMatriz = ((long) paradas * paradas - paradas - 1) / 4 + 1;
    }

    /** Cuántas veces ocupa la matriz lo que ocupa la lista. */
    public double relacionMatrizSobreLista() {
        return (double) bytesMatriz / bytesLista;
    }
}
