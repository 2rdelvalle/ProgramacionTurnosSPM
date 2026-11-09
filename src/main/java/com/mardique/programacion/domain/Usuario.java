package com.mardique.programacion.domain;

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
 * Usuario del aplicativo. La lista de roles vive en {@link UsuarioRol} y el
 * alcance por área en {@link UsuarioArea}.
 *
 * <p>El vínculo con {@link Colaborador} es opcional: un usuario de Talento
 * Humano o Nómina puede no ser parte de la planta.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario extends EntidadConFechaCreacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "username", nullable = false, length = 60, unique = true)
	private String username;

	/** Hash de la contraseña. Nunca se registra en auditoría ni en logs. */
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "nombre_completo", nullable = false, length = 160)
	private String nombreCompleto;

	@Column(name = "email", length = 160)
	private String email;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "colaborador_id")
	private Colaborador colaborador;

	@Column(name = "activo", nullable = false)
	private boolean activo = true;
}
