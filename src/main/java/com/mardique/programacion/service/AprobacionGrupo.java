package com.mardique.programacion.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Resultado de aprobar un día entero de un grupo de áreas.
 *
 * <p>Lleva la cuenta de cuántas se aprobaron y cuántas quedaron fuera, porque
 * "se aprobó el grupo" sin números no sirve para después preguntar por qué
 * alguien no quedó aprobado. Los casos saltados se nombran, no se cuentan
 * solamente: la diferencia entre "ya estaba aprobada" y "no era de este grupo"
 * importa cuando alguien va a mirar.
 *
 * @param dia      día aprobado.
 * @param grupo    etiqueta del grupo, por ejemplo {@code Puerto}.
 * @param approved cuántas quedaron en estado aprobada.
 * @param skipped cuántas se dejaron sin tocar.
 * @param motivos  detalle de las saltadas, para mostrarlo.
 */
public record AprobacionGrupo(LocalDate dia, String grupo, int approved, int skipped, List<String> motivos) {

	public static AprobacionGrupo vacio(LocalDate dia, String grupo) {
		return new AprobacionGrupo(dia, grupo, 0, 0, List.of());
	}

	/**
	 * @return frase lista para pantalla. Dice explícitamente cuando no había nada
	 *         que aprobar, para que el silencio no se confunda con "se aprobó".
	 */
	public String resumen() {
		if (approved == 0) {
			return "No había programaciones pendientes de " + grupo + " el " + dia + ".";
		}
		String base = "Aprobadas " + approved + " de " + grupo + " del " + dia + ".";
		return skipped == 0 ? base : base + " " + skipped + " quedaron sin tocar: " + String.join("; ", motivos) + ".";
	}
}
