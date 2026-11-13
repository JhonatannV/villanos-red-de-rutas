package com.villanos.ruta.algoritmo;

import com.villanos.ruta.red.Red;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalisisCiclosTest {

    private static AnalisisCiclos.Resultado analizar(Red r) {
        return AnalisisCiclos.analizar(r.numParadas(), r.tramoId(), r.origen(), r.destino());
    }

    @Test
    void unArbolNoTieneCiclos() {
        // 0-1, 1-2, 1-3: 4 paradas, 3 tramos, 1 componente -> 3 - 4 + 1 = 0
        Red arbol = new Red(new String[]{"A", "B", "C", "D"}, new int[]{1, 2, 3},
                new int[]{0, 1, 1}, new int[]{1, 2, 3}, new int[]{1, 1, 1});
        AnalisisCiclos.Resultado r = analizar(arbol);
        assertFalse(r.tieneCiclos());
        assertEquals(0, r.numeroCiclomatico);
        assertTrue(r.ciclos.isEmpty());
    }

    @Test
    void unCuadradoTieneUnCicloDeCuatroParadas() {
        // 0-1-2-3-0: el tramo 4 (3-0) cierra el ciclo
        Red cuadrado = new Red(new String[]{"A", "B", "C", "D"}, new int[]{1, 2, 3, 4},
                new int[]{0, 1, 2, 3}, new int[]{1, 2, 3, 0}, new int[]{1, 1, 1, 1});
        AnalisisCiclos.Resultado r = analizar(cuadrado);
        assertTrue(r.tieneCiclos());
        assertEquals(1, r.numeroCiclomatico);
        assertEquals(1, r.ciclos.size());
        assertEquals(4, r.ciclos.get(0).paradas.size());
        assertEquals(new HashSet<>(List.of(0, 1, 2, 3)), new HashSet<>(r.ciclos.get(0).paradas));
    }

    @Test
    void dosTriangulosSeparadosDanDosCiclosYDosComponentes() {
        Red r2 = new Red(new String[]{"A", "B", "C", "D", "E", "F"}, new int[]{1, 2, 3, 4, 5, 6},
                new int[]{0, 1, 0, 3, 4, 3}, new int[]{1, 2, 2, 4, 5, 5}, new int[]{1, 1, 1, 1, 1, 1});
        AnalisisCiclos.Resultado r = analizar(r2);
        assertEquals(2, r.componentes);
        assertEquals(2, r.numeroCiclomatico);
        assertTrue(r.ciclos.stream().allMatch(c -> c.paradas.size() == 3));
    }

    @Test
    void cadaCicloEsUnCaminoCerradoPorTramosQueExisten() throws IOException {
        Red red = RecorridosTest.red40();
        AnalisisCiclos.Resultado r = analizar(red);
        for (AnalisisCiclos.Ciclo c : r.ciclos) {
            List<Integer> p = c.paradas;
            assertEquals(p.size(), new HashSet<>(p).size(), "un ciclo fundamental no repite paradas");
            for (int i = 0; i < p.size(); i++) {
                int a = p.get(i), b = p.get((i + 1) % p.size());
                assertTrue(hayTramo(red, a, b), "falta el tramo " + a + "-" + b);
            }
        }
    }

    @Test
    void laRed40Tiene51CiclosIndependientes() throws IOException {
        // 90 tramos - 40 paradas + 1 componente = 51
        AnalisisCiclos.Resultado r = analizar(RecorridosTest.red40());
        assertTrue(r.tieneCiclos());
        assertEquals(51, r.numeroCiclomatico);
        assertEquals(51, r.ciclos.size());
        assertEquals(3, r.ciclos.get(0).paradas.size(), "el más corto es un triángulo");
    }

    @Test
    void conElTramo90CerradoSiguenLos51CiclosPorqueEraUnPuente() throws IOException {
        // 89 - 40 + 2 = 51: un puente no forma parte de ningún ciclo
        AnalisisCiclos.Resultado r = analizar(RecorridosTest.red40().sinTramos(List.of(90)));
        assertEquals(2, r.componentes);
        assertEquals(51, r.numeroCiclomatico);
    }

    @Test
    void memoriaDeLaRed40() {
        MemoriaRepresentacion m = new MemoriaRepresentacion(40, 90);
        assertEquals(4L * (41 + 360), m.bytesLista);    // 1.604 bytes
        assertEquals(4L * 40 * 40, m.bytesMatriz);      // 6.400 bytes
        assertEquals(390, m.tramosParaQueConvengaLaMatriz);
        assertEquals(90.0 / 780, m.densidad, 1e-9);
        assertEquals(1 - 180.0 / 1600, m.casillasVaciasMatriz, 1e-9);
    }

    @Test
    void memoriaDeLaRedGrande() {
        MemoriaRepresentacion m = new MemoriaRepresentacion(400, 1100);
        assertEquals(19_204, m.bytesLista);
        assertEquals(640_000, m.bytesMatriz);
        assertTrue(m.relacionMatrizSobreLista() > 33);
    }

    private static boolean hayTramo(Red red, int a, int b) {
        for (int i = 0; i < red.numTramos(); i++) {
            if ((red.origen()[i] == a && red.destino()[i] == b) || (red.origen()[i] == b && red.destino()[i] == a)) {
                return true;
            }
        }
        return false;
    }
}
