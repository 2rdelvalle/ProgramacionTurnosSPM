package com.mardique.programacion.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Regla crítica: datos personales y de compensación no se auditan en claro. */
class RedactorTest {

	@Test
	@DisplayName("Oculta la clave aunque venga en snake_case, camelCase o con mayúsculas")
	void ocultaSinImportarElFormatoDeLaClave() {
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("salario_base", 1);
		valores.put("salarioBase", 2);
		valores.put("SALARIOBASE", 3);

		Map<String, Object> resultado = Redactor.sanear(valores);

		assertThat(resultado).containsEntry("salario_base", Redactor.OCULTO)
				.containsEntry("salarioBase", Redactor.OCULTO)
				.containsEntry("SALARIOBASE", Redactor.OCULTO);
	}

	@Test
	@DisplayName("Deja intactas las claves que no son sensibles")
	void conservaLasClavesNoSensibles() {
		Map<String, Object> valores = Map.of("areaId", 1L, "nombre", "Operaciones", "estado", "APROBADA");

		assertThat(Redactor.sanear(valores)).isEqualTo(valores);
	}

	@Test
	@DisplayName("Un mapa nulo sigue siendo nulo")
	void toleraNulo() {
		assertThat(Redactor.sanear(null)).isNull();
	}

	@Test
	@DisplayName("Rechaza una lista impar de clave/valor")
	void mapaRechazaParesImpares() {
		assertThatThrownBy(() -> Redactor.mapa("soloUnaCosa")).isInstanceOf(IllegalArgumentException.class);
	}
}
