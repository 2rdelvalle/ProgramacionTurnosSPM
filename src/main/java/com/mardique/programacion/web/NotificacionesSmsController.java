package com.mardique.programacion.web;

import java.util.NoSuchElementException;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mardique.programacion.domain.EstadoSms;
import com.mardique.programacion.domain.NotificacionSms;
import com.mardique.programacion.service.NotificacionSmsService;

/**
 * La bandeja de SMS: lo que la cola dejó para enviar, con su estado, intentos
 * y último error.
 *
 * <p>Es la contraparte visible de la cola programada: si un mensaje se quedó
 * en FALLIDO, aquí se ve por qué sin tocar la base de datos. No muestra datos
 * sensibles — el mensaje de SMS ya no los lleva por diseño.
 */
@Controller
public class NotificacionesSmsController {

	private final NotificacionSmsService sms;

	public NotificacionesSmsController(NotificacionSmsService sms) {
		this.sms = sms;
	}

	/**
	 * Los SMS más recientes, filtrables por estado. Un estado escrito mal cae
	 * en "todos", igual que los demás filtros escritos a mano en las URLs de
	 * esta aplicación.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','NOMINA','ADMIN')")
	@GetMapping("/notificaciones/sms")
	public String bandeja(@RequestParam(required = false) String estado, @RequestParam(defaultValue = "0") int pagina,
			Model modelo) {
		EstadoSms filtro = estadoDe(estado);
		Page<NotificacionSms> mensajes = sms.bandeja(filtro, pagina);

		modelo.addAttribute("mensajes", mensajes.getContent());
		modelo.addAttribute("estado", filtro == null ? "" : filtro.name());
		modelo.addAttribute("total", mensajes.getTotalElements());
		modelo.addAttribute("pagina", mensajes.getNumber());
		modelo.addAttribute("haySiguiente", mensajes.hasNext());
		return "notificaciones-sms";
	}

	/**
	 * Reintento manual de un mensaje que ya se rindió.
	 *
	 * <p>La cola automática se detiene al tope de intentos y el mensaje queda
	 * FALLIDO para siempre; este botón lo vuelve a poner en cola con las cuentas
	 * a cero. Si no estaba fallido o no existe, se vuelve a la bandeja con el
	 * aviso, no con una página de error.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','NOMINA','ADMIN')")
	@PostMapping("/notificaciones/sms/{id}/reintentar")
	public String reintentar(@PathVariable Long id, @RequestParam(required = false) String estado,
			RedirectAttributes redireccion) {
		try {
			sms.reintentar(id);
			redireccion.addFlashAttribute("mensaje",
					"Mensaje reintentado: vuelve a estar pendiente de envio por la cola.");
		} catch (NoSuchElementException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/notificaciones/sms" + (estado == null || estado.isBlank() ? "" : "?estado=" + estado);
	}

	private static EstadoSms estadoDe(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		try {
			return EstadoSms.valueOf(texto.trim().toUpperCase());
		} catch (IllegalArgumentException excepcion) {
			return null;
		}
	}
}
