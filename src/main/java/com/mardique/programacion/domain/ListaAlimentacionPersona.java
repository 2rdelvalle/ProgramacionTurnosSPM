package com.mardique.programacion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persona elegida para una lista de meriendas o cenas.
 *
 * <p>El área se guarda como columna propia y no se lee de la relación del
 * colaborador: la lista es un documento con fecha y el proveedor debe recibir
 * el área que la persona tenía el día que se armó, aunque después se mueva.
 * La programación queda como referencia de dónde salió la elegibilidad.
 */
@Entity
@Table(name = "lista_alimentacion_persona",
		uniqueConstraints = @UniqueConstraint(name = "uq_lista_alimentacion_persona",
				columnNames = { "lista_id", "colaborador_id" }))
@Getter
@Setter
@NoArgsConstructor
public class ListaAlimentacionPersona {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lista_id", nullable = false)
	private ListaAlimentacion lista;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "colaborador_id", nullable = false)
	private Colaborador colaborador;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "area_id", nullable = false)
	private Area area;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "programacion_id")
	private ProgramacionExtendido programacion;
}
