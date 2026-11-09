package com.mardique.programacion.domain;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Hora fija que se usa una y otra vez al programar turnos.
 *
 * <p>El grupo de ruta repite siempre las mismas horas de ingreso y salida (3:40
 * pm, 7:00 pm, 11:00 pm, 7:00 am…). Sin esta lista había que digitarlas a mano
 * en cada ficha; con ella aparecen como botones de un clic en el formulario.
 * La etiqueta se guarda escrita ("Ingreso 7:00 am") porque así la reconoce
 * quien la creó: el sistema solo necesita la hora.
 */
@Entity
@Table(name = "horario_frecuente")
@Getter
@Setter
@NoArgsConstructor
public class HorarioFrecuente {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "etiqueta", nullable = false, length = 60)
	private String etiqueta;

	@Column(name = "hora", nullable = false)
	private LocalTime hora;

	@Column(name = "activo", nullable = false)
	private boolean activo = true;
}
