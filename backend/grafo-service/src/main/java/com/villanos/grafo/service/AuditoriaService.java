package com.villanos.grafo.service;

import com.villanos.grafo.model.RegistroAuditoria;
import com.villanos.grafo.repository.AuditoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    @Transactional
    public RegistroAuditoria registrarCambio(String usuarioSub, String usuarioUsername, String operacion, String detalle, Integer redId) {
        String sub = (usuarioSub != null && !usuarioSub.isBlank()) ? usuarioSub : "anonimo";
        String username = (usuarioUsername != null && !usuarioUsername.isBlank()) ? usuarioUsername : "desconocido";
        RegistroAuditoria registro = new RegistroAuditoria(sub, username, Instant.now(), operacion, detalle, redId);
        return auditoriaRepository.save(registro);
    }

    public List<RegistroAuditoria> obtenerRegistrosPorRed(Integer redId) {
        return auditoriaRepository.findByRedIdOrderByFechaDesc(redId);
    }

    public List<RegistroAuditoria> obtenerTodos() {
        return auditoriaRepository.findAllByOrderByFechaDesc();
    }
}
