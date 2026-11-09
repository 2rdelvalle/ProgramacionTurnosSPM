package com.mardique.programacion.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Lista de meriendas o cenas guardada para un día: el historial de lo que se
 * entregó al proveedor de alimentación.
 *
 * <p>La lista se persiste en el momento de elegirla, no se recalcula después:
 * si mañana cambia una programación, la lista enviada ayer sigue diciendo lo
 * que se envió. Por eso cada fila además guarda el área de la persona como
 * estaba ese día.
 */
@Entity
@Table(name = "lista_alimentacion")
@Getter
@Setter
@NoArgsConstructor
public class ListaAlimentacion extends EntidadConFechaCreacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "fecha", nullable = false)
	private LocalDate fecha;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 10)
	private TipoAlimentacion tipo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "creado_por", nullable = false)
	private Usuario creadoPor;
}
