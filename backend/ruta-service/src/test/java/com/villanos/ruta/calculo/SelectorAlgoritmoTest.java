package com.villanos.ruta.calculo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villanos.ruta.algoritmo.DijkstraJava;
import com.villanos.ruta.algoritmo.DijkstraJava.ResultadoRuta;
import com.villanos.ruta.nativo.LibGrafo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SelectorAlgoritmoTest {

    // Grafo guía A=0 ... E=4: de A a E el camino es A -> B -> D -> E con costo 11
    private static final int[] ORIGEN = {0, 0, 1, 1, 2, 2, 3};
    private static final int[] DESTINO = {1, 2, 2, 3, 3, 4, 4};
    private static final int[] PESO = {4, 2, 3, 5, 8, 15, 2};

    private static void assertRutaGuia(ResultadoRuta r) {
        assertEquals(11, r.costoTotal);
        assertEquals(List.of(0, 1, 3, 4), r.camino);
    }

    @Nested
    class LecturaDelModo {

        @Test
        void aceptaNativeYJavaSinImportarMayusculasNiEspacios() {
            assertEquals(ModoAlgoritmo.NATIVE, ModoAlgoritmo.desdeTexto("native"));
            assertEquals(ModoAlgoritmo.NATIVE, ModoAlgoritmo.desdeTexto("NATIVE"));
            assertEquals(ModoAlgoritmo.JAVA, ModoAlgoritmo.desdeTexto(" Java "));
        }

        @Test
        void vacioONuloUsaElModoPorDefecto() {
            assertEquals(ModoAlgoritmo.NATIVE, ModoAlgoritmo.desdeTexto(null));
            assertEquals(ModoAlgoritmo.NATIVE, ModoAlgoritmo.desdeTexto("  "));
        }

        @Test
        void unValorDesconocidoFallaConMensajeClaro() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ModoAlgoritmo.desdeTexto("rust"));
            assertTrue(e.getMessage().contains("app.algorithm.mode"));
        }
    }

    @Nested
    class Seleccion {

        @Test
        void enModoJavaNoIntentaCargarLaBiblioteca() {
            AtomicBoolean intento = new AtomicBoolean(false);
            SelectorAlgoritmo s = new SelectorAlgoritmo(ModoAlgoritmo.JAVA, () -> {
                intento.set(true);
                throw new AssertionError("no debía cargar la biblioteca");
            });

            assertFalse(intento.get());
            assertEquals(ModoAlgoritmo.JAVA, s.modoEfectivo());
            assertRutaGuia(s.calcular(5, ORIGEN, DESTINO, PESO, 0, 4));
        }

        @Test
        void sinBibliotecaCambiaAJavaSinCaerse() {
            SelectorAlgoritmo s = new SelectorAlgoritmo(ModoAlgoritmo.NATIVE, () -> {
                throw new UnsatisfiedLinkError("Unable to load library 'grafo'");
            });

            assertEquals(ModoAlgoritmo.NATIVE, s.modoPedido());
            assertEquals(ModoAlgoritmo.JAVA, s.modoEfectivo());
            assertTrue(s.motivo().contains("no se pudo cargar"));
            assertRutaGuia(s.calcular(5, ORIGEN, DESTINO, PESO, 0, 4));
        }

        @Test
        void siLaLlamadaNativaFallaCambiaAJavaYRespondeIgual() {
            SelectorAlgoritmo s = new SelectorAlgoritmo(ModoAlgoritmo.NATIVE, () -> SIN_BIBLIOTECA_EN_LLAMADA);

            assertEquals(ModoAlgoritmo.NATIVE, s.modoEfectivo());
            assertRutaGuia(s.calcular(5, ORIGEN, DESTINO, PESO, 0, 4));
            assertEquals(ModoAlgoritmo.JAVA, s.modoEfectivo());
            assertRutaGuia(s.calcular(5, ORIGEN, DESTINO, PESO, 0, 4));
        }

        @Test
        void sinMemoriaEnCRepiteEsaConsultaEnJava() {
            AtomicInteger llamadas = new AtomicInteger();
            LibGrafo sinMemoria = (n, m, o, d, p, i, f, camino, costo) -> {
                llamadas.incrementAndGet();
                return SelectorAlgoritmo.GRAFO_SIN_MEMORIA;
            };
            SelectorAlgoritmo s = new SelectorAlgoritmo(ModoAlgoritmo.NATIVE, () -> sinMemoria);

            assertRutaGuia(s.calcular(5, ORIGEN, DESTINO, PESO, 0, 4));
            assertEquals(1, llamadas.get());
            assertEquals(ModoAlgoritmo.NATIVE, s.modoEfectivo());
        }

        @Test
        void unCodigoNoDocumentadoSeReportaComoError() {
            LibGrafo raro = (n, m, o, d, p, i, f, camino, costo) -> -99;
            SelectorAlgoritmo s = new SelectorAlgoritmo(ModoAlgoritmo.NATIVE, () -> raro);

            assertThrows(IllegalStateException.class, () -> s.calcular(5, ORIGEN, DESTINO, PESO, 0, 4));
        }
    }

    // Biblioteca que "carga" pero falla al buscar la función, como pasa con un .so equivocado
    private static final LibGrafo SIN_BIBLIOTECA_EN_LLAMADA = (n, m, o, d, p, i, f, camino, costo) -> {
        throw new UnsatisfiedLinkError("Error looking up function 'dijkstra'");
    };

    @Nested
    class MismasReglasEnLosDosModos {

        private final SelectorAlgoritmo java = new SelectorAlgoritmo(ModoAlgoritmo.JAVA);
        private final SelectorAlgoritmo nativo = new SelectorAlgoritmo(ModoAlgoritmo.NATIVE);

        @Test
        void paradaFueraDeRango() {
            for (SelectorAlgoritmo s : List.of(java, nativo)) {
                assertThrows(IllegalArgumentException.class, () -> s.calcular(5, ORIGEN, DESTINO, PESO, 0, 99));
                assertThrows(IllegalArgumentException.class, () -> s.calcular(5, ORIGEN, DESTINO, PESO, -1, 4));
            }
        }

        @Test
        void pesoNegativoYTramoHaciaParadaInexistente() {
            for (SelectorAlgoritmo s : List.of(java, nativo)) {
                assertThrows(IllegalArgumentException.class,
                        () -> s.calcular(2, new int[]{0}, new int[]{1}, new int[]{-3}, 0, 1));
                assertThrows(IllegalArgumentException.class,
                        () -> s.calcular(3, new int[]{0}, new int[]{7}, new int[]{1}, 0, 1));
            }
        }

        @Test
        void sinRutaDevuelveCostoMenosUnoYCaminoVacio() {
            for (SelectorAlgoritmo s : List.of(java, nativo)) {
                ResultadoRuta r = s.calcular(4, new int[]{0, 2}, new int[]{1, 3}, new int[]{5, 5}, 0, 3);
                assertEquals(-1, r.costoTotal);
                assertTrue(r.camino.isEmpty());
            }
        }
    }

    /**
     * Solo corre si la biblioteca compilada está disponible: grafo.dll en Windows o
     * libgrafo.so en Linux, en backend/ruta-service/native (ver pom.xml).
     */
    @Nested
    class ConLaBibliotecaReal {

        private SelectorAlgoritmo nativoDisponible() {
            SelectorAlgoritmo s = new SelectorAlgoritmo(ModoAlgoritmo.NATIVE);
            assumeTrue(s.modoEfectivo() == ModoAlgoritmo.NATIVE,
                    "biblioteca nativa no compilada: " + s.motivo());
            return s;
        }

        @Test
        void usaCYDaElResultadoDelGrafoGuia() {
            SelectorAlgoritmo s = nativoDisponible();
            assertRutaGuia(s.calcular(5, ORIGEN, DESTINO, PESO, 0, 4));
            assertEquals(ModoAlgoritmo.NATIVE, s.modoEfectivo());
        }

        @Test
        void cYJavaCoincidenEnTodasLasParejasDeLaRed40() throws IOException {
            SelectorAlgoritmo c = nativoDisponible();
            int[][] red = leerRed40();
            int n = 40, comparadas = 0;
            for (int a = 0; a < n; a++) {
                for (int b = 0; b < n; b++) {
                    ResultadoRuta rc = c.calcular(n, red[0], red[1], red[2], a, b);
                    ResultadoRuta rj = DijkstraJava.calcular(n, red[0].length, red[0], red[1], red[2], a, b);
                    assertEquals(rj.costoTotal, rc.costoTotal, "costo " + a + " -> " + b);
                    assertEquals(rj.camino, rc.camino, "camino " + a + " -> " + b);
                    comparadas++;
                }
            }
            assertEquals(1600, comparadas);
        }
    }

    /** Red de 40 paradas y 90 tramos de grafo-service, como arreglos paralelos. */
    private static int[][] leerRed40() throws IOException {
        Path json = Path.of("..", "grafo-service", "data", "grafo_40_paradas.json");
        JsonObject raiz = JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject();
        int m = raiz.getAsJsonArray("tramos").size();
        int[] o = new int[m], d = new int[m], p = new int[m];
        int k = 0;
        for (JsonElement e : raiz.getAsJsonArray("tramos")) {
            JsonObject t = e.getAsJsonObject();
            o[k] = t.get("origen").getAsInt();
            d[k] = t.get("destino").getAsInt();
            p[k] = t.get("peso").getAsInt();
            k++;
        }
        assertEquals(90, m);
        assertTrue(Arrays.stream(p).allMatch(x -> x > 0));
        return new int[][]{o, d, p};
    }
}
