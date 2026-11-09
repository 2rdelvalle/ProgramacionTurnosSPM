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
 * Habilitación exigida por un tipo de operación. La clave primaria es
 * compuesta ({@code tipo_operacion_id, habilitacion_id}).
 */
@Entity
@Table(name = "tipo_operacion_habilitacion")
@Getter
@Setter
@NoArgsConstructor
public class TipoOperacionHabilitacion {

	@EmbeddedId
	private TipoOperacionHabilitacionId id;

	@MapsId("tipoOperacionId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tipo_operacion_id", nullable = false)
	private TipoOperacion tipoOperacion;

	@MapsId("habilitacionId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "habilitacion_id", nullable = false)
	private Habilitacion habilitacion;
}
