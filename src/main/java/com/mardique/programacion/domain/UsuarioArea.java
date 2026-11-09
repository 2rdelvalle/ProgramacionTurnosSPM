package com.mardique.programacion.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Área sobre la que un usuario tiene alcance. Un coordinador solo ve y programa
 * personal de las áreas registradas acá; los intentos fuera de alcance se
 * auditan.
 *
 * <p>Los roles {@link TipoUsuarioRol#TALENTO_HUMANO}, {@link TipoUsuarioRol#NOMINA},
 * {@link TipoUsuarioRol#ADMIN} y {@link TipoUsuarioRol#AUDITOR} no dependen de
 * esta tabla: su alcance se define por rol, no por área.
 */
@Entity
@Table(name = "usuario_area")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioArea {

	@EmbeddedId
	private UsuarioAreaId id;

	@MapsId("usuarioId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@MapsId("areaId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "area_id", nullable = false)
	private Area area;
}
