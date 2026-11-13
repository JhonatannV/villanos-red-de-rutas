package com.villanos.grafo.repository;

import com.villanos.grafo.model.RegistroAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {
    List<RegistroAuditoria> findByRedIdOrderByFechaDesc(Integer redId);
    List<RegistroAuditoria> findAllByOrderByFechaDesc();
}
