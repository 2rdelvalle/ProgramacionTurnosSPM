package com.mardique.programacion.service;

/**
 * Resultado de crear la misma salida (hora, vehículo y destino) para cada día
 * de un tramo.
 *
 * <p>Los días que ya tenían esa salida se saltan sin más: repetir un tramo
 * sobre una semana que ya está media armada no debe tirar lo demás ni obligar
 * a descubrir cuál día falló. Los dos números se dicen siempre, porque
 * "creadas 5" sin decir que dos ya existían deja la duda de si algo salió mal.
 *
 * @param creadas  cuántas salidas nuevas se guardaron.
 * @param saltadas cuántos días ya tenían esa salida del vehículo.
 */
public record RangoSalidas(int creadas, int saltadas) {

	public String resumen() {
		if (creadas == 0) {
			return "No se creó ninguna salida: los " + saltadas + " día(s) ya la tenían.";
		}
		String base = "Salidas creadas: " + creadas + ".";
		return saltadas == 0 ? base
				: base + " " + saltadas + " día(s) ya tenían esa salida y se saltaron.";
	}
}
