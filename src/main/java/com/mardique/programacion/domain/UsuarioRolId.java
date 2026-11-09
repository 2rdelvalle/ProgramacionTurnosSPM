package com.mardique.programacion.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Clave primaria embebida de {@link UsuarioRol}
 * ({@code usuario_id, rol}).
 *
 * <p>El atributo {@code usuarioId} es el que la entidad {@link UsuarioRol}
 * enlaza con su asociación {@code usuario} mediante {@code @MapsId}; Hibernate
 * mantiene ambos en sincronización.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioRolId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "usuario_id", nullable = false)
	private Long usuarioId;

	@Enumerated(EnumType.STRING)
	@Column(name = "rol", nullable = false, length = 30)
	private TipoUsuarioRol rol;

	@Override
	public boolean equals(Object otro) {
		if (this == otro) {
			return true;
		}
		if (!(otro instanceof UsuarioRolId that)) {
			return false;
		}
		return Objects.equals(usuarioId, that.usuarioId) && rol == that.rol;
	}

	@Override
	public int hashCode() {
		return Objects.hash(usuarioId, rol);
	}
}
