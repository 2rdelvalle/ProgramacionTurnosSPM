package com.mardique.programacion.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Clave primaria embebida de {@link UsuarioArea}
 * ({@code usuario_id, area_id}).
 *
 * <p>Cada atributo es el que la entidad {@link UsuarioArea} enlaza con su
 * asociación mediante {@code @MapsId}; Hibernate mantiene ambos en
 * sincronización.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioAreaId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "usuario_id", nullable = false)
	private Long usuarioId;

	@Column(name = "area_id", nullable = false)
	private Long areaId;

	@Override
	public boolean equals(Object otro) {
		if (this == otro) {
			return true;
		}
		if (!(otro instanceof UsuarioAreaId that)) {
			return false;
		}
		return Objects.equals(usuarioId, that.usuarioId) && Objects.equals(areaId, that.areaId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(usuarioId, areaId);
	}
}
