package com.mardique.programacion.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.domain.TipoUsuarioRol;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.domain.UsuarioArea;
import com.mardique.programacion.domain.UsuarioAreaId;
import com.mardique.programacion.domain.UsuarioRol;
import com.mardique.programacion.domain.UsuarioRolId;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.UsuarioAreaRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.repository.UsuarioRolRepository;

/**
 * Crea los usuarios de arranque para poder entrar al aplicativo.
 *
 * <p>Solo actúa cuando la tabla {@code usuario} está vacía, así que es
 * idempotente: después de la primera ejecución no vuelve a tocar nada. No es
 * un alta de usuarios normal, que llega en el Paso 3 con su propia pantalla y
 * su auditoría; es el equivalente al "usuario por defecto" que Spring Boot
 * usaba, pero apuntando a la base real.
 *
 * <p>Las contraseñas vienen de variables de entorno. Las que trae por defecto
 * son {@code basico} a propósito, porque son para desarrollo: hay que
 * cambiarlas antes de exponer esto fuera de la máquina del desarrollador. El
 * hash se calcula con BCrypt en el arranque, nunca se guarda en claro.
 */
@Component
public class InicializadorUsuarios implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(InicializadorUsuarios.class);

	private final UsuarioRepository usuarios;

	private final UsuarioRolRepository rolesPorUsuario;

	private final UsuarioAreaRepository areasPorUsuario;

	private final AreaRepository areas;

	private final PasswordEncoder codificador;

	// Deshabilitado por defecto; se activa explicitamente en el perfil 'dev'
	// o con app.inicializador.habilitado=true en configuracion externa.
	@Value("${app.inicializador.habilitado:false}")
	private boolean habilitado;

	@Value("${app.inicializador.th.usuario:th}")
	private String thUsuario;

	// NOTA DE CONTRATO: la propiedad canonica es 'app.inicializador.th.clave'.
	// La forma anterior 'app.inicializador.thclave' estaba mal nombrada (sin
	// el segmento '.th.') y se mantiene como alias de compatibilidad temporal.
	// Los nuevos despliegues deben usar 'app.inicializador.th.clave'.
	// Deprecacion: 'app.inicializador.thclave' se eliminara en la proxima version mayor.
	@Value("${app.inicializador.th.clave:${app.inicializador.thclave:}}")
	private String thClave;

	@Value("${app.inicializador.operaciones.usuario:operaciones}")
	private String operacionesUsuario;

	@Value("${app.inicializador.operaciones.clave:}")
	private String operacionesClave;

	@Value("${app.inicializador.aduanas.usuario:basculas}")
	private String aduanasUsuario;

	@Value("${app.inicializador.aduanas.clave:}")
	private String aduanasClave;

	public InicializadorUsuarios(UsuarioRepository usuarios, UsuarioRolRepository rolesPorUsuario,
			UsuarioAreaRepository areasPorUsuario, AreaRepository areas, PasswordEncoder codificador) {
		this.usuarios = usuarios;
		this.rolesPorUsuario = rolesPorUsuario;
		this.areasPorUsuario = areasPorUsuario;
		this.areas = areas;
		this.codificador = codificador;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments argumentos) {
		if (!habilitado) {
			return;
		}
		// Claves vacias o ausentes bloquean el arranque: mejor un error claro
		// que un usuario con clave vacia en produccion.
		if (thClave == null || thClave.isBlank()
				|| operacionesClave == null || operacionesClave.isBlank()
				|| aduanasClave == null || aduanasClave.isBlank()) {
			throw new IllegalStateException(
				"InicializadorUsuarios habilitado pero faltan claves. "
				+ "Define app.inicializador.th.clave, operaciones.clave y aduanas.clave "
				+ "via variables de entorno o perfil 'dev'.");
		}
		if (usuarios.count() > 0) {
			log.debug("La tabla usuario ya tiene filas: no se crea ningún usuario de arranque.");
			return;
		}
		log.warn("La tabla usuario estaba vacía. Se crean los usuarios de arranque de desarrollo. "
				+ "Cambiá las claves ({}) antes de usar esto fuera de tu máquina.",
				"app.inicializador.*.clave");

		crear(thUsuario, "Talento Humano (arranque)", thClave, TipoUsuarioRol.TALENTO_HUMANO, List.of());
		crear(operacionesUsuario, "Coordinación de Operaciones (arranque)", operacionesClave,
				TipoUsuarioRol.COORD_OPERACIONES, List.of());
		crear(aduanasUsuario, "Coordinación de Documentación Aduanera (arranque)", aduanasClave,
				TipoUsuarioRol.COORD_AREA, List.of("ADU"));
	}

	private void crear(String username, String nombreCompleto, String clave, TipoUsuarioRol rol,
			List<String> codigosDeArea) {
		Usuario usuario = new Usuario();
		usuario.setUsername(username);
		usuario.setPasswordHash(codificador.encode(clave));
		usuario.setNombreCompleto(nombreCompleto);
		usuario.setActivo(true);
		Usuario guardado = usuarios.save(usuario);

		UsuarioRol usuarioRol = new UsuarioRol();
		UsuarioRolId idRol = new UsuarioRolId(guardado.getId(), rol);
		usuarioRol.setId(idRol);
		usuarioRol.setUsuario(guardado);
		rolesPorUsuario.save(usuarioRol);

		for (String codigo : codigosDeArea) {
			areas.findByCodigo(codigo).ifPresent(area -> {
				UsuarioArea usuarioArea = new UsuarioArea();
				usuarioArea.setId(new UsuarioAreaId(guardado.getId(), area.getId()));
				usuarioArea.setUsuario(guardado);
				usuarioArea.setArea(area);
				areasPorUsuario.save(usuarioArea);
			});
		}

		log.info("Usuario de arranque creado: {} con el rol {} y {} área(s) asignada(s).", username, rol,
				codigosDeArea.size());
	}
}
