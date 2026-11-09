package com.mardique.programacion.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ParametroTextoRepository;

/**
 * Agrupa áreas para presentar y aprobar en bloque.
 *
 * <p>Operaciones y Básculas trabajan el mismo frente de trabajo: la operación de
 * puerto se reparte entre las dos áreas, las personas salen del mismo turno y un
 * mismo vehículo las busca. Para aprobar, avisar y sacar el excel de salidas no
 * tiene sentido separarlas, porque la decisión se toma sobre el conjunto.
 *
 * <p><strong>Agrupar no es ampliar alcance.</strong> Este servicio solo decide
 * cómo se presenta y agrupa la información. Quien puede ver qué datos sigue
 * mandándolo el alcance por área de
 * {@link com.mardique.programacion.security.AlcanceAreaService}, y el alcance
 * implícito de {@code COORD_OPERACIONES} sigue siendo solo el área de
 * Operaciones. Si agrupar ampliara lo que un coordinador ve, un coordinador de
 * Básculas acabaría leyendo nóminas de Operaciones.
 *
 * <p>La configuración vive en {@code parametro_texto} con la clave
 * {@value #CLAVE_GRUPOS}, en formato {@code NOMBRE=COD1,COD2;OTRO=COD3}. Así se
 * cambia un grupo sin recompilar, igual que el resto de la configuración.
 * Un área que no aparece en ningún grupo se presenta sola, con su propio nombre.
 */
@Service
public class GrupoAreasService {

	/** Clave del parámetro con los grupos de áreas. */
	public static final String CLAVE_GRUPOS = "GRUPOS_AREAS";

	private static final Logger log = LoggerFactory.getLogger(GrupoAreasService.class);

	private final ParametroTextoRepository parametros;

	private final AreaRepository areas;

	public GrupoAreasService(ParametroTextoRepository parametros, AreaRepository areas) {
		this.parametros = parametros;
		this.areas = areas;
	}

	/**
	 * @return nombre del grupo por clave de área, para las áreas que pertenecen a
	 *         algún grupo configurado. Las que no, no aparecen en el mapa.
	 */
	@Transactional(readOnly = true)
	public Map<String, String> gruposPorArea() {
		Map<String, String> resultado = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> grupo : gruposConfigurados().entrySet()) {
			for (String codigo : grupo.getValue()) {
				resultado.putIfAbsent(codigo, grupo.getKey());
			}
		}
		return resultado;
	}

	/**
	 * Grupos tal como están configurados.
	 *
	 * @return nombre del grupo -> códigos de área que lo componen. Vacío si no hay
	 *         configuración, y en ese caso cada área se presenta sola.
	 */
	@Transactional(readOnly = true)
	public Map<String, List<String>> gruposConfigurados() {
		return parametros.findById(CLAVE_GRUPOS)
				.map(parametro -> leerGrupos(parametro.getValor()))
				.orElseGet(LinkedHashMap::new);
	}

	/**
	 * Áreas que componen una etiqueta de agrupación.
	 *
	 * <p>Resuelve dos casos porque las dos etiquetas que salen en pantalla son de
	 * naturalezas distintas: un grupo configurado ({@code Puerto}) o un área que
	 * no pertenece a ningún grupo y aparece con su propio nombre.
	 *
	 * <p>No es una puerta de seguridad: quien llame a esto tiene que volver a
	 * verificar el alcance de cada programación encontrada. Solo evita que un
	 * grupo mal escrito en el formulario devuelva cero resultados y se confunda
	 * con "no había nada que aprobar".
	 *
	 * @param nombre etiqueta tal como se muestra en la pantalla.
	 * @return identificadores de las áreas activas de la etiqueta, o lista vacía
	 *         si no corresponde a ningún grupo ni a ninguna área activa.
	 */
	@Transactional(readOnly = true)
	public List<Long> areasDeEtiqueta(String nombre) {
		if (nombre == null || nombre.isBlank()) {
			return List.of();
		}
		String buscado = nombre.trim();

		for (Map.Entry<String, List<String>> grupo : gruposConfigurados().entrySet()) {
			if (grupo.getKey().equalsIgnoreCase(buscado)) {
				return idsDeCodigos(grupo.getValue());
			}
		}
		return areas.findByActivaTrueOrderByNombreAsc().stream()
				.filter(area -> area.getNombre().equalsIgnoreCase(buscado))
				.map(Area::getId)
				.toList();
	}

	private List<Long> idsDeCodigos(List<String> codigos) {
		return codigos.stream()
				.map(codigo -> areas.findByCodigo(codigo).map(Area::getId).orElse(null))
				.filter(java.util.Objects::nonNull)
				.toList();
	}

	/**
	 * @return código de área -> nombre del grupo al que pertenece.
	 */
	@Transactional(readOnly = true)
	public Map<Long, String> gruposPorId() {
		Map<String, String> porCodigo = gruposPorArea();
		Map<Long, String> porId = new LinkedHashMap<>();
		for (Area area : areas.findByActivaTrueOrderByNombreAsc()) {
			String grupo = porCodigo.get(area.getCodigo());
			if (grupo != null) {
				porId.put(area.getId(), grupo);
			}
		}
		return porId;
	}

	/**
	 * Nombre con el que se presenta un área: el del grupo si pertenece a alguno,
	 * el nombre del área si no.
	 *
	 * @param area     área a etiquetar.
	 * @param porId    resultado de {@link #gruposPorId()}, para no repetir la
	 *                 consulta en un listado.
	 * @param nombreArea nombre del área, ya cargado.
	 * @return etiqueta lista para mostrar.
	 */
	public String etiquetaDe(Area area, Map<Long, String> porId, String nombreArea) {
		String grupo = porId.get(area.getId());
		return grupo != null ? grupo : nombreArea;
	}

	/**
	 * Lee la configuración con formato {@code NOMBRE=COD1,COD2;OTRO=COD3}.
	 *
	 * <p>Es deliberadamente tolerante: una entrada mal formada se avisa por log y
	 * se salta, en vez de tumbar la pantalla de alguien que solo venía a mirar el
	 * panel.
	 *
	 * @return grupo -> lista de códigos de área.
	 */
	private Map<String, List<String>> leerGrupos(String configuracion) {
		Map<String, List<String>> grupos = new LinkedHashMap<>();
		if (configuracion == null || configuracion.isBlank()) {
			return grupos;
		}
		for (String entrada : configuracion.split(";")) {
			String[] partes = entrada.split("=", 2);
			if (partes.length != 2 || partes[0].isBlank()) {
				log.warn("Entrada de {} ignorada por formato inválido: '{}'", CLAVE_GRUPOS, entrada);
				continue;
			}
			List<String> codigos = new ArrayList<>();
			for (String codigo : partes[1].split(",")) {
				String limpio = codigo.trim();
				if (limpio.isEmpty()) {
					continue;
				}
				if (areas.findByCodigo(limpio).isEmpty()) {
					log.warn("El grupo {} referencia el código '{}', que no existe en la tabla area.", partes[0].trim(),
							limpio);
					continue;
				}
				codigos.add(limpio);
			}
			if (!codigos.isEmpty()) {
				grupos.put(partes[0].trim(), List.copyOf(codigos));
			}
		}
		return grupos;
	}

	/** Para las pruebas: el grupo configurado, si existe. */
	@Transactional(readOnly = true)
	public Optional<String> grupoDeCodigo(String codigoArea) {
		return Optional.ofNullable(gruposPorArea().get(codigoArea));
	}
}
