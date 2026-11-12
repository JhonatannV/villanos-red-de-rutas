package com.villanos.ruta.calculo;

import com.villanos.ruta.algoritmo.DijkstraJava;
import com.villanos.ruta.algoritmo.DijkstraJava.ResultadoRuta;
import com.villanos.ruta.nativo.LibGrafo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Elige entre la biblioteca nativa (C por JNA) y la versión en Java según
 * {@code app.algorithm.mode}, y cambia a Java sin detener el servicio si la
 * biblioteca no se puede cargar o falla al invocarla.
 *
 * <p>Las entradas se validan aquí, antes de elegir implementación, con las mismas
 * reglas que {@code grafo.h}. Así una entrada inválida produce el mismo error en
 * los dos modos.
 */
public class SelectorAlgoritmo implements CalculadorRutas {

    private static final Logger LOG = Logger.getLogger(SelectorAlgoritmo.class.getName());

    // Códigos de retorno de dijkstra() definidos en native/grafo.h
    static final int GRAFO_SIN_RUTA = -1;
    static final int GRAFO_FUERA_DE_RANGO = -2;
    static final int GRAFO_ARGUMENTO_INVALIDO = -3;
    static final int GRAFO_SIN_MEMORIA = -4;
    static final int GRAFO_COSTO_DESBORDADO = -5;

    private final ModoAlgoritmo modoPedido;
    private volatile LibGrafo nativa;
    private volatile ModoAlgoritmo modoEfectivo;
    private volatile String motivo;

    /** Usa el modo configurado y la biblioteca {@code grafo} del sistema. */
    public SelectorAlgoritmo() {
        this(ModoAlgoritmo.desdeConfiguracion(), LibGrafo::cargar);
    }

    public SelectorAlgoritmo(ModoAlgoritmo modoPedido) {
        this(modoPedido, LibGrafo::cargar);
    }

    /**
     * @param cargador obtiene la biblioteca nativa; solo se invoca si el modo pedido es NATIVE
     */
    public SelectorAlgoritmo(ModoAlgoritmo modoPedido, Supplier<LibGrafo> cargador) {
        this.modoPedido = modoPedido;
        if (modoPedido == ModoAlgoritmo.JAVA) {
            usarJava("configurado en modo java");
            return;
        }
        try {
            this.nativa = cargador.get();
            this.modoEfectivo = ModoAlgoritmo.NATIVE;
            this.motivo = "biblioteca nativa cargada";
            LOG.info("ruta-service usa la biblioteca nativa libgrafo");
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            usarJava("no se pudo cargar la biblioteca nativa: " + primeraLinea(e.getMessage()));
        }
    }

    // JNA agrega al mensaje todas las rutas que revisó; para el log basta la primera línea
    private static String primeraLinea(String mensaje) {
        if (mensaje == null) {
            return "sin detalle";
        }
        int fin = mensaje.indexOf('\n');
        return (fin < 0 ? mensaje : mensaje.substring(0, fin)).trim();
    }

    @Override
    public ResultadoRuta calcular(int numParadas, int[] origen, int[] destino, int[] peso,
                                  int paradaInicio, int paradaFin) {
        validar(numParadas, origen, destino, peso, paradaInicio, paradaFin);

        LibGrafo lib = nativa;
        if (lib != null) {
            try {
                ResultadoRuta r = calcularNativo(lib, numParadas, origen, destino, peso, paradaInicio, paradaFin);
                if (r != null) {
                    return r;
                }
            } catch (UnsatisfiedLinkError e) {
                usarJava("falló la llamada a la biblioteca nativa: " + primeraLinea(e.getMessage()));
            }
        }
        return DijkstraJava.calcular(numParadas, origen.length, origen, destino, peso, paradaInicio, paradaFin);
    }

    /** Devuelve null cuando la llamada debe repetirse en Java. */
    private ResultadoRuta calcularNativo(LibGrafo lib, int numParadas, int[] origen, int[] destino, int[] peso,
                                         int paradaInicio, int paradaFin) {
        int[] camino = new int[numParadas];
        int[] costo = new int[1];
        int r = lib.dijkstra(numParadas, origen.length, origen, destino, peso,
                paradaInicio, paradaFin, camino, costo);

        if (r > 0) {
            List<Integer> lista = new ArrayList<>(r);
            for (int i = 0; i < r; i++) {
                lista.add(camino[i]);
            }
            return new ResultadoRuta(costo[0], lista, Collections.emptyList());
        }
        switch (r) {
            case GRAFO_SIN_RUTA:
                return new ResultadoRuta(-1, Collections.emptyList(), Collections.emptyList());
            case GRAFO_SIN_MEMORIA:
                LOG.warning("libgrafo sin memoria; la consulta se resuelve en Java");
                return null;
            case GRAFO_COSTO_DESBORDADO:
                throw new ArithmeticException("El costo total de la ruta no cabe en un int");
            case GRAFO_FUERA_DE_RANGO:
                throw new IllegalArgumentException("Una parada está fuera de rango");
            case GRAFO_ARGUMENTO_INVALIDO:
                throw new IllegalArgumentException("Argumento inválido para dijkstra()");
            default:
                throw new IllegalStateException("Código de retorno no documentado de libgrafo: " + r);
        }
    }

    /** Mismas reglas que dijkstra() en C; se aplican igual en los dos modos. */
    static void validar(int numParadas, int[] origen, int[] destino, int[] peso,
                        int paradaInicio, int paradaFin) {
        if (numParadas <= 0) {
            throw new IllegalArgumentException("La red debe tener al menos una parada");
        }
        if (origen == null || destino == null || peso == null) {
            throw new IllegalArgumentException("Los arreglos de tramos no pueden ser nulos");
        }
        if (origen.length != destino.length || origen.length != peso.length) {
            throw new IllegalArgumentException("origen, destino y peso deben tener el mismo tamaño");
        }
        if (paradaInicio < 0 || paradaInicio >= numParadas) {
            throw new IllegalArgumentException("Parada de inicio fuera de rango: " + paradaInicio);
        }
        if (paradaFin < 0 || paradaFin >= numParadas) {
            throw new IllegalArgumentException("Parada de fin fuera de rango: " + paradaFin);
        }
        for (int i = 0; i < origen.length; i++) {
            if (origen[i] < 0 || origen[i] >= numParadas || destino[i] < 0 || destino[i] >= numParadas) {
                throw new IllegalArgumentException("El tramo " + i + " apunta a una parada inexistente");
            }
            if (peso[i] < 0) {
                throw new IllegalArgumentException("El tramo " + i + " tiene peso negativo");
            }
        }
    }

    private void usarJava(String razon) {
        Level nivel = modoPedido == ModoAlgoritmo.JAVA ? Level.INFO : Level.WARNING;
        LOG.log(nivel, "ruta-service usa la implementación Java: {0}", razon);
        this.nativa = null;
        this.modoEfectivo = ModoAlgoritmo.JAVA;
        this.motivo = razon;
    }

    public ModoAlgoritmo modoPedido() {
        return modoPedido;
    }

    @Override
    public ModoAlgoritmo modoEfectivo() {
        return modoEfectivo;
    }

    /** Por qué se está usando el modo efectivo (para el log y el endpoint de estado). */
    public String motivo() {
        return motivo;
    }
}
