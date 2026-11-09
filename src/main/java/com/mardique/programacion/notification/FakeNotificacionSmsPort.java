package com.mardique.programacion.notification;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Adaptador de desarrollo: simula el proveedor sin enviar datos fuera del sistema. */
@Component
@Profile("!sms-real")
public class FakeNotificacionSmsPort implements NotificacionSmsPort {

	@Override
	public String enviar(String celular, String mensaje) {
		return "FAKE-" + UUID.randomUUID();
	}
}
