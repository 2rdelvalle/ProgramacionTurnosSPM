package com.mardique.programacion.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Base de las entidades cuyo {@code created_at} lo asigna PostgreSQL con
 * {@code DEFAULT now()}.
 *
 * <p>La columna se declara {@code insertable = false, updatable = false} para
 * que Hibernate no intente escribirla y el valor siga siendo responsabilidad
 * de la base de datos, igual que en la migración de Flyway.
 */
@MappedSuperclass
public abstract class EntidadConFechaCreacion {

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
