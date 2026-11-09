/*
	Chips de hora rápida.

	Viven dentro del mismo .campo que su input (de ahí el closest), así que el
	mismo script sirve para el formulario de propuesta (input[type=time]) y
	para el ajuste de TH (input[type=datetime-local]): en el segundo solo se
	reemplaza la parte de la hora y la fecha escrita se conserva.

	El disparo de input y change es deliberado: lote.js y la validación del
	navegador reaccionan a esos eventos, y poner el valor a mano sin avisar
	dejaría el formulario creyendo que el campo sigue vacío.
*/
document.addEventListener('click', function (evento) {
	var destino = evento.target;
	if (!destino || !destino.closest) {
		return;
	}
	var chip = destino.closest('.chip-hora[data-hora]');
	if (!chip) {
		return;
	}
	evento.preventDefault();
	var campo = chip.closest('.campo');
	if (!campo) {
		return;
	}
	var input = campo.querySelector('input[type="time"], input[type="datetime-local"]');
	if (!input) {
		return;
	}
	var hora = chip.getAttribute('data-hora');
	if (input.type === 'datetime-local') {
		// Sin fecha todavía no hay a qué hora aplicar el chip: se enfoca el
		// campo para que el usuario elija el día y vuelva a pulsar.
		if (!input.value) {
			input.focus();
			return;
		}
		input.value = input.value.slice(0, 11) + hora;
	} else {
		input.value = hora;
	}
	input.dispatchEvent(new Event('input', { bubbles: true }));
	input.dispatchEvent(new Event('change', { bubbles: true }));
});
