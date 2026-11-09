package com.mardique.programacion.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.domain.TipoUsuarioRol;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.domain.UsuarioArea;
import com.mardique.programacion.domain.UsuarioRol;
import com.mardique.programacion.repository.UsuarioAreaRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.repository.UsuarioRolRepository;

/**
 * Carga el usuario, sus roles y su alcance por área.
 *
 * <p>Al definir este bean, Spring Boot deja de crear el usuario por defecto en
 * memoria, que era el que avisaba por consola.
 *
 * <p>El alcance se resuelve una sola vez, acá en el acceso, y viaja dentro del
 * principal: las pantallas no vuelven a consultar la base para saber si un
 * coordinador puede ver un área. Se suman las áreas de {@code usuario_area} con
 * las que el rol alcanza por definición (ver {@link AlcanceImplicitoService}).
 */
@Service
public class UsuarioDetailsServiceImpl implements UserDetailsService {

	private final UsuarioRepository usuarios;

	private final UsuarioRolRepository rolesPorUsuario;

	private final UsuarioAreaRepository areasPorUsuario;

	private final AlcanceImplicitoService alcanceImplicito;

	public UsuarioDetailsServiceImpl(UsuarioRepository usuarios, UsuarioRolRepository rolesPorUsuario,
			UsuarioAreaRepository areasPorUsuario, AlcanceImplicitoService alcanceImplicito) {
		this.usuarios = usuarios;
		this.rolesPorUsuario = rolesPorUsuario;
		this.areasPorUsuario = areasPorUsuario;
		this.alcanceImplicito = alcanceImplicito;
	}

	/**
	 * @param username usuariodigitado en el formulario de acceso.
	 * @return el usuario con roles y áreas cargados.
	 * @throws UsernameNotFoundException si no existe.
	 */
	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Usuario usuario = usuarios.findByUsernameAndActivoTrue(username)
				.orElseThrow(() -> new UsernameNotFoundException("Usuario inexistente o inactivo"));

		List<UsuarioRol> roles = rolesPorUsuario.findByIdUsuarioId(usuario.getId());
		List<UsuarioArea> areas = areasPorUsuario.findByIdUsuarioId(usuario.getId());

		Set<TipoUsuarioRol> tiposDeRol = new LinkedHashSet<>(
				roles.stream().map(rol -> rol.getId().getRol()).toList());
		Set<Long> areasAsignadas = new LinkedHashSet<>(
				areas.stream().map(area -> area.getArea().getId()).toList());

		return new UsuarioPrincipal(usuario.getId(), usuario.getUsername(), usuario.getPasswordHash(),
				usuario.getNombreCompleto(), usuario.isActivo(), tiposDeRol,
				alcanceImplicito.alcanceCompleto(tiposDeRol, areasAsignadas));
	}
}
