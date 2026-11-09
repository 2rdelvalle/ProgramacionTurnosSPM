package com.mardique.programacion.service;

import java.util.List;

/**
 * Resultado de mandar a revisión varias propuestas de golpe.
 *
 * <p>Mismo criterio que {@link AprobacionGrupo}: se dicen los números y se
 * nombran las que quedaron fuera. Guardar seis propuestas y que solo se envíen
 * cuatro tiene que ser visible, porque las dos que se quedaron en borrador
 * todavía necesitan una decisión del coordinador y si no se dice, se pierden.
 *
 * <p>Los casos saltados se nombran uno por uno en vez de resumir en un "2
 * fallaron": el motivo típico es que a alguien ya se le envió la suya, y eso se
 * reconoce por el nombre, no por un número.
 *
 * @param enviados cuántas pasaron a estado enviada.
 * @param skipped  cuántas se dejaron sin tocar.
 * @param motivos  detalle de las saltadas, para mostrarlo.
 */
public record EnvioLote(int enviados, int skipped, List<String> motivos) {

	public String resumen() {
		if (enviados == 0) {
			return skipped == 0 ? "No había propuestas que enviar."
					: "No se envió ninguna: " + String.join("; ", motivos) + ".";
		}
		String base = "Enviadas a revisión " + enviados + (enviados == 1 ? " propuesta." : " propuestas.");
		return skipped == 0 ? base : base + " " + skipped + " quedaron sin tocar: " + String.join("; ", motivos) + ".";
	}
}
