package com.mardique.programacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.HoraExtraEjecutada;
import com.mardique.programacion.domain.NotificacionSms;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.ViajePasajero;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.NotificacionSmsRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.ViajePasajeroRepository;
import com.mardique.programacion.service.dto.ColaboradorSalarioDto;
import com.mardique.programacion.service.dto.PerfilColaboradorDto;
import com.mardique.programacion.service.dto.PerfilProgramacionDto;
import com.mardique.programacion.service.dto.PerfilSerieDto;

/**
 * El historial completo de un colaborador, en una sola pantalla.
 *
 * <p>Reúne lo que hizo (programaciones con su motivo y horario), lo que
 * ejecutó (horas confirmadas con observación), en qué vehículo se subió y qué
 * se le notificó: nada se pierde entre pantallas. Los KPIs y las gráficas
 * responden al rango de fechas; el buscador de texto acota solo el historial,
 * para no cambiar los totales con una búsqueda.
 *
 * <p>El perfil comparte el filtro de roles con el dato de salario: quien no
 * puede ver un sueldo tampoco puede ver esta pantalla.
 */
@Service
public class PerfilColaboradorService {

	/** Últimos días con horas que dibuja la gráfica diaria. */
	static final int MAX_DIAS_BARRAS = 30;

	/** Últimos meses que dibuja la gráfica mensual. */
	static final int MAX_MESES_BARRAS = 12;

	private static final DateTimeFormatter DIA_CORTO = DateTimeFormatter.ofPattern("dd/MM");

	private static final DateTimeFormatter DIA_LARGO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final List<String> MESES_CORTOS = List.of("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago",
			"sep", "oct", "nov", "dic");

	private final ColaboradorRepository colaboradores;

	private final ProgramacionExtendidoRepository programaciones;

	private final HoraExtraEjecutadaRepository horas;

	private final ViajePasajeroRepository pasajeros;

	private final NotificacionSmsRepository notificaciones;

	private final AlcanceAreaService alcance;

	private final SalarioService salarios;

	private final ValorHorasService valores;

	private final NotificacionSmsService sms;

	public PerfilColaboradorService(ColaboradorRepository colaboradores, ProgramacionExtendidoRepository programaciones,
			HoraExtraEjecutadaRepository horas, ViajePasajeroRepository pasajeros,
			NotificacionSmsRepository notificaciones, AlcanceAreaService alcance, SalarioService salarios,
			ValorHorasService valores, NotificacionSmsService sms) {
		this.colaboradores = colaboradores;
		this.programaciones = programaciones;
		this.horas = horas;
		this.pasajeros = pasajeros;
		this.notificaciones = notificaciones;
		this.alcance = alcance;
		this.salarios = salarios;
		this.valores = valores;
		this.sms = sms;
	}

	/**
	 * El perfil completo del colaborador.
	 *
	 * @param colaboradorId persona consultada.
	 * @param desde         inicio del rango; {@code null} es desde el principio.
	 * @param hasta         fin del rango; {@code null} es hasta hoy.
	 * @param texto         buscador sobre el historial (motivo, área, placa,
	 *                      conductor, destino, fechas); vacío no filtra.
	 * @throws NoSuchElementException     si el colaborador no existe.
	 * @throws com.mardique.programacion.security.AccesoFueraDeAlcanceException
	 *                                    si su área queda fuera del alcance del
	 *                                    usuario actual (queda auditado).
	 */
	@Transactional(readOnly = true)
	public PerfilColaboradorDto perfil(Long colaboradorId, LocalDate desde, LocalDate hasta, String texto) {
		Colaborador persona = colaboradores.findById(colaboradorId)
				.orElseThrow(() -> new NoSuchElementException("El colaborador no existe."));
		alcance.verificar(persona.getArea().getId());

		String busqueda = texto == null || texto.isBlank() ? null : texto.trim().toLowerCase(Locale.ROOT);
		LocalDate referencia = hasta != null ? hasta : LocalDate.now();

		List<ProgramacionExtendido> porFecha = programaciones
				.findByColaboradorIdOrderByInicioProgramadoDesc(colaboradorId).stream().filter(programacion -> {
					LocalDate dia = programacion.getInicioEfectivo().toLocalDate();
					return (desde == null || !dia.isBefore(desde)) && (hasta == null || !dia.isAfter(hasta));
				}).toList();

		List<Long> ids = porFecha.stream().map(ProgramacionExtendido::getId).toList();
		Map<Long, ViajePasajero> conVehiculo = new HashMap<>();
		for (ViajePasajero pasajero : pasajeros.findByProgramacionIdIn(ids)) {
			if (pasajero.getProgramacion() != null) {
				conVehiculo.putIfAbsent(pasajero.getProgramacion().getId(), pasajero);
			}
		}
		Map<Long, NotificacionSms> ultimoAviso = new HashMap<>();
		for (NotificacionSms aviso : notificaciones.findByProgramacionIdIn(ids)) {
			if (aviso.getProgramacion() != null) {
				ultimoAviso.merge(aviso.getProgramacion().getId(), aviso, PerfilColaboradorService::masReciente);
			}
		}

		List<ProgramacionExtendido> historial = porFecha.stream()
				.filter(programacion -> coincide(programacion, busqueda, conVehiculo.get(programacion.getId())))
				.toList();

		List<HoraExtraEjecutada> todasLasHoras = horas.findByColaboradorIdOrderByFechaAsc(colaboradorId);
		List<HoraExtraEjecutada> enRango = todasLasHoras.stream()
				.filter(hora -> (desde == null || !hora.getFecha().isBefore(desde))
						&& (hasta == null || !hora.getFecha().isAfter(hasta)))
				.toList();

		Map<Long, HoraExtraEjecutada> porTurno = new HashMap<>();
		for (HoraExtraEjecutada hora : todasLasHoras) {
			if (hora.getProgramacion() != null) {
				porTurno.putIfAbsent(hora.getProgramacion().getId(), hora);
			}
		}

		BigDecimal totalHoras = enRango.stream().map(HoraExtraEjecutada::getHoras).reduce(BigDecimal.ZERO,
				BigDecimal::add);
		long diasConHoras = enRango.stream().map(HoraExtraEjecutada::getFecha).distinct().count();

		Map<TipoHoraExtra, BigDecimal> porTipo = new EnumMap<>(TipoHoraExtra.class);
		Map<LocalDate, BigDecimal> porDia = new LinkedHashMap<>();
		Map<YearMonth, BigDecimal> porMes = new LinkedHashMap<>();
		for (HoraExtraEjecutada hora : enRango) {
			porTipo.merge(hora.getTipoHora(), hora.getHoras(), BigDecimal::add);
			porDia.merge(hora.getFecha(), hora.getHoras(), BigDecimal::add);
			porMes.merge(YearMonth.from(hora.getFecha()), hora.getHoras(), BigDecimal::add);
		}

		BigDecimal salario = salarios.vigente(colaboradorId, referencia).map(ColaboradorSalarioDto::salarioBase)
				.orElse(null);
		BigDecimal valor = salario == null ? null : valores.valorDe(porTipo, salario, referencia).orElse(null);
		BigDecimal supuesto = salario == null || valor == null ? null : salario.add(valor);

		List<PerfilColaboradorDto.DirectaDto> directas = enRango.stream()
				.filter(hora -> hora.getProgramacion() == null)
				.map(hora -> new PerfilColaboradorDto.DirectaDto(hora.getFecha(), hora.getHoras(),
						etiquetaTipoHora(hora.getTipoHora()), hora.getObservacion()))
				.sorted(Comparator.comparing(PerfilColaboradorDto.DirectaDto::fecha).reversed()).toList();

		List<PerfilProgramacionDto> filas = historial.stream()
				.map(programacion -> fila(programacion, persona, porTurno, conVehiculo, ultimoAviso)).toList();

		return new PerfilColaboradorDto(persona.getId(), persona.getNombreCompleto(), persona.getDocumento(),
				persona.getArea().getNombre(), persona.getCargo() == null ? null : persona.getCargo().getNombre(),
				persona.getTurno() == null ? null : persona.getTurno().getNombre(), persona.getCelular(),
				persona.getFechaIngreso(), salario, totalHoras, (int) diasConHoras, filas.size(), valor, supuesto,
				serieDias(porDia), serieMeses(porMes), serieTipos(porTipo), filas, directas);
	}

	/**
	 * Deja en cola el aviso de asignación de una programación del perfil, con
	 * el mensaje de horario, motivo y vehículo.
	 *
	 * @param colaboradorId dueño del perfil.
	 * @param programacionId turno a notificar; debe pertenecer al colaborador.
	 * @throws NoSuchElementException si alguno de los dos no existe o no
	 *                               coinciden.
	 * @throws IllegalStateException  si el estado no permite notificar o no
	 *                               tiene celular (ambas reglas del SMS).
	 */
	@Transactional
	public void notificar(Long colaboradorId, Long programacionId) {
		Colaborador persona = colaboradores.findById(colaboradorId)
				.orElseThrow(() -> new NoSuchElementException("El colaborador no existe."));
		ProgramacionExtendido programacion = programaciones.findById(programacionId)
				.orElseThrow(() -> new NoSuchElementException("La programacion ya no existe."));
		if (programacion.getColaborador() == null
				|| !persona.getId().equals(programacion.getColaborador().getId())) {
			throw new NoSuchElementException("La programacion no pertenece a este colaborador.");
		}
		alcance.verificar(programacion.getArea().getId());
		sms.crearAsignacion(programacion);
	}

	// ------------------------------------------------------------------
	// Series de las gráficas
	// ------------------------------------------------------------------

	/** Últimos {@link #MAX_DIAS_BARRAS} días con horas, en orden cronológico. */
	private static List<PerfilSerieDto> serieDias(Map<LocalDate, BigDecimal> porDia) {
		List<Map.Entry<String, BigDecimal>> puntos = new ArrayList<>();
		int desde = Math.max(0, porDia.size() - MAX_DIAS_BARRAS);
		int indice = 0;
		for (var entrada : porDia.entrySet()) {
			if (indice++ >= desde) {
				puntos.add(Map.entry(entrada.getKey().format(DIA_CORTO), entrada.getValue()));
			}
		}
		return aSerie(puntos);
	}

	/** Últimos {@link #MAX_MESES_BARRAS} meses con horas, en orden cronológico. */
	private static List<PerfilSerieDto> serieMeses(Map<YearMonth, BigDecimal> porMes) {
		List<Map.Entry<String, BigDecimal>> puntos = new ArrayList<>();
		int desde = Math.max(0, porMes.size() - MAX_MESES_BARRAS);
		int indice = 0;
		for (var entrada : porMes.entrySet()) {
			if (indice++ >= desde) {
				YearMonth mes = entrada.getKey();
				puntos.add(Map.entry(MESES_CORTOS.get(mes.getMonthValue() - 1) + " "
						+ String.valueOf(mes.getYear()).substring(2), entrada.getValue()));
			}
		}
		return aSerie(puntos);
	}

	/** Horas por tipo, del mayor al menor, solo los que tienen horas. */
	private static List<PerfilSerieDto> serieTipos(Map<TipoHoraExtra, BigDecimal> porTipo) {
		List<Map.Entry<String, BigDecimal>> puntos = porTipo.entrySet().stream().filter(e -> e.getValue().signum() > 0)
				.sorted(Map.Entry.<TipoHoraExtra, BigDecimal>comparingByValue().reversed())
				.map(e -> Map.entry(etiquetaTipoHora(e.getKey()), e.getValue())).toList();
		return aSerie(puntos);
	}

	/** Porcentaje de cada punto sobre el máximo de la serie, con piso visible. */
	private static List<PerfilSerieDto> aSerie(List<Map.Entry<String, BigDecimal>> puntos) {
		if (puntos.isEmpty()) {
			return List.of();
		}
		BigDecimal maximoCalculado = puntos.stream().map(Map.Entry::getValue).max(Comparator.naturalOrder())
				.orElse(BigDecimal.ONE);
		BigDecimal maximo = maximoCalculado.signum() == 0 ? BigDecimal.ONE : maximoCalculado;
		return puntos.stream()
				.map(punto -> new PerfilSerieDto(punto.getKey(), punto.getValue(),
						Math.min(100, Math.max(3, punto.getValue().multiply(BigDecimal.valueOf(100))
								.divide(maximo, 0, RoundingMode.HALF_UP).intValue()))))
				.toList();
	}

	// ------------------------------------------------------------------
	// Fila del historial
	// ------------------------------------------------------------------

	private PerfilProgramacionDto fila(ProgramacionExtendido programacion, Colaborador persona,
			Map<Long, HoraExtraEjecutada> porTurno, Map<Long, ViajePasajero> conVehiculo,
			Map<Long, NotificacionSms> ultimoAviso) {
		HoraExtraEjecutada hora = porTurno.get(programacion.getId());
		ViajePasajero pasajero = conVehiculo.get(programacion.getId());
		NotificacionSms aviso = ultimoAviso.get(programacion.getId());
		String mensaje = sms.mensajeAsignacion(programacion, pasajero);
		return new PerfilProgramacionDto(programacion.getId(), programacion.getInicioEfectivo(),
				programacion.getFinEfectivo(), etiquetaTipo(programacion.getTipo()), programacion.getEstado().name(),
				programacion.getArea().getNombre(), programacion.getMotivo(), programacion.getMotivoAjuste(),
				programacion.getHorasProgramadas(), programacion.getHorasEfectivas(),
				programacion.isRequeriaNivelacion(), programacion.isEsEmergencia(), programacion.getReferenciaEvento(),
				hora == null ? null : hora.getFecha(), hora == null ? null : hora.getHoras(),
				hora == null ? null : etiquetaTipoHora(hora.getTipoHora()), hora == null ? null : hora.getObservacion(),
				hora == null || hora.getRegistradoPor() == null ? null : hora.getRegistradoPor().getNombreCompleto(),
				NotificacionSmsService.esNotificable(programacion.getEstado()), aviso == null ? null : aviso.getEstado().name(),
				aviso == null ? null : aviso.getMensaje(),
				pasajero == null ? null : pasajero.getViaje().getVehiculo().getPlaca(),
				pasajero == null ? null : pasajero.getViaje().getVehiculo().getConductor(),
				pasajero == null || pasajero.getViaje().getDestino() == null ? null
						: pasajero.getViaje().getDestino().getNombre(),
				pasajero == null ? null : pasajero.getViaje().getSalida(),
				NotificacionSmsService.whatsappEnlace(persona.getCelular(), mensaje));
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	/**
	 * El SMS más reciente entre varios: si se avisó más de una vez, el perfil
	 * muestra el último estado.
	 */
	private static NotificacionSms masReciente(NotificacionSms a, NotificacionSms b) {
		if (a.getCreatedAt() == null) {
			return b;
		}
		if (b.getCreatedAt() == null) {
			return a;
		}
		return b.getCreatedAt().isAfter(a.getCreatedAt()) ? b : a;
	}

	/** Coincidencia del buscador sobre motivo, fechas, área, estado y vehículo. */
	private static boolean coincide(ProgramacionExtendido programacion, String busqueda, ViajePasajero pasajero) {
		if (busqueda == null) {
			return true;
		}
		StringBuilder texto = new StringBuilder(programacion.getMotivo()).append(' ')
				.append(programacion.getArea().getNombre()).append(' ').append(etiquetaTipo(programacion.getTipo()))
				.append(' ').append(programacion.getEstado()).append(' ')
				.append(programacion.getInicioEfectivo().toLocalDate().format(DIA_LARGO)).append(' ')
				.append(programacion.getInicioEfectivo().toLocalDate()).append(' ')
				.append(programacion.getInicioEfectivo().format(DIA_CORTO));
		if (programacion.getMotivoAjuste() != null) {
			texto.append(' ').append(programacion.getMotivoAjuste());
		}
		if (programacion.getComentarioTh() != null) {
			texto.append(' ').append(programacion.getComentarioTh());
		}
		if (programacion.getReferenciaEvento() != null) {
			texto.append(' ').append(programacion.getReferenciaEvento());
		}
		if (pasajero != null) {
			texto.append(' ').append(pasajero.getViaje().getVehiculo().getPlaca());
			if (pasajero.getViaje().getVehiculo().getConductor() != null) {
				texto.append(' ').append(pasajero.getViaje().getVehiculo().getConductor());
			}
			if (pasajero.getViaje().getDestino() != null) {
				texto.append(' ').append(pasajero.getViaje().getDestino().getNombre());
			}
		}
		return texto.toString().toLowerCase(Locale.ROOT).contains(busqueda);
	}

	private static String etiquetaTipo(TipoProgramacion tipo) {
		return switch (tipo) {
			case EXTENDIDO -> "Extendido";
			case INGRESO -> "Ingreso";
			case SABADO -> "Sábado";
			case DOMINGO -> "Domingo";
			case FESTIVO -> "Festivo";
		};
	}

	private static String etiquetaTipoHora(TipoHoraExtra tipo) {
		return switch (tipo) {
			case EXTRA_DIURNA -> "Diurnas";
			case EXTRA_NOCTURNA -> "Nocturnas";
			case EXTRA_DOMINICAL_DIURNA -> "Dominicales diurnas";
			case EXTRA_DOMINICAL_NOCTURNA -> "Dominicales nocturnas";
			case TURNO_DIURNO -> "Turno diurno";
			case TURNO_NOCTURNO -> "Turno nocturno";
		};
	}
}
