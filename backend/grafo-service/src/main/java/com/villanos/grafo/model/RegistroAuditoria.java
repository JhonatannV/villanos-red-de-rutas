package com.villanos.grafo.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auditoria_cambios")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_sub", nullable = false)
    private String usuarioSub;

    @Column(name = "usuario_username")
    private String usuarioUsername;

    @Column(name = "fecha", nullable = false)
    private Instant fecha;

    @Column(name = "operacion", nullable = false)
    private String operacion;

    @Column(name = "detalle", length = 1000)
    private String detalle;

    @Column(name = "red_id")
    private Integer redId;

    public RegistroAuditoria() {
    }

    public RegistroAuditoria(String usuarioSub, String usuarioUsername, Instant fecha, String operacion, String detalle, Integer redId) {
        this.usuarioSub = usuarioSub;
        this.usuarioUsername = usuarioUsername;
        this.fecha = fecha;
        this.operacion = operacion;
        this.detalle = detalle;
        this.redId = redId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsuarioSub() {
        return usuarioSub;
    }

    public void setUsuarioSub(String usuarioSub) {
        this.usuarioSub = usuarioSub;
    }

    public String getUsuarioUsername() {
        return usuarioUsername;
    }

    public void setUsuarioUsername(String usuarioUsername) {
        this.usuarioUsername = usuarioUsername;
    }

    public Instant getFecha() {
        return fecha;
    }

    public void setFecha(Instant fecha) {
        this.fecha = fecha;
    }

    public String getOperacion() {
        return operacion;
    }

    public void setOperacion(String operacion) {
        this.operacion = operacion;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public Integer getRedId() {
        return redId;
    }

    public void setRedId(Integer redId) {
        this.redId = redId;
    }
}
