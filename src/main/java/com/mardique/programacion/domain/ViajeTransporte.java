package com.mardique.programacion.domain;

import java.time.LocalDateTime;

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
 * Viaje de transporte del personal: un vehículo que sale a una hora hacia un
 * destino.
 *
 * <p>La base de datos impide dos viajes del mismo vehículo a la misma hora de
 * salida mediante un índice único <em>parcial</em> que excluye los viajes
 * cancelados. Eso no se puede expresar en JPA, así que la aplicación debe
 * respetarlo: al crear un viaje hay que ofrecer primero las horas libres del
 * vehículo y rechazar los duplicados.
 */
@Entity
@Table(name = "viaje_transporte")
@Getter
@Setter
@NoArgsConstructor
public class ViajeTransporte extends EntidadConFechaCreacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "salida", nullable = false)
	private LocalDateTime salida;

	/**
	 * Sentido del viaje. {@code SALIDA} (el valor de siempre) recoge al
	 * personal al terminar su turno; {@code INGRESO} lo recoge antes de que
	 * empiece. Cambia qué hora del programa se usa como referencia y el
	 * encabezado del reporte.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 10)
	private TipoViaje tipo = TipoViaje.SALIDA;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehiculo_id", nullable = false)
	private Vehiculo vehiculo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "destino_id", nullable = false)
	private Destino destino;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoViaje estado = EstadoViaje.PROGRAMADO;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "creado_por", nullable = false)
	private Usuario creadoPor;
}
