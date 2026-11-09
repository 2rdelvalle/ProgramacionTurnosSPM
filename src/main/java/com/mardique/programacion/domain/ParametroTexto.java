package com.mardique.programacion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Parámetro de configuración con valor de texto.
 *
 * <p><strong>No es lo mismo que {@link ParametroLaboral}.</strong> Allí viven
 * los valores legales con vigencia (recargos, topes, umbrales) y el valor es
 * numérico. Aquí solo van claves de configuración que no son un número, como
 * el código del área que coordina el rol {@code COORD_OPERACIONES}.
 *
 * <p>Si algún día un parámetro de texto necesita vigencia, se le agregan las
 * columnas {@code vigencia_desde} y {@code vigencia_hasta} en una migración
 * nueva, igual que en {@code parametro_laboral}.
 */
@Entity
@Table(name = "parametro_texto")
@Getter
@Setter
@NoArgsConstructor
public class ParametroTexto {

	@Id
	@Column(name = "clave", nullable = false, length = 80)
	private String clave;

	@Column(name = "valor", nullable = false, length = 255)
	private String valor;

	@Column(name = "descripcion", length = 255)
	private String descripcion;
}
