package com.mardique.programacion.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.ParametroLaboral;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.ParametroLaboralRepository;
import com.mardique.programacion.service.dto.NivelacionFilaDto;

/**
 * El supuesto sueldo de cada colaborador con sus horas extra de la quincena.
 *
 * <p>No es nómina: no paga ni modifica nada. Toma las horas ejecutadas del
 * periodo, las valora con {@link ValorHorasService} y las suma al salario base
 * para que Talento Humano vea, en teoría, a cuánto asciende quien trabajó
 * extra —y, con el umbral de nivelación, a quién conviene nivelar.
 *
 * <p>La quincena se calcula de la fecha (1a: 1-15, 2a: 16-fin de mes): la
 * pantalla no depende de que exista un corte de nómina abierto.
 */
@Service
public class NivelacionService {

	private static final String UMBRAL_NIVELACION = "UMBRAL_NIVELACION";

	private static final List<String> MESES = List.of("enero", "febrero", "marzo", "abril", "mayo", "junio",
			"julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre");

	private final ColaboradorRepository colaboradores;

	private final AreaRepository areas;

	private final HoraExtraEjecutadaRepository horas;

	private final ParametroLaboralRepository parametros;

	private final AlcanceAreaService alcance;

	private final SalarioService salarios;

	private final ValorHorasService valores;

	public NivelacionService(ColaboradorRepository colaboradores, AreaRepository areas,
			HoraExtraEjecutadaRepository horas, ParametroLaboralRepository parametros, AlcanceAreaService alcance,
			SalarioService salarios, ValorHorasService valores) {
		this.colaboradores = colaboradores;
		this.areas = areas;
		this.horas = horas;
		this.parametros = parametros;
		this.alcance = alcance;
		this.salarios = salarios;
		this.valores = valores;
	}

	/** Rango de fechas que cubre una quincena. */
	public record Quincena(LocalDate desde, LocalDate hasta) {

		/** Etiqueta para la pantalla y el archivo: {@code 1a quincena de octubre de 2026}. */
		public String etiqueta() {
			YearMonth periodo = YearMonth.from(desde);
			return (desde.getDayOfMonth() == 1 ? "1a" : "2a") + " quincena de "
					+ MESES.get(periodo.getMonthValue() - 1) + " de " + periodo.getYear();
		}
	}

	/**
	 * El rango de fechas de una quincena.
	 *
	 * @param periodo mes del corte.
	 * @param quincena 1 para el día 1 al 15, 2 para el 16 al fin de mes.
	 * @throws IllegalArgumentException si la quincena no es 1 ni 2.
	 */
	public Quincena periodoDe(YearMonth periodo, int quincena) {
		return switch (quincena) {
			case 1 -> new Quincena(periodo.atDay(1), periodo.atDay(15));
			case 2 -> new Quincena(periodo.atDay(16), periodo.atEndOfMonth());
			default -> throw new IllegalArgumentException("La quincena debe ser 1 o 2.");
		};
	}

	/**
	 * Las áreas activas dentro del alcance del usuario: exactamente las que
	 * ofrece el desplegable de filtro de la pantalla y sobre las que se puede
	 * filtrar.
	 */
	@Transactional(readOnly = true)
	public List<Area> areasVisibles() {
		List<Area> activas = areas.findByActivaTrueOrderByNombreAsc();
		Set<Long> permitidas = alcance.areasVisibles(activas.stream().map(Area::getId).toList());
		return activas.stream().filter(area -> permitidas.contains(area.getId())).toList();
	}

	/**
	 * Las filas de la pantalla: colaboradores con horas en la quincena, sin
	 * filtros de texto ni de área.
	 *
	 * @see #listar(YearMonth, int, String, Long)
	 */
	@Transactional(readOnly = true)
	public List<NivelacionFilaDto> listar(YearMonth periodo, int quincena) {
		return listar(periodo, quincena, null, null);
	}

	/**
	 * Las filas de la pantalla, acotadas por los filtros del formulario.
	 *
	 * <p>Quien no trabajó extra no aparece. El texto busca por nombre completo
	 * o cédula y el área acota a una sola área visible (una fuera del alcance
	 * devuelve lista vacía: es un filtro, no un error). Ninguno de los dos
	 * mueve el umbral: el promedio del área sigue calculándose sobre todo el
	 * personal visible, no solo sobre lo que dejó pasar el filtro.
	 *
	 * @param periodo mes del corte.
	 * @param quincena 1 o 2.
	 * @param q       texto a buscar en nombre o cédula; {@code null} o vacío no filtra.
	 * @param areaId  área a filtrar; {@code null} son todas las visibles.
	 * @return filas con quien requiere nivelación primero.
	 * @throws IllegalStateException si falta el umbral de nivelación vigente.
	 */
	@Transactional(readOnly = true)
	public List<NivelacionFilaDto> listar(YearMonth periodo, int quincena, String q, Long areaId) {
		Quincena rango = periodoDe(periodo, quincena);

		List<Area> visiblesAreas = areasVisibles();
		if (visiblesAreas.isEmpty()) {
			return List.of();
		}
		if (areaId != null && visiblesAreas.stream().noneMatch(area -> area.getId().equals(areaId))) {
			return List.of();
		}
		List<Long> visibles = areaId != null ? List.of(areaId)
				: visiblesAreas.stream().map(Area::getId).toList();

		List<Colaborador> personal = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(visibles);
		Map<Long, BigDecimal> porPersona = new HashMap<>();
		for (Object[] fila : horas.sumarHorasPorColaborador(rango.desde(), rango.hasta())) {
			porPersona.put((Long) fila[0], ((BigDecimal) fila[1]).setScale(2, RoundingMode.HALF_UP));
		}
		Map<Long, Map<TipoHoraExtra, BigDecimal>> porTipo = new HashMap<>();
		for (Object[] fila : horas.sumarHorasPorColaboradorYTipo(rango.desde(), rango.hasta())) {
			porTipo.computeIfAbsent((Long) fila[0], ignorado -> new EnumMap<>(TipoHoraExtra.class))
					.put((TipoHoraExtra) fila[1], (BigDecimal) fila[2]);
		}

		Map<Long, BigDecimal> sueldos = salarios.vigentesDe(
				personal.stream().map(Colaborador::getId).toList(), rango.hasta());
		BigDecimal umbral = parametros.findVigentePorClave(UMBRAL_NIVELACION, rango.hasta()).map(ParametroLaboral::getValor)
				.orElseThrow(() -> new IllegalStateException(
						"No existe el parametro laboral " + UMBRAL_NIVELACION + " para el periodo."));

		// El promedio es del ÁREA, no del área y el cargo: así un cargo con una
		// sola persona no queda exento de nivelar por no tener con quién
		// compararse. Es el mismo criterio con el que Proponer extendido marca
		// "requiere nivelación" (supera el umbral del área).
		Map<Long, BigDecimal> promedios = new HashMap<>();
		Map<Long, List<Colaborador>> grupos = personal.stream().collect(java.util.stream.Collectors
				.groupingBy(c -> c.getArea().getId(), java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));
		for (var grupo : grupos.entrySet()) {
			BigDecimal total = grupo.getValue().stream()
					.map(c -> porPersona.getOrDefault(c.getId(), BigDecimal.ZERO))
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			promedios.put(grupo.getKey(), total.divide(BigDecimal.valueOf(grupo.getValue().size()), 2,
					RoundingMode.HALF_UP));
		}

		return personal.stream()
				.filter(c -> porPersona.getOrDefault(c.getId(), BigDecimal.ZERO).signum() > 0)
				.filter(c -> coincide(c.getNombreCompleto(), q) || coincide(c.getDocumento(), q))
				.map(c -> {
					BigDecimal acumulado = porPersona.get(c.getId());
					BigDecimal promedio = promedios.get(c.getArea().getId());
					boolean requiere = promedio.signum() > 0
							&& acumulado.compareTo(promedio.multiply(BigDecimal.ONE.add(umbral))) > 0;
					BigDecimal sueldo = sueldos.get(c.getId());
					Map<TipoHoraExtra, BigDecimal> tipos = porTipo.getOrDefault(c.getId(), Map.of());
					BigDecimal valor = sueldo == null ? null
							: valores.valorDe(tipos, sueldo, rango.hasta()).orElse(null);
					BigDecimal supuesto = sueldo == null || valor == null ? null : sueldo.add(valor);
					return new NivelacionFilaDto(c.getId(), c.getNombreCompleto(), c.getArea().getNombre(),
							c.getCargo().getNombre(), sueldo, acumulado, tipos, valor, supuesto, requiere);
				})
				.sorted(Comparator.comparing(NivelacionFilaDto::requiereNivelacion, Comparator.reverseOrder())
						.thenComparing(NivelacionFilaDto::horasEjecutadas, Comparator.reverseOrder())
						.thenComparing(NivelacionFilaDto::nombreCompleto))
				.toList();
	}

	/**
	 * El mismo listado como libro Excel (.xlsx), listo para descargar.
	 *
	 * <p>Los anchos de columna se fijan a mano: {@code autoSizeColumn} mide con
	 * fuentes de AWT y puede fallar en un servidor headless. La descarga lleva
	 * los mismos filtros que la pantalla, para que el archivo coincida con lo
	 * que se ve.
	 *
	 * @param periodo mes del corte.
	 * @param quincena 1 o 2.
	 * @param q       texto a buscar en nombre o cédula, o {@code null}.
	 * @param areaId  área a filtrar, o {@code null}.
	 * @return el contenido del archivo.
	 */
	@Transactional(readOnly = true)
	public byte[] exportar(YearMonth periodo, int quincena, String q, Long areaId) {
		List<NivelacionFilaDto> filas = listar(periodo, quincena, q, areaId);
		try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			Sheet hoja = libro.createSheet("Nivelacion");

			Row titulo = hoja.createRow(0);
			titulo.createCell(0).setCellValue("Nivelacion de horas extra — " + periodoDe(periodo, quincena).etiqueta());

			CellStyle estiloEncabezado = libro.createCellStyle();
			Font negrita = libro.createFont();
			negrita.setBold(true);
			estiloEncabezado.setFont(negrita);

			String[] columnas = { "Colaborador", "Area", "Cargo", "Salario base", "Total horas extra",
					"Horas diurnas", "Horas nocturnas", "Horas dom. diurnas", "Horas dom. nocturnas",
					"Horas turno diurno", "Horas turno nocturno",
					"Valor horas extra", "Supuesto sueldo", "Nivelacion" };
			Row encabezado = hoja.createRow(1);
			for (int i = 0; i < columnas.length; i++) {
				Cell celda = encabezado.createCell(i);
				celda.setCellValue(columnas[i]);
				celda.setCellStyle(estiloEncabezado);
			}

			CellStyle estiloDinero = libro.createCellStyle();
			estiloDinero.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));
			CellStyle estiloHoras = libro.createCellStyle();
			estiloHoras.setDataFormat(libro.createDataFormat().getFormat("0.00"));

			int numeroFila = 2;
			for (NivelacionFilaDto fila : filas) {
				Row datos = hoja.createRow(numeroFila++);
				datos.createCell(0).setCellValue(fila.nombreCompleto());
				datos.createCell(1).setCellValue(fila.area());
				datos.createCell(2).setCellValue(fila.cargo());
				numerica(datos, 3, fila.salarioBase(), estiloDinero);
				numerica(datos, 4, fila.horasEjecutadas(), estiloHoras);
				numerica(datos, 5, horasDe(fila, TipoHoraExtra.EXTRA_DIURNA), estiloHoras);
				numerica(datos, 6, horasDe(fila, TipoHoraExtra.EXTRA_NOCTURNA), estiloHoras);
				numerica(datos, 7, horasDe(fila, TipoHoraExtra.EXTRA_DOMINICAL_DIURNA), estiloHoras);
				numerica(datos, 8, horasDe(fila, TipoHoraExtra.EXTRA_DOMINICAL_NOCTURNA), estiloHoras);
				numerica(datos, 9, horasDe(fila, TipoHoraExtra.TURNO_DIURNO), estiloHoras);
				numerica(datos, 10, horasDe(fila, TipoHoraExtra.TURNO_NOCTURNO), estiloHoras);
				numerica(datos, 11, fila.valorHorasExtra(), estiloDinero);
				numerica(datos, 12, fila.supuestoSueldo(), estiloDinero);
				datos.createCell(13).setCellValue(fila.requiereNivelacion() ? "Requiere nivelacion" : "En rango");
			}

			int[] anchos = { 30, 16, 16, 16, 14, 13, 15, 17, 19, 18, 18, 16, 16, 20 };
			for (int i = 0; i < anchos.length; i++) {
				hoja.setColumnWidth(i, anchos[i] * 256);
			}

			libro.write(salida);
			return salida.toByteArray();
		} catch (IOException excepcion) {
			throw new IllegalStateException("No se pudo generar el archivo Excel.", excepcion);
		}
	}

	/** Celda numérica; sin valor no se crea (la celda queda vacía en el Excel). */
	private static void numerica(Row fila, int columna, BigDecimal valor, CellStyle estilo) {
		if (valor == null) {
			return;
		}
		Cell celda = fila.createCell(columna);
		celda.setCellValue(valor.doubleValue());
		celda.setCellStyle(estilo);
	}

	private static BigDecimal horasDe(NivelacionFilaDto fila, TipoHoraExtra tipo) {
		return fila.horasPorTipo().getOrDefault(tipo, BigDecimal.ZERO);
	}

	/** Coincidencia del buscador: sin texto no filtra; por defecto, contiene. */
	private static boolean coincide(String valor, String texto) {
		if (texto == null || texto.isBlank()) {
			return true;
		}
		return valor != null && valor.toLowerCase(Locale.ROOT).contains(texto.trim().toLowerCase(Locale.ROOT));
	}
}
