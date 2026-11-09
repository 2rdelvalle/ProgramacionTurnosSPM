package com.mardique.programacion.security;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.domain.TipoUsuarioRol;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ParametroTextoRepository;

/**
 * Áreas que un rol alcanza sin tener filas en {@code usuario_area}.
 *
 * <p>Hoy existe una sola regla de este tipo: {@code COORD_OPERACIONES} ve el
 * área de Operaciones completa. No se modela con filas de {@code usuario_area}
 * porque el alcance no depende de a quién se asignó el coordinador, sino del
 * rol, y meterlo como filas haría fácil que alguien las borrara por error y
 * dejara al coordinador sin nada que ver.
 *
 * <p>El código del área sale del parámetro {@value #CLAVE_AREA_OPERACIONES} de
 * la tabla {@code parametro_texto}. Si el parámetro falta o el código no
 * corresponde a un área existente, el servicio avisa por log y no inventa
 * alcance: el usuario conserva el que tenga en {@code usuario_area}.
 */
@Service
public class AlcanceImplicitoService {

	/** Clave del parámetro con el código del área de Operaciones. */
	public static final String CLAVE_AREA_OPERACIONES = "CODIGO_AREA_OPERACIONES";

	private static final Logger log = LoggerFactory.getLogger(AlcanceImplicitoService.class);

	private final ParametroTextoRepository parametros;

	private final AreaRepository areas;

	public AlcanceImplicitoService(ParametroTextoRepository parametros, AreaRepository areas) {
		this.parametros = parametros;
		this.areas = areas;
	}

	/**
	 * @param rol rol que se está evaluando.
	 * @return identificadores de las áreas que ese rol alcanza por definición,
	 *         sin contar las que tenga asignadas en {@code usuario_area}.
	 */
	@Transactional(readOnly = true)
	public Set<Long> areasDe(TipoUsuarioRol rol) {
		if (rol != TipoUsuarioRol.COORD_OPERACIONES) {
			return Set.of();
		}
		return areaOperacionesId().map(id -> Set.of(id)).orElseGet(Set::of);
	}

	/**
	 * Une el alcance explícito de {@code usuario_area} con el implícito del rol.
	 *
	 * @param roles         roles del usuario.
	 * @param areasAsignadas áreas registradas en {@code usuario_area}.
	 * @return todas las áreas que el usuario puede ver.
	 */
	@Transactional(readOnly = true)
	public Set<Long> alcanceCompleto(Set<TipoUsuarioRol> roles, Set<Long> areasAsignadas) {
		Set<Long> alcance = new LinkedHashSet<>(areasAsignadas);
		for (TipoUsuarioRol rol : roles) {
			alcance.addAll(areasDe(rol));
		}
		return Set.copyOf(alcance);
	}

	private Optional<Long> areaOperacionesId() {
		Optional<String> codigo = parametros.findById(CLAVE_AREA_OPERACIONES)
				.map(parametro -> parametro.getValor().trim())
				.filter(valor -> !valor.isEmpty());
		if (codigo.isEmpty()) {
			log.warn("Falta el parámetro {} o viene vacío. El rol COORD_OPERACIONES no tendrá alcance implícito.",
					CLAVE_AREA_OPERACIONES);
			return Optional.empty();
		}
		Optional<Long> areaId = areas.findByCodigo(codigo.get()).map(area -> area.getId());
		if (areaId.isEmpty()) {
			log.warn("El parámetro {} apunta al código '{}', que no existe en la tabla area.",
					CLAVE_AREA_OPERACIONES, codigo.get());
		}
		return areaId;
	}
}
