package com.mardique.programacion.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.audit.AccionAuditada;
import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.audit.EntidadAuditada;
import com.mardique.programacion.audit.RegistroAuditoria;
import com.mardique.programacion.domain.Habilitacion;
import com.mardique.programacion.domain.Maquinaria;
import com.mardique.programacion.domain.TipoOperacion;
import com.mardique.programacion.domain.TipoOperacionHabilitacion;
import com.mardique.programacion.domain.TipoOperacionHabilitacionId;
import com.mardique.programacion.repository.ColaboradorHabilitacionRepository;
import com.mardique.programacion.repository.HabilitacionRepository;
import com.mardique.programacion.repository.MaquinariaRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.TipoOperacionHabilitacionRepository;
import com.mardique.programacion.repository.TipoOperacionRepository;

@Service
public class OperacionCatalogoService {

	private final TipoOperacionRepository operaciones;

	private final HabilitacionRepository habilitaciones;

	private final TipoOperacionHabilitacionRepository requisitos;

	private final ColaboradorHabilitacionRepository colaboradoresHabilitacion;

	private final ProgramacionExtendidoRepository programaciones;

	private final MaquinariaRepository maquinarias;

	private final AuditoriaService auditoria;

	public OperacionCatalogoService(TipoOperacionRepository operaciones, HabilitacionRepository habilitaciones,
			TipoOperacionHabilitacionRepository requisitos, ColaboradorHabilitacionRepository colaboradoresHabilitacion,
			ProgramacionExtendidoRepository programaciones, MaquinariaRepository maquinarias,
			AuditoriaService auditoria) {
		this.operaciones = operaciones;
		this.habilitaciones = habilitaciones;
		this.requisitos = requisitos;
		this.colaboradoresHabilitacion = colaboradoresHabilitacion;
		this.programaciones = programaciones;
		this.maquinarias = maquinarias;
		this.auditoria = auditoria;
	}

	@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<TipoOperacion> operaciones() {
		return operaciones.findByActivoTrueOrderByNombreAsc();
	}

	@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<Habilitacion> habilitaciones() {
		return habilitaciones.findByActivaTrueOrderByNombreAsc();
	}

	@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<TipoOperacionHabilitacion> requisitos(Long operacionId) {
		return requisitos.findByTipoOperacionId(operacionId);
	}

	@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<Maquinaria> maquinarias() {
		return maquinarias.findAllByOrderByIdentificadorAsc();
	}

	@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void crearMaquinaria(String identificador) {
		if (identificador == null || identificador.isBlank()) {
			throw new IllegalArgumentException("El identificador de la maquinaria es obligatorio.");
		}
		String limpio = identificador.trim().toUpperCase();
		if (maquinarias.findByIdentificador(limpio).isPresent()) {
			throw new IllegalStateException("La maquinaria ya existe.");
		}
		Maquinaria equipo = new Maquinaria();
		equipo.setIdentificador(limpio);
		Maquinaria guardada = maquinarias.save(equipo);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR, EntidadAuditada.MAQUINARIA)
				.entidadId(guardada.getId()).motivo("Maquinaria agregada al catálogo"));
	}

	@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void eliminarMaquinaria(Long maquinariaId) {
		Maquinaria equipo = maquinarias.findById(maquinariaId)
				.orElseThrow(() -> new IllegalArgumentException("La maquinaria no existe."));
		maquinarias.delete(equipo);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR, EntidadAuditada.MAQUINARIA)
				.entidadId(maquinariaId).motivo("Maquinaria quitada del catálogo"));
	}

	@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void crearOperacion(String codigo, String nombre) {
		if (codigo == null || codigo.isBlank() || nombre == null || nombre.isBlank()) {
			throw new IllegalArgumentException("Código y nombre de operación son obligatorios.");
		}
		if (operaciones.findByCodigo(codigo.trim().toUpperCase()).isPresent()) {
			throw new IllegalStateException("El código de operación ya existe.");
		}
		TipoOperacion operacion = new TipoOperacion();
		operacion.setCodigo(codigo.trim().toUpperCase());
		operacion.setNombre(nombre.trim());
		TipoOperacion guardada = operaciones.save(operacion);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR, EntidadAuditada.TIPO_OPERACION)
				.entidadId(guardada.getId()).motivo("Operación creada"));
	}

	@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void crearHabilitacion(String codigo, String nombre, String descripcion) {
		if (codigo == null || codigo.isBlank() || nombre == null || nombre.isBlank()) {
			throw new IllegalArgumentException("Código y nombre de aptitud son obligatorios.");
		}
		if (habilitaciones.findByCodigo(codigo.trim().toUpperCase()).isPresent()) {
			throw new IllegalStateException("El código de aptitud ya existe.");
		}
		Habilitacion habilitacion = new Habilitacion();
		habilitacion.setCodigo(codigo.trim().toUpperCase());
		habilitacion.setNombre(nombre.trim());
		habilitacion.setDescripcion(descripcion == null || descripcion.isBlank() ? null : descripcion.trim());
		Habilitacion guardada = habilitaciones.save(habilitacion);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR, EntidadAuditada.HABILITACION)
				.entidadId(guardada.getId()).motivo("Aptitud creada"));
	}

	@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void agregarRequisito(Long operacionId, Long habilitacionId) {
		if (requisitos.existsByIdTipoOperacionIdAndIdHabilitacionId(operacionId, habilitacionId)) {
			return;
		}
		TipoOperacion operacion = operaciones.findById(operacionId)
				.orElseThrow(() -> new IllegalArgumentException("La operación no existe."));
		Habilitacion habilitacion = habilitaciones.findById(habilitacionId)
				.orElseThrow(() -> new IllegalArgumentException("La aptitud no existe."));
		TipoOperacionHabilitacion relacion = new TipoOperacionHabilitacion();
		relacion.setId(new TipoOperacionHabilitacionId(operacionId, habilitacionId));
		relacion.setTipoOperacion(operacion);
		relacion.setHabilitacion(habilitacion);
		requisitos.save(relacion);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.EDITAR,
				EntidadAuditada.TIPO_OPERACION_HABILITACION).entidadId(operacionId)
				.motivo("Aptitud requerida agregada a operación"));
	}

	/**
	 * Quita una operación del catálogo.
	 *
	 * <p>El borrado se deshace solo de lo que cuelga de la operación en lugar de
	 * bloquearse: los requisitos son suyos y las programaciones que ya la citan
	 * quedan sin operación, que es lo que corresponde cuando esa operación deja
	 * de existir. Lo único que no se puede deshacer es borrar datos históricos,
	 * así que aquí solo se desvincula.
	 */
	@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void eliminarOperacion(Long operacionId) {
		TipoOperacion operacion = operaciones.findById(operacionId)
				.orElseThrow(() -> new IllegalArgumentException("La operación no existe."));
		long requisitosQuitados = requisitos.deleteByTipoOperacionId(operacionId);
		int desvinculadas = programaciones.desvincularTipoOperacion(operacionId);
		operaciones.delete(operacion);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR, EntidadAuditada.TIPO_OPERACION)
				.entidadId(operacionId).motivo(describir("Operación eliminada del catálogo", "requisito(s)",
						requisitosQuitados, "programación(es) desvinculadas", desvinculadas)));
	}

	/**
	 * Quita una aptitud o curso del catálogo.
	 *
	 * <p>Mismo criterio que con las operaciones: lo que la referencia desaparece
	 * con ella. Borrar la aptitud sin borrar sus vínculos dejaría filas apuntando
	 * a un id inexistente, que es exactamente lo que antes impedía el borrado.
	 */
	@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void eliminarHabilitacion(Long habilitacionId) {
		Habilitacion habilitacion = habilitaciones.findById(habilitacionId)
				.orElseThrow(() -> new IllegalArgumentException("La aptitud no existe."));
		long enOperaciones = requisitos.deleteByHabilitacionId(habilitacionId);
		long enPersonas = colaboradoresHabilitacion.deleteByHabilitacionId(habilitacionId);
		habilitaciones.delete(habilitacion);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR, EntidadAuditada.HABILITACION)
				.entidadId(habilitacionId)
				.motivo(describir("Aptitud eliminada del catálogo", "operación(es)", enOperaciones,
						"asignación(es) de personas", enPersonas)));
	}

	/**
	 * Desasocia una aptitud de una operación sin tocar ninguna de las dos.
	 *
	 * <p>Existe para poder corregir un requisito mal cargado: antes solo se podía
	 * agregar, y quitar exigía borrar la operación entera.
	 */
	@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void quitarRequisito(Long operacionId, Long habilitacionId) {
		TipoOperacionHabilitacionId id = new TipoOperacionHabilitacionId(operacionId, habilitacionId);
		if (!requisitos.existsById(id)) {
			return;
		}
		requisitos.deleteById(id);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR,
				EntidadAuditada.TIPO_OPERACION_HABILITACION).entidadId(operacionId)
				.motivo("Aptitud requerida quitada de la operación"));
	}

	/** Deja constancia en la bitácora de cuántos vínculos se borraron con el catálogo. */
	private static String describir(String accion, String primero, long totalPrimero, String segundo,
			long totalSegundo) {
		if (totalPrimero == 0 && totalSegundo == 0) {
			return accion;
		}
		return accion + " (" + totalPrimero + " " + primero + ", " + totalSegundo + " " + segundo + ")";
	}
}
