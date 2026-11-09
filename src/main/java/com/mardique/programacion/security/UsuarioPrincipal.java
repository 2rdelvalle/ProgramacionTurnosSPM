package com.mardique.programacion.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.mardique.programacion.domain.TipoUsuarioRol;

/**
 * Usuario autenticado, con la información que la capa de servicio necesita
 * para aplicar el alcance por área sin volver a consultar la base.
 *
 * <p>Los identificadores de área se copian aquí a propósito: el filtro de
 * alcance se evalúa muchas veces por pantalla y debe ser barato.
 */
public class UsuarioPrincipal implements UserDetails {

	private static final long serialVersionUID = 1L;

	private final Long usuarioId;

	private final String username;

	private final String passwordHash;

	private final String nombreCompleto;

	private final boolean activo;

	private final Set<TipoUsuarioRol> roles;

	private final Set<Long> areaIds;

	public UsuarioPrincipal(Long usuarioId, String username, String passwordHash, String nombreCompleto,
			boolean activo, Collection<TipoUsuarioRol> roles, Collection<Long> areaIds) {
		this.usuarioId = usuarioId;
		this.username = username;
		this.passwordHash = passwordHash;
		this.nombreCompleto = nombreCompleto;
		this.activo = activo;
		this.roles = Set.copyOf(roles);
		this.areaIds = Set.copyOf(areaIds);
	}

	public Long usuarioId() {
		return usuarioId;
	}

	public String nombreCompleto() {
		return nombreCompleto;
	}

	public Set<TipoUsuarioRol> roles() {
		return roles;
	}

	public boolean tieneRol(TipoUsuarioRol rol) {
		return roles.contains(rol);
	}

	/** Áreas asignadas en {@code usuario_area}. Vacías si el rol es universal. */
	public Set<Long> areaIds() {
		return areaIds;
	}

	/**
	 * Indica si el usuario trabaja sobre toda la planta y por lo tanto el filtro
	 * de alcance no aplica.
	 */
	public boolean tieneAlcanceUniversal() {
		return roles.stream().anyMatch(TipoUsuarioRol::tieneAlcanceUniversal);
	}

	/**
	 * Indica si el usuario puede operar sobre el área indicada. Siempre es
	 * cierto para roles de alcance universal.
	 */
	public boolean puedeVerArea(Long areaId) {
		return tieneAlcanceUniversal() || (areaId != null && areaIds.contains(areaId));
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return roles.stream().map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(rol.autoridad()))
				.toList();
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return activo;
	}

	/** Nombres de rol, para mostrar en la interfaz de inicio de sesión. */
	public List<String> nombresDeRol() {
		return roles.stream().map(Enum::name).sorted().toList();
	}
}
