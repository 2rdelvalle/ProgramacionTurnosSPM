package com.mardique.programacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Cargo;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.HoraExtraEjecutada;
import com.mardique.programacion.domain.ParametroLaboral;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.CargoRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.ParametroLaboralRepository;
import com.mardique.programacion.service.dto.NivelacionAreaDto;
import com.mardique.programacion.service.dto.NivelacionColaboradorDto;

/**
 * El panel de nivelación: la sobrecarga de horas extra por área y por
 * colaborador, en un mes, medida contra los <strong>topes legales</strong>.
 *
 * <p>Cada persona tiene una capacidad en el mes que sale de la ley: por cada
 * semana (lunes a domingo) que toque el mes se puede hacer como máximo el tope
 * diario por los días de esa semana que caen dentro del mes, sin pasar del tope
 * semanal. En octubre de 2026 —que arranca en jueves— eso da 8 + 12 + 12 + 12 +
 * 12 = 56 horas. El porcentaje de una fila es, por defecto, sus horas del mes
 * sobre esa capacidad, y el 100 % es el techo legal.
 *
 * <p>Los topes no viven en el código: se leen de {@code parametro_laboral}
 * ({@code TOPE_HORAS_EXTRA_DIA} y {@code TOPE_HORAS_EXTRA_SEMANA}) a la fecha
 * del corte. Si a alguno le falta valor vigente, el panel falla en vez de
 * inventar una capacidad.
 *
 * <p>Cuando la persona <em>rompe</em> un tope aunque el total del mes no llegue
 * a la capacidad —un día por encima del tope diario, o una semana por encima
 * del semanal— el estado pasa a "Excede tope legal" y la fila cita el motivo
 * ({@code "12/oct: 8.00 h (tope 2)"}), pero el porcentaje sigue siendo el del
 * mes, recortado en el rango de 1 a 100.
 *
 * <p>La barra de cada área mide la carga del <em>área completa</em>: las horas
 * de todas sus filas sobre el máximo legal de toda su plantilla visible, no el
 * peor caso de una persona. Las personas que rompen un tope se cuentan aparte,
 * en la etiqueta del área.
 *
 * <p>Los filtros de cargo y de nombre acotan lo que se ve —filas, barras y
 * personas del área—, pero no mueven la capacidad del mes, que es igual para
 * todos y no depende de quién deje pasar el filtro.
 */
@Service
public class PanelNivelacionService {

	private static final String TOPE_DIA = "TOPE_HORAS_EXTRA_DIA";

	private static final String TOPE_SEMANA = "TOPE_HORAS_EXTRA_SEMANA";

	/** Los únicos tipos que cuentan como hora extra; los {@code TURNO_*} no. */
	private static final Set<TipoHoraExtra> TIPOS_EXTRA = EnumSet.of(TipoHoraExtra.EXTRA_DIURNA,
			TipoHoraExtra.EXTRA_NOCTURNA, TipoHoraExtra.EXTRA_DOMINICAL_DIURNA, TipoHoraExtra.EXTRA_DOMINICAL_NOCTURNA);

	/** A partir de qué porcentaje el JS pinta la zona de atención (cerca del tope). */
	private static final int ZONA_ATENCION = 80;

	private static final List<String> MESES = List.of("enero", "febrero", "marzo", "abril", "mayo", "junio",
			"julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre");

	private static final List<String> MESES_ABREV = List.of("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago",
			"sep", "oct", "nov", "dic");

	private final ColaboradorRepository colaboradores;

	private final AreaRepository areas;

	private final CargoRepository cargos;

	private final HoraExtraEjecutadaRepository horas;

	private final ParametroLaboralRepository parametros;

	private final AlcanceAreaService alcance;

	public PanelNivelacionService(ColaboradorRepository colaboradores, AreaRepository areas, CargoRepository cargos,
			HoraExtraEjecutadaRepository horas, ParametroLaboralRepository parametros, AlcanceAreaService alcance) {
		this.colaboradores = colaboradores;
		this.areas = areas;
		this.cargos = cargos;
		this.horas = horas;
		this.parametros = parametros;
		this.alcance = alcance;
	}

	/**
	 * Todo lo que dibuja la pantalla: las filas por colaborador, las barras por
	 * área y los números de las tarjetas.
	 *
	 * @param periodo mes del corte.
	 * @param areaId  área a mostrar, o {@code null} para todas las visibles.
	 * @param cargoId cargo a mostrar, o {@code null} para todos.
	 * @param q       texto a buscar en nombre o cédula, o {@code null}.
	 * @return el panel listo para pintar.
	 * @throws IllegalStateException si falta el tope diario o el semanal vigente.
	 */
	@Transactional(readOnly = true)
	public PanelNivelacion calcular(YearMonth periodo, Long areaId, Long cargoId, String q) {
		String etiqueta = MESES.get(periodo.getMonthValue() - 1) + " de " + periodo.getYear();
		LocalDate desde = periodo.atDay(1);
		LocalDate hasta = periodo.atEndOfMonth();

		BigDecimal topeDia = parametro(TOPE_DIA, hasta);
		BigDecimal topeSemana = parametro(TOPE_SEMANA, hasta);
		BigDecimal capacidad = capacidadDelMes(periodo, topeDia, topeSemana);

		List<Area> visibles = areasVisibles();
		List<Long> alcanceIds;
		if (areaId != null) {
			if (visibles.stream().noneMatch(area -> area.getId().equals(areaId))) {
				return PanelNivelacion.vacio(periodo, etiqueta, capacidad, topeDia, topeSemana);
			}
			alcanceIds = List.of(areaId);
		} else {
			alcanceIds = visibles.stream().map(Area::getId).toList();
		}
		if (alcanceIds.isEmpty()) {
			return PanelNivelacion.vacio(periodo, etiqueta, capacidad, topeDia, topeSemana);
		}

		List<Colaborador> personal = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(alcanceIds);

		// Las horas se traen desde el lunes de la primera semana hasta el domingo
		// de la última: hace falta la semana completa para juzgar el tope semanal
		// aunque sus días caigan fuera del mes. El acumulado del mes, en cambio,
		// solo cuenta los días del periodo.
		LocalDate desdeSemana = desde.with(DayOfWeek.MONDAY);
		LocalDate hastaSemana = hasta.with(DayOfWeek.SUNDAY);
		Map<Long, BigDecimal> porPersona = new HashMap<>();
		Map<Long, Map<LocalDate, BigDecimal>> porDia = new HashMap<>();
		for (HoraExtraEjecutada fila : horas.findByFechaBetween(desdeSemana, hastaSemana)) {
			if (!TIPOS_EXTRA.contains(fila.getTipoHora()) || fila.getHoras() == null || fila.getColaborador() == null) {
				continue;
			}
			Long id = fila.getColaborador().getId();
			LocalDate fecha = fila.getFecha();
			BigDecimal cantidad = fila.getHoras();
			porDia.computeIfAbsent(id, clave -> new TreeMap<>()).merge(fecha, cantidad, BigDecimal::add);
			if (!fecha.isBefore(desde) && !fecha.isAfter(hasta)) {
				porPersona.merge(id, cantidad, BigDecimal::add);
			}
		}

		List<Colaborador> filtrados = personal.stream()
				.filter(colaborador -> cargoId == null
						|| (colaborador.getCargo() != null && colaborador.getCargo().getId().equals(cargoId)))
				.filter(colaborador -> coincide(colaborador.getNombreCompleto(), q)
						|| coincide(colaborador.getDocumento(), q))
				.toList();

		List<NivelacionColaboradorDto> filas = filtrados.stream()
				.filter(colaborador -> porPersona.getOrDefault(colaborador.getId(), BigDecimal.ZERO).signum() > 0)
				.map(colaborador -> fila(colaborador, porPersona.get(colaborador.getId()), capacidad, topeDia,
						topeSemana, porDia.get(colaborador.getId())))
				.sorted(orden())
				.toList();

		List<NivelacionAreaDto> barras = barrasPorArea(filas, personal, filtrados, capacidad);

		int areasEnAlerta = (int) barras.stream().filter(NivelacionAreaDto::enAlerta).count();
		int personasEnAlerta = (int) filas.stream().filter(NivelacionColaboradorDto::enAlerta).count();
		BigDecimal horasDelMes = filas.stream().map(NivelacionColaboradorDto::horasEjecutadas)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		return new PanelNivelacion(periodo, etiqueta, filas, barras, areasEnAlerta, personasEnAlerta, horasDelMes,
				capacidad, topeDia, topeSemana);
	}

	/**
	 * Las áreas activas dentro del alcance del usuario: las mismas que ofrece el
	 * desplegable de área del filtro.
	 */
	@Transactional(readOnly = true)
	public List<Area> areasVisibles() {
		List<Area> activas = areas.findByActivaTrueOrderByNombreAsc();
		Set<Long> permitidas = alcance.areasVisibles(activas.stream().map(Area::getId).toList());
		return activas.stream().filter(area -> permitidas.contains(area.getId())).toList();
	}

	/** Los cargos activos, para el desplegable de cargo del filtro. */
	@Transactional(readOnly = true)
	public List<Cargo> cargosVisibles() {
		return cargos.findByActivoTrueOrderByNombreAsc();
	}

	/**
	 * La capacidad legal del mes: por cada semana lunes a domingo que toque el
	 * periodo, el mínimo entre el tope semanal y el tope diario por los días de
	 * esa semana que caen dentro del mes.
	 */
	private static BigDecimal capacidadDelMes(YearMonth periodo, BigDecimal topeDia, BigDecimal topeSemana) {
		LocalDate desde = periodo.atDay(1);
		LocalDate hasta = periodo.atEndOfMonth();
		BigDecimal total = BigDecimal.ZERO;
		for (LocalDate lunes = desde.with(DayOfWeek.MONDAY); !lunes.isAfter(hasta); lunes = lunes.plusWeeks(1)) {
			LocalDate primero = lunes.isBefore(desde) ? desde : lunes;
			LocalDate domingo = lunes.plusDays(6);
			LocalDate ultimo = domingo.isAfter(hasta) ? hasta : domingo;
			long dias = ChronoUnit.DAYS.between(primero, ultimo) + 1;
			BigDecimal topeDeLaSemana = topeDia.multiply(BigDecimal.valueOf(dias));
			total = total.add(topeDeLaSemana.min(topeSemana));
		}
		return total;
	}

	/** Una fila del detalle: porcentaje del mes sobre la capacidad y su aviso. */
	private static NivelacionColaboradorDto fila(Colaborador colaborador, BigDecimal acumulado, BigDecimal capacidad,
			BigDecimal topeDia, BigDecimal topeSemana, Map<LocalDate, BigDecimal> dias) {
		BigDecimal delMes = capacidad.signum() > 0
				? acumulado.multiply(BigDecimal.valueOf(100)).divide(capacidad, 4, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;
		String motivo = excesoDeTope(dias, topeDia, topeSemana);

		/*
		 * El porcentaje es siempre el del mes, recortado a 100: romper un tope
		 * diario o semanal cambia el estado y añade el motivo, pero no esta cifra.
		 * Con horas se muestra al menos 1 % para que la barra no quede en cero.
		 */
		int porcentaje = Math.min(delMes.setScale(0, RoundingMode.HALF_UP).intValue(), 100);
		if (acumulado.signum() > 0 && porcentaje < 1) {
			porcentaje = 1;
		}

		String estado;
		String severidad;
		if (motivo != null) {
			estado = "Excede tope legal";
			severidad = "alta";
		} else if (acumulado.compareTo(capacidad) >= 0) {
			estado = "Tope alcanzado";
			severidad = "alta";
		} else if (delMes.compareTo(BigDecimal.valueOf(ZONA_ATENCION)) >= 0) {
			estado = "Cerca del tope";
			severidad = "media";
		} else {
			estado = "En rango";
			severidad = "baja";
		}
		return new NivelacionColaboradorDto(colaborador.getId(), colaborador.getNombreCompleto(),
				colaborador.getArea().getNombre(), cargoDe(colaborador), acumulado, capacidad, porcentaje, estado,
				severidad, motivo);
	}

	/**
	 * El motivo del tope roto por una persona, o {@code null} si no rompió ninguno.
	 *
	 * <p>Se comparan el peor día y la peor semana (lunes a domingo, con los días
	 * de la semana que caen fuera del mes, que ya se traen con las horas). El
	 * motivo prioriza el día —la infracción más concreta— y, si ningún día se
	 * pasa pero la semana sí, cita la semana.
	 */
	private static String excesoDeTope(Map<LocalDate, BigDecimal> dias, BigDecimal topeDia, BigDecimal topeSemana) {
		if (dias == null || dias.isEmpty()) {
			return null;
		}
		LocalDate peorDia = null;
		BigDecimal peorHoras = null;
		Map<LocalDate, BigDecimal> porSemana = new TreeMap<>();
		for (Map.Entry<LocalDate, BigDecimal> entrada : dias.entrySet()) {
			if (peorHoras == null || entrada.getValue().compareTo(peorHoras) > 0) {
				peorDia = entrada.getKey();
				peorHoras = entrada.getValue();
			}
			porSemana.merge(entrada.getKey().with(DayOfWeek.MONDAY), entrada.getValue(), BigDecimal::add);
		}
		LocalDate peorLunes = null;
		BigDecimal peorSemana = null;
		for (Map.Entry<LocalDate, BigDecimal> entrada : porSemana.entrySet()) {
			if (peorSemana == null || entrada.getValue().compareTo(peorSemana) > 0) {
				peorLunes = entrada.getKey();
				peorSemana = entrada.getValue();
			}
		}

		if (peorHoras.compareTo(topeDia) > 0) {
			return abreviar(peorDia) + ": " + dosDecimales(peorHoras) + " h (tope " + sinDecimales(topeDia) + ")";
		}
		if (peorSemana != null && peorSemana.compareTo(topeSemana) > 0) {
			return "semana del " + abreviar(peorLunes) + ": " + dosDecimales(peorSemana) + " h (tope "
					+ sinDecimales(topeSemana) + ")";
		}
		return null;
	}

	/** Horas sobre un tope, en porcentaje con cuatro decimales (100 % = el tope). */
	private static BigDecimal porcentajeDe(BigDecimal horas, BigDecimal tope) {
		return tope.signum() > 0
				? horas.multiply(BigDecimal.valueOf(100)).divide(tope, 4, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;
	}

	/**
	 * Las barras: una por área con filas visibles. La carga es la suma de las
	 * horas del área sobre el máximo legal de toda su plantilla, y las personas
	 * que rompen un tope se cuentan aparte.
	 */
	private static List<NivelacionAreaDto> barrasPorArea(List<NivelacionColaboradorDto> filas,
			List<Colaborador> personal, List<Colaborador> filtrados, BigDecimal capacidadPersona) {
		Map<Long, Long> areaDe = personal.stream().collect(Collectors.toMap(Colaborador::getId,
				colaborador -> colaborador.getArea().getId()));
		Map<Long, Integer> personas = new HashMap<>();
		for (Colaborador colaborador : filtrados) {
			personas.merge(colaborador.getArea().getId(), 1, Integer::sum);
		}
		Map<Long, List<NivelacionColaboradorDto>> porArea = filas.stream().collect(Collectors.groupingBy(
				fila -> areaDe.get(fila.colaboradorId()), LinkedHashMap::new, Collectors.toList()));

		List<NivelacionAreaDto> barras = new ArrayList<>();
		for (Map.Entry<Long, List<NivelacionColaboradorDto>> entrada : porArea.entrySet()) {
			List<NivelacionColaboradorDto> delArea = entrada.getValue();
			BigDecimal horas = delArea.stream().map(NivelacionColaboradorDto::horasEjecutadas)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			int plantilla = personas.getOrDefault(entrada.getKey(), delArea.size());
			BigDecimal capacidad = capacidadPersona.multiply(BigDecimal.valueOf(plantilla));
			BigDecimal exacto = porcentajeDe(horas, capacidad);
			int porcentaje = exacto.setScale(0, RoundingMode.HALF_UP).intValue();
			if (horas.signum() > 0 && porcentaje < 1) {
				porcentaje = 1;
			}
			String estado;
			String severidadCarga;
			if (exacto.compareTo(BigDecimal.valueOf(100)) > 0) {
				estado = "Excede tope legal";
				severidadCarga = "alta";
			} else if (exacto.compareTo(BigDecimal.valueOf(100)) == 0) {
				estado = "Tope alcanzado";
				severidadCarga = "alta";
			} else if (exacto.compareTo(BigDecimal.valueOf(ZONA_ATENCION)) >= 0) {
				estado = "Cerca del tope";
				severidadCarga = "media";
			} else {
				estado = "En rango";
				severidadCarga = "baja";
			}
			int personasEnAlerta = (int) delArea.stream().filter(NivelacionColaboradorDto::enAlerta).count();
			barras.add(new NivelacionAreaDto(delArea.get(0).area(), horas, capacidad, porcentaje, estado,
					severidadCarga, delArea.size(), plantilla, personasEnAlerta));
		}
		barras.sort(Comparator.comparingInt(NivelacionAreaDto::porcentaje).reversed()
				.thenComparing(Comparator.comparingInt(NivelacionAreaDto::personasEnAlerta).reversed())
				.thenComparing(NivelacionAreaDto::nombre));
		return barras;
	}

	/**
	 * El orden de las filas: primero lo que exige atención (peor severidad),
	 * luego el porcentaje de mayor a menor y, a igualdad, el nombre.
	 */
	private static Comparator<NivelacionColaboradorDto> orden() {
		return Comparator.comparingInt((NivelacionColaboradorDto fila) -> rango(fila.severidad())).reversed()
				.thenComparing(NivelacionColaboradorDto::porcentaje, Comparator.reverseOrder())
				.thenComparing(NivelacionColaboradorDto::nombreCompleto);
	}

	/** Peso de cada severidad, para ordenar de la más grave a la más leve. */
	private static int rango(String severidad) {
		return switch (severidad) {
			case "alta" -> 2;
			case "media" -> 1;
			default -> 0;
		};
	}

	private BigDecimal parametro(String clave, LocalDate fecha) {
		return parametros.findVigentePorClave(clave, fecha).map(ParametroLaboral::getValor)
				.orElseThrow(() -> new IllegalStateException(
						"No existe el parametro laboral " + clave + " para el periodo."));
	}

	private static String cargoDe(Colaborador colaborador) {
		return colaborador.getCargo() == null ? "Sin cargo" : colaborador.getCargo().getNombre();
	}

	/** Coincidencia del buscador: sin texto no filtra; por defecto, contiene. */
	private static boolean coincide(String valor, String texto) {
		if (texto == null || texto.isBlank()) {
			return true;
		}
		return valor != null && valor.toLowerCase(Locale.ROOT).contains(texto.trim().toLowerCase(Locale.ROOT));
	}

	/** Algo como {@code "12/oct"}. */
	private static String abreviar(LocalDate fecha) {
		return fecha.getDayOfMonth() + "/" + MESES_ABREV.get(fecha.getMonthValue() - 1);
	}

	private static String dosDecimales(BigDecimal valor) {
		return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private static String sinDecimales(BigDecimal valor) {
		return valor.stripTrailingZeros().toPlainString();
	}

	/**
	 * El panel ya calculado.
	 *
	 * @param periodo          mes del corte.
	 * @param etiqueta         algo como {@code "octubre de 2026"}.
	 * @param colaboradores    las filas de la tabla.
	 * @param areas            las barras de la gráfica.
	 * @param areasEnAlerta    cuántas áreas alcanzan o exceden el tope legal.
	 * @param personasEnAlerta cuántos colaboradores alcanzan o exceden el tope.
	 * @param horasDelMes      suma de las horas extra visibles del mes.
	 * @param capacidadMes     máximo legal de horas extra por persona en el mes.
	 * @param topeDia          tope legal diario vigente.
	 * @param topeSemana       tope legal semanal vigente.
	 */
	public record PanelNivelacion(YearMonth periodo, String etiqueta, List<NivelacionColaboradorDto> colaboradores,
			List<NivelacionAreaDto> areas, int areasEnAlerta, int personasEnAlerta, BigDecimal horasDelMes,
			BigDecimal capacidadMes, BigDecimal topeDia, BigDecimal topeSemana) {

		/** Sin datos: listas vacías y tarjetas en cero, sin romper la plantilla. */
		static PanelNivelacion vacio(YearMonth periodo, String etiqueta, BigDecimal capacidad, BigDecimal topeDia,
				BigDecimal topeSemana) {
			return new PanelNivelacion(periodo, etiqueta, List.of(), List.of(), 0, 0, BigDecimal.ZERO, capacidad,
					topeDia, topeSemana);
		}
	}
}
