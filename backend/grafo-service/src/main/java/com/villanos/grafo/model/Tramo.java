package com.villanos.grafo.model;

public class Tramo {
    private int id;
    private int origen;
    private int destino;
    private int peso;
    private boolean abierto = true;

    public Tramo() {}

    public Tramo(int id, int origen, int destino, int peso, boolean abierto) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.peso = peso;
        this.abierto = abierto;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOrigen() { return origen; }
    public void setOrigen(int origen) { this.origen = origen; }

    public int getDestino() { return destino; }
    public void setDestino(int destino) { this.destino = destino; }

    public int getPeso() { return peso; }
    public void setPeso(int peso) { this.peso = peso; }

    public boolean isAbierto() { return abierto; }
    public void setAbierto(boolean abierto) { this.abierto = abierto; }
}
