package com.mardique.programacion.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Interruptores de la interfaz que dependen del ambiente.
 *
 * <p>Se expone como bean para que las plantillas lo consulten directamente
 * ({@code ${@entorno.muestraCredencialesDemo}}). Los valores sensibles de
 * configuración no se leen desde aquí: las credenciales demo son una ayuda de
 * desarrollo y su visibilidad se apaga con una propiedad, no hardcodeando.
 */
@Component("entorno")
public class EntornoUI {

	private final boolean muestraCredencialesDemo;

	public EntornoUI(@Value("${app.mostrarCredencialesDemo:true}") boolean muestraCredencialesDemo) {
		this.muestraCredencialesDemo = muestraCredencialesDemo;
	}

	/**
	 * @return {@code true} si la pantalla de acceso debe mostrar el cuadro con
	 *         los usuarios de prueba.
	 */
	public boolean muestraCredencialesDemo() {
		return muestraCredencialesDemo;
	}
}
