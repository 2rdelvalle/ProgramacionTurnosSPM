package com.mardique.programacion.domain;

import java.time.LocalDateTime;

/**
 * Tipos de hora. El <strong>recargo porcentual de cada tipo NO vive
 * aquí</strong>: se lee de la tabla {@code parametro_laboral} usando
 * {@link #claveParametro()}, de modo que los valores legales los define y
 * valida Talento Humano / Jurídica.
 *
 * <p>Los tipos {@code TURNO_*} son distintos: un turno no es una hora extra, es
 * la jornada propia de una persona que entra en un horario especial (tipo de
 * programación {@code INGRESO}). Por eso no tienen recargo sembrado y se valoran
 * a la hora base. El resto de tipos sí son recargos de hora extra.
 *
 * <p>Si en el futuro TH necesita agregar un tipo nuevo sin tocar el código,
 * la alternativa es convertir esto en una tabla catálogo (migración
 * {@code V2}) y dejar el enum solo como respaldo.
 */
public enum TipoHoraExtra {

	EXTRA_DIURNA("RECARGO_EXTRA_DIURNA"),
	EXTRA_NOCTURNA("RECARGO_EXTRA_NOCTURNA"),
	EXTRA_DOMINICAL_DIURNA("RECARGO_EXTRA_DOMINICAL_DIURNA"),
	EXTRA_DOMINICAL_NOCTURNA("RECARGO_EXTRA_DOMINICAL_NOCTURNA"),

	/** Turno propio que empieza de día (después de las 6:00 y antes de 19:00). */
	TURNO_DIURNO("RECARGO_TURNO_DIURNO"),

	/** Turno propio que empieza de noche (19:00 a 06:00). */
	TURNO_NOCTURNO("RECARGO_TURNO_NOCTURNO");

	/** Desde qué hora (0-23) cuenta como horario nocturno. */
	private static final int DESDE_NOCTURNO = 19;

	/** Hasta qué hora (0-23, exclusiva) sigue siendo nocturno. */
	private static final int HASTA_NOCTURNO = 6;

	private final String claveParametro;

	TipoHoraExtra(String claveParametro) {
		this.claveParametro = claveParametro;
	}

	/**
	 * @return clave de {@code parametro_laboral} que contiene el recargo vigente
	 *         de este tipo de hora.
	 */
	public String claveParametro() {
		return claveParametro;
	}

	/**
	 * El turno que corresponde a un horario que empieza a esta hora.
	 *
	 * <p>La banda nocturna colombiana vigente va de las 19:00 a las 06:00: un
	 * turno que empieza ahí es nocturno, y diurno el que empieza en el resto del
	 * día. Es la regla con la que la pantalla propone el tipo de un
	 * {@code INGRESO} sin que nadie lo elija a mano.
	 *
	 * @param inicio hora en que empieza el turno.
	 * @return {@link #TURNO_NOCTURNO} si empieza de noche; {@link #TURNO_DIURNO}
	 *         si no.
	 */
	public static TipoHoraExtra turnoDe(LocalDateTime inicio) {
		int hora = inicio.getHour();
		return hora >= DESDE_NOCTURNO || hora < HASTA_NOCTURNO ? TURNO_NOCTURNO : TURNO_DIURNO;
	}
}
