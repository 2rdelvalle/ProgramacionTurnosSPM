package com.mardique.programacion.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.EstadoSms;
import com.mardique.programacion.domain.NotificacionSms;

public interface NotificacionSmsRepository extends JpaRepository<NotificacionSms, Long> {

	/**
	 * SMS pendientes de reintento. La toma el proceso programado con
	 * {@code @Scheduled}.
	 */
	List<NotificacionSms> findByEstadoInAndCreatedAtLessThan(Collection<EstadoSms> estados,
			LocalDateTime limite);

	/**
	 * Pantalla de fallos de Talento Humano. Con el grafo de colaborador y
	 * programación la vista pinta sin sesión abierta (la app corre con
	 * {@code open-in-view=false}).
	 */
	@EntityGraph(attributePaths = { "colaborador", "programacion" })
	Page<NotificacionSms> findByEstadoOrderByCreatedAtDesc(EstadoSms estado, Pageable pageable);

	@EntityGraph(attributePaths = { "colaborador", "programacion" })
	Page<NotificacionSms> findAllByOrderByCreatedAtDesc(Pageable pageable);

	long countByEstado(EstadoSms estado);

	List<NotificacionSms> findByProgramacionIdOrderByCreatedAtAsc(Long programacionId);

	/** Los SMS de un listado de programaciones, para el perfil del colaborador. */
	List<NotificacionSms> findByProgramacionIdIn(Collection<Long> programacionIds);
}
