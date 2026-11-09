package com.mardique.programacion.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.audit.AccionAuditada;
import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.audit.EntidadAuditada;
import com.mardique.programacion.audit.RegistroAuditoria;
import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoOperacion;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.event.TurnoExtendidoAsignadoEvent;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.TipoOperacionRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.security.ActualUsuario;
import com.mardique.programacion.security.UsuarioPrincipal;
import com.mardique.programacion.service.asignacion.EvaluadorReglasAsignacion;
import com.mardique.programacion.service.asignacion.ValidarHorarioRule;
import com.mardique.programacion.service.dto.FichaProgramacion;
import com.mardique.programacion.service.dto.FichasPreparadas;

/**
 * Flujo de una programación de extendido, de la propuesta a la aprobación.
 *
 * <p>Esto es lo que antes no existía: hasta ahora solo había pantallas de
 * consulta, así que un coordinador no tenía forma de proponer nada y el flujo
 * se quedaba en un listado. Aquí está el lado de escritura.
 *
 * <p><strong>El reparto de responsabilidades es deliberado:</strong>
 * <ul>
 * <li>quién puede actuar lo decide la seguridad (anotaciones {@code @PreAuthorize}
 * en el controlador) y el alcance por área ({@link AlcanceAreaService});</li>
 * <li>si el cambio de estado tiene sentido, {@link TransicionEstadoService};</li>
 * <li>el contenido de la decisión, aquí.</li>
 * </ul>
 *
 * <p>Ni el estado ni las transiciones se validan en la entidad a propósito: la
 * entidad guarda, el servicio decide.
 *
 * <p>Cada decisión queda en la bitácora con el valor anterior y el nuevo, y
 * todas las consultas filtran por el alcance del usuario. Un coordinador que
 * escriba el identificador de otra área a mano recibe 403 y su intento queda
 * registrado, no una lista filtrada en silencio.
 */
@Service
public class ProgramacionService {

	private final ProgramacionExtendidoRepository programaciones;

	private final ColaboradorRepository colaboradores;

	private final AreaRepository areas;

	private final UsuarioRepository usuarios;

	private final TipoOperacionRepository tiposOperacion;

	private final AlcanceAreaService alcance;

	private final TransicionEstadoService transiciones;

	private final GrupoAreasService grupos;

	private final AuditoriaService auditoria;

	private final ApplicationEventPublisher eventos;

	private final ActualUsuario actualUsuario;

	private final CandidatoProgramacionService candidatos;

	private final HoraExtraEjecutadaRepository horasEjecutadas;

	private final EvaluadorReglasAsignacion evaluador;

	private final ValidarHorarioRule reglaHorario;

	public ProgramacionService(ProgramacionExtendidoRepository programaciones, ColaboradorRepository colaboradores,
							   AreaRepository areas, UsuarioRepository usuarios, TipoOperacionRepository tiposOperacion,
							   AlcanceAreaService alcance, TransicionEstadoService transiciones, GrupoAreasService grupos,
							   AuditoriaService auditoria, ActualUsuario actualUsuario, ApplicationEventPublisher eventos,
							   CandidatoProgramacionService candidatos, HoraExtraEjecutadaRepository horasEjecutadas,
							   EvaluadorReglasAsignacion evaluador, ValidarHorarioRule reglaHorario) {
		this.programaciones = programaciones;
		this.colaboradores = colaboradores;
		this.areas = areas;
		this.usuarios = usuarios;
		this.tiposOperacion = tiposOperacion;
		this.alcance = alcance;
		this.transiciones = transiciones;
		this.grupos = grupos;
		this.auditoria = auditoria;
		this.actualUsuario = actualUsuario;
		this.eventos = eventos;
		this.candidatos = candidatos;
		this.horasEjecutadas = horasEjecutadas;
		this.evaluador = evaluador;
		this.reglaHorario = reglaHorario;
	}

	// ------------------------------------------------------------------
	// Propuesta del coordinador
	// ------------------------------------------------------------------

	/**
	 * Crea una propuesta en estado {@code BORRADOR}.
	 *
	 * <p>Se valida aquí, y no al enviar, porque un borrador es editable: tiene
	 * sentido que se pueda guardar a medias. Lo que no se permite es mandar a
	 * revisión una propuesta inválida, y eso lo hace {@link #enviar}.
	 *
	 * <p>Las marcas de excepción ({@code requiereNivelacion},
	 * {@code cumpleAptitud}) no se piden: se derivan de la operación, de la fecha y
	 * del área, y las calcula el servicio con el mismo criterio con el que ordena el
	 * listado de candidatos. Si las marcara el formulario, bastaría con no marcar la
	 * casilla para que una excepción quedara sin registrar.
	 *
	 * @throws AccesoFueraDeAlcanceException si el área no le corresponde.
	 */
	@Transactional
	public ProgramacionExtendido crear(PropuestaNueva propuesta) {
		return crear(propuesta, null);
	}

	/**
	 * Lo mismo, con la evaluación ya hecha.
	 *
	 * @param evaluacionPrevia veredicto de requisitos que el lote ya calculó, o
	 *                         {@code null} para calcularlo aquí.
	 */
	private ProgramacionExtendido crear(PropuestaNueva propuesta, EvaluacionCandidato evaluacionPrevia) {
		alcance.verificar(propuesta.areaId());

		// La cadena de validaciones (horario, choque y ausencia, tope semanal)
		// se corre aquí y no solo en el formulario: las columnas de inicio y fin
		// son NOT NULL, así que si alguien llama a este método desde otro sitio
		// sin pasar por la pantalla, el error tiene que ser un mensaje y no una
		// excepción de PostgreSQL.
		Map<String, String> problemas = evaluador.ejecutarValidaciones(propuesta);
		if (!problemas.isEmpty()) {
			throw new PropuestaIncompletaException(problemas);
		}

		Colaborador colaborador = colaboradores.findById(propuesta.colaboradorId())
				.orElseThrow(() -> new NoSuchElementException("El colaborador indicado no existe."));

		// El área se toma de la del colaborador, no de un parámetro libre: si un
		// coordinador pudiera programar personal de otra área, solo tendría que
		// cambiar el campo.
		if (!colaborador.getArea().getId().equals(propuesta.areaId())) {
			alcance.verificar(colaborador.getArea().getId());
			throw new IllegalStateException("El colaborador seleccionado pertenece a otra área.");
		}

		EvaluacionCandidato evaluacion = evaluacionPrevia != null ? evaluacionPrevia
				: candidatos.evaluar(propuesta.areaId(), propuesta.colaboradorId(), propuesta.tipoOperacionId(),
				propuesta.fecha());

		Map<String, String> problemasDeExcepcion = problemasDeExcepcion(propuesta, evaluacion);
		if (!problemasDeExcepcion.isEmpty()) {
			throw new PropuestaIncompletaException(problemasDeExcepcion);
		}

		ProgramacionExtendido programacion = new ProgramacionExtendido();
		programacion.setColaborador(colaborador);
		programacion.setArea(colaborador.getArea());
		programacion.setTipo(propuesta.tipo());
		programacion.setTipoOperacion(resolverTipoOperacion(propuesta.tipoOperacionId()));
		programacion.setInicioProgramado(propuesta.inicio());
		programacion.setFinProgramado(propuesta.fin());
		programacion.setHorasProgramadas(horasEntre(propuesta.inicio(), propuesta.fin()));
		programacion.setMotivo(propuesta.motivo());
		programacion.setEstado(EstadoProgramacion.BORRADOR);
		programacion.setSolicitante(usuarioActual());
		programacion.setRequeriaNivelacion(evaluacion.requiereNivelacion());
		programacion.setIncumpleAptitud(!evaluacion.cumpleAptitud());
		programacion.setMotivoExcepcionCandidato(motivoExcepcionDe(propuesta));
		programacion.setEsEmergencia(propuesta.esEmergencia());
		programacion.setReferenciaEvento(referenciaEventoDe(propuesta));

		ProgramacionExtendido guardada = programaciones.save(programacion);

		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR, EntidadAuditada.PROGRAMACION_EXTENDIDO)
				.entidadId(guardada.getId())
				.nuevo(valores(guardada))
				.motivo("Propuesta creada por el coordinador"));

		return guardada;
	}

	/**
	 * Qué le falta a la propuesta para poder existir, en lo que tiene que ver con la
	 * excepción y la emergencia.
	 *
	 * <p>La regla se lee al revés de como se lee en la pantalla. En la pantalla la
	 * excepción es una excepción: se puede elegir a alguien que no cumple y lo que se
	 * pide es el motivo. En emergencia desaparece, porque el motivo ya está en la
	 * referencia del evento. El choque de horario y la ausencia quedan en la cadena
	 * de reglas de la asignación, que en emergencia también los salta.
	 *
	 * @return campo -> problema. Vacío significa que la propuesta se puede guardar.
	 */
	private Map<String, String> problemasDeExcepcion(PropuestaNueva propuesta, EvaluacionCandidato evaluacion) {
		Map<String, String> problemas = new LinkedHashMap<>();
		if (propuesta.esEmergencia()) {
			if (vacio(propuesta.referenciaEvento())) {
				problemas.put("referenciaEvento", "Indique el evento que originó la emergencia.");
			}
			return problemas;
		}
		if (evaluacion.esExcepcion() && vacio(propuesta.motivoExcepcionCandidato())) {
			problemas.put("motivoExcepcionCandidato", "Explique por qué se eligió a esta persona.");
		}
		return problemas;
	}

	/**
	 * Motivo con el que queda guardada la excepción.
	 *
	 * <p>Solo lo que escribió el coordinador. En emergencia no se rellena nada:
	 * el evento que originó la situación ya queda en {@code referencia_evento}, es
	 * obligatorio, y con eso queda documentada la excepción. Antes había que
	 * inventar un "Emergencia: ..." solo porque el CHECK de la base lo pedía, y
	 * ese texto repetía el evento que la pantalla de revisión ya muestra aparte.
	 *
	 * @return el motivo escrito, o {@code null} si no lo hay.
	 */
	private static String motivoExcepcionDe(PropuestaNueva propuesta) {
		return vacio(propuesta.motivoExcepcionCandidato()) ? null
				: propuesta.motivoExcepcionCandidato().trim();
	}

	/** Referencia del evento, o {@code null} si no es una propuesta de emergencia. */
	private static String referenciaEventoDe(PropuestaNueva propuesta) {
		if (!propuesta.esEmergencia() || vacio(propuesta.referenciaEvento())) {
			return null;
		}
		return propuesta.referenciaEvento().trim();
	}

	private static boolean vacio(String texto) {
		return texto == null || texto.isBlank();
	}

	/**
	 * Crea varias propuestas en borrador con un solo envío.
	 *
	 * <p><strong>Es todo o nada.</strong> Si la tercera de seis fichas tiene un
	 * horario imposible, no se crea ninguna: dejar cinco guardadas y una
	 * rechazada obligaría al coordinador a discover cuáles se guardaron, y lo
	 * natural es que las seis volverse a escribir. La transacción cubre el
	 * lote entero, así que la excepción que sale de aquí deshace lo anterior.
	 *
	 * <p><strong>Se valida todo antes de crear la primera.</strong> No se
	 * acumula el error en el momento de insertar, porque en ese punto la
	 * transacción ya está a medias y el índice que necesita el mensaje sería el
	 * de inserción, no el de la tarjeta. Validando antes, el índice es el que
	 * tiene la ficha en pantalla y el mensaje cae donde el usuario está mirando.
	 *
	 * <p>El alcance se verifica en una pasada aparte, y aquí sí se deja que la
	 * excepción suba. Un área ajena no es un error de formulario: es un intento
	 * fuera de alcance, y tiene que quedar en la bitácora con su 403, no
	 * convertirse en un "corrija el área" que ni siquiera es del usuario. Como
	 * ocurre antes de escribir, no deja nada a medias.
	 */
	@Transactional
	public List<ProgramacionExtendido> crearLote(List<PropuestaNueva> propuestas) {
		if (propuestas == null || propuestas.isEmpty()) {
			throw new LoteIncompletoException(
					Map.of(0, Map.of("colaboradorId", "Agregue al menos una propuesta.")));
		}

		// 1. Alcance de cada propuesta, antes de tocar nada.
		for (PropuestaNueva propuesta : propuestas) {
			if (propuesta.areaId() != null) {
				alcance.verificar(propuesta.areaId());
			}
		}

		// 2. Problemas de horario, con el índice de la tarjeta.
		Map<Integer, Map<String, String>> problemas = new LinkedHashMap<>();
		for (int indice = 0; indice < propuestas.size(); indice++) {
			PropuestaNueva propuesta = propuestas.get(indice);
			Map<String, String> deEsta = reglaHorario.validarHorario(propuesta.inicio(), propuesta.fin());
			if (!deEsta.isEmpty()) {
				problemas.put(indice, deEsta);
			}
		}

		// 3. La misma persona dos veces en el mismo envío. Va aquí y no en el
		// formulario porque es una regla del envío, no de una ficha: sola es
		// válida, y solo choca al estar junto a la otra.
		marcarRepetidos(propuestas, problemas);

		if (!problemas.isEmpty()) {
			throw new LoteIncompletoException(problemas);
		}

		// 4. Evaluación de requisitos, agrupada. Las propuestas del mismo área, operación y
		// fecha se resuelven juntas, porque el cálculo necesita el acumulado de todo
		// el área: cien fichas de un lote no son cien cálculos.
		Map<ClaveEvaluacion, Map<Long, EvaluacionCandidato>> evaluaciones = evaluarLote(propuestas);

		// 5. Creación de todas, una por una. Si algo revienta, la excepción sale
		// del método y la transacción deshace el lote completo.
		List<ProgramacionExtendido> creadas = new ArrayList<>();
		for (int indice = 0; indice < propuestas.size(); indice++) {
			PropuestaNueva propuesta = propuestas.get(indice);
			try {
				Map<Long, EvaluacionCandidato> delGrupo = evaluaciones.get(claveDe(propuesta));
				creadas.add(crear(propuesta, delGrupo == null ? null : delGrupo.get(propuesta.colaboradorId())));
			} catch (PropuestaIncompletaException excepcion) {
				// Los problemas vienen por nombre de campo, así que caen en el input
				// concreto de la tarjeta: el motivo de la excepción en una, la persona
				// en otra. Relanza igual, porque el lote es todo o nada.
				problemas.put(indice, excepcion.problemas());
				throw new LoteIncompletoException(problemas);
			} catch (NoSuchElementException | IllegalStateException excepcion) {
				// Se anotan por ficha para que el error caiga en su tarjeta, pero
				// la excepción se relanza al final y deshace el lote.
				problemas.put(indice, Map.of("colaboradorId", excepcion.getMessage()));
				throw new LoteIncompletoException(problemas);
			}
		}

		return creadas;
	}

	/**
	 * Veredictos de todas las fichas del lote, en una consulta por área, operación y
	 * fecha.
	 *
	 * <p>Se calcula antes de crear nada, y no durante la creación, porque el cálculo
	 * necesita el acumulado del área y el área se arma con las propuestas que ya
	 * existen en la base, no con las del mismo envío.
	 */
	private Map<ClaveEvaluacion, Map<Long, EvaluacionCandidato>> evaluarLote(List<PropuestaNueva> propuestas) {
		Map<ClaveEvaluacion, List<Long>> porClave = new LinkedHashMap<>();
		for (PropuestaNueva p : propuestas) {
			if (p.areaId() == null || p.colaboradorId() == null || p.fecha() == null) {
				continue;
			}
			porClave.computeIfAbsent(claveDe(p), clave -> new ArrayList<>()).add(p.colaboradorId());
		}

		Map<ClaveEvaluacion, Map<Long, EvaluacionCandidato>> evaluaciones = new LinkedHashMap<>();
		porClave.forEach((clave, personas) -> evaluaciones.put(clave,
				candidatos.evaluarVarios(clave.areaId(), clave.tipoOperacionId(), clave.fecha(), personas)));
		return evaluaciones;
	}

	private static ClaveEvaluacion claveDe(PropuestaNueva p) {
		return new ClaveEvaluacion(p.areaId(), p.tipoOperacionId(), p.fecha());
	}

	/**
	 * Lo que hace que dos fichas compartan cálculo.
	 *
	 * <p>El área, la operación y la fecha son los tres datos de los que depende la
	 * nivelación. Si mañana el cálculo dependiera de otra cosa, esta clase es donde
	 * hay que añadirla: dejar la clave incompleta haría que dos fichas que deberían
	 * compararse evaluaran solas y no se detectara.
	 */
	private record ClaveEvaluacion(Long areaId, Long tipoOperacionId, LocalDate fecha) {
	}

	/**
	 * Marca la segunda ficha en adelante cuando una persona ya salió en el mismo
	 * envío para la misma fecha.
	 *
	 * <p>Se marca la repetida, no la primera: la primera es una propuesta
	 * legítima y quitarla sería tirar trabajo del usuario. La clave es persona y
	 * fecha, no solo persona, porque programar a alguien dos veces en el mismo
	 * día con horarios distintos es válido.
	 */
	private void marcarRepetidos(List<PropuestaNueva> propuestas, Map<Integer, Map<String, String>> problemas) {
		Set<String> vistas = new LinkedHashSet<>();
		for (int indice = 0; indice < propuestas.size(); indice++) {
			PropuestaNueva p = propuestas.get(indice);
			if (p.colaboradorId() == null || p.fecha() == null) {
				continue;
			}
			String clave = p.colaboradorId() + "@" + p.fecha();
			if (!vistas.add(clave)) {
				problemas.put(indice, Map.of("colaboradorId",
						"Esta persona ya está en otra propuesta de este mismo envío para la misma fecha."));
			}
		}
	}

	// ------------------------------------------------------------------
	// Atajos del formulario: repetir un día, y la selección masiva
	// ------------------------------------------------------------------

	/**
	 * Estados desde los que se puede repetir un día a otro.
	 *
	 * <p>Borradores, enviados y en revisión quedan fuera: repetir lo que todavía
	 * no está decidido duplicaría la cola de TH. Rechazadas y canceladas tampoco:
	 * repetir un no sería traer de vuelta lo que se descartó. Cerradas se dejan
	 * fuera porque el día ya se liquó y reabrirlo por atajo enmascararía el corte
	 * de nómina.
	 */
	private static final Set<EstadoProgramacion> REPETIBLES = EnumSet.of(EstadoProgramacion.APROBADA,
			EstadoProgramacion.AJUSTADA, EstadoProgramacion.NOTIFICADA, EstadoProgramacion.EJECUTADA);

	/**
	 * Estados que ya no ocupan el horario. Las consultas de choque lo reciben como
	 * {@code estadosExcluidos} y descartan lo que esté ahí con {@code not in}, así
	 * que aquí van solo los que se ignoran: pasar el conjunto contrario (todo salvo
	 * rechazada y cancelada) haría que la consulta viera <em>únicamente</em> las
	 * rechazadas y canceladas, justo lo que no debe bloquear.
	 */
	private static final Set<EstadoProgramacion> ESTADOS_EXCLUIDOS_DE_CHOQUE = EnumSet
			.of(EstadoProgramacion.RECHAZADA, EstadoProgramacion.CANCELADA);

	/**
	 * Copia las fichas de un día a otro, para repetir la programación sin
	 * redactarla de nuevo.
	 *
	 * <p>Se copia el turno <em>efectivo</em>, es decir lo que realmente se
	 * trabajó (con el ajuste de TH si lo hubo), porque repetir un día es repetir
	 * lo que pasó y no lo que en su momento se propuso.
	 *
	 * <p>Se saltan las personas que ya tengan programación en el día destino y
	 * las que quedaron fuera de su área: preparar cien fichas de las que cinco
	 * van a rechazarse al enviar obliga a descubrir cuáles son.
	 */
	@Transactional(readOnly = true)
	public FichasPreparadas fichasRepitiendo(LocalDate origen, LocalDate destino) {
		List<ProgramacionExtendido> base = programaciones.findPorInicioEfectivoEnEstados(REPETIBLES,
				origen.atStartOfDay(), origen.plusDays(1).atStartOfDay());

		Set<Long> yaProgramados = new LinkedHashSet<>();
		if (!base.isEmpty()) {
			List<Long> gente = base.stream().map(p -> p.getColaborador().getId()).distinct().toList();
			LocalDateTime diaInicio = destino.atStartOfDay();
			LocalDateTime diaFin = destino.plusDays(1).atStartOfDay();
			programaciones.findChoqueDeHorarioEfectivo(gente, diaInicio, diaFin, ESTADOS_EXCLUIDOS_DE_CHOQUE).stream()
					// Solo cuenta lo que <em>empieza</em> el día destino: la cola de la noche
					// anterior que cruza la medianoche no debe bloquear repetir hacia ese día,
					// o el atajo no serviría para los turnos que cruzan.
					.filter(choque -> !choque.getInicioEfectivo().isBefore(diaInicio)
							&& choque.getInicioEfectivo().isBefore(diaFin))
					.forEach(choque -> yaProgramados.add(choque.getColaborador().getId()));
		}

		List<FichaProgramacion> fichas = new ArrayList<>();
		int total = 0;
		for (ProgramacionExtendido original : base) {
			Colaborador persona = original.getColaborador();
			if (persona == null || !persona.isActivo() || !alcance.puedeVer(original.getArea().getId())) {
				continue;
			}
			if (yaProgramados.contains(persona.getId())) {
				continue;
			}
			total++;
			if (fichas.size() >= FichasPreparadas.LIMITE) {
				continue;
			}
			fichas.add(fichaRepetida(original, persona, destino));
		}
		return FichasPreparadas.conTope(fichas, total);
	}

	private static FichaProgramacion fichaRepetida(ProgramacionExtendido original, Colaborador persona,
												   LocalDate destino) {
		LocalDateTime inicio = original.getInicioEfectivo();
		LocalDateTime fin = original.getFinEfectivo();

		FichaProgramacion ficha = new FichaProgramacion();
		ficha.setAreaId(original.getArea().getId());
		ficha.setColaboradorId(persona.getId());
		ficha.setFecha(destino);
		ficha.setHoraInicio(inicio.toLocalTime());
		ficha.setHoraFin(fin.toLocalTime());
		long dias = ChronoUnit.DAYS.between(inicio.toLocalDate(), fin.toLocalDate());
		if (dias > 0) {
			ficha.setFechaFin(destino.plusDays(dias));
		}
		ficha.setTipo(original.getTipo());
		ficha.setTipoOperacionId(original.getTipoOperacion() != null ? original.getTipoOperacion().getId() : null);
		String motivo = "Repetido del " + inicio.toLocalDate()
				+ (original.getMotivo() == null || original.getMotivo().isBlank() ? "" : ": " + original.getMotivo());
		ficha.setMotivo(motivo.length() > 500 ? motivo.substring(0, 497) + "..." : motivo);
		return ficha;
	}

	/**
	 * Fichas para la selección masiva: varias personas con la misma fecha y
	 * las mismas horas, preparadas de una sola vez.
	 *
	 * <p>El coordinador marca a quiénes le va a programar y define el tramo
	 * una sola vez; el formulario queda con una ficha por persona ya
	 * rellenada, en vez de abrirla y teclearla por separado. El alcance se
	 * verifica aquí, no en la pantalla: los identificadores llegan en el
	 * envío y un id de otra área es un intento fuera de alcance, no una
	 * selección silenciosamente filtrada.
	 *
	 * <p>Las inactivas o sin área se dejan fuera sin más: la selección no las
	 * ofrece y llevarlas a la cola de TH no ayuda a nadie.
	 *
	 * @param colaboradorIds personas elegidas; se ignoran duplicados.
	 * @param fecha          fecha de la jornada.
	 * @param horaInicio     hora de inicio del tramo, igual para todas.
	 * @param horaFin        hora de fin del tramo; si no queda después del
	 *                       inicio se asume que cruza la medianoche.
	 * @param motivo         motivo compartido; vacío, uno por defecto.
	 * @return las fichas listas y la nota de truncamiento si se pasaron del tope.
	 */
	@Transactional(readOnly = true)
	public FichasPreparadas fichasSeleccionadas(List<Long> colaboradorIds, LocalDate fecha, LocalTime horaInicio,
												LocalTime horaFin, String motivo) {
		return fichasSeleccionadas(colaboradorIds, fecha, fecha, horaInicio, horaFin, motivo);
	}

	/**
	 * Lo mismo, pero con una ficha por persona y por día de un tramo: las
	 * horas y el motivo son iguales en todo el rango y solo cambia la fecha
	 * de cada ficha, que es lo único que cambia de un día a otro en la
	 * programación en bloque.
	 *
	 * <p>El motivo por defecto nombra la fecha de su propia ficha, no la del
	 * inicio: cuando el lote se revisa ficha por ficha, "Programación en
	 * bloque del 2026-10-13" al lado de una ficha del 2026-10-17 es peor que
	 * no decir nada.
	 *
	 * @param desde primer día, incluido; validado en la pantalla.
	 * @param hasta último día, incluido; validado en la pantalla.
	 */
	@Transactional(readOnly = true)
	public FichasPreparadas fichasSeleccionadas(List<Long> colaboradorIds, LocalDate desde, LocalDate hasta,
												LocalTime horaInicio, LocalTime horaFin, String motivo) {
		Set<Long> pedidos = new LinkedHashSet<>(colaboradorIds);
		List<FichaProgramacion> fichas = new ArrayList<>();
		int personas = 0;
		for (Colaborador persona : colaboradores.findAllById(pedidos)) {
			if (persona.getArea() == null) {
				continue;
			}
			alcance.verificar(persona.getArea().getId());
			if (!persona.isActivo()) {
				continue;
			}
			personas++;
			for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
				if (fichas.size() >= FichasPreparadas.LIMITE) {
					break;
				}
				FichaProgramacion ficha = new FichaProgramacion();
				ficha.setAreaId(persona.getArea().getId());
				ficha.setColaboradorId(persona.getId());
				ficha.setFecha(fecha);
				ficha.setHoraInicio(horaInicio);
				ficha.setHoraFin(horaFin);
				// Igual que los otros atajos: el tipo no se adivina, quien arma el
				// lote lo cambia en cada ficha si le toca.
				ficha.setTipo(TipoProgramacion.EXTENDIDO);
				ficha.setMotivo(vacio(motivo) ? "Programación en bloque del " + fecha : motivo.trim());
				fichas.add(ficha);
			}
		}
		long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
		long total = (long) personas * dias;
		return FichasPreparadas.conTope(fichas, total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total);
	}

	/**
	 * Agrega la cola del mismo día: el tramo que sigue al final del turno, en
	 * la misma jornada, hasta la hora que pide el coordinador.
	 *
	 * <p>Antes este atajo saltaba al día siguiente con la misma duración, y el
	 * coordinador quedaba con una propuesta que no era la que quería y con dos
	 * toques para corregirla. Ahora la cola arranca donde terminó la propuesta
	 * original (el fin efectivo, con el ajuste de TH si lo hubo) y termina en
	 * la hora que se le pida: programación de 7:00 a 15:00 con cola "hasta las
	 * 17:00" queda como 15:00 → 17:00 del mismo día.
	 *
	 * <p>Pasa por {@link #crear} entero: alcance, horario, disponibilidad y
	 * evaluación se revisan igual que en cualquier otra propuesta. El atajo
	 * salta el teclado, no las reglas.
	 *
	 * @param hasta hora de fin de la cola; si no queda después del inicio, se
	 *              asume que cruza la medianoche (como en el formulario).
	 * @throws IllegalStateException           si el estado no permite extender o
	 *                                         si la persona ya está ocupada en el
	 *                                         tramo nuevo.
	 * @throws PropuestaIncompletaException    si los requisitos de la operación
	 *                                         piden un motivo de excepción.
	 */
	@Transactional
	public ProgramacionExtendido extender(Long programacionId, LocalTime hasta) {
		ProgramacionExtendido original = programaciones.findConRelacionesById(programacionId)
				.orElseThrow(() -> new NoSuchElementException("La programación no existe."));
		alcance.verificar(original.getArea().getId());
		if (!REPETIBLES.contains(original.getEstado())) {
			throw new IllegalStateException("Solo se extienden turnos aprobados, ajustados, notificados o ejecutados.");
		}

		LocalDateTime inicio = original.getFinEfectivo();
		LocalDateTime fin = inicio.toLocalDate().atTime(hasta);
		if (!fin.isAfter(inicio)) {
			fin = fin.plusDays(1);
		}

		List<ProgramacionExtendido> choques = programaciones.findChoqueDeHorarioEfectivo(
				List.of(original.getColaborador().getId()), inicio, fin, ESTADOS_EXCLUIDOS_DE_CHOQUE);
		boolean ocupada = choques.stream().anyMatch(otra -> !otra.getId().equals(programacionId));
		if (ocupada) {
			throw new IllegalStateException("La persona ya tiene una programación que empieza el "
					+ inicio.toLocalDate() + " a las " + inicio.toLocalTime() + ".");
		}

		String motivo = "Extensión automática del turno del " + original.getInicioEfectivo().toLocalDate()
				+ (original.getMotivo() == null || original.getMotivo().isBlank() ? "" : ": " + original.getMotivo());
		PropuestaNueva propuesta = new PropuestaNueva(original.getColaborador().getId(), original.getArea().getId(),
				original.getTipo(),
				original.getTipoOperacion() != null ? original.getTipoOperacion().getId() : null,
				inicio, fin, motivo.length() > 500 ? motivo.substring(0, 497) + "..." : motivo, false, null, null);
		return crear(propuesta);
	}

	/**
	 * Manda la propuesta a revisión de Talento Humano.
	 *
	 * <p>Valida antes de cambiar el estado, porque después de enviar ya no se
	 * puede corregir sin volver atrás: el aviso al personal y el excel de salidas
	 * se apoyan en estos datos.
	 *
	 * @throws IllegalStateException si la propuesta no es válida todavía.
	 * @throws TransicionNoValidaException si el estado actual no admite el envío.
	 */
	@Transactional
	public ProgramacionExtendido enviar(Long programacionId) {
		ProgramacionExtendido programacion = cargarConAlcance(programacionId);
		transiciones.exigir(programacion.getEstado(), EstadoProgramacion.ENVIADA);

		Map<String, String> problemas = validar(programacion);
		if (!problemas.isEmpty()) {
			throw new PropuestaIncompletaException(problemas);
		}

		EstadoProgramacion anterior = programacion.getEstado();
		programacion.setEstado(EstadoProgramacion.ENVIADA);
		programaciones.save(programacion);

		auditarCambioEstado(programacion, anterior, EstadoProgramacion.ENVIADA, "Propuesta enviada a revisión");
		return programacion;
	}

	// ------------------------------------------------------------------
	// Decisión de Talento Humano
	// ------------------------------------------------------------------

	/**
	 * Aprueba la propuesta tal como la propuso el coordinador.
	 *
	 * @param comentario observación de TH, opcional si no hay nada que decir.
	 */
	@Transactional
	public ProgramacionExtendido aprobar(Long programacionId, String comentario) {
		ProgramacionExtendido programacion = cargarParaDecision(programacionId);
		transiciones.exigir(programacion.getEstado(), EstadoProgramacion.APROBADA);

		EstadoProgramacion anterior = programacion.getEstado();
		programacion.setEstado(EstadoProgramacion.APROBADA);
		programacion.setRevisor(usuarioActual());
		programacion.setComentarioTh(comentario);
		programaciones.save(programacion);

		auditarCambioEstado(programacion, anterior, EstadoProgramacion.APROBADA,
				comentario == null || comentario.isBlank() ? "Aprobada por Talento Humano" : comentario);
		return programacion;
	}

	/**
	 * Aprueba con cambios: TH corrige las horas o el horario.
	 *
	 * <p><strong>No se pisa lo que propuso el coordinador.</strong> Lo original se
	 * queda en {@code inicio_programado} y {@code fin_programado}; la corrección
	 * va en los campos ajustados, con su motivo. Así siempre se puede comparar lo
	 * que se pidió con lo que se aprobó, que es justo lo que se necesita cuando
	 * alguien pregunta por qué una persona salió antes.
	 *
	 * @param inicio  inicio corregido.
	 * @param fin     fin corregido.
	 * @param motivo  por qué se corrige. Obligatorio: sin motivo un ajuste no se
	 *                puede defender después.
	 * @throws IllegalStateException si el motivo viene vacío.
	 */
	@Transactional
	public ProgramacionExtendido ajustar(Long programacionId, LocalDateTime inicio, LocalDateTime fin, String motivo,
										 String comentario) {
		if (motivo == null || motivo.isBlank()) {
			throw new IllegalStateException("El motivo del ajuste es obligatorio.");
		}

		ProgramacionExtendido programacion = cargarParaDecision(programacionId);
		transiciones.exigir(programacion.getEstado(), EstadoProgramacion.AJUSTADA);

		EstadoProgramacion anterior = programacion.getEstado();
		Map<String, Object> antes = valores(programacion);

		programacion.setInicioAjustado(inicio);
		programacion.setFinAjustado(fin);
		programacion.setHorasAjustadas(horasEntre(inicio, fin));
		programacion.setMotivoAjuste(motivo);
		programacion.setEstado(EstadoProgramacion.AJUSTADA);
		programacion.setRevisor(usuarioActual());
		programacion.setComentarioTh(comentario);
		programaciones.save(programacion);

		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.AJUSTAR, EntidadAuditada.PROGRAMACION_EXTENDIDO)
				.entidadId(programacion.getId())
				.anterior(antes)
				.nuevo(valores(programacion))
				.motivo(motivo));

		return programacion;
	}

	/** Pasa una aprobacion a notificada y deja el SMS en cola, sin enviarlo dentro de la decision. */
	@Transactional
	public ProgramacionExtendido notificar(Long programacionId) {
		ProgramacionExtendido programacion = cargarConAlcance(programacionId);
		if (!EnumSet.of(EstadoProgramacion.APROBADA, EstadoProgramacion.AJUSTADA)
				.contains(programacion.getEstado())) {
			throw new TransicionNoValidaException(programacion.getEstado(), EstadoProgramacion.NOTIFICADA,
					transiciones.siguientes(programacion.getEstado()));
		}
		EstadoProgramacion anterior = programacion.getEstado();
		transiciones.exigir(anterior, EstadoProgramacion.NOTIFICADA);
		programacion.setEstado(EstadoProgramacion.NOTIFICADA);
		programaciones.save(programacion);
		// Si ya tiene ruta asignada, el aviso sale con el vehículo; si no, la
		// confirmación de siempre. Los dos quedan en cola, sin enviar acá.
		eventos.publishEvent(new TurnoExtendidoAsignadoEvent(programacion));
		auditarCambioEstado(programacion, anterior, EstadoProgramacion.NOTIFICADA,
				"Programacion notificada; SMS dejado en cola");
		return programacion;
	}

	/**
	 * Marca la programación como ejecutada.
	 *
	 * <p>No es un clic suelto: llega junto con las horas reales confirmadas en la
	 * pantalla de horas extra, que es quien registra además lo que la persona
	 * trabajó. Por eso el motivo de la bitácora habla de ejecución confirmada.
	 */
	@Transactional
	public ProgramacionExtendido ejecutar(Long programacionId) {
		ProgramacionExtendido programacion = cargarConAlcance(programacionId);
		transiciones.exigir(programacion.getEstado(), EstadoProgramacion.EJECUTADA);
		EstadoProgramacion anterior = programacion.getEstado();
		programacion.setEstado(EstadoProgramacion.EJECUTADA);
		programaciones.save(programacion);
		auditarCambioEstado(programacion, anterior, EstadoProgramacion.EJECUTADA,
				"Ejecucion confirmada con las horas reales");
		return programacion;
	}

	/**
	 * Cierra la programación ejecutada: la última palabra de Talento Humano.
	 *
	 * <p>Después de cerrar el registro es histórico y no se reabre. Exige que las
	 * horas reales estén confirmadas, porque cerrar un turno sin ellas deja una
	 * fila que la nómina y la nivelación no pueden contar, y el día después
	 * nadie recuerda por qué quedó vacía.
	 */
	@Transactional
	public ProgramacionExtendido cerrar(Long programacionId) {
		ProgramacionExtendido programacion = cargarConAlcance(programacionId);
		transiciones.exigir(programacion.getEstado(), EstadoProgramacion.CERRADA);
		if (!horasEjecutadas.existsByProgramacionId(programacion.getId())) {
			throw new IllegalStateException("Confirme las horas ejecutadas antes de cerrar la programación.");
		}
		EstadoProgramacion anterior = programacion.getEstado();
		programacion.setEstado(EstadoProgramacion.CERRADA);
		programaciones.save(programacion);
		auditarCambioEstado(programacion, anterior, EstadoProgramacion.CERRADA, "Programacion cerrada");
		return programacion;
	}

	/**
	 * Rechaza la propuesta. El motivo es obligatorio: el coordinador tiene que
	 * saber qué corregir.
	 */
	@Transactional
	public ProgramacionExtendido rechazar(Long programacionId, String motivo) {
		if (motivo == null || motivo.isBlank()) {
			throw new IllegalStateException("El motivo del rechazo es obligatorio.");
		}

		ProgramacionExtendido programacion = cargarParaDecision(programacionId);
		transiciones.exigir(programacion.getEstado(), EstadoProgramacion.RECHAZADA);

		EstadoProgramacion anterior = programacion.getEstado();
		programacion.setEstado(EstadoProgramacion.RECHAZADA);
		programacion.setRevisor(usuarioActual());
		programacion.setComentarioTh(motivo);
		programaciones.save(programacion);

		auditarCambioEstado(programacion, anterior, EstadoProgramacion.RECHAZADA, motivo);
		return programacion;
	}

	/**
	 * Aprueba en bloque todo lo pendiente de un día en un grupo de áreas.
	 *
	 * <p>Existe porque aprobar el 30 de septiembre son veinte filas, no veinte
	 * decisiones independientes: si todas están bien, la decisión es una sola.
	 *
	 * <p><strong>Lo que este método no hace:</strong>
	 * <ul>
	 * <li>No confía en una lista de identificadores que venga del navegador. Se
	 * le da el día y la etiqueta del grupo, y se vuelve a consultar. Si el
	 * formulario se manipuló, el conjunto aprobado se recalcula igual.</li>
	 * <li>No salta validaciones. Cada fila pasa por
	 * {@link #aprobar(Long, String)}, que vuelve a comprobar alcance y
	 * transición.</li>
	 * <li>No amplía el alcance. El grupo se intersecta con las áreas visibles del
	 * usuario antes de buscar nada.</li>
	 * </ul>
	 *
	 * <p>Una fila que ya no está en revisión se salta y se cuenta como omitida en
	 * lugar de tumbar el lote entero: durante una revisión de veinte personas es
	 * normal que alguien se adelante, y perder las otras diecinueve aprobaciones
	 * por eso sería peor que el problema.
	 *
	 * @param dia       día a aprobar.
	 * @param etiqueta  etiqueta de agrupación, por ejemplo {@code Puerto}.
	 * @param comentario observación que queda en todas las aprobaciones del lote.
	 * @return recuento de aprobadas y omitidas, con el detalle de las últimas.
	 */
	@Transactional
	public AprobacionGrupo aprobarGrupo(LocalDate dia, String etiqueta, String comentario) {
		Set<Long> visibles = alcance.areasVisibles(grupos.areasDeEtiqueta(etiqueta));
		if (visibles.isEmpty()) {
			return AprobacionGrupo.vacio(dia, etiqueta);
		}

		LocalDateTime desde = dia.atStartOfDay();
		LocalDateTime hasta = dia.plusDays(1).atStartOfDay();

		List<ProgramacionExtendido> candidatas = programaciones
				.findByAreaIdInAndEstadoInAndInicioProgramadoGreaterThanEqualAndInicioProgramadoLessThanOrderByInicioProgramadoAsc(
						visibles, ordenDeRevision(), desde, hasta);

		int aprobadas = 0;
		List<String> omitidas = new ArrayList<>();

		for (ProgramacionExtendido candidata : candidatas) {
			try {
				aprobar(candidata.getId(), comentario);
				aprobadas++;
			} catch (TransicionNoValidaException excepcion) {
				omitidas.add(candidata.getColaborador().getNombreCompleto() + " ya está en " + excepcion.origen());
			}
		}

		return new AprobacionGrupo(dia, etiqueta, aprobadas, omitidas.size(), List.copyOf(omitidas));
	}

	// ------------------------------------------------------------------
	// Consultas de apoyo a las pantallas
	// ------------------------------------------------------------------
	/**
	 * Propuestas que esperan decisión de TH, acotadas al alcance del usuario.
	 *
	 * @return programaciones en {@code ENVIADA} o {@code EN_REVISION_TH}, de la
	 *         más próxima a la más lejana.
	 */
	@Transactional(readOnly = true)
	public List<ProgramacionExtendido> pendientesDeDecision() {
		UsuarioPrincipal principal = actualUsuario.principal();
		if (principal.tieneAlcanceUniversal()) {
			return programaciones.findByEstadoInOrderByInicioProgramadoAsc(ordenDeRevision());
		}
		if (principal.areaIds().isEmpty()) {
			return List.of();
		}
		return programaciones.findByEstadoInAndAreaIdInOrderByInicioProgramadoAsc(ordenDeRevision(),
				principal.areaIds());
	}

	/**
	 * Programaciones de un día, ya acotadas al alcance, para agrupar en la
	 * pantalla de revisión.
	 *
	 * @param dia día a traer, en la zona horaria del servidor.
	 */
	@Transactional(readOnly = true)
	public List<ProgramacionExtendido> delDia(LocalDate dia) {
		LocalDateTime desde = dia.atStartOfDay();
		LocalDateTime hasta = dia.plusDays(1).atStartOfDay();

		UsuarioPrincipal principal = actualUsuario.principal();
		if (principal.tieneAlcanceUniversal()) {
			return programaciones.findByAreaIdInAndInicioProgramadoGreaterThanEqualAndInicioProgramadoLessThanOrderByInicioProgramadoAsc(
					areaIdsVisibles(), desde, hasta);
		}
		if (principal.areaIds().isEmpty()) {
			return List.of();
		}
		return programaciones.findByAreaIdInAndInicioProgramadoGreaterThanEqualAndInicioProgramadoLessThanOrderByInicioProgramadoAsc(
				principal.areaIds(), desde, hasta);
	}

	/** @return estados en los que la propuesta está esperando a TH. */
	public List<EstadoProgramacion> estadosEnRevision() {
		return ordenDeRevision();
	}

	/**
	 * Carga una programación verificando el alcance del usuario.
	 *
	 * @throws NoSuchElementException si no existe.
	 * @throws AccesoFueraDeAlcanceException si es de un área ajena; el intento
	 *                                        queda auditado.
	 */
	@Transactional(readOnly = true)
	public ProgramacionExtendido obtener(Long programacionId) {
		return cargarConAlcance(programacionId);
	}

	/**
	 * Qué le falta a una propuesta para poder mandarse a revisión.
	 *
	 * @return mapa de campo -> problema. Vacío significa que está lista.
	 */
	public Map<String, String> validar(ProgramacionExtendido programacion) {
		Map<String, String> problemas = new HashMap<>();
		if (programacion.getColaborador() == null) {
			problemas.put("colaborador", "Falta elegir a la persona.");
		}
		if (programacion.getTipo() == null) {
			problemas.put("tipo", "Falta indicar el tipo de programación.");
		}
		if (programacion.getInicioProgramado() == null) {
			problemas.put("inicio", "Falta la hora de inicio.");
		}
		if (programacion.getFinProgramado() == null) {
			problemas.put("fin", "Falta la hora de fin.");
		}
		if (programacion.getInicioProgramado() != null && programacion.getFinProgramado() != null
				&& !programacion.getFinProgramado().isAfter(programacion.getInicioProgramado())) {
			problemas.put("fin", "La hora de fin debe ser posterior a la de inicio.");
		}
		if (programacion.getMotivo() == null || programacion.getMotivo().isBlank()) {
			problemas.put("motivo", "Falta explicar por qué se programa el extendido.");
		}
		return problemas;
	}

	// ------------------------------------------------------------------
	// Internos
	// ------------------------------------------------------------------

	/**
	 * Carga y verifica alcance. Todo acceso por identificador pasa por aquí: es
	 * lo que impide que un coordinador llegue a la programación de otra área
	 * escribiendo el id en la barra de direcciones.
	 *
	 * <p>Usa el finder con grafo de entidades porque {@code open-in-view} está
	 * apagado: lo que se devuelva tiene que venir completo o la vista no podrá
	 * leer ni el nombre de la persona.
	 */
	private ProgramacionExtendido cargarConAlcance(Long programacionId) {
		ProgramacionExtendido programacion = programaciones.findConRelacionesById(programacionId)
				.orElseThrow(() -> new NoSuchElementException("La programación indicada no existe."));
		alcance.verificar(programacion.getArea().getId());
		return programacion;
	}

	/**
	 * Igual que {@link #cargarConAlcance}, pero además exige que la propuesta
	 * esté esperando decisión. Evita mostrar botones de aprobar sobre algo que
	 * ya se aprobó.
	 */
	private ProgramacionExtendido cargarParaDecision(Long programacionId) {
		ProgramacionExtendido programacion = cargarConAlcance(programacionId);
		if (!ordenDeRevision().contains(programacion.getEstado())) {
			throw new TransicionNoValidaException(programacion.getEstado(), EstadoProgramacion.APROBADA,
					transiciones.siguientes(programacion.getEstado()));
		}
		return programacion;
	}

	private List<EstadoProgramacion> ordenDeRevision() {
		return List.of(EstadoProgramacion.ENVIADA, EstadoProgramacion.EN_REVISION_TH);
	}

	/** Todas las áreas activas, para el alcance universal. */
	private Set<Long> areaIdsVisibles() {
		return new LinkedHashSet<>(areas.findByActivaTrueOrderByNombreAsc().stream().map(Area::getId).toList());
	}

	/**
	 * Usuario conectado, cargado de verdad desde la base.
	 *
	 * <p>Se consulta y no se arma un {@code Usuario} en memoria con el id del
	 * principal: la asociación {@code solicitante} apunta a una fila real, y si
	 * alguien abre la programación dentro de otra sesión, Hibernate espera
	 * encontrar los mismos datos. Un objeto temporal además dejaría la fila en
	 * blanco para el resto de la aplicación.
	 */
	private Usuario usuarioActual() {
		UsuarioPrincipal principal = actualUsuario.principal();
		return usuarios.findById(principal.usuarioId())
				.orElseThrow(() -> new IllegalStateException(
						"El usuario de la sesión ya no existe: " + principal.getUsername()));
	}

	private TipoOperacion resolverTipoOperacion(Long tipoOperacionId) {
		if (tipoOperacionId == null) {
			return null;
		}
		return tiposOperacion.findById(tipoOperacionId)
				.orElseThrow(() -> new NoSuchElementException("El tipo de operación no existe."));
	}

	private static BigDecimal horasEntre(LocalDateTime inicio, LocalDateTime fin) {
		if (inicio == null || fin == null) {
			return BigDecimal.ZERO;
		}
		long minutos = java.time.Duration.between(inicio, fin).toMinutes();
		return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
	}

	private void auditarCambioEstado(ProgramacionExtendido programacion, EstadoProgramacion anterior,
									 EstadoProgramacion nuevo, String motivo) {
		AccionAuditada accion = switch (nuevo) {
			case APROBADA -> AccionAuditada.APROBAR;
			case AJUSTADA -> AccionAuditada.AJUSTAR;
			case RECHAZADA -> AccionAuditada.RECHAZAR;
			case CANCELADA -> AccionAuditada.CANCELAR;
			case EJECUTADA -> AccionAuditada.EJECUTAR;
			case CERRADA -> AccionAuditada.CERRAR;
			default -> AccionAuditada.EDITAR;
		};

		Map<String, Object> valores = new HashMap<>(valores(programacion));
		valores.put("estado", nuevo.name());

		auditoria.registrar(RegistroAuditoria.de(accion, EntidadAuditada.PROGRAMACION_EXTENDIDO)
				.entidadId(programacion.getId())
				.anterior(Map.of("estado", anterior.name()))
				.nuevo(valores)
				.motivo(motivo));
	}

	/**
	 * Valores que interesan en la bitácora. Sin datos personales.
	 *
	 * <p>Van las horas propuestas y las ajustadas por separado porque un ajuste escribe
	 * en su propio par de columnas: con solo las propuestas, la bitácora de una
	 * corrección mostraba el antes y el después idénticos, y el cambio de horario que
	 * justificaba el motivo quedaba fuera del rastro.
	 *
	 * <p>La excepción y la emergencia van dentro porque el rastro de una emergencia que
	 * se ajusta tiene que seguir diciendo que era una emergencia y por qué evento: sin
	 * estos campos, cuenta que se movieron horas y no que hubo una excepción de
	 * compatibilidad que alguien tuvo que autorizar.
	 *
	 * <p>El motivo de la excepción entra tal cual. Es la explicación que escribió el
	 * coordinador sobre por qué eligió a esa persona, no un dato personal, y es
	 * precisamente lo que después hay que poder defender.
	 */
	private static Map<String, Object> valores(ProgramacionExtendido programacion) {
		Map<String, Object> mapa = new HashMap<>();
		mapa.put("colaboradorId", programacion.getColaborador() == null ? null : programacion.getColaborador().getId());
		mapa.put("areaId", programacion.getArea() == null ? null : programacion.getArea().getId());
		mapa.put("tipo", programacion.getTipo() == null ? null : programacion.getTipo().name());
		mapa.put("inicioProgramado", programacion.getInicioProgramado());
		mapa.put("finProgramado", programacion.getFinProgramado());
		mapa.put("horasProgramadas", programacion.getHorasProgramadas());
		mapa.put("inicioAjustado", programacion.getInicioAjustado());
		mapa.put("finAjustado", programacion.getFinAjustado());
		mapa.put("horasAjustadas", programacion.getHorasAjustadas());
		mapa.put("requeriaNivelacion", programacion.isRequeriaNivelacion());
		mapa.put("incumpleAptitud", programacion.isIncumpleAptitud());
		mapa.put("motivoExcepcionCandidato", programacion.getMotivoExcepcionCandidato());
		mapa.put("esEmergencia", programacion.isEsEmergencia());
		mapa.put("referenciaEvento", programacion.getReferenciaEvento());
		return mapa;
	}
}