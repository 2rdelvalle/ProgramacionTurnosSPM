package com.mardique.programacion.service.dto;

import java.math.BigDecimal;

/**
 * Una barra de la gráfica de nivelación: un área con la carga completa de su
 * personal —la suma de las horas extra de sus colaboradores sobre el máximo
 * legal de todas sus personas— y cuántas de ellas rompen un tope.
 *
 * <p>El porcentaje es la carga del área frente a su capacidad total (la del mes
 * por persona multiplicada por la plantilla visible). Por debajo del 80 % está
 * en rango, a partir de ahí queda cerca del tope, y al tope o por encima exige
 * nivelación.
 *
 * <p>La barra colorea con {@code severidadCarga} (la carga del área), pero la
 * etiqueta avisa cuando hay personas que rompen un tope aunque el área todavía
 * no llegue al 100 %: un solo colaborador por encima del tope diario es una
 * infracción que el área debe ver.
 *
 * @param nombre           nombre del área.
 * @param horas            suma de las horas extra del mes de sus filas.
 * @param capacidad        máximo legal de todas sus personas en el mes.
 * @param porcentaje       carga sobre capacidad, redondeado y sin recortar a 100.
 * @param estado           rótulo de la carga del área: excede, tope, cerca o en rango.
 * @param severidadCarga   {@code alta}, {@code media} o {@code baja} según la carga.
 * @param conHoras         personas del área con horas extra del mes (lo visible).
 * @param personas         plantilla del área dentro de los filtros (con o sin horas).
 * @param personasEnAlerta personas del área que rompen o alcanzan un tope.
 */
public record NivelacionAreaDto(String nombre, BigDecimal horas, BigDecimal capacidad, int porcentaje, String estado,
		String severidadCarga, int conHoras, int personas, int personasEnAlerta) {

	/** Ancho de la barra dentro de la pista: relativa al 100 %, recortada a 100. */
	public int ancho() {
		return Math.min(porcentaje, 100);
	}

	/** Si el área exige atención: alguna persona en alerta o la carga ya al tope. */
	public boolean enAlerta() {
		return personasEnAlerta > 0 || "alta".equals(severidadCarga);
	}

	/** La severidad que se pinta: cualquier persona en alerta sube el área a alta. */
	public String severidad() {
		return enAlerta() ? "alta" : severidadCarga;
	}

	/** La etiqueta de la barra: el conteo de personas en alerta manda sobre el estado. */
	public String etiqueta() {
		return personasEnAlerta > 0 ? personasEnAlerta + " en alerta" : estado;
	}
}
