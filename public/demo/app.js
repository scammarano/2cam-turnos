(function () {
  'use strict';
  const queue = new TurnosDemo.Queue();
  const displays = {a: new TurnosDemo.Display(), b: new TurnosDemo.Display()};
  const labels = {waiting: 'En espera', called: 'Llamado', serving: 'En atención', finished: 'Finalizado'};
  const el = id => document.getElementById(id);
  let key = 0;
  Object.values(displays).forEach(display => display.snapshot(queue.snapshot()));
  function render() {
    el('waiting').textContent = queue.records.filter(row => row.state === 'waiting').length;
    el('finished').textContent = queue.records.filter(row => row.state === 'finished').length;
    el('sequence').textContent = queue.sequence;
    el('operator-state').textContent = queue.current ? queue.current.label + ' · ' + labels[queue.current.state] + ' · Ventanilla ' + queue.current.counter_id : 'Sin llamado activo';
    el('call').disabled = Boolean(queue.current) || !queue.records.some(row => row.state === 'waiting');
    el('counter').disabled = Boolean(queue.current);
    el('repeat').disabled = !queue.current || queue.current.state !== 'called';
    el('start').disabled = !queue.current || queue.current.state !== 'called';
    el('finish').disabled = !queue.current || queue.current.state !== 'serving';
    const body = el('queue-body');
    body.replaceChildren();
    const rows = queue.records.slice(-30).reverse();
    if (!rows.length) {
      const row = document.createElement('tr'), cell = document.createElement('td');
      cell.colSpan = 3; cell.textContent = 'Todavía no hay tickets'; row.append(cell); body.append(row);
    }
    rows.forEach(ticket => {
      const row = document.createElement('tr');
      [ticket.label, labels[ticket.state], ticket.counter_id ? 'Ventanilla ' + ticket.counter_id : '—'].forEach(value => {
        const cell = document.createElement('td'); cell.textContent = value; row.append(cell);
      });
      body.append(row);
    });
    Object.entries(displays).forEach(([id, display]) => {
      el('ticket-' + id).textContent = display.current ? display.current.ticket_label : '—';
      el('counter-' + id).textContent = display.current ? display.current.counter_label : 'Espere su llamado';
      el('status-' + id).textContent = display.connected ? 'En línea · simulado' : 'SIN CONEXIÓN · Estado desactualizado';
      el('status-' + id).classList.toggle('offline', !display.connected);
      el('sync-' + id).textContent = 'Secuencia ' + display.sequence + (display.lastSync ? ' · Último estado recibido: ' + display.lastSync : '');
    });
    el('events').textContent = queue.log.length ? JSON.stringify(queue.log[queue.log.length - 1], null, 2) : 'Sin eventos';
  }
  ['issue', 'call', 'repeat', 'start', 'finish'].forEach(action => el(action).addEventListener('click', () => {
    try {
      const result = queue.execute('demo-command-' + ++key, action, {counter_id: Number(el('counter').value)});
      Object.values(displays).forEach(display => display.accept(result.event));
      if (action === 'issue') el('issued').textContent = 'Ticket emitido: ' + result.ticket.label;
      el('feedback').textContent = result.ticket.label + ' · ' + labels[result.ticket.state];
    } catch (error) { el('feedback').textContent = error.message; }
    render();
  }));
  el('network').addEventListener('click', () => {
    if (displays.b.connected) {
      displays.b.connected = false;
      el('network').textContent = 'Reconectar pantalla B';
      el('network').setAttribute('aria-pressed', 'true');
      el('feedback').textContent = 'Pantalla B desconectada: conserva el último estado recibido, marcado como desactualizado.';
    } else {
      const mode = displays.b.reconnect(queue);
      el('network').textContent = 'Desconectar pantalla B';
      el('network').setAttribute('aria-pressed', 'false');
      el('feedback').textContent = 'Pantalla B recuperada mediante ' + mode + '. Audio no implementado en T0.';
    }
    render();
  });
  el('reset').addEventListener('click', () => { if (window.confirm('¿Borrar los datos ficticios y reiniciar la demo?')) window.location.reload(); });
  el('export').addEventListener('click', () => {
    const blob = new Blob([queue.log.map(event => JSON.stringify(event)).join('\n') + '\n'], {type: 'application/x-ndjson'});
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url; link.download = 'turnos-demo-eventos.jsonl'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    el('feedback').textContent = 'Exportados los últimos ' + queue.log.length + ' eventos ficticios (máximo 100).';
  });
  render();
}());
