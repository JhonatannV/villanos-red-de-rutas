package com.villanos.ruta.algoritmo;

import com.villanos.ruta.red.Red;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConectividadTest {

    /*
     * Dos triángulos, {0,1,2} y {3,4,5}, unidos por un solo tramo (el 7, entre 2 y 3).
     * Tramos (id: origen-destino): 1:0-1  2:1-2  3:0-2  7:2-3  4:3-4  5:4-5  6:3-5
     * Puente: solo el 7. Al cerrarlo quedan {0,1,2} y {3,4,5}.
     */
    private static final Red DOS_TRIANGULOS = new Red(
            new String[]{"A", "B", "C", "D", "E", "F"},
            new int[]{1, 2, 3, 7, 4, 5, 6},
            new int[]{0, 1, 0, 2, 3, 4, 3},
            new int[]{1, 2, 2, 3, 4, 5, 5},
            new int[]{1, 1, 1, 1, 1, 1, 1});

    @Test
    void dosTriangulosUnidosForman1ComponenteYTienenUnPuente() {
        Red r = DOS_TRIANGULOS;
        assertEquals(1, Conectividad.componentes(r.numParadas(), r.origen(), r.destino()).size());
        List<Integer> puentes = Conectividad.puentes(r.numParadas(), r.origen(), r.destino());
        assertEquals(List.of(3), puentes, "el índice 3 corresponde al tramo 7");
    }

    @Test
    void alCerrarElPuenteQuedanDosGrupos() {
        Red r = DOS_TRIANGULOS.sinTramos(List.of(7));
        List<List<Integer>> grupos = Conectividad.componentes(r.numParadas(), r.origen(), r.destino());
        assertEquals(List.of(List.of(0, 1, 2), List.of(3, 4, 5)), grupos);
        assertTrue(Conectividad.puentes(r.numParadas(), r.origen(), r.destino()).isEmpty());
    }

    @Test
    void tramosRepetidosYBuclesNoSonPuentes() {
        // 0-1 dos veces y un bucle en 1: el par 0-1 sigue unido si se quita uno
        Red r = new Red(new String[]{"A", "B"}, new int[]{1, 2, 3},
                new int[]{0, 0, 1}, new int[]{1, 1, 1}, new int[]{1, 1, 1});
        assertTrue(Conectividad.puentes(r.numParadas(), r.origen(), r.destino()).isEmpty());
    }

    @Test
    void paradaSinTramosEsSuPropioGrupo() {
        Red r = new Red(new String[]{"A", "B", "C"}, new int[]{1}, new int[]{0}, new int[]{1}, new int[]{1});
        assertEquals(List.of(List.of(0, 1), List.of(2)),
                Conectividad.componentes(r.numParadas(), r.origen(), r.destino()));
    }

    @Test
    void enLaRed40ElUnicoPuenteEsElTramo90YAlCerrarloQuedanAisladas37a39() throws IOException {
        Red red = RecorridosTest.red40();
        assertEquals(1, Conectividad.componentes(red.numParadas(), red.origen(), red.destino()).size());

        List<Integer> puentes = Conectividad.puentes(red.numParadas(), red.origen(), red.destino());
        assertEquals(1, puentes.size());
        assertEquals(90, red.tramoId()[puentes.get(0)]);

        Red cerrada = red.sinTramos(List.of(90));
        List<List<Integer>> grupos = Conectividad.componentes(cerrada.numParadas(), cerrada.origen(), cerrada.destino());
        assertEquals(2, grupos.size());
        assertEquals(37, grupos.get(0).size());
        assertEquals(List.of(37, 38, 39), grupos.get(1));
        assertEquals(List.of("Peaje Aeropuerto", "Terminal Aérea", "Zona Rural La Marina"), cerrada.nombres(grupos.get(1)));
    }

    @Test
    void cerrarUnTramoQueNoEsPuenteNoAislaParadas() throws IOException {
        Red red = RecorridosTest.red40().sinTramos(List.of(1));
        assertEquals(1, Conectividad.componentes(red.numParadas(), red.origen(), red.destino()).size());
    }
}
