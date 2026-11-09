package com.mardique.programacion.audit;

import java.util.Map;

/**
 * Datos de una entrada de auditoría en construcción. Se arma con
 * {@link #de(AccionAuditada, EntidadAuditada)} y se completa con los métodos
 * faltantes.
 *
 * <p>Es intencionadamente tonto: no toca la base de datos ni conoce al usuario
 * actor, eso lo resuelve {@link AuditoriaService}.
 */
public class RegistroAuditoria {

	private final AccionAuditada accion;

	private final EntidadAuditada entidad;

	private Long entidadId;

	private Map<String, Object> valorAnterior;

	private Map<String, Object> valorNuevo;

	private String motivo;

	private RegistroAuditoria(AccionAuditada accion, EntidadAuditada entidad) {
		this.accion = accion;
		this.entidad = entidad;
	}

	public static RegistroAuditoria de(AccionAuditada accion, EntidadAuditada entidad) {
		return new RegistroAuditoria(accion, entidad);
	}

	public RegistroAuditoria entidadId(Long entidadId) {
		this.entidadId = entidadId;
		return this;
	}

	public RegistroAuditoria anterior(Map<String, Object> valorAnterior) {
		this.valorAnterior = valorAnterior;
		return this;
	}

	public RegistroAuditoria nuevo(Map<String, Object> valorNuevo) {
		this.valorNuevo = valorNuevo;
		return this;
	}

	/** Motivo. Obligatorio en las decisiones que se apartan de la regla. */
	public RegistroAuditoria motivo(String motivo) {
		this.motivo = motivo;
		return this;
	}

	public AccionAuditada accion() {
		return accion;
	}

	public EntidadAuditada entidad() {
		return entidad;
	}

	public Long entidadId() {
		return entidadId;
	}

	public Map<String, Object> valorAnterior() {
		return valorAnterior;
	}

	public Map<String, Object> valorNuevo() {
		return valorNuevo;
	}

	public String motivo() {
		return motivo;
	}
}
