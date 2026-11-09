package com.mardique.programacion.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Cargo;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.ColaboradorSalario;
import com.mardique.programacion.domain.Turno;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.CargoRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.ColaboradorSalarioRepository;
import com.mardique.programacion.repository.TurnoRepository;
import com.mardique.programacion.repository.UsuarioRepository;

/**
 * Datos de demostración para la primera prueba.
 *
 * <p>Solo corre cuando la tabla {@code colaborador} está vacía, así que es
 * idempotente y no pisa nada cargado a mano. Es una troupe de pruebas, no una
 * carga inicial de producción: las personas son ficticias y los salarios
 * redondeados.
 *
 * <p>Lo mínimo indispensable es <em>un área con personal</em>. Sin
 * colaboradores los listados salen vacíos y no hay forma de comprobar que el
 * filtro de alcance esté acotando lo que debe, que es justamente lo que esta
 * primera prueba viene a verificar.
 */
@Component
public class DatosDeDemostracion implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DatosDeDemostracion.class);

	private final AreaRepository areas;

	private final CargoRepository cargos;

	private final TurnoRepository turnos;

	private final ColaboradorRepository colaboradores;

	private final ColaboradorSalarioRepository salarios;

	private final UsuarioRepository usuarios;

	// Deshabilitado por defecto; se activa con app.datosDemostracion=true
	// solo en el perfil 'dev'. Produccion jamas debe activarlo.
	@Value("${app.datosDemostracion:false}")
	private boolean habilitado;

	public DatosDeDemostracion(AreaRepository areas, CargoRepository cargos, TurnoRepository turnos,
			ColaboradorRepository colaboradores, ColaboradorSalarioRepository salarios,
			UsuarioRepository usuarios) {
		this.areas = areas;
		this.cargos = cargos;
		this.turnos = turnos;
		this.colaboradores = colaboradores;
		this.salarios = salarios;
		this.usuarios = usuarios;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments argumentos) {
		if (!habilitado) {
			return;
		}

		// El catálogo de turnos se siembra aunque la troupe ya exista: es
		// idempotente (busca por código) y la base de pruebas ya tiene
		// colaboradores desde la primera corrida, así que sin esta separación
		// los turnos parametrizados nunca llegarían. La flag de doce horas de
		// TNOCHE la pone la migración V17 sobre lo que ya esté en la base.
		Turno dia = turno("TDIA", "Turno de día", LocalTime.of(7, 0), LocalTime.of(15, 0), false);
		Turno noche = turno("TNOCHE", "Turno de noche", LocalTime.of(19, 0), LocalTime.of(7, 0), true);
		// La jornada real de la operación: un solo turno de mañana, de 7:00 am
		// a las 4 pm, con salida a las 5 pm los lunes y martes (columna de fin
		// especial, no un segundo turno: es la misma jornada con el fin
		// distinto de esos dos días). Sábado hasta las 11 am, y las dos
		// franjas de noche de 19:00 a 23:00 y de 23:00 a 07:00. Las mañanas
		// no son jornada larga: son ocho horas efectivas, las diez de reloj de
		// lunes y martes descuentan el almuerzo, y esa redistribución existe
		// para no trabajar sábados.
		turno("TMAN", "Mañana", LocalTime.of(7, 0), LocalTime.of(16, 0), LocalTime.of(17, 0), false);
		turno("TSAB", "Sábado", LocalTime.of(7, 0), LocalTime.of(11, 0), false);
		turno("TNOCHE_19", "Noche 7:00 pm – 11:00 pm", LocalTime.of(19, 0), LocalTime.of(23, 0), false);
		turno("TNOCHE_23", "Noche 11:00 pm – 7:00 am", LocalTime.of(23, 0), LocalTime.of(7, 0), false);

		if (colaboradores.count() > 0) {
			log.debug("La tabla colaborador ya tiene filas: no se carga la troupe de demostración.");
			return;
		}
		log.warn("La tabla colaborador estaba vacía. Se carga la troupe de demostración. "
				+ "Desactívela con app.datosDemostracion=false antes de usarla en serio.");

		Area operaciones = area("OPE");
		Area aduanas = area("ADU");

		Cargo operador = cargo("Operador de báscula");
		Cargo supervisor = cargo("Supervisor de báscula");
		Cargo almacenista = cargo("Almacenista");
		Cargo coordinador = cargo("Coordinador de operaciones");

		colaborador("CC10000001", "Ana", "Restrepo Marín", operaciones, operador, dia,
				"3200000", LocalDate.of(2019, 3, 11));
		colaborador("CC10000002", "Juan", "Bedoya Salas", operaciones, operador, dia,
				"3200000", LocalDate.of(2021, 7, 5));
		colaborador("CC10000003", "Lucía", "Ferrer Ocampo", operaciones, supervisor, noche,
				"4100000", LocalDate.of(2017, 1, 23));
		colaborador("CC10000004", "Carlos", "Mejía Pardo", operaciones, almacenista, dia,
				"2950000", LocalDate.of(2022, 9, 12));
		colaborador("CC10000005", "Milena", "Arroyave Cruz", aduanas, supervisor, dia,
				"4450000", LocalDate.of(2018, 6, 4));
		colaborador("CC10000006", "Tomás", "Quintero Londoño", aduanas, operador, noche,
				"3350000", LocalDate.of(2020, 11, 30));
		colaborador("CC10000007", "Sara", "Villalobos Ríos", aduanas, almacenista, dia, "2800000",
				LocalDate.of(2023, 2, 13));
		colaborador("CC10000008", "Iván", "Cardona Puerta", operaciones, coordinador, dia, "5200000",
				LocalDate.of(2015, 4, 7));

		log.info("Personal de demostración cargado: {} colaboradores.", colaboradores.count());
	}

	// -----------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------

	private Area area(String codigo) {
		return areas.findByCodigo(codigo)
				.orElseThrow(() -> new IllegalStateException("Falta el área " + codigo + ", que crea la migración V2"));
	}

	private Cargo cargo(String nombre) {
		return cargos.findByNombre(nombre).orElseGet(() -> {
			Cargo nuevo = new Cargo();
			nuevo.setNombre(nombre);
			nuevo.setActivo(true);
			return cargos.save(nuevo);
		});
	}

	private Turno turno(String codigo, String nombre, LocalTime inicio, LocalTime fin, boolean permiteDoceHoras) {
		return turno(codigo, nombre, inicio, fin, null, permiteDoceHoras);
	}

	/** Igual que {@link #turno}, con fin especial de lunes y martes opcional. */
	private Turno turno(String codigo, String nombre, LocalTime inicio, LocalTime fin, LocalTime finLunesMartes,
			boolean permiteDoceHoras) {
		return turnos.findByCodigo(codigo).orElseGet(() -> {
			Turno nuevo = new Turno();
			nuevo.setCodigo(codigo);
			nuevo.setNombre(nombre);
			nuevo.setHoraInicio(inicio);
			nuevo.setHoraFin(fin);
			nuevo.setHoraFinLunesMartes(finLunesMartes);
			nuevo.setPermiteDoceHoras(permiteDoceHoras);
			nuevo.setActivo(true);
			return turnos.save(nuevo);
		});
	}

	private Colaborador colaborador(String documento, String nombres, String apellidos, Area area, Cargo cargo,
			Turno turno, String salario, LocalDate ingreso) {
		Colaborador nuevo = new Colaborador();
		nuevo.setDocumento(documento);
		nuevo.setNombres(nombres);
		nuevo.setApellidos(apellidos);
		nuevo.setArea(area);
		nuevo.setCargo(cargo);
		nuevo.setTurno(turno);
		nuevo.setFechaIngreso(ingreso);
		nuevo.setActivo(true);
		// Celular inventado, no vacío: las notificaciones SMS exigen un
		// número colombiano de diez dígitos y los reportes quieren mostrar el
		// contacto de todos. Un vacío corta el envío y deja la pantalla sin
		// el dato de la persona.
		nuevo.setCelular(celularDe(documento));
		Colaborador guardado = colaboradores.save(nuevo);

		ColaboradorSalario vigente = new ColaboradorSalario();
		vigente.setColaborador(guardado);
		vigente.setSalarioBase(new BigDecimal(salario));
		vigente.setVigenciaDesde(ingreso);
		salarios.save(vigente);
		return guardado;
	}

	/**
	 * El celular inventado de una persona, en formato colombiano de diez
	 * dígitos que empieza por 3 (lo que exige {@code celularEnviado}).
	 *
	 * <p>Se deriva del documento para que sea distinto por persona y siempre
	 * el mismo en cada corrida: "3" más los nueve dígitos del documento con
	 * ceros a la izquierda.
	 */
	private static String celularDe(String documento) {
		String digitos = documento.replaceAll("\\D", "");
		long valor = digitos.isEmpty() ? 0 : Long.parseLong(digitos);
		return "3" + String.format("%09d", valor % 1_000_000_000L);
	}

}
