package com.mardique.programacion.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Manda a revisión varias propuestas en una sola pulsación.
 *
 * <p><strong>Va en su propia clase a propósito.</strong> Cada envío tiene que
 * ser una transacción independiente, y para eso hay que llamar a
 * {@link ProgramacionService#enviar(Long)} a través del proxy de Spring. Si
 * este bucle viviera dentro de {@code ProgramacionService} la llamada sería
 * interna, el proxy no interviene, las transacciones se fusionarían en una
 * sola y bastaría que la tercera propuesta estuviera mal para que las otras
 * cinco también se perdieran. Y eso es justo lo que no puede pasar: el
 * coordinador ya escribió las seis, y perder cinco porque una estaba repetida
 * no tiene arreglo.
 *
 * <p>Se reporta qué quedó fuera y por qué en lugar de dejar que una excepción
 * aborte el resto. El objetivo es que se envíe todo lo que se pueda, y que la
 * persona vea con nombre y apellido lo que falta por revisar.
 */
@Service
public class EnvioLoteService {

	private final ProgramacionService programacion;

	public EnvioLoteService(ProgramacionService programacion) {
		this.programacion = programacion;
	}

	/**
	 * @param ids identificadores de las propuestas a enviar.
	 * @return cuántas salieron y cuáles quedaron sin tocar, con el motivo.
	 */
	public EnvioLote enviar(List<Long> ids) {
		List<String> motivos = new ArrayList<>();
		int enviados = 0;

		for (Long id : ids == null ? List.<Long>of() : ids) {
			try {
				// Cada iteración es su propia transacción porque entra por el
				// proxy, no por una llamada interna.
				programacion.enviar(id);
				enviados++;
			} catch (PropuestaIncompletaException excepcion) {
				motivos.add(describir(id) + " está incompleta: " + String.join(" ", excepcion.problemas().values()));
			} catch (TransicionNoValidaException | IllegalStateException | java.util.NoSuchElementException excepcion) {
				motivos.add(describir(id) + ": " + excepcion.getMessage());
			}
		}

		return new EnvioLote(enviados, motivos.size(), List.copyOf(motivos));
	}

	/**
	 * @return nombre de la persona, para que el motivo sea reconocible. Si la
	 *         propuesta ya no existe, el identificador sirve de referencia.
	 */
	private String describir(Long id) {
		try {
			return programacion.obtener(id).getColaborador().getNombreCompleto();
		} catch (RuntimeException excepcion) {
			return "Propuesta " + id;
		}
	}
}
