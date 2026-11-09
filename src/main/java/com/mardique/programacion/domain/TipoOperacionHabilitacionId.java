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
 * Clave primaria embebida de {@link TipoOperacionHabilitacion}
 * ({@code tipo_operacion_id, habilitacion_id}).
 *
 * <p>Cada atributo es el que la entidad {@link TipoOperacionHabilitacion}
 * enlaza con su asociación mediante {@code @MapsId}; Hibernate mantiene ambos
 * en sincronización.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TipoOperacionHabilitacionId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "tipo_operacion_id", nullable = false)
	private Long tipoOperacionId;

	@Column(name = "habilitacion_id", nullable = false)
	private Long habilitacionId;

	@Override
	public boolean equals(Object otro) {
		if (this == otro) {
			return true;
		}
		if (!(otro instanceof TipoOperacionHabilitacionId that)) {
			return false;
		}
		return Objects.equals(tipoOperacionId, that.tipoOperacionId)
				&& Objects.equals(habilitacionId, that.habilitacionId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tipoOperacionId, habilitacionId);
	}
}
