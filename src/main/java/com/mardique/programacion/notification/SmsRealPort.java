package com.mardique.programacion.notification;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * El proveedor de verdad: Twilio, sin SDK.
 *
 * <p>Se llama al mismo recurso que la guía oficial de Twilio (POST
 * {@code /2010-04-01/Accounts/{sid}/Messages.json} con autenticación básica),
 * pero con el cliente HTTP que ya trae Spring: la cola de SMS no necesita la
 * librería completa para mandar un texto.
 *
 * <p>Solo existe con el perfil {@code sms-real}: sin ese perfil la aplicación
 * usa {@link FakeNotificacionSmsPort} y no sale nada del equipo. Si el perfil
 * está puesto y falta alguna clave, la aplicación no arranca —es mejor que
 * fallen las credenciales en el arranque a descubrirlo en el primer mensaje—.
 *
 * <p>Números: la cola guarda diez dígitos colombianos y acá se marcan con el
 * 57 para cumplir E.164, que es lo que Twilio exige.
 */
@Component
@Profile("sms-real")
public class SmsRealPort implements NotificacionSmsPort {

	/** {@code "sid": "SM..."} del mensaje creado. */
	static final Pattern SID = Pattern.compile("\"sid\"\\s*:\\s*\"([^\"]+)\"");

	/** {@code "message": "..."} del error que Twilio devuelve en el cuerpo. */
	static final Pattern MENSAJE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"");

	private final RestClient http;

	private final String cuentaSid;

	private final String desde;

	public SmsRealPort(@Value("${app.sms.twilio.cuenta-sid:}") String cuentaSid,
			@Value("${app.sms.twilio.token:}") String token, @Value("${app.sms.twilio.desde:}") String desde) {
		if (cuentaSid == null || cuentaSid.isBlank() || token == null || token.isBlank() || desde == null
				|| desde.isBlank()) {
			throw new IllegalStateException(
					"Falta configurar Twilio para SMS reales: define app.sms.twilio.cuenta-sid, "
							+ "app.sms.twilio.token y app.sms.twilio.desde (o las variables de entorno "
							+ "TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN y TWILIO_FROM) y arranca con el perfil sms-real.");
		}
		this.cuentaSid = cuentaSid.trim();
		this.desde = desde.trim();
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(Duration.ofSeconds(10));
		fabrica.setReadTimeout(Duration.ofSeconds(20));
		// Se construye el cliente a mano y no se inyecta RestClient.Builder:
		// su autoconfiguración no está garantizada en todos los arranques y un
		// SMS no necesita pelearse con el ciclo de vida del contenedor.
		this.http = RestClient.builder().baseUrl("https://api.twilio.com/2010-04-01")
				.defaultHeader(HttpHeaders.AUTHORIZATION,
						"Basic " + Base64.getEncoder()
								.encodeToString((this.cuentaSid + ":" + token.trim()).getBytes(StandardCharsets.UTF_8)))
				.requestFactory(fabrica).build();
	}

	/**
	 * Enía el texto por Twilio y devuelve el SID del mensaje.
	 *
	 * <p>Un rechazo del proveedor (número no verificado en cuenta de prueba,
	 * saldo agotado, celular inválido) se convierte en una excepción con el
	 * mensaje de Twilio, que es lo que la bandeja muestra como último error.
	 */
	@Override
	public String enviar(String celular, String mensaje) {
		MultiValueMap<String, String> cuerpo = new LinkedMultiValueMap<>();
		cuerpo.add("To", e164(celular));
		cuerpo.add("From", desde);
		cuerpo.add("Body", mensaje);
		String respuesta;
		try {
			respuesta = http.post().uri("/Accounts/{sid}/Messages.json", cuentaSid)
					.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(cuerpo).retrieve().body(String.class);
		} catch (RestClientResponseException excepcion) {
			throw new IllegalStateException(
					"Twilio rechazo el envio (HTTP " + excepcion.getStatusCode().value() + "): "
							+ primerCampo(excepcion.getResponseBodyAsString(), MENSAJE, "sin detalle"),
					excepcion);
		}
		String sid = primerCampo(respuesta, SID, null);
		if (sid == null) {
			throw new IllegalStateException("Twilio respondio sin sid: " + respuesta);
		}
		return sid;
	}

	/**
	 * El número en formato E.164: los diez dígitos colombianos se marcan con 57.
	 *
	 * @param celular tal como lo guardó la cola.
	 * @return por ejemplo {@code +573001234567}.
	 */
	static String e164(String celular) {
		String digitos = celular == null ? "" : celular.replaceAll("\\D", "");
		if (digitos.length() == 10) {
			return "+57" + digitos;
		}
		return "+" + digitos;
	}

	/** Primer texto que casa con el patrón, o el valor por defecto. */
	static String primerCampo(String json, Pattern patron, String porDefecto) {
		if (json == null) {
			return porDefecto;
		}
		Matcher coincidencia = patron.matcher(json);
		return coincidencia.find() ? coincidencia.group(1) : porDefecto;
	}
}
