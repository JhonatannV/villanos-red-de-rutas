package com.villanos.ruta.nativo;

import com.villanos.ruta.algoritmo.DijkstraJava;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PruebaContratoJnaTest {

    @Test
    void verificaContratoYRespaldoCuandoNativoNoEstaCompilado() {
        int numParadas = 5;
        int[] origen  = {0, 0, 1, 1, 2, 2, 3};
        int[] destino = {1, 2, 2, 3, 3, 4, 4};
        int[] peso    = {4, 2, 3, 5, 8, 15, 2};

        DijkstraJava.ResultadoRuta res = PuenteRutas.calcularConContrato(
                numParadas, origen.length, origen, destino, peso, 0, 4, true
        );

        System.out.println("Biblioteca nativa C disponible en este PC?: " + PuenteRutas.estaDisponibleNativo());
        System.out.println("Resultado del puente: costo=" + res.costoTotal + ", camino=" + res.camino);

        assertEquals(11, res.costoTotal);
        assertEquals(Arrays.asList(0, 1, 3, 4), res.camino);
    }
}