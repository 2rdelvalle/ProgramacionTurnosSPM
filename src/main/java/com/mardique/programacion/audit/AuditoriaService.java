package com.mardique.programacion.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.domain.Auditoria;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.repository.AuditoriaRepository;
import com.mardique.programacion.security.ActualUsuario;

/**
 * Bitácora de decisiones: quién hizo qué, sobre qué entidad, con el valor
 * anterior y el nuevo, el motivo y la fecha.
 *
 * <p><strong>Se escribe desde la capa de servicio, nunca desde un
 * controlador.</strong>
 *
 * <p>La escritura usa {@link Propagation#REQUIRES_NEW} a propósito: si la
 * operación de negocio falla y su transacción se revierte, el rastro de lo
 * intentado debe permanecer. Es lo que hace útil auditar un intento no
 * autorizado, que además va a lanzar una excepción.
 */
@Service
public class AuditoriaService {

	private final AuditoriaRepository auditoria;

	private final ActualUsuario actualUsuario;

	public AuditoriaService(AuditoriaRepository auditoria, ActualUsuario actualUsuario) {
		this.auditoria = auditoria;
		this.actualUsuario = actualUsuario;
	}

	/**
	 * Registra una decisión. El actor se toma del contexto de seguridad; si la
	 * operación no tiene usuario (una tarea programada, por ejemplo) la entrada
	 * queda sin actor en lugar de fallar.
	 *
	 * @param registro datos de la decisión ya armados.
	 * @return la entrada persistida.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Auditoria registrar(RegistroAuditoria registro) {
		Auditoria entrada = new Auditoria();
		entrada.setAccion(registro.accion().name());
		entrada.setEntidad(registro.entidad().nombreTabla());
		entrada.setEntidadId(registro.entidadId());
		entrada.setValorAnterior(Redactor.sanear(registro.valorAnterior()));
		entrada.setValorNuevo(Redactor.sanear(registro.valorNuevo()));
		entrada.setMotivo(registro.motivo());

		// El username se copia para que la trazabilidad sobreviva aunque el
		// usuario se renombre o se elimine.
		actualUsuario.opcional().ifPresent(principal -> {
			entrada.setActorUsername(principal.getUsername());
			entrada.setActor(referenciaActor(principal.usuarioId()));
		});

		return auditoria.save(entrada);
	}

	/**
	 * Solo carga la referencia del actor por id, sin tocar el resto de la
	 * entidad, para no disparar cargas perezosas al serializar la bitácora.
	 */
	private Usuario referenciaActor(Long usuarioId) {
		Usuario actor = new Usuario();
		actor.setId(usuarioId);
		return actor;
	}
}
