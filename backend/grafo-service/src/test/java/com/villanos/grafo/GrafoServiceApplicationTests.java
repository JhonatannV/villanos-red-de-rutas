package com.villanos.grafo;

import com.villanos.grafo.model.RedGrafo;
import com.villanos.grafo.service.GrafoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GrafoServiceApplicationTests {

    @Autowired
    private GrafoService grafoService;

    @Test
    void contextLoads() {
        assertNotNull(grafoService);
    }

    @Test
    void redInicialCargadaCon40ParadasY90Tramos() {
        Optional<RedGrafo> opt = grafoService.obtenerRed(1);
        assertTrue(opt.isPresent(), "La red 1 debe existir");
        RedGrafo red = opt.get();

        assertEquals(40, red.getParadas().size(), "Deben cargarse exactamente 40 paradas");
        assertEquals(90, red.getTramos().size(), "Deben cargarse exactamente 90 tramos");
    }

    @Test
    void puedeCambiarEstadoDeTramo() {
        // Cerrar tramo 90 (puente)
        var opt = grafoService.cambiarEstadoTramo(1, 90, false);
        assertTrue(opt.isPresent());
        assertFalse(opt.get().isAbierto(), "El tramo 90 debe quedar cerrado");

        // Restaurar estado abierto
        grafoService.cambiarEstadoTramo(1, 90, true);
    }
}
