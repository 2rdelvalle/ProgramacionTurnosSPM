package com.mardique.programacion.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persona de la planta que puede ser programada.
 *
 * <p><strong>El salario no está aquí a propósito.</strong> Vive en
 * {@link ColaboradorSalario}, en otra tabla, y solo debe leerla la capa de
 * servicio para los roles {@link TipoUsuarioRol#TALENTO_HUMANO} y
 * {@link TipoUsuarioRol#NOMINA}.
 *
 * <p>El celular es dato personal (Ley 1581 de 2012): no debe aparecer en logs.
 */
@Entity
@Table(name = "colaborador")
@Getter
@Setter
@NoArgsConstructor
public class Colaborador extends EntidadConFechaCreacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "documento", nullable = false, length = 30, unique = true)
	private String documento;

	@Column(name = "nombres", nullable = false, length = 120)
	private String nombres;

	@Column(name = "apellidos", nullable = false, length = 120)
	private String apellidos;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "area_id", nullable = false)
	private Area area;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cargo_id", nullable = false)
	private Cargo cargo;

	/** Opcional: hay collaborators sin turno asignado. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "turno_id")
	private Turno turno;

	/** Dato personal. Acceso mínimo y auditado. */
	@Column(name = "celular", length = 20)
	private String celular;

	@Column(name = "lugar_residencia", length = 160)
	private String lugarResidencia;

	@Column(name = "ubicacion_residencia", length = 255)
	private String ubicacionResidencia;

	@Column(name = "fecha_ingreso")
	private LocalDate fechaIngreso;

	@Column(name = "activo", nullable = false)
	private boolean activo = true;

	/** Nombre completo para mostrar en listados. */
	public String getNombreCompleto() {
		return nombres + " " + apellidos;
	}
}
