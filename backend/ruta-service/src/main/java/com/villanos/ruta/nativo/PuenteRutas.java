package com.villanos.ruta.nativo;

import com.villanos.ruta.algoritmo.DijkstraJava;
import com.villanos.ruta.calculo.ModoAlgoritmo;
import com.villanos.ruta.calculo.SelectorAlgoritmo;

import java.util.Arrays;

/**
 * Acceso estático al cálculo de rutas. Delega en {@link SelectorAlgoritmo}, que
 * decide entre C y Java y hace el respaldo automático.
 */
public class PuenteRutas {

    private static SelectorAlgoritmo selectorNativo;
    private static final SelectorAlgoritmo SELECTOR_JAVA = new SelectorAlgoritmo(ModoAlgoritmo.JAVA);

    // Intenta cargar el binario C una sola vez sin tumbar el programa si aún no existe
    private static synchronized SelectorAlgoritmo selectorNativo() {
        if (selectorNativo == null) {
            selectorNativo = new SelectorAlgoritmo(ModoAlgoritmo.NATIVE);
        }
        return selectorNativo;
    }

    public static boolean estaDisponibleNativo() {
        return selectorNativo().modoEfectivo() == ModoAlgoritmo.NATIVE;
    }

    // Ejecuta por C si está disponible y activado; si no, usa el respaldo Java puro
    public static DijkstraJava.ResultadoRuta calcularConContrato(int numParadas,
                                                                 int numTramos,
                                                                 int[] origen,
                                                                 int[] destino,
                                                                 int[] peso,
                                                                 int paradaInicio,
                                                                 int paradaFin,
                                                                 boolean usarNativo) {
        SelectorAlgoritmo selector = usarNativo ? selectorNativo() : SELECTOR_JAVA;
        return selector.calcular(numParadas,
                Arrays.copyOf(origen, numTramos),
                Arrays.copyOf(destino, numTramos),
                Arrays.copyOf(peso, numTramos),
                paradaInicio, paradaFin);
    }
}
