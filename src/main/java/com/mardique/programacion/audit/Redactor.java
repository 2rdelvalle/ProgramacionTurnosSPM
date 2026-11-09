package com.mardique.programacion.audit;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Quita de un registro de auditoría los datos que no pueden quedar en claro.
 *
 * <p>La Ley 1581 de 2012 obliga a tratar el salario y el celular como datos
 * personales. La auditoría guarda el <em>hecho</em> de que se consultó o
 * cambió, nunca el valor.
 */
public final class Redactor {

	/** Texto con que se sustituye un valor sensible. */
	public static final String OCULTO = "***";

	/**
	 * Claves cuyo valor se oculta, en minúsculas. Se comparan sin distinguir
	 * mayúsculas y seIgnora el separador de palabras, de modo que
	 * {@code salarioBase}, {@code salario_base} y {@code SALARIOBASE} caen en
	 * la misma regla.
	 */
	private static final Set<String> CLAVES_SENSIBLES = Set.of("salario", "salariobase", "valorhora",
			"valorporpagado", "celular", "telefono", "password", "passwordhash", "contrasena",
			"token", "secret");

	private Redactor() {
	}

	/**
	 * Devuelve una copia del mapa con los valores sensibles reemplazados por
	 * {@link #OCULTO}. Si el mapa es {@code null} devuelve {@code null}.
	 */
	public static Map<String, Object> sanear(Map<String, Object> valores) {
		if (valores == null) {
			return null;
		}
		Map<String, Object> resultado = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entrada : valores.entrySet()) {
			String clave = entrada.getKey();
			resultado.put(clave, esSensible(clave) ? OCULTO : entrada.getValue());
		}
		return resultado;
	}

	/** Indica si una clave corresponde a un dato que no se audita en claro. */
	public static boolean esSensible(String clave) {
		return clave != null && CLAVES_SENSIBLES.contains(normalizar(clave));
	}

	/**
	 * Construye un mapa a partir de pares clave/valor, útil para armar el valor
	 * anterior y el nuevo sin escribir el {@code Map.of} con claves repetidas.
	 *
	 * <p><strong>El mapa resultante no se sanea:</strong> se hace al serializar
	 * en {@link AuditoriaService}, para que un olvido en la construcción no
	 * deje datos expuestos.
	 */
	public static Map<String, Object> mapa(Object... pares) {
		if (pares.length % 2 != 0) {
			throw new IllegalArgumentException("Se esperaba una lista par de clave/valor");
		}
		Map<String, Object> mapa = new LinkedHashMap<>();
		for (int i = 0; i < pares.length; i += 2) {
			mapa.put(String.valueOf(pares[i]), pares[i + 1]);
		}
		return mapa;
	}

	/** Claves sensibles conocidas, para exponerlas en la documentación o pruebas. */
	public static List<String> clavesSensibles() {
		return Arrays.stream(CLAVES_SENSIBLES.toArray(String[]::new)).sorted().toList();
	}

	private static String normalizar(String clave) {
		return clave.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").trim();
	}
}
