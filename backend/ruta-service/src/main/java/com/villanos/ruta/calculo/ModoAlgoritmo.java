package com.villanos.ruta.calculo;

import java.util.Locale;

/**
 * Implementación de Dijkstra que usa ruta-service.
 * Se configura con la propiedad {@code app.algorithm.mode=native|java}.
 */
public enum ModoAlgoritmo {

    /** Biblioteca nativa libgrafo.so (C) invocada con JNA. */
    NATIVE,

    /** Implementación en Java puro (línea base y respaldo). */
    JAVA;

    public static final String PROPIEDAD = "app.algorithm.mode";
    public static final String VARIABLE_ENTORNO = "APP_ALGORITHM_MODE";
    public static final ModoAlgoritmo POR_DEFECTO = NATIVE;

    /**
     * Interpreta el valor de la propiedad. Vacío o nulo devuelve el modo por defecto.
     *
     * @throws IllegalArgumentException si el valor no es {@code native} ni {@code java}
     */
    public static ModoAlgoritmo desdeTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return POR_DEFECTO;
        }
        switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "native":
                return NATIVE;
            case "java":
                return JAVA;
            default:
                throw new IllegalArgumentException(
                        "Valor inválido para " + PROPIEDAD + ": '" + valor + "'. Use 'native' o 'java'.");
        }
    }

    /**
     * Lee el modo de la propiedad del sistema {@code app.algorithm.mode} o, si no está,
     * de la variable de entorno {@code APP_ALGORITHM_MODE}.
     */
    public static ModoAlgoritmo desdeConfiguracion() {
        String valor = System.getProperty(PROPIEDAD);
        if (valor == null || valor.isBlank()) {
            valor = System.getenv(VARIABLE_ENTORNO);
        }
        return desdeTexto(valor);
    }

    public String valor() {
        return name().toLowerCase(Locale.ROOT);
    }
}
