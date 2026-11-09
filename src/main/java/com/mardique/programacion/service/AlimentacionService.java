package com.mardique.programacion.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.audit.AccionAuditada;
import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.audit.EntidadAuditada;
import com.mardique.programacion.audit.RegistroAuditoria;
import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.ListaAlimentacion;
import com.mardique.programacion.domain.ListaAlimentacionPersona;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoAlimentacion;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ListaAlimentacionPersonaRepository;
import com.mardique.programacion.repository.ListaAlimentacionRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.security.ActualUsuario;

/**
 * Meriendas y cenas: arma la lista de personas que comen según las horas de su
 * programación y la guarda como documento histórico.
 *
 * <p>La clasificación no la elige el usuario. Quien <strong>entra</strong> a
 * las 15:40 cena (arranca un turno que trabaja de noche); quien
 * <strong>termina</strong> su turno a las 19:00 en punto se lleva la merienda
 * de la tarde. Quien hace su jornada habitual (07:00 a 15:00), o cualquier
 * otra combinación de horas, no recibe nada en esta pantalla, porque su
 * alimentación es la jornada normal.
 */
@Service
public class AlimentacionService {

	/**
	 * Hora de <strong>entrada</strong> que identifica a quien cena.
	 *
	 * <p>Es una constante y no una configuración porque es la regla del
	 * contrato con el proveedor: la cena entra con quien empieza el turno de la
	 * noche a esa hora.
	 */
	public static final LocalTime HORA_ENTRADA_CENA = LocalTime.of(15, 40);

	/**
	 * Hora de <strong>salida</strong> que identifica a quien recibe merienda.
	 *
	 * <p>La merienda se entrega al salir del turno de la tarde: quien termina a
	 * las 19:00 en punto.
	 */
	public static final LocalTime HORA_SALIDA_MERIENDA = LocalTime.of(19, 0);

	/**
	 * Estados que cuentan como confirmados: lo que Talento Humano ya aprobó,
	 * ajustó o notificó, más lo ya ejecutado o cerrado para poder armar
	 * listas de días pasados.
	 */
	private static final List<EstadoProgramacion> CONFIRMADOS = List.of(EstadoProgramacion.APROBADA,
			EstadoProgramacion.AJUSTADA, EstadoProgramacion.NOTIFICADA, EstadoProgramacion.EJECUTADA,
			EstadoProgramacion.CERRADA);

	/** Candidatos de un día ya clasificados, con lo que ya está en lista. */
	public record Candidatos(List<ProgramacionExtendido> programaciones, Map<Long, TipoAlimentacion> tipoDe,
			Set<Long> enListaMerienda, Set<Long> enListaCena) {

		static Candidatos vacio() {
			return new Candidatos(List.of(), Map.of(), Set.of(), Set.of());
		}
	}

	/** Resultado de guardar una lista: qué se guardó y qué se rechazó. */
	public record ResultadoCreacion(ListaAlimentacion lista, int guardadas, List<String> motivos) {
	}

	/** Resultado de guardar meriendas y cenas del día en un solo paso. */
	public record ResultadoAmbas(int meriendas, int cenas, List<String> motivos) {
	}

	private final ProgramacionExtendidoRepository programaciones;

	private final ListaAlimentacionRepository listas;

	private final ListaAlimentacionPersonaRepository personas;

	private final AreaRepository areas;

	private final UsuarioRepository usuarios;

	private final AlcanceAreaService alcance;

	private final ActualUsuario actualUsuario;

	private final AuditoriaService auditoria;

	public AlimentacionService(ProgramacionExtendidoRepository programaciones, ListaAlimentacionRepository listas,
			ListaAlimentacionPersonaRepository personas, AreaRepository areas, UsuarioRepository usuarios,
			AlcanceAreaService alcance, ActualUsuario actualUsuario, AuditoriaService auditoria) {
		this.programaciones = programaciones;
		this.listas = listas;
		this.personas = personas;
		this.areas = areas;
		this.usuarios = usuarios;
		this.alcance = alcance;
		this.actualUsuario = actualUsuario;
		this.auditoria = auditoria;
	}

	/**
	 * Clasifica una programación del día según sus horas efectivas.
	 *
	 * <p>Dos salidas posibles: quien <strong>entra</strong> a las 15:40 cena
	 * (empieza el turno de la noche), y quien <strong>termina</strong> a las
	 * 19:00 en punto se lleva la merienda. Cualquier otra combinación de horas
	 * es jornada normal y no va en esta pantalla.
	 *
	 * <p>La cena se decide primero: un turno que entrara a las 15:40 y saliera
	 * a las 19:00 es cena, no merienda, porque manda la entrada.
	 *
	 * @param p programación con inicio y fin efectivos.
	 * @return el tipo, o {@code null} si no corresponde a esta pantalla.
	 */
	public static TipoAlimentacion clasificar(ProgramacionExtendido p) {
		LocalDateTime inicio = p.getInicioEfectivo();
		LocalDateTime fin = p.getFinEfectivo();
		if (inicio == null || fin == null) {
			return null;
		}
		if (inicio.toLocalTime().equals(HORA_ENTRADA_CENA)) {
			return TipoAlimentacion.CENA;
		}
		if (fin.toLocalTime().equals(HORA_SALIDA_MERIENDA)) {
			return TipoAlimentacion.MERIENDA;
		}
		return null;
	}

	/**
	 * Personas que pueden ir en la lista de meriendas y en la de cenas de un
	 * día, ya separadas.
	 *
	 * <p>El día se arma por <strong>inicio</strong> efectivo a propósito: en
	 * alimentación importa que la persona entró ese día (es la que se le
	 * sirve el refrigerio o la cena de ese turno), a diferencia del transporte
	 * que la recoge al terminar.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public Candidatos candidatos(LocalDate dia) {
		Collection<Long> areaIds = areas.findByActivaTrueOrderByNombreAsc().stream().map(Area::getId).toList();
		Set<Long> visibles = alcance.areasVisibles(areaIds);
		if (visibles.isEmpty()) {
			return Candidatos.vacio();
		}
		LocalDateTime desde = dia.atStartOfDay();
		LocalDateTime hasta = dia.plusDays(1).atStartOfDay();
		var delDia = programaciones.findPorInicioEfectivoEnEstadosEnAreas(visibles, CONFIRMADOS, desde, hasta);
		Map<Long, TipoAlimentacion> tipoDe = new LinkedHashMap<>();
		List<ProgramacionExtendido> clasificadas = new ArrayList<>();
		for (ProgramacionExtendido p : delDia) {
			TipoAlimentacion tipo = clasificar(p);
			if (tipo != null) {
				tipoDe.put(p.getId(), tipo);
				clasificadas.add(p);
			}
		}
		// Personas que ya entraron en una lista de este día: la pantalla las
		// deshabilita y el servidor las rechaza, para no servir dos veces.
		Set<Long> enMerienda = personas.colaboradoresEnListasDe(dia, TipoAlimentacion.MERIENDA);
		Set<Long> enCena = personas.colaboradoresEnListasDe(dia, TipoAlimentacion.CENA);
		return new Candidatos(clasificadas, tipoDe, enMerienda, enCena);
	}

	/**
	 * Guarda la lista de meriendas o cenas de un día.
	 *
	 * <p>Cada persona se valida por su cuenta y los rechazos se acumulan en un
	 * resumen: que una no corresponda al tipo no debe frenar a las demás. Si
	 * ninguna pasa, no se crea la cabecera: una lista vacía en el historial
	 * solo ensucia la pantalla.
	 *
	 * @return la lista creada (nula si ninguna persona pasó) y los motivos.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public ResultadoCreacion crearLista(LocalDate dia, TipoAlimentacion tipo, List<Long> programacionIds) {
		if (dia == null || tipo == null) {
			throw new IllegalArgumentException("Indique el dia y el tipo de alimento.");
		}
		if (programacionIds == null || programacionIds.isEmpty()) {
			throw new IllegalArgumentException("Marque al menos una persona.");
		}
		Collection<Long> areaIds = areas.findByActivaTrueOrderByNombreAsc().stream().map(Area::getId).toList();
		Set<Long> visibles = alcance.areasVisibles(areaIds);
		// El filtro de áreas va en la consulta: un id de otra área ni siquiera
		// llega de vuelta, igual que en la asignación masiva de transporte.
		List<ProgramacionExtendido> encontradas = programaciones.findByIdInAndAreaIdIn(programacionIds, visibles);
		Set<Long> enLista = personas.colaboradoresEnListasDe(dia, tipo);
		String nombreTipo = tipo == TipoAlimentacion.MERIENDA ? "merienda" : "cena";

		List<String> motivos = new ArrayList<>();
		List<ProgramacionExtendido> aceptadas = new ArrayList<>();
		Set<Long> devueltas = new LinkedHashSet<>();
		encontradas.forEach(p -> devueltas.add(p.getId()));
		for (Long programacionId : programacionIds) {
			if (!devueltas.contains(programacionId)) {
				motivos.add("La programacion " + programacionId + " no esta disponible en su alcance.");
			}
		}
		for (ProgramacionExtendido p : encontradas) {
			String nombre = p.getColaborador().getNombres() + " " + p.getColaborador().getApellidos();
			if (!CONFIRMADOS.contains(p.getEstado())) {
				motivos.add(nombre + ": su programacion no esta confirmada.");
			} else if (!p.getInicioEfectivo().toLocalDate().equals(dia)) {
				motivos.add(nombre + ": su turno no es de este dia.");
			} else if (clasificar(p) != tipo) {
				motivos.add(nombre + ": sus horas no corresponden a " + nombreTipo + ".");
			} else if (enLista.contains(p.getColaborador().getId())) {
				motivos.add(nombre + ": ya esta en una lista de " + nombreTipo + " de este dia.");
			} else {
				aceptadas.add(p);
			}
		}
		if (aceptadas.isEmpty()) {
			return new ResultadoCreacion(null, 0, motivos);
		}

		ListaAlimentacion lista = new ListaAlimentacion();
		lista.setFecha(dia);
		lista.setTipo(tipo);
		lista.setCreadoPor(usuarios.findById(actualUsuario.principal().usuarioId())
				.orElseThrow(() -> new IllegalStateException("El usuario autenticado no existe.")));
		ListaAlimentacion guardada = listas.save(lista);
		for (ProgramacionExtendido p : aceptadas) {
			ListaAlimentacionPersona fila = new ListaAlimentacionPersona();
			fila.setLista(guardada);
			fila.setColaborador(p.getColaborador());
			// El área como estaba hoy: la lista es un documento con fecha y el
			// proveedor recibe el área del día que se armó, no la de mañana.
			fila.setArea(p.getArea());
			fila.setProgramacion(p);
			personas.save(fila);
		}
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR, EntidadAuditada.LISTA_ALIMENTACION)
				.entidadId(guardada.getId()).motivo("Lista de " + nombreTipo + " guardada con " + aceptadas.size()
						+ (aceptadas.size() == 1 ? " persona" : " personas")));
		return new ResultadoCreacion(guardada, aceptadas.size(), motivos);
	}

	/**
	 * Guarda las meriendas <strong>y</strong> las cenas del día de una sola
	 * vez, con todas las candidatas que aún no estén en lista.
	 *
	 * <p>La clasificación ya la hace el servidor: marcar casilla por casilla
	 * era un paso mecánico que no cambiaba el resultado. Quien solo quiere una
	 * de las dos sigue pudiendo usar el formulario de su tarjeta.
	 *
	 * @return cuántas entraron en cada lista y qué se dejó fuera.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public ResultadoAmbas guardarAmbas(LocalDate dia) {
		if (dia == null) {
			throw new IllegalArgumentException("Indique el dia de las listas.");
		}
		Candidatos candidatos = candidatos(dia);
		List<Long> merienda = new ArrayList<>();
		List<Long> cena = new ArrayList<>();
		for (ProgramacionExtendido p : candidatos.programaciones()) {
			TipoAlimentacion tipo = candidatos.tipoDe().get(p.getId());
			boolean yaEnLista = tipo == TipoAlimentacion.MERIENDA
					? candidatos.enListaMerienda().contains(p.getColaborador().getId())
					: candidatos.enListaCena().contains(p.getColaborador().getId());
			if (yaEnLista) {
				continue;
			}
			(tipo == TipoAlimentacion.MERIENDA ? merienda : cena).add(p.getId());
		}
		ResultadoCreacion m = merienda.isEmpty() ? new ResultadoCreacion(null, 0, List.of())
				: crearLista(dia, TipoAlimentacion.MERIENDA, merienda);
		ResultadoCreacion c = cena.isEmpty() ? new ResultadoCreacion(null, 0, List.of())
				: crearLista(dia, TipoAlimentacion.CENA, cena);
		List<String> motivos = new ArrayList<>(m.motivos());
		motivos.addAll(c.motivos());
		return new ResultadoAmbas(m.guardadas(), c.guardadas(), motivos);
	}

	/** Listas guardadas para un día, con su creador ya cargado. */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<ListaAlimentacion> listasDelDia(LocalDate dia) {
		return listas.findByFechaOrderByIdDesc(dia);
	}

	/** Historial completo, de lo más reciente a lo más antiguo. */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<ListaAlimentacion> historial() {
		return listas.findAllByOrderByIdDesc();
	}

	/** Personas que lleva cada lista, para los listados. */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public Map<Long, Long> conteos(Collection<Long> listaIds) {
		Map<Long, Long> conteos = new LinkedHashMap<>();
		if (listaIds == null || listaIds.isEmpty()) {
			return conteos;
		}
		for (Object[] fila : personas.contarPorLista(listaIds)) {
			conteos.put((Long) fila[0], (Long) fila[1]);
		}
		return conteos;
	}

	/** Una lista para su reporte, con el creador ya cargado. */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public ListaAlimentacion lista(Long listaId) {
		return listas.findConCreadoPorById(listaId)
				.orElseThrow(() -> new IllegalArgumentException("La lista no existe."));
	}

	/** Personas de una lista en el orden en que se eligieron. */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<ListaAlimentacionPersona> personasDe(Long listaId) {
		return personas.findByListaIdOrderByIdAsc(listaId);
	}

	/**
	 * Elimina una lista guardada y sus personas.
	 *
	 * <p>Después de borrar, las personas vuelven a estar disponibles para
	 * armar otra lista del mismo día: lo que se quitó es el documento, no la
	 * programación de nadie.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void eliminarLista(Long listaId) {
		ListaAlimentacion lista = listas.findById(listaId)
				.orElseThrow(() -> new IllegalArgumentException("La lista no existe."));
		personas.deleteByListaId(listaId);
		// Los borrados derivados entran en la sesión y se ejecutan al volcar;
		// se vuelcan aquí para que las personas desaparezcan antes que la lista.
		personas.flush();
		listas.delete(lista);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR, EntidadAuditada.LISTA_ALIMENTACION)
				.entidadId(listaId)
				.motivo("Lista de " + (lista.getTipo() == TipoAlimentacion.MERIENDA ? "merienda" : "cena")
						+ " del " + lista.getFecha() + " eliminada"));
	}
}
