package com.mardique.programacion.web;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * El envío por lote: varias propuestas de extendido en un solo formulario.
 *
 * <p>Existe porque el flujo anterior obligaba a guardar, volver atrás y repetir
 * por cada persona. Para un turno con seis personas eso son seis guardados, seis
 * pantallas de confirmación y seis envíos a revisión separados, cuando en
 * realidad es <em>una</em> decisión: la misma fecha, las mismas horas y el mismo
 * motivo para un grupo de gente.
 *
 * <p><strong>La lista se inicializa con una ficha vacía</strong> a propósito. Si
 * arrancara vacía, Spring no tendría un elemento al que colgar
 * {@code extendidos[0].colaboradorId} y el primer error de validación no
 * aparecería en ningún sitio. Con un elemento de arranque, la página abre con
 * una ficha lista para rellenar y las siguientes las añade el {@code +}.
 *
 * <p>El {@link Valid} va en el argumento de tipo y no en el campo porque
 * Hibernate Validator lo pide así desde la versión 8: aplicado al campo está
 * obsoleto y avisa por log. El efecto es el mismo, pero el aviso desaparece.
 *
 * <p>El tope de cien es una decisión de pantalla, no de negocio: nadie arma
 * cien fichas a mano, y un límite evita que un formulario alterado en el
 * navegador eleve un lote gigante en una sola transacción. Se subió de veinte
 * a cien cuando la selección masiva pasó a armar los lotes sola.
 */
public class FormularioProgramacionLote {

	@NotEmpty(message = "Agregue al menos una propuesta.")
	@Size(max = 100, message = "Se pueden enviar hasta 100 propuestas a la vez.")
	private List<@Valid FormularioProgramacion> extendidos = new ArrayList<>(
			List.of(new FormularioProgramacion()));

	public List<FormularioProgramacion> getExtendidos() {
		return extendidos;
	}

	public void setExtendidos(List<FormularioProgramacion> extendidos) {
		this.extendidos = extendidos;
	}
}
