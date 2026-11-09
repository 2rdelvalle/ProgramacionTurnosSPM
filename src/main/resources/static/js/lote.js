/*
 * Alta de varias propuestas de extendido en un solo envío.
 *
 * Por qué clonar la última ficha y no llevar una plantilla aparte: el marcado de
 * una ficha es largo (selectores anidados, optgroups por área, siete campos con
 * su mensaje de error). Si el HTML inicial y la plantilla de la ficha nueva los
 * escribieran por separado, habría dos lugares que corregir cada vez que se
 * agregue un campo, y tarde o temprano uno de los dos se queda viejo. Aquí la
 * ficha se clona de la última que ya está en la página, así que solo existe una
 * copia del marcado.
 *
 * El "+" solo se habilita cuando la ficha actual está completa. Es lo que
 * pidió el flujo: no tiene sentido ofrecer agregar otra cuando la que está
 * abierta todavía está a medio llenar, y una lista de fichas incompletas al
 * final obligaría a corregir más trabajo del necesario. La validación real la
 * sigue haciendo el servidor; esto es solo para no dejar llegar el caso.
 */
(function () {
	"use strict";

	var formulario = document.getElementById("formulario-lote");
	if (!formulario) {
		return;
	}

	var contenedor = document.getElementById("fichas");
	var botonAgregar = document.getElementById("agregar-ficha");
	var pista = document.getElementById("pista-agregar");
	var botonGuardar = document.getElementById("guardar-lote");

	function fichas() {
		return Array.prototype.slice.call(contenedor.querySelectorAll(".ficha"));
	}

	/*
	 * Reescribe los índices de todas las fichas para que sean 0, 1, 2... y en
	 * ese orden.
	 *
	 * Hace falta aunque solo se agregue al final, y no por gusto: el servidor
	 * puede repintar el formulario con las fichas que el usuario había quitado,
	 * y si al quitar se dejara un hueco en la numeración, Spring dejaría de
	 * enlazar los campos de ese hueco con la lista. Renumerar siempre es la
	 * opción que no depende de por dónde se entró.
	 *
	 * Thymeleaf escribe los id con un punto después del índice ("extendidos0.fecha")
	 * y los name con corchetes ("extendidos[0].fecha"), así que los dos formatos se
	 * tratan por separado, y el separador se conserva al renumerar. Antes el regex
	 * esperaba guion, no coincidía con nada y los id se quedaban duplicados en
	 * cuanto se agregaba una ficha.
	 *
	 * Los radios del "Tipo" (que ahora viven dentro de un .selector-pills) usan
	 * name="extendidos[0].tipo" e id="extendidos0_tipo1", "extendidos0_tipo2"...
	 * Los mismos regex cubren ambos casos, así que no hay que hacer nada extra.
	 */
	function renumerar() {
		fichas().forEach(function (ficha, indice) {
			ficha.setAttribute("data-indice", indice);

			ficha.querySelectorAll("[name]").forEach(function (campo) {
				campo.setAttribute("name", campo.getAttribute("name")
					.replace(/extendidos\[\d+\]/g, "extendidos[" + indice + "]"));
			});

			ficha.querySelectorAll("[id]").forEach(function (campo) {
				campo.setAttribute("id", campo.getAttribute("id")
					.replace(/extendidos\d+([._])/g, "extendidos" + indice + "$1"));
			});

			ficha.querySelectorAll("[for]").forEach(function (campo) {
				campo.setAttribute("for", campo.getAttribute("for")
					.replace(/extendidos\d+([._])/g, "extendidos" + indice + "$1"));
			});

			var numero = ficha.querySelector('[data-parte="numero"]');
			if (numero) {
				/*
				 * El título ahora es un <h3 data-parte="numero"> que arranca con
				 * "Propuesta 1". Se sustituye el contenido completo por el nuevo
				 * número, sin tocar el resto del título.
				 */
				numero.innerHTML = "Propuesta " + (indice + 1);
			}
		});
	}

	/*
	 * Oculta o muestra un elemento por el atributo hidden Y por style.display.
	 *
	 * El atributo por sí solo no basta: las clases del sistema declaran
	 * display:block, y eso le gana al display:none que el navegador aplica a
	 * [hidden] por defecto. Sin el style inline, el bloque de la referencia de
	 * emergencia aparece visible desde el primer render, antes de que el usuario
	 * marque la casilla.
	 */
	function ocultar(elemento, valor) {
		if (!elemento) {
			return;
		}
		elemento.hidden = valor;
		elemento.style.display = valor ? "none" : "";
	}

	/*
	 * Vacía una ficha recién clonada.
	 *
	 * Se limpian los valores y también las clases y los mensajes de error: si la
	 * ficha de la que se copió venía con un "la hora de fin debe ser posterior",
	 * ese texto se clonaría tal cual y la nueva aparecería marcada por un error
	 * que no es suyo.
	 */
	function limpiar(ficha) {
		ficha.querySelectorAll("input, select, textarea").forEach(function (campo) {
			if (campo.type === "date" || campo.type === "time" || campo.tagName === "SELECT"
				|| campo.type === "text" || !campo.type) {
				campo.value = "";
			}
		});

		/*
		 * El select de "Persona" no es estatico: cargarCandidatos() lo reescribe por
		 * completo con la lista filtrada y ordenada para la operacion de ESA ficha.
		 * cloneNode(true) copia esas <option> tal cual venian en la ficha original, así
		 * que una ficha nueva heredaba el personal (y los "no aptos") de la operación
		 * de la ficha anterior hasta que se disparaba un fetch nuevo. Se repone aquí la
		 * única opción neutral para que no quede candidato alguno de otra operación.
		 */
		var persona = campo(ficha, "colaboradorId");
		if (persona) {
			persona.innerHTML = "<option value=''>Seleccione...</option>";
		}

		/*
		 * La hora de inicio puede haber quedado fija por el modo EXTENDIDO
		 * (readOnly, con sus chips ocultos). Una ficha clonada arranca
		 * editable: si no, el "+" dejaría un campo vacío que nadie puede
		 * escribir hasta que se vuelva a elegir persona y tipo.
		 */
		ficha.querySelectorAll('input[type=time]').forEach(function (hora) {
			hora.readOnly = false;
			hora.title = '';
			var chips = hora.parentNode ? hora.parentNode.querySelector('.chips-horas') : null;
			if (chips) {
				chips.hidden = false;
			}
		});

		/*
		 * Lo mismo para las cajas que el script del formulario llena al
		 * aplicar el modo y al calcular las horas: el texto del original se
		 * clonaría tal cual y la nueva ficha nacería explicando un horario
		 * que todavía no tiene.
		 */
		ocultar(ficha.querySelector('[data-parte="horario-habitual"]'), true);
		var avisoHoras = ficha.querySelector('[data-parte="aviso-duracion"]');
		if (avisoHoras) {
			avisoHoras.hidden = true;
			avisoHoras.textContent = '';
			avisoHoras.className = 'aviso-duracion';
		}

		ficha.querySelectorAll(".campo--error").forEach(function (campo) {
			campo.classList.remove("campo--error");
		});

		ficha.querySelectorAll(".campo__error").forEach(function (error) {
			error.textContent = "";
		});

		/*
		 * Las casillas no entran en el barrido de arriba porque no son un valor
		 * que se pueda vaciar: si la ficha de la que se copió venía marcada como
		 * emergencia, la nueva nacería en emergencia, con su evento ya escrito y su
		 * bloque abierto, sin que nadie lo haya pedido.
		 */
		ficha.querySelectorAll('input[type=checkbox]').forEach(function (marca) {
			marca.checked = false;
		});

		/*
		 * Los radios del "Tipo" tampoco son un valor que se vacíe con value="":
		 * cloneNode(true) copia el estado checked del clon, así que una ficha
		 * nueva heredaría el tipo marcado en la anterior y el usuario tendría
		 * que desmarcarlo a mano antes de elegir el suyo. Se desmarcan todos y
		 * el navegador los repinta como "sin selección".
		 */
		ficha.querySelectorAll('input[type=radio]').forEach(function (radio) {
			radio.checked = false;
		});

		ocultar(ficha.querySelector("[data-excepcion]"), true);
		ficha.classList.remove("ficha--emergencia");

		var compatibilidad = ficha.querySelector("[data-compatibilidad]");
		if (compatibilidad) {
			compatibilidad.textContent = "Seleccione área, operación y fecha para ordenar candidatos.";
			compatibilidad.className = "compatibilidad";
		}
	}

	function campo(ficha, sufijo) {
		return ficha.querySelector('[name$=".' + sufijo + '"]');
	}

	function esEmergencia(ficha) {
		var marca = campo(ficha, "esEmergencia");
		return marca ? marca.checked : false;
	}

	/*
	 * El tramo que se está proponiendo, en el formato que espera el servidor.
	 *
	 * Va en la consulta porque sin horas no hay contra qué comparar la agenda: sin
	 * este dato el listado no puede decir quién tiene choque ni cuánto descanso le
	 * queda, y el coordinador elige a ciegas hasta que guarda y el servidor lo
	 * rechaza.
	 */
	function tramoDe(ficha) {
		var fecha = campo(ficha, "fecha");
		var desde = campo(ficha, "horaInicio");
		var hasta = campo(ficha, "horaFin");
		if (!fecha || !fecha.value || !desde || !hasta || !desde.value || !hasta.value) {
			return null;
		}
		return { inicio: fecha.value + "T" + desde.value, fin: fecha.value + "T" + hasta.value };
	}

	/* Muestra u oculta lo que la ficha necesita según cómo va. */
	function sincronizarEmergencia(ficha) {
		ocultar(ficha.querySelector("[data-referencia]"), !esEmergencia(ficha));
		ficha.classList.toggle("ficha--emergencia", esEmergencia(ficha));
	}

	/*
	 * Abre el motivo de la excepción cuando la persona elegida necesita una
	 * explicación, y lo cierra cuando no.
	 *
	 * También lo abre si ya hay algo escrito o un mensaje del servidor adentro. Si
	 * el bloque se cerrara solo, un rechazo quedaría invisible justo en la ficha a la
	 * que pertenece, que es lo contrario de lo que hace el resto de los errores.
	 */
	function revisarExcepcion(ficha) {
		var bloque = ficha.querySelector("[data-excepcion]");
		if (!bloque) {
			return;
		}
		var persona = campo(ficha, "colaboradorId");
		var elegida = persona && persona.selectedOptions.length ? persona.selectedOptions[0] : null;
		var necesitaExcepcion = elegida && elegida.dataset.excepcion === "1";
		var motivo = bloque.querySelector("textarea");
		var escrito = motivo && motivo.value.trim() !== "";
		var conError = bloque.querySelector(".campo__error") !== null;
		ocultar(bloque, !(necesitaExcepcion || escrito || conError));
	}

	/*
	 * Abre el motivo si la persona elegida lo necesita y, si el bloque estaba
	 * cerrado, lleva el foco al textarea: lo que sigue es escribir ahí, y esperar a
	 * que el guardado lo revele con un error cuesta un envío completo y un repintado
	 * que parece una recarga.
	 */
	function abrirExcepcionSiCorresponde(ficha) {
		var bloque = ficha.querySelector("[data-excepcion]");
		var estabaAbierto = bloque ? !bloque.hidden : true;
		revisarExcepcion(ficha);
		if (bloque && !estabaAbierto && !bloque.hidden) {
			var motivo = bloque.querySelector("textarea");
			if (motivo && !motivo.value.trim()) {
				motivo.focus();
			}
		}
	}

	/*
	 * Marca el textarea del motivo y le dice al coordinador qué falta escribir.
	 *
	 * Es el mismo aviso que dejaría el servidor, pero antes de que el formulario
	 * viaje: no se puede pedir required en el marcado porque el motivo solo es
	 * obligatorio cuando la persona lo necesita (y ni siquiera entonces en una
	 * emergencia), así que la regla se aplica aquí, al enviar.
	 */
	function mostrarMotivoFaltante(motivo) {
		motivo.classList.add("campo--error");
		var etiqueta = motivo.closest(".campo");
		var aviso = etiqueta ? etiqueta.querySelector(".campo__error") : null;
		if (!aviso) {
			aviso = document.createElement("span");
			aviso.className = "campo__error";
			if (etiqueta) {
				etiqueta.appendChild(aviso);
			}
		}
		aviso.textContent = "Escriba por qué se elige a esta persona.";
		if (window.mdqFocus) {
			window.mdqFocus(motivo);
		} else {
			motivo.focus();
		}
	}

	function cargarCandidatos(ficha) {
		var area = campo(ficha, "areaId");
		var operacion = campo(ficha, "tipoOperacionId");
		var fecha = campo(ficha, "fecha");
		var persona = campo(ficha, "colaboradorId");
		var aviso = ficha.querySelector("[data-compatibilidad]");
		if (!area || !fecha || !persona || !area.value || !fecha.value) {
			/*
			 * Antes este caso se quedaba en silencio: el selector de persona no
			 * mostraba a nadie y el aviso no decía nada, y parecía que la pantalla
			 * estaba rota. Ahora se dice qué falta.
			 */
			if (aviso) {
				aviso.textContent = "Seleccione área y fecha para ver el personal disponible.";
				aviso.className = "compatibilidad";
			}
			return;
		}
		var params = new URLSearchParams({ areaId: area.value, fecha: fecha.value });
		if (operacion && operacion.value) {
			params.set("tipoOperacionId", operacion.value);
		}
		var tramo = tramoDe(ficha);
		if (tramo) {
			params.set("inicio", tramo.inicio);
			params.set("fin", tramo.fin);
		}
		if (esEmergencia(ficha)) {
			params.set("emergencia", "true");
		}
		if (aviso) {
			aviso.textContent = "Consultando compatibilidad y acumulado...";
			aviso.className = "compatibilidad";
		}
		fetch("/programaciones/candidatos?" + params.toString(), { headers: { Accept: "application/json" } })
			.then(function (respuesta) {
				if (!respuesta.ok) throw new Error("No se pudo consultar el personal.");
				return respuesta.json();
			})
			.then(function (candidatos) {
				var seleccionado = persona.value;
				persona.innerHTML = "<option value=''>Seleccione...</option>";
				var conExcepcion = [];
				var sinDisponibilidad = [];
				candidatos.forEach(function (candidato) {
					var marcas = [];
					if (candidato.requiereNivelacion) {
						marcas.push("REQUIERE NIVELACIÓN");
					}
					if (!candidato.cumpleAptitud) {
						marcas.push("SIN LA APTITUD DE LA OPERACIÓN");
					}
					if (!candidato.disponible) {
						marcas.push(candidato.detalle || "NO DISPONIBLE");
					}
					var opcion = document.createElement("option");
					opcion.value = candidato.id;
					opcion.textContent = candidato.nombreCompleto + " · " + candidato.horasEjecutadas + " h"
						+ (marcas.length ? " · " + marcas.join(" · ") : "");
					opcion.dataset.excepcion =
						(candidato.requiereNivelacion || !candidato.cumpleAptitud) ? "1" : "0";
					/*
					 * Nadie queda deshabilitado. Antes se bloqueaba a quien no
					 * cumplía los requisitos y no había forma de marcar la excepción:
					 * la única salida era no proponerlo. Ahora se puede elegir y
					 * aparece el motivo; el bloqueo real, el de choque y ausencia,
					 * lo aplica el servidor al guardar con su mensaje.
					 */
					if (candidato.id == seleccionado) {
						opcion.selected = true;
					}
					persona.appendChild(opcion);
					if (candidato.requiereNivelacion || !candidato.cumpleAptitud) {
						conExcepcion.push(candidato.nombreCompleto);
					}
					if (!candidato.disponible) {
						sinDisponibilidad.push(candidato.nombreCompleto);
					}
			});
			revisarExcepcion(ficha);
			if (aviso) {
				/*
				 * Sin candidatos no hay nada que mostrar, y decir "personal
				 * ordenado" cuando la lista está vacía es una mentira que se
				 * confunde con un fallo.
				 */
				if (candidatos.length === 0) {
					aviso.textContent = "No hay personal disponible para ese área y fecha.";
					aviso.className = "compatibilidad compatibilidad--alerta";
					return;
				}
				var resumen = [];
				if (conExcepcion.length) {
					resumen.push("Requieren excepción: " + conExcepcion.slice(0, 3).join(" | "));
				}
				if (sinDisponibilidad.length) {
					resumen.push("No disponibles: " + sinDisponibilidad.slice(0, 3).join(" | "));
				}
				aviso.textContent = resumen.length ? resumen.join(" · ")
					: "Personal ordenado por compatibilidad y menor acumulado de horas extra.";
				aviso.className = "compatibilidad "
					+ (resumen.length ? "compatibilidad--alerta" : "compatibilidad--ok");
			}
		})
			.catch(function () {
				if (aviso) {
					aviso.textContent = "No se pudo cargar la compatibilidad; puede continuar y el servidor validará la propuesta.";
					aviso.className = "compatibilidad compatibilidad--alerta";
				}
			});
	}

	function agregar() {
		var lista = fichas();
		if (lista.length === 0) {
			return;
		}

		var anterior = lista[lista.length - 1];
		var copia = anterior.cloneNode(true);
		limpiar(copia);
		contenedor.appendChild(copia);
		renumerar();
		sincronizar();

		/*
		 * La ficha nueva hereda el área y la fecha de la anterior.
		 *
		 * Antes nacía vacía y el coordinador se encontraba un selector de persona
		 * sin nadie dentro y un aviso que no decía nada: parecía roto. Con el área
		 * y la fecha ya puestas, el personal carga de inmediato y se ve que la
		 * pantalla funciona.
		 */
		var nueva = fichas()[fichas().length - 1];
		var areaAnterior = campo(anterior, "areaId");
		var fechaAnterior = campo(anterior, "fecha");
		if (areaAnterior && areaAnterior.value) {
			campo(nueva, "areaId").value = areaAnterior.value;
		}
		if (fechaAnterior && fechaAnterior.value) {
			campo(nueva, "fecha").value = fechaAnterior.value;
		}
		cargarCandidatos(nueva);

		var primerCampo = nueva.querySelector("select");
		if (primerCampo) {
			primerCampo.focus();
		}
	}

	/*
	 * Además de renumerar y sincronizar, hay que volver a consultar los
	 * candidatos de TODAS las fichas. Si el usuario elimina una ficha
	 * intermedia, las siguientes cambian de índice; su <select> de persona sigue
	 * apuntando a las opciones que cargó con el índice viejo, y el servidor
	 * rechazaría los ids por no corresponderle al nuevo índice. Volver a
	 * consultar deja cada ficha coherente con su nueva posición.
	 */
	function quitar(ficha) {
		if (fichas().length <= 1) {
			// Nunca se queda el formulario sin ninguna ficha: el envío lo exige
			// y una pantalla vacía no ofrece salida.
			return;
		}
		ficha.remove();
		renumerar();
		sincronizar();
		fichas().forEach(function (restante) {
			cargarCandidatos(restante);
		});
	}

	/*
	 * ¿Está la ficha completa? Solo miramos los campos obligatorios.
	 *
	 * Los radios del "Tipo" (que ahora son .selector-pills) no entran en el
	 * barrido de [required] porque un radio individual con required falla
	 * checkValidity() aunque sus hermanos del mismo grupo sí estén marcados.
	 * Se validan aparte, como grupo.
	 */
	function completa(ficha) {
		var obligatorios = ficha.querySelectorAll(
			"input[required]:not([type=radio]), select[required], textarea[required]");
		for (var i = 0; i < obligatorios.length; i++) {
			if (!obligatorios[i].value || !obligatorios[i].checkValidity()) {
				return false;
			}
		}

		/*
		 * Grupo de radios del tipo: si existe al menos uno con required,
		 * exigimos que alguno esté marcado. El campo "tipo" del formulario es
		 * obligatorio, así que siempre habrá al menos uno.
		 */
		var tipos = ficha.querySelectorAll('input[type=radio][name$=".tipo"]');
		if (tipos.length > 0) {
			var marcado = false;
			tipos.forEach(function (r) { if (r.checked) marcado = true; });
			if (!marcado) return false;
		}

		/*
		 * Los dos campos de la excepción y la emergencia no pueden llevar el atributo
		 * required porque no siempre aplican: la referencia solo cuando la ficha es
		 * de emergencia, y el motivo solo cuando la persona elegida necesita
		 * excepción. Marcarlos aquí y no en el marcado es lo que evita el error
		 * clásico de un formulario que no deja enviar porque pide un dato que esa
		 * ficha no tiene por qué tener.
		 *
		 * En emergencia el motivo no se pide tampoco cuando el bloque está abierto:
		 * el evento que la originó ya lo documenta, que es la misma regla que aplica
		 * el servidor en problemasDeExcepcion.
		 */
		var referencia = campo(ficha, "referenciaEvento");
		if (esEmergencia(ficha) && referencia && !referencia.value.trim()) {
			return false;
		}
		var excepcion = ficha.querySelector("[data-excepcion]");
		if (excepcion && !excepcion.hidden && !esEmergencia(ficha)) {
			var motivo = excepcion.querySelector("textarea");
			if (motivo && !motivo.value.trim()) {
				return false;
			}
		}
		return true;
	}

	/*
	 * Deja la pantalla coherente: qué botón se puede pulsar, cuántas fichas hay
	 * y cómo se llama el botón de guardado.
	 */
	function sincronizar() {
		var lista = fichas();
		var ultima = lista[lista.length - 1];

		// El "quitar" solo tiene sentido con más de una ficha.
		lista.forEach(function (ficha) {
			var boton = ficha.querySelector('[data-accion="quitar"]');
			if (boton) {
				boton.hidden = lista.length <= 1;
			}
		});

		if (botonGuardar) {
			var texto = botonGuardar.querySelector('[data-parte="guardar"]');
			if (texto) {
				texto.textContent = lista.length === 1 ? "Guardar propuesta" : "Guardar " + lista.length + " propuestas";
			}
		}

		if (!ultima || !botonAgregar) {
			return;
		}

		var listaCompleta = completa(ultima);
		botonAgregar.disabled = !listaCompleta;

		if (pista) {
			if (listaCompleta) {
				pista.textContent = "Puede seguir agregando o guardar estas " + lista.length + " propuestas.";
				pista.classList.add("agregar__pista--listo");
			} else {
				pista.textContent = "Complete la propuesta " + lista.length
					+ " para poder agregar otra.";
				pista.classList.remove("agregar__pista--listo");
			}
		}
	}

	/*
	 * El motivo de la excepción se revisa aquí, al enviar, y no con required en el
	 * marcado, porque solo hace falta cuando la persona elegida lo necesita —y ni
	 * siquiera entonces en una emergencia—. Sin esta barrera el navegador mandaría
	 * el formulario igual, el servidor lo rechazaría y la página volvería
	 * repintada, que es justo el refresco que hacía perder la fecha escrita.
	 */
	formulario.addEventListener("submit", function (evento) {
		var pendiente = null;
		fichas().forEach(function (ficha) {
			if (pendiente || esEmergencia(ficha)) {
				return;
			}
			var bloque = ficha.querySelector("[data-excepcion]");
			if (!bloque || bloque.hidden) {
				return;
			}
			var motivo = bloque.querySelector("textarea");
			if (motivo && !motivo.value.trim()) {
				pendiente = motivo;
			}
		});
		if (pendiente) {
			evento.preventDefault();
			mostrarMotivoFaltante(pendiente);
		}
	});

	formulario.addEventListener("click", function (evento) {
		var boton = evento.target.closest('[data-accion="quitar"]');
		if (boton) {
			evento.preventDefault();
			quitar(boton.closest(".ficha"));
		}
	});

	if (botonAgregar) {
		botonAgregar.addEventListener("click", agregar);
	}

	// El "+" se habilita mientras se escribe, no solo al salir del campo.
	formulario.addEventListener("input", sincronizar);
	formulario.addEventListener("change", sincronizar);
	formulario.addEventListener("change", function (evento) {
		var ficha = evento.target.closest(".ficha");
		if (!ficha) {
			return;
		}
		/*
		 * Elegir a la persona va primero y no consulta de nuevo: la lista ya viene
		 * ordenada para el área, la operación y el tramo de esta ficha, y la marca de
		 * excepción está en la opción. Antes este select caía en la rama genérica de
		 * abajo, volvía a consultar y el motivo recién se abría cuando la respuesta
		 * llegaba —o nunca, si la consulta fallaba—, de modo que elegir a alguien no
		 * apto no mostraba nada y el error aparecía recién al guardar.
		 */
		if (evento.target.matches('[name$=".colaboradorId"]')) {
			abrirExcepcionSiCorresponde(ficha);
			return;
		}
		/*
		 * Cualquier otra cosa que cambie a quién está disponible obliga a reconsultar:
		 * sin las horas no hay choque ni descanso que comparar, así que las horas
		 * también entran. La casilla va aparte porque además de reordenar la lista
		 * destapa el campo del evento.
		 */
		if (evento.target.matches("[data-parte=emergencia]")) {
			sincronizarEmergencia(ficha);
			cargarCandidatos(ficha);
			return;
		}
		if (evento.target.matches("select, input[type=date], input[type=time]")) {
			cargarCandidatos(ficha);
		}
	});
	formulario.addEventListener("input", function (evento) {
		var ficha = evento.target.closest(".ficha");
		if (!ficha || !evento.target.closest("[data-excepcion]")) {
			return;
		}
		// El motivo ya escrito es motivo suficiente para no cerrar el bloque.
		revisarExcepcion(ficha);
		/*
		 * Escrito el motivo, desaparece el aviso: el del servidor, si vino de un
		 * repintado, y el del navegador, si se quedó sin enviar en el intento
		 * anterior. Si el texto sigue vacío, el aviso se queda hasta que se escriba.
		 */
		if (evento.target.value.trim()) {
			evento.target.classList.remove("campo--error");
			var aviso = evento.target.parentNode.querySelector(".campo__error");
			if (aviso) {
				aviso.textContent = "";
			}
		}
	});

	/*
	 * Al abrir la página con fichas que el servidor ya traía, los índices se
	 * normalizan antes de nada. Si el servidor repintó tras un error y el
	 * navegador recargó, son los mismos, pero normalizar es barato y evita
	 * depender de ese supuesto.
	 */
	renumerar();
	sincronizar();
	fichas().forEach(function (ficha) {
		/*
		 * El servidor devuelve la ficha como quedó: con la emergencia marcada y su
		 * evento, y con el motivo de la excepción escrito si la hubo. Antes de
		 * buscar nada hay que dejar la ficha igual de visible que cuando se envió.
		 */
		sincronizarEmergencia(ficha);
		revisarExcepcion(ficha);
		cargarCandidatos(ficha);
	});
})();