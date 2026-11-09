package com.mardique.programacion.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una programación del historial del perfil, con todo lo que explica lo que
 * la persona hizo ese día: qué se le asignó (motivo y horario), qué ejecutó
 * (horas confirmadas con su observación), en qué vehículo se subió y qué se
 * le notificó.
 *
 * <p>Los bloques de horas, vehículo y SMS son opcionales: cada uno se muestra
 * con su aviso cuando no existe dato, nunca se rellena con algo inventado.
 *
 * @param notificable si la programación está en un estado que permite enviar
 *                    la notificación de asignación.
 * @param whatsapp    enlace wa.me con el mensaje armado, o {@code null} si el
 *                    colaborador no tiene celular registrado.
 */
public record PerfilProgramacionDto(Long programacionId, LocalDateTime inicio, LocalDateTime fin, String tipo,
		String estado, String area, String motivo, String ajuste, BigDecimal horasProgramadas,
		BigDecimal horasEfectivas, boolean requeriaNivelacion, boolean esEmergencia, String referenciaEvento,
		LocalDate fechaHoras, BigDecimal horasEjecutadas, String tipoHoras, String observacionHoras,
		String registradoPor, boolean notificable, String smsEstado, String smsMensaje, String placa,
		String conductor, String destino, LocalDateTime salida, String whatsapp) {
}
