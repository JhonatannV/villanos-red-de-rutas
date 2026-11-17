package com.villanos.ruta.algoritmo;

import com.villanos.ruta.algoritmo.DijkstraJava.PasoTraza;
import com.villanos.ruta.algoritmo.DijkstraJava.Relajacion;
import com.villanos.ruta.algoritmo.DijkstraJava.ResultadoRuta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RF-04: la traza paso a paso que consume el reproductor (SCRUM-31). */
class TrazaDijkstraTest {

    // Grafo guía de 5 paradas (A=0 ... E=4), el mismo de la traza a mano
    private static final int[] ORIGEN = {0, 0, 1, 1, 2, 2, 3};
    private static final int[] DESTINO = {1, 2, 2, 3, 3, 4, 4};
    private static final int[] PESO = {4, 2, 3, 5, 8, 15, 2};

    private static ResultadoRuta guia(int desde, int hasta) {
        return DijkstraJava.calcular(5, ORIGEN.length, ORIGEN, DESTINO, PESO, desde, hasta);
    }

    private static void relajacion(Relajacion r, int vecino, Integer anterior, int propuesta, boolean mejora) {
        assertEquals(vecino, r.vecino, "vecino");
        assertEquals(anterior, r.anterior, "distancia anterior de " + vecino);
        assertEquals(propuesta, r.propuesta, "propuesta para " + vecino);
        assertEquals(mejora, r.mejora, "mejora de " + vecino);
    }

    @Test
    void trazaDelGrafoGuiaCoincideConLaTrazaAMano() {
        ResultadoRuta r = guia(0, 4);
        List<PasoTraza> pasos = r.pasos;

        assertEquals(11, r.costoTotal);
        assertEquals(List.of(0, 1, 3, 4), r.camino);
        assertEquals(5, pasos.size());

        // Paso 1: se cierra A; B y C pasan de infinito a 4 y 2
        PasoTraza p1 = pasos.get(0);
        assertEquals(0, p1.actual);
        assertEquals(0, p1.distancia);
        assertEquals(-1, p1.desde);
        assertEquals(2, p1.relajaciones.size());
        relajacion(p1.relajaciones.get(0), 1, null, 4, true);
        relajacion(p1.relajaciones.get(1), 2, null, 2, true);

        // Paso 2: se cierra C; por C, B costaría 5 y ya tiene 4, así que no mejora
        PasoTraza p2 = pasos.get(1);
        assertEquals(2, p2.actual);
        assertEquals(0, p2.desde);
        assertEquals(3, p2.relajaciones.size());
        relajacion(p2.relajaciones.get(0), 1, 4, 5, false);
        relajacion(p2.relajaciones.get(1), 3, null, 10, true);
        relajacion(p2.relajaciones.get(2), 4, null, 17, true);

        // Paso 3: se cierra B; D baja de 10 a 9
        PasoTraza p3 = pasos.get(2);
        assertEquals(1, p3.actual);
        assertEquals(4, p3.distancia);
        relajacion(p3.relajaciones.get(0), 3, 10, 9, true);

        // Paso 4: se cierra D; E baja de 17 a 11
        PasoTraza p4 = pasos.get(3);
        assertEquals(3, p4.actual);
        assertEquals(1, p4.desde);
        relajacion(p4.relajaciones.get(0), 4, 17, 11, true);

        // Paso 5: se cierra E (el destino) y el algoritmo se detiene
        PasoTraza p5 = pasos.get(4);
        assertEquals(4, p5.actual);
        assertEquals(11, p5.distancia);
        assertEquals(3, p5.desde);
        assertTrue(p5.relajaciones.isEmpty());
    }

    @Test
    void pasosNumeradosYAlineadosConNodosCerrados() {
        ResultadoRuta r = guia(4, 0);
        assertEquals(r.nodosCerrados.size(), r.pasos.size());
        for (int i = 0; i < r.pasos.size(); i++) {
            assertEquals(i + 1, r.pasos.get(i).paso);
            assertEquals(r.nodosCerrados.get(i).nodo, r.pasos.get(i).actual);
            assertEquals(r.nodosCerrados.get(i).distancia, r.pasos.get(i).distancia);
        }
    }

    @Test
    void mismaParadaDaUnSoloPaso() {
        ResultadoRuta r = guia(2, 2);
        assertEquals(1, r.pasos.size());
        assertEquals(2, r.pasos.get(0).actual);
        assertEquals(-1, r.pasos.get(0).desde);
    }

    @Test
    void destinoInalcanzableConservaLaTrazaDeLoExplorado() {
        // 0-1 conectados; 2 aislada
        int[] o = {0};
        int[] d = {1};
        int[] w = {3};
        ResultadoRuta r = DijkstraJava.calcular(3, 1, o, d, w, 0, 2);
        assertEquals(-1, r.costoTotal);
        assertEquals(2, r.pasos.size());
        assertFalse(r.pasos.stream().anyMatch(p -> p.actual == 2));
        assertNull(r.pasos.get(0).relajaciones.get(0).anterior);
    }
}
