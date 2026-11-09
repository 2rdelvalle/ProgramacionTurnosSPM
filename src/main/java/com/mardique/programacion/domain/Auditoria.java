package com.mardique.programacion.domain;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Auditoría de toda decisión: quién hizo qué, sobre qué entidad, con el valor
 * anterior y el nuevo, el motivo y la fecha.
 *
 * <p>Se escribe desde la capa de servicio, nunca desde un controlador. Los
 * campos JSONB se mapean como {@code Map} con
 * {@link SqlTypes#JSON} para que Hibernate los tipifique como {@code jsonb} y
 * no como {@code varchar} (con {@code ddl-auto=validate} el tipo debe
 * coincidir con el DDL).
 *
 * <p><strong>Antes de serializar a JSON hay que excluir los datos sensibles:</strong>
 * el salario no se audita en claro.
 */
@Entity
@Table(name = "auditoria")
@Getter
@Setter
@NoArgsConstructor
public class Auditoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id")
	private Usuario actor;

	/**
	 * Se copia el username para que la trazabilidad sobreviva aunque el usuario
	 * se elimine o se renombre.
	 */
	@Column(name = "actor_username", length = 60)
	private String actorUsername;

	@Column(name = "accion", nullable = false, length = 60)
	private String accion;

	@Column(name = "entidad", nullable = false, length = 60)
	private String entidad;

	@Column(name = "entidad_id")
	private Long entidadId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "valor_anterior", columnDefinition = "jsonb")
	private Map<String, Object> valorAnterior;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "valor_nuevo", columnDefinition = "jsonb")
	private Map<String, Object> valorNuevo;

	/** Obligatorio en las decisiones que se apartan de la regla. */
	@Column(name = "motivo", length = 500)
	private String motivo;

	@Column(name = "fecha", nullable = false, insertable = false, updatable = false)
	private LocalDateTime fecha;
}
