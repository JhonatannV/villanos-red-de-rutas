package com.villanos.grafo.model;

import java.util.ArrayList;
import java.util.List;

public class RedGrafo {
    private int redId;
    private String nombre;
    private List<Parada> paradas = new ArrayList<>();
    private List<Tramo> tramos = new ArrayList<>();

    public RedGrafo() {}

    public RedGrafo(int redId, String nombre, List<Parada> paradas, List<Tramo> tramos) {
        this.redId = redId;
        this.nombre = nombre;
        this.paradas = paradas != null ? paradas : new ArrayList<>();
        this.tramos = tramos != null ? tramos : new ArrayList<>();
    }

    public int getRedId() { return redId; }
    public void setRedId(int redId) { this.redId = redId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public List<Parada> getParadas() { return paradas; }
    public void setParadas(List<Parada> paradas) { this.paradas = paradas; }

    public List<Tramo> getTramos() { return tramos; }
    public void setTramos(List<Tramo> tramos) { this.tramos = tramos; }
}
