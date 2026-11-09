package com.mardique.programacion.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.audit.AccionAuditada;
import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.audit.EntidadAuditada;
import com.mardique.programacion.audit.RegistroAuditoria;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.ColaboradorSalario;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.ColaboradorSalarioRepository;
import com.mardique.programacion.security.ActualUsuario;
import com.mardique.programacion.security.Autoridades;
import com.mardique.programacion.service.dto.ColaboradorSalarioDto;

/**
 * Único punto de entrada al dato sensible de compensación.
 *
 * <p>El control de acceso está en la anotación del método, no en la vista: un
 * endpoint que olvide ocultar la columna sigue sin poder devolver el salario a
 * un coordinador, porque el servicio lo impide antes de consultar la base.
 *
 * <p>El valor nunca se audita: la bitácora registra que se consultó, no
 * cuánto era.
 */
@Service
public class SalarioService {

	private final ColaboradorSalarioRepository salarios;

	private final ColaboradorRepository colaboradores;

	private final AlcanceAreaService alcance;

	private final AuditoriaService auditoria;

	private final ActualUsuario actualUsuario;

	@Autowired
	public SalarioService(ColaboradorSalarioRepository salarios, ColaboradorRepository colaboradores,
			AlcanceAreaService alcance, AuditoriaService auditoria, ActualUsuario actualUsuario) {
		this.salarios = salarios;
		this.colaboradores = colaboradores;
		this.alcance = alcance;
		this.auditoria = auditoria;
		this.actualUsuario = actualUsuario;
	}

	/** Constructor de compatibilidad para consumidores que solo consultan salarios. */
	public SalarioService(ColaboradorSalarioRepository salarios, AuditoriaService auditoria,
			ActualUsuario actualUsuario) {
		this(salarios, null, null, auditoria, actualUsuario);
	}

	/** Registra o actualiza el salario vigente sin exponer su valor en auditoría. */
	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional
	public void guardar(Long colaboradorId, BigDecimal salarioBase, LocalDate vigenciaDesde) {
		if (colaboradores == null || alcance == null) {
			throw new IllegalStateException("La edición de salarios no está disponible en esta configuración.");
		}
		if (salarioBase == null || salarioBase.signum() <= 0) {
			throw new IllegalArgumentException("El salario debe ser mayor que cero.");
		}
		if (vigenciaDesde == null) {
			throw new IllegalArgumentException("Indique la fecha de vigencia del salario.");
		}

		Colaborador colaborador = colaboradores.findById(colaboradorId)
				.orElseThrow(() -> new IllegalArgumentException("El colaborador no existe."));
		alcance.verificar(colaborador.getArea().getId());

		ColaboradorSalario vigente = salarios.findVigente(colaboradorId, vigenciaDesde).orElse(null);
		if (vigente != null && vigenciaDesde.equals(vigente.getVigenciaDesde())) {
			vigente.setSalarioBase(salarioBase);
			salarios.save(vigente);
		} else {
			if (vigente != null) {
				vigente.setVigenciaHasta(vigenciaDesde.minusDays(1));
				salarios.save(vigente);
			}
			ColaboradorSalario nuevo = new ColaboradorSalario();
			nuevo.setColaborador(colaborador);
			nuevo.setSalarioBase(salarioBase);
			nuevo.setVigenciaDesde(vigenciaDesde);
			salarios.save(nuevo);
		}

		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.EDITAR, EntidadAuditada.COLABORADOR_SALARIO)
				.entidadId(colaboradorId).motivo("Salario actualizado por Talento Humano"));
	}

	/**
	 * Salario vigente de un colaborador en una fecha.
	 *
	 * @param colaboradorId colaborador consultado.
	 * @param fecha         fecha de corte de la vigencia.
	 * @return el salario vigente, o vacío si todavía no tiene uno registrado.
	 */
	@PreAuthorize(Autoridades.EXPRESION_VER_SALARIO)
	@Transactional(readOnly = true)
	public Optional<ColaboradorSalarioDto> vigente(Long colaboradorId, LocalDate fecha) {
		Optional<ColaboradorSalarioDto> resultado = salarios.findVigente(colaboradorId, fecha)
				.map(ColaboradorSalarioDto::de);
		resultado.ifPresent(dto -> auditoria.registrar(RegistroAuditoria
				.de(AccionAuditada.CONSULTAR_DATO_SENSIBLE, EntidadAuditada.COLABORADOR_SALARIO)
				.entidadId(dto.colaboradorId())
				.motivo("Consulta de salario vigente al " + fecha)));
		return resultado;
	}

	/**
	 * Histórico de salarios de un colaborador, del más reciente al más antiguo.
	 *
	 * @param colaboradorId colaborador consultado.
	 * @return todas sus vigencias.
	 */
	@PreAuthorize(Autoridades.EXPRESION_VER_SALARIO)
	@Transactional(readOnly = true)
	public List<ColaboradorSalarioDto> historico(Long colaboradorId) {
		return salarios.findByColaboradorIdOrderByVigenciaDesdeDesc(colaboradorId).stream()
				.map(ColaboradorSalarioDto::de).toList();
	}

	/**
	 * Salarios vigentes de varios colaboradores de una vez, para armar un listado
	 * sin disparar una consulta por fila.
	 *
	 * <p>Al igual que {@link #vigente}, deja un solo asiento de auditoría por
	 * llamada: en un listado de veinte personas se audita la consulta del
	 * conjunto, no veinte consultas.
	 *
	 * @param colaboradorIds colaboradores consultados.
	 * @return salario vigente indexado por identificador de colaborador. Los que
	 *         no tienen salario vigente no aparecen en el mapa.
	 * @throws org.springframework.security.access.AccessDeniedException si el rol
	 *         actual no tiene acceso al dato de compensación.
	 */
	@PreAuthorize(Autoridades.EXPRESION_VER_SALARIO)
	@Transactional(readOnly = true)
	public Map<Long, BigDecimal> vigentesDe(Collection<Long> colaboradorIds) {
		return vigentesDe(colaboradorIds, LocalDate.now());
	}

	@PreAuthorize(Autoridades.EXPRESION_VER_SALARIO)
	@Transactional(readOnly = true)
	public Map<Long, BigDecimal> vigentesDe(Collection<Long> colaboradorIds, LocalDate fecha) {
		if (colaboradorIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, BigDecimal> resultado = new LinkedHashMap<>();
		for (Object[] fila : salarios.findVigentesDe(colaboradorIds, fecha)) {
			resultado.put((Long) fila[0], (BigDecimal) fila[1]);
		}
		auditoria.registrar(RegistroAuditoria
				.de(AccionAuditada.CONSULTAR_DATO_SENSIBLE, EntidadAuditada.COLABORADOR_SALARIO)
				.motivo("Consulta masiva de salarios vigentes de " + colaboradorIds.size() + " colaborador(es)"));
		return resultado;
	}

	/**
	 * Indica si el usuario actual puede leer salarios. Lo consumen los
	 * servicios que arman DTOs con o sin compensación según el rol.
	 */
	public boolean usuarioActualPuedeVerSalario() {
		return actualUsuario.opcional()
				.map(principal -> principal.roles().stream()
						.anyMatch(rol -> Autoridades.rolesConAccesoASalario().contains(rol)))
				.orElse(false);
	}
}
