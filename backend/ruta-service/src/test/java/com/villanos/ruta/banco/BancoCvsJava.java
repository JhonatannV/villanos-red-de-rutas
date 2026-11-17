package com.villanos.ruta.banco;

import com.villanos.ruta.algoritmo.DijkstraJava;
import com.villanos.ruta.algoritmo.DijkstraJava.ResultadoRuta;
import com.villanos.ruta.nativo.LibGrafo;
import com.villanos.ruta.red.ClienteRed;
import com.villanos.ruta.red.Red;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * SCRUM-37 [RN-BENCH] · Banco C vs. Java con 1.000 rutas.
 *
 * <p>Mide, sobre la misma red y las mismas 1.000 consultas origen-destino:
 * <ul>
 *   <li><b>Java</b>: {@link DijkstraJava#calcular}, el respaldo que usa ruta-service.</li>
 *   <li><b>C por JNA</b>: {@code dijkstra()} de libgrafo.so llamada como en producción
 *       (arreglos {@code int[]} que JNA copia en cada llamada, D-02).</li>
 *   <li><b>C puro</b>: el mismo {@code dijkstra()} desde un programa en C ({@code banco_c}),
 *       sin cruzar de Java a C. La diferencia con "C por JNA" es el costo de JNA.</li>
 * </ul>
 * Cada motor corre 6 veces las 1.000 consultas; la primera corrida se descarta y se
 * reporta la mediana de las otras 5. Antes se verifica que C y Java den el mismo costo
 * y el mismo camino en las 1.000 consultas.
 *
 * <p>No es una prueba JUnit (no termina en Test): se ejecuta con Dockerfile.banco.
 * Uso: {@code java ... BancoCvsJava <grafo_400_paradas.json> <carpeta salida> <ruta de banco_c>}
 */
public final class BancoCvsJava {

    static final int CONSULTAS = 1000;
    static final int CORRIDAS = 6;            // la primera se descarta: quedan 5
    static final int CALENTAMIENTO = 2000;    // consultas previas para que el JIT compile
    static final double TRAMOS_POR_PARADA = 2.75;
    static final int[] TAMANOS = {50, 150, 400, 1000, 2000, 4000};

    /** Una red y sus consultas, listas para los tres motores. */
    record Caso(String nombre, int paradas, int[] origen, int[] destino, int[] peso, int[] pares) {
        int tramos() { return origen.length; }
    }

    record Medicion(String caso, int paradas, int tramos, String motor, double[] usPorCorrida, long sumaCostos) {
        double mediana() {
            double[] v = Arrays.copyOfRange(usPorCorrida, 1, usPorCorrida.length);
            Arrays.sort(v);
            return v[v.length / 2];
        }
    }

    private static PrintStream out;

    public static void main(String[] args) throws Exception {
        Path json = Path.of(args.length > 0 ? args[0] : "grafo_400_paradas.json");
        Path salida = Path.of(args.length > 1 ? args[1] : ".");
        String bancoC = args.length > 2 ? args[2] : "banco_c";
        Files.createDirectories(salida);
        out = new PrintStream(Files.newOutputStream(salida.resolve("scrum37_salida.txt")), true, StandardCharsets.UTF_8);

        LibGrafo lib = LibGrafo.cargar();
        decir("SCRUM-37 [RN-BENCH] - Banco C vs. Java");
        decir("%d consultas por red, %d corridas (se descarta la 1.a), mediana por consulta", CONSULTAS, CORRIDAS);
        decir("Java %s (%s), %d procesadores visibles, %s %s",
                System.getProperty("java.version"), System.getProperty("java.vm.name"),
                Runtime.getRuntime().availableProcessors(), System.getProperty("os.name"), System.getProperty("os.arch"));
        decir("");

        List<Caso> casos = new ArrayList<>();
        casos.add(casoRedReal(json));
        for (int v : TAMANOS) {
            casos.add(casoGenerado(v));
        }

        List<Medicion> mediciones = new ArrayList<>();
        decir("%-26s %7s %7s %10s %12s %10s %9s %9s %10s", "Red", "Paradas", "Tramos",
                "C puro", "C por JNA", "Java", "Java/C", "JNA/C", "Caminos=");
        for (Caso caso : casos) {
            int caminosIguales = verificarParidad(lib, caso);
            calentar(lib, caso);
            Medicion java = new Medicion(caso.nombre, caso.paradas, caso.tramos(), "java", new double[CORRIDAS], 0);
            Medicion jna = new Medicion(caso.nombre, caso.paradas, caso.tramos(), "c_jna", new double[CORRIDAS], 0);
            long sumaJava = 0;
            long sumaJna = 0;
            for (int c = 0; c < CORRIDAS; c++) {
                // Se alterna el orden para que ninguno se beneficie siempre de ir segundo
                if (c % 2 == 0) {
                    sumaJava = correrJava(caso, java.usPorCorrida, c);
                    sumaJna = correrJna(lib, caso, jna.usPorCorrida, c);
                } else {
                    sumaJna = correrJna(lib, caso, jna.usPorCorrida, c);
                    sumaJava = correrJava(caso, java.usPorCorrida, c);
                }
            }
            Medicion cPuro = correrCPuro(bancoC, caso, salida);
            if (sumaJava != sumaJna || sumaJava != cPuro.sumaCostos()) {
                throw new IllegalStateException("Los motores no suman los mismos costos en " + caso.nombre
                        + ": java=" + sumaJava + " jna=" + sumaJna + " c=" + cPuro.sumaCostos());
            }
            java = new Medicion(java.caso(), java.paradas(), java.tramos(), java.motor(), java.usPorCorrida(), sumaJava);
            jna = new Medicion(jna.caso(), jna.paradas(), jna.tramos(), jna.motor(), jna.usPorCorrida(), sumaJna);
            mediciones.add(cPuro);
            mediciones.add(jna);
            mediciones.add(java);
            decir("%-26s %7d %7d %10.2f %12.2f %10.2f %8.1fx %8.1fx %5d/%d", caso.nombre, caso.paradas, caso.tramos(),
                    cPuro.mediana(), jna.mediana(), java.mediana(),
                    java.mediana() / cPuro.mediana(), jna.mediana() / cPuro.mediana(), caminosIguales, CONSULTAS);
        }
        decir("");
        decir("Tiempos en microsegundos por consulta. Caminos= : consultas en que C y Java dan el mismo camino");
        decir("(el costo coincide en todas; si no, el banco se detiene).");

        escribirCsv(salida, mediciones);
        out.close();
    }

    /* ---------------- Casos ---------------- */

    static Caso casoRedReal(Path json) throws IOException {
        Red red = ClienteRed.desdeJson(Files.readString(json, StandardCharsets.UTF_8));
        Xorshift r = new Xorshift(20261117);
        return new Caso("Red real 400 (redId 2)", red.numParadas(), red.origen(), red.destino(), red.peso(),
                pares(r, red.numParadas()));
    }

    /** Las mismas redes que generar_red() de bench.c (SCRUM-6): mismas semillas, mismos tramos. */
    static Caso casoGenerado(int paradas) {
        int tramos = (int) Math.round(paradas * TRAMOS_POR_PARADA);
        int[] o = new int[tramos], d = new int[tramos], w = new int[tramos];
        boolean[] usado = new boolean[paradas * paradas];
        Xorshift r = new Xorshift(20261111 + paradas);
        int k = 0;
        for (int v = 1; v < paradas; v++) {
            int u = r.entre(0, v - 1);
            o[k] = u; d[k] = v; w[k] = r.entre(1, 15); k++;
            usado[u * paradas + v] = usado[v * paradas + u] = true;
        }
        while (k < tramos) {
            int u = r.entre(0, paradas - 1), v = r.entre(0, paradas - 1);
            if (u == v || usado[u * paradas + v]) {
                continue;
            }
            usado[u * paradas + v] = usado[v * paradas + u] = true;
            o[k] = u; d[k] = v; w[k] = r.entre(1, 15); k++;
        }
        return new Caso("Generada " + paradas, paradas, o, d, w, pares(new Xorshift(7 + paradas), paradas));
    }

    static int[] pares(Xorshift r, int paradas) {
        int[] p = new int[2 * CONSULTAS];
        for (int q = 0; q < CONSULTAS; q++) {
            p[2 * q] = r.entre(0, paradas - 1);
            do {
                p[2 * q + 1] = r.entre(0, paradas - 1);
            } while (p[2 * q + 1] == p[2 * q]);
        }
        return p;
    }

    /** Mismo generador xorshift32 que bench.c, con aritmética sin signo. */
    static final class Xorshift {
        private int s;

        Xorshift(int semilla) { this.s = semilla; }

        int siguiente() {
            s ^= s << 13;
            s ^= s >>> 17;
            s ^= s << 5;
            return s;
        }

        int entre(int a, int b) {
            return a + (int) (Integer.toUnsignedLong(siguiente()) % (b - a + 1));
        }
    }

    /* ---------------- Motores ---------------- */

    static long correrJava(Caso k, double[] us, int corrida) {
        long suma = 0;
        long t0 = System.nanoTime();
        for (int q = 0; q < CONSULTAS; q++) {
            ResultadoRuta r = DijkstraJava.calcular(k.paradas, k.tramos(), k.origen, k.destino, k.peso,
                    k.pares[2 * q], k.pares[2 * q + 1]);
            if (r.costoTotal >= 0) {
                suma += r.costoTotal;
            }
        }
        us[corrida] = (System.nanoTime() - t0) / 1000.0 / CONSULTAS;
        return suma;
    }

    /** Igual que SelectorAlgoritmo.calcularNativo: reserva la salida en Java y llama por JNA. */
    static long correrJna(LibGrafo lib, Caso k, double[] us, int corrida) {
        long suma = 0;
        long t0 = System.nanoTime();
        for (int q = 0; q < CONSULTAS; q++) {
            int[] camino = new int[k.paradas];
            int[] costo = new int[1];
            int r = lib.dijkstra(k.paradas, k.tramos(), k.origen, k.destino, k.peso,
                    k.pares[2 * q], k.pares[2 * q + 1], camino, costo);
            if (r > 0) {
                suma += costo[0];
            }
        }
        us[corrida] = (System.nanoTime() - t0) / 1000.0 / CONSULTAS;
        return suma;
    }

    static Medicion correrCPuro(String bancoC, Caso k, Path carpeta) throws IOException, InterruptedException {
        Path entrada = Files.createTempFile("banco_c_", ".txt");
        StringBuilder sb = new StringBuilder();
        sb.append(k.paradas).append(' ').append(k.tramos()).append('\n');
        for (int i = 0; i < k.tramos(); i++) {
            sb.append(k.origen[i]).append(' ').append(k.destino[i]).append(' ').append(k.peso[i]).append('\n');
        }
        sb.append(CONSULTAS).append('\n');
        for (int q = 0; q < CONSULTAS; q++) {
            sb.append(k.pares[2 * q]).append(' ').append(k.pares[2 * q + 1]).append('\n');
        }
        Files.writeString(entrada, sb.toString());

        Process p = new ProcessBuilder(bancoC, entrada.toString(), String.valueOf(CORRIDAS)).redirectErrorStream(true).start();
        double[] us = new double[CORRIDAS];
        long suma = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] c = linea.split(",");
                if (c.length != 3) {
                    throw new IllegalStateException("Salida inesperada de banco_c: " + linea);
                }
                us[Integer.parseInt(c[0]) - 1] = Double.parseDouble(c[1]);
                suma = Long.parseLong(c[2]);
            }
        }
        if (p.waitFor() != 0) {
            throw new IllegalStateException("banco_c terminó con código " + p.exitValue());
        }
        Files.deleteIfExists(entrada);
        return new Medicion(k.nombre, k.paradas, k.tramos(), "c_puro", us, suma);
    }

    /* ---------------- Verificación y calentamiento ---------------- */

    /**
     * C y Java deben dar el mismo costo en las 1.000 consultas; si no, el banco se detiene.
     * Devuelve en cuántas dan además exactamente el mismo camino (regla de desempate D-06).
     */
    static int verificarParidad(LibGrafo lib, Caso k) {
        int caminosIguales = 0;
        for (int q = 0; q < CONSULTAS; q++) {
            int a = k.pares[2 * q], b = k.pares[2 * q + 1];
            ResultadoRuta j = DijkstraJava.calcular(k.paradas, k.tramos(), k.origen, k.destino, k.peso, a, b);
            int[] camino = new int[k.paradas];
            int[] costo = new int[1];
            int n = lib.dijkstra(k.paradas, k.tramos(), k.origen, k.destino, k.peso, a, b, camino, costo);
            int costoC = n > 0 ? costo[0] : -1;
            if (costoC != j.costoTotal) {
                throw new IllegalStateException(String.format("Costo distinto en %s, %d -> %d: C=%d Java=%d",
                        k.nombre, a, b, costoC, j.costoTotal));
            }
            List<Integer> cc = new ArrayList<>();
            for (int i = 0; i < Math.max(n, 0); i++) {
                cc.add(camino[i]);
            }
            if (cc.equals(j.camino)) {
                caminosIguales++;
            }
        }
        return caminosIguales;
    }

    static void calentar(LibGrafo lib, Caso k) {
        double[] basura = new double[1];
        for (int i = 0; i < CALENTAMIENTO / CONSULTAS; i++) {
            correrJava(k, basura, 0);
            correrJna(lib, k, basura, 0);
        }
    }

    /* ---------------- Salida ---------------- */

    static void escribirCsv(Path carpeta, List<Medicion> mediciones) throws IOException {
        StringBuilder crudo = new StringBuilder("red,paradas,tramos,motor,corrida,us_por_consulta,descartada\n");
        StringBuilder resumen = new StringBuilder("red,paradas,tramos,us_c_puro,us_c_jna,us_java\n");
        for (Medicion m : mediciones) {
            for (int c = 0; c < m.usPorCorrida().length; c++) {
                crudo.append(String.format(Locale.ROOT, "%s,%d,%d,%s,%d,%.4f,%s%n", m.caso(), m.paradas(), m.tramos(),
                        m.motor(), c + 1, m.usPorCorrida()[c], c == 0 ? "si" : "no"));
            }
        }
        for (int i = 0; i < mediciones.size(); i += 3) {
            Medicion c = mediciones.get(i), jna = mediciones.get(i + 1), java = mediciones.get(i + 2);
            resumen.append(String.format(Locale.ROOT, "%s,%d,%d,%.4f,%.4f,%.4f%n", c.caso(), c.paradas(), c.tramos(),
                    c.mediana(), jna.mediana(), java.mediana()));
        }
        Files.writeString(carpeta.resolve("scrum37_banco.csv"), crudo.toString());
        Files.writeString(carpeta.resolve("scrum37_resumen.csv"), resumen.toString());
    }

    private static void decir(String formato, Object... args) {
        String linea = String.format(Locale.ROOT, formato, args);
        System.out.println(linea);
        out.println(linea);
    }

    private BancoCvsJava() {
    }
}
