package com.mardique.programacion.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SMS al colaborador. El envío sale de la capa de servicio a través de un
 * puerto intercambiable; esta tabla es la bitácora, con estado, número de
 * intentos y último error para que Talento Humano vea los fallos.
 *
 * <p>El mensaje no debe contener datos sensibles: ni salario, ni documento,
 * ni información de la programación completa.
 */
@Entity
@Table(name = "notificacion_sms")
@Getter
@Setter
@NoArgsConstructor
public class NotificacionSms extends EntidadConFechaCreacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "colaborador_id", nullable = false)
	private Colaborador colaborador;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "programacion_id")
	private ProgramacionExtendido programacion;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 20)
	private TipoSms tipo;

	/** Copia del celular usado: si el número cambia, el histórico no se altera. */
	@Column(name = "celular_destino", nullable = false, length = 20)
	private String celularDestino;

	@Column(name = "mensaje", nullable = false, length = 320)
	private String mensaje;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoSms estado = EstadoSms.PENDIENTE;

	@Column(name = "intentos", nullable = false)
	private Integer intentos = 0;

	@Column(name = "ultimo_error", length = 500)
	private String ultimoError;

	/** Identificador que devuelve el proveedor, para trazar la entrega. */
	@Column(name = "id_mensaje_proveedor", length = 80)
	private String idMensajeProveedor;

	@Column(name = "enviado_at")
	private LocalDateTime enviadoAt;
}
