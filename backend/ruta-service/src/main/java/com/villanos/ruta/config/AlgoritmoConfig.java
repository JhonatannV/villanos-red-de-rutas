package com.villanos.ruta.config;

import com.villanos.ruta.calculo.ModoAlgoritmo;
import com.villanos.ruta.calculo.SelectorAlgoritmo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Crea el selector de algoritmo a partir de {@code app.algorithm.mode}.
 * En application.yml esa propiedad toma la variable de entorno ALGORITHM_MODE
 * (por defecto {@code native}). Un valor distinto de native o java detiene el arranque.
 */
@Configuration
public class AlgoritmoConfig {

    @Bean
    public SelectorAlgoritmo selectorAlgoritmo(@Value("${app.algorithm.mode:native}") String modo) {
        return new SelectorAlgoritmo(ModoAlgoritmo.desdeTexto(modo));
    }

    /** Publica en /actuator/info qué implementación se pidió, cuál está activa y por qué. */
    @Bean
    public InfoContributor algoritmoInfo(SelectorAlgoritmo selector) {
        return builder -> {
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("modoPedido", selector.modoPedido().valor());
            detalle.put("modoEfectivo", selector.modoEfectivo().valor());
            detalle.put("motivo", selector.motivo());
            builder.withDetail("algoritmo", detalle);
        };
    }
}
