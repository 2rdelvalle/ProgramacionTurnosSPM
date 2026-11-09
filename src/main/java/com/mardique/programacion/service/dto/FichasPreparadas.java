package com.mardique.programacion.service.dto;

import java.util.List;

/**
 * Fichas listas para repintar el formulario de propuesta, con la nota de lo
 * que se cortó.
 *
 * <p>Los atajos "repetir día" y la selección masiva arman varias fichas de
 * una vez, y el lote está limitado a {@value #LIMITE}. Cuando el origen da
 * más, el llamador guarda solo hasta el tope y pasa el total original a
 * {@link #conTope}: la nota resultante informa al coordinador cuántas
 * quedaron fuera.
 *
 * <p>Las fichas son de tipo {@link FichaProgramacion}, sin anotaciones web.
 * La conversión a {@code FormularioProgramacion} para la vista es
 * responsabilidad del controlador.
 *
 * @param fichas lista ya acotada al tope por el llamador.
 * @param nota   aviso de truncamiento, o {@code null} cuando no se cortó nada.
 */
public record FichasPreparadas(List<FichaProgramacion> fichas, String nota) {

	/** Tope del lote: el mismo que valida el formulario. */
	public static final int LIMITE = 100;

	/**
	 * Construye el aviso de truncamiento si el total supera el tope.
	 *
	 * <p><strong>No recorta la lista.</strong> El recorte es responsabilidad
	 * del llamador, que solo añade fichas mientras {@code fichas.size() < LIMITE}
	 * y luego pasa el conteo total aquí. Este método recibe la lista ya acotada
	 * y decide si añadir la nota comparando {@code total} con {@value #LIMITE}.
	 *
	 * @param fichas lista previamente acotada a como máximo {@value #LIMITE}
	 *               elementos por el llamador.
	 * @param total  cuántas fichas habría en total sin el tope; puede ser mayor
	 *               que {@code fichas.size()}.
	 */
	public static FichasPreparadas conTope(List<FichaProgramacion> fichas, int total) {
		if (total <= LIMITE) {
			return new FichasPreparadas(fichas, null);
		}
		String nota = "El lote admite máximo " + LIMITE + " fichas: se prepararon " + LIMITE + " de " + total
				+ ". Quite las que no vaya a usar y vuelva a preparar para traer las demás.";
		return new FichasPreparadas(fichas, nota);
	}
}
