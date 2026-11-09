package com.mardique.programacion.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.domain.TipoUsuarioRol;
import com.mardique.programacion.repository.AuditoriaRepository;
import com.mardique.programacion.security.ActualUsuario;
import com.mardique.programacion.security.UsuarioPrincipal;
import com.mardique.programacion.service.dto.AuditoriaFilaDto;

/**
 * Lectura de la bitácora de auditoría para la pantalla de control.
 *
 * <p><strong>Un coordinador solo ve su propia huella.</strong> No es un filtro
 * cosmético: la bitácora registra intentos denegados y decisiones con motivo,
 * que son información de la operación interna de otra área. Los roles
 * universales ven todo.
 */@Service
public class AuditoriaConsultaService {

	/**
	 * Tope duro de entradas. La pantalla pide 100 y el tope lo limita igual, así
	 * que un parameter alterado no puede terminar trayendo la bitácora entera.
	 */
	private static final int LIMITE = 100;

	private static final Sort MAS_RECIENTE_Primero = Sort.by(Sort.Direction.DESC, "fecha");

	private final AuditoriaRepository auditoria;

	private final ActualUsuario actualUsuario;

	public AuditoriaConsultaService(AuditoriaRepository auditoria, ActualUsuario actualUsuario) {
		this.auditoria = auditoria;
		this.actualUsuario = actualUsuario;
	}

	/**
	 * Entradas que el usuario actual puede ver.
	 *
	 * @param limite máximo de entradas a devolver.
	 */
	@Transactional(readOnly = true)
	public List<AuditoriaFilaDto> visibles(int limite) {
		UsuarioPrincipal principal = actualUsuario.principal();
		PageRequest pagina = PageRequest.of(0, Math.min(limite, LIMITE), MAS_RECIENTE_Primero);

		var entradas = principal.roles().stream().anyMatch(this::veBitacoraCompleta)
				? auditoria.findAllByOrderByFechaDesc(pagina)
				: auditoria.findByActorIdOrderByFechaDesc(principal.usuarioId(), pagina);

		return entradas.getContent().stream().map(AuditoriaFilaDto::de).toList();
	}

	private boolean veBitacoraCompleta(TipoUsuarioRol rol) {
		return rol == TipoUsuarioRol.TALENTO_HUMANO || rol == TipoUsuarioRol.ADMIN
				|| rol == TipoUsuarioRol.AUDITOR;
	}
}
