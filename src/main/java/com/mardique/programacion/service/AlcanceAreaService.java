package com.mardique.programacion.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.mardique.programacion.audit.AccionAuditada;
import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.audit.EntidadAuditada;
import com.mardique.programacion.audit.RegistroAuditoria;
import com.mardique.programacion.audit.Redactor;
import com.mardique.programacion.security.AccesoFueraDeAlcanceException;
import com.mardique.programacion.security.ActualUsuario;
import com.mardique.programacion.security.UsuarioPrincipal;

/**
 * Filtro de alcance por área. Un coordinador solo ve y programa personal de
 * las áreas que tiene registradas en {@code usuario_area}; los intentos fuera
 * de alcance quedan auditados.
 *
 * <p><strong>El filtro vive aquí y no en la vista.</strong> Un controlador que
 * olvide aplicarlo no abre la puerta: cualquier servicio que devuelva
 * colaboradores o programaciones debe pasar por estos métodos.
 */
@Service
public class AlcanceAreaService {

	private final ActualUsuario actualUsuario;

	private final AuditoriaService auditoria;

	public AlcanceAreaService(ActualUsuario actualUsuario, AuditoriaService auditoria) {
		this.actualUsuario = actualUsuario;
		this.auditoria = auditoria;
	}

	/**
	 * Verifica que el usuario autenticado pueda operar sobre el área indicada.
	 *
	 * @param areaId área a verificar.
	 * @throws AccesoFueraDeAlcanceException si el área no está en su alcance. El
	 *                                        intento queda registrado antes de
	 *                                        lanzar la excepción.
	 */
	public void verificar(Long areaId) {
		UsuarioPrincipal principal = actualUsuario.principal();
		if (principal.puedeVerArea(areaId)) {
			return;
		}
		registrarIntento(principal, areaId);
		throw new AccesoFueraDeAlcanceException(principal.getUsername(), areaId);
	}

	/**
	 * Verifica varias áreas a la vez, por ejemplo las áreas de un conjunto de
	 * programaciones.
	 *
	 * @param areaIds áreas a verificar; las vacías o {@code null} se ignoran.
	 * @throws AccesoFueraDeAlcanceException en la primera que quede fuera de
	 *                                        alcance.
	 */
	public void verificarTodas(Collection<Long> areaIds) {
		if (areaIds == null) {
			return;
		}
		for (Long areaId : areaIds) {
			verificar(areaId);
		}
	}

	/**
	 * Áreas que puede ver el usuario. Para los roles de alcance universal
	 * devuelve el conjunto completo de áreas candidatas, no un conjunto vacío:
	 * un vacío se interpretaría como "no ve nada".
	 *
	 * @param areaIdsConAlcance todas las áreas que el filtro debe considerar.
	 * @return las áreas visibles para el usuario actual.
	 */
	public Set<Long> areasVisibles(Collection<Long> areaIdsConAlcance) {
		UsuarioPrincipal principal = actualUsuario.principal();
		if (principal.tieneAlcanceUniversal()) {
			return new LinkedHashSet<>(areaIdsConAlcance);
		}
		Set<Long> visibles = new LinkedHashSet<>(principal.areaIds());
		visibles.retainAll(areaIdsConAlcance);
		return visibles;
	}

	/** Indica sin lanzar excepción si el usuario puede operar sobre el área. */
	public boolean puedeVer(Long areaId) {
		return actualUsuario.principal().puedeVerArea(areaId);
	}

	/**
	 * Audita el intento. Se separa para dejar explícito que la escritura ocurre
	 * en su propia transacción y sobrevive al error que se lanza después.
	 */
	private void registrarIntento(UsuarioPrincipal principal, Long areaId) {
		auditoria.registrar(RegistroAuditoria
				.de(AccionAuditada.ACCESO_FUERA_DE_ALCANCE, EntidadAuditada.AREA)
				.entidadId(areaId)
				.anterior(Redactor.mapa("usuario", principal.getUsername(), "areaRequerida", areaId,
					"areasAsignadas", principal.areaIds()))
				.motivo("Intento de operación fuera del alcance por área"));
	}
}
