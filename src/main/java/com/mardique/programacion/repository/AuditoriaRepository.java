package com.mardique.programacion.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.Auditoria;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

	/** Historial de una entidad concreta, del más reciente al más antiguo. */
	List<Auditoria> findByEntidadAndEntidadIdOrderByFechaDesc(String entidad, Long entidadId);

	Page<Auditoria> findByEntidadOrderByFechaDesc(String entidad, Pageable pageable);

	Page<Auditoria> findByActorIdOrderByFechaDesc(Long actorId, Pageable pageable);

	Page<Auditoria> findAllByOrderByFechaDesc(Pageable pageable);

	List<Auditoria> findByFechaBetweenOrderByFechaDesc(LocalDateTime desde, LocalDateTime hasta);
}
