package com.mardique.programacion.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El proveedor real de Twilio, pieza por pieza: cómo se marca el número, cómo
 * se lee la respuesta y qué pasa si llega el perfil {@code sms-real} sin las
 * credenciales puestas. La llamada HTTP en sí no se prueba contra la red: el
 * perfil está apagado en la suite y Twilio no es un colaborador confiable de
 * las pruebas.
 */
class SmsRealPortTest {

	@Test
	@DisplayName("El celular colombiano se marca con 57 para cumplir E.164")
	void elNumeroColombianoSeMarcaCon57() {
		assertThat(SmsRealPort.e164("3001234567")).isEqualTo("+573001234567");
		assertThat(SmsRealPort.e164("+57 300 123 4567")).isEqualTo("+573001234567");
		assertThat(SmsRealPort.e164("17372508034")).as("un número que ya trae su código no se marca dos veces")
				.isEqualTo("+17372508034");
	}

	@Test
	@DisplayName("De la respuesta de Twilio se lee el SID del mensaje, y de un error su mensaje")
	void seLeenSidYErrorDeLaRespuesta() {
		String exito = "{\"sid\": \"SMccf1ee74daddc18c7a80870c4d494072\", \"status\": \"queued\", "
				+ "\"accountSid\": \"cuenta-ficticia-qa\"}";
		assertThat(SmsRealPort.primerCampo(exito, SmsRealPort.SID, null))
				.as("el sid es el que se guarda para trazar la entrega")
				.isEqualTo("SMccf1ee74daddc18c7a80870c4d494072");

		String error = "{\"code\": 21608, \"message\": \"The 'To' number +573001234567 is not a valid SMS-enabled "
				+ "number.\", \"status\": 400}";
		assertThat(SmsRealPort.primerCampo(error, SmsRealPort.MENSAJE, null))
				.as("ese texto es el que verá la bandeja como último error")
				.contains("not a valid SMS-enabled number");
		assertThat(SmsRealPort.primerCampo(null, SmsRealPort.SID, "sin detalle")).isEqualTo("sin detalle");
	}

	@Test
	@DisplayName("Con el perfil sms-real y sin credenciales, el error es claro desde el arranque")
	void sinCredencialesNoArranca() {
		assertThatThrownBy(() -> new SmsRealPort("", "token", "+17372508034"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("app.sms.twilio.cuenta-sid")
				.hasMessageContaining("TWILIO_ACCOUNT_SID");
	}

	@Test
	@DisplayName("Con las tres credenciales puestas, el proveedor se construye sin pedir nada a la red")
	void conCredencialesSeConstruye() {
		SmsRealPort proveedor = new SmsRealPort("AC123", "secreto", "+17372508034");
		assertThat(proveedor).isNotNull();
	}
}
