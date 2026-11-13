package com.villanos.ruta.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las entradas inválidas (parada inexistente, modo desconocido, tramo
 * que no existe) a HTTP 400 con un mensaje, en lugar de un 500 genérico.
 */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> entradaInvalida(IllegalArgumentException e) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", HttpStatus.BAD_REQUEST.value());
        cuerpo.put("error", "Bad Request");
        cuerpo.put("mensaje", e.getMessage());
        return ResponseEntity.badRequest().body(cuerpo);
    }
}
