/* T0 synthetic single-process simulator. Never use as a production queue authority. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.TurnosDemo = factory();
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const copy = value => JSON.parse(JSON.stringify(value));
  class Queue {
    constructor(options = {}) {
      this.clock = options.clock || (() => new Date().toISOString());
      this.retention = options.retention || 100;
      this.sequence = 0;
      this.callSequence = 0;
      this.number = 0;
      this.records = [];
      this.log = [];
      this.receipts = new Map();
      this.current = null;
    }
    execute(key, action, input = {}) {
      if (typeof key !== 'string' || key.length < 1 || key.length > 128) throw new Error('Clave idempotente inválida');
      if (!['issue', 'call', 'repeat', 'start', 'finish'].includes(action)) throw new Error('Acción desconocida');
      if (input.area_id !== undefined && input.area_id !== 5) throw new Error('Área fuera del ámbito de demostración');
      if (input.counter_id !== undefined && ![1, 2].includes(input.counter_id)) throw new Error('Puesto desconocido');
      const normalized = {area_id: 5, counter_id: input.counter_id || 1};
      const fingerprint = JSON.stringify([action, normalized]);
      const previous = this.receipts.get(key);
      if (previous) {
        if (previous.fingerprint !== fingerprint) throw new Error('Clave reutilizada para otra operación');
        return copy(previous.result);
      }
      if (this.receipts.size >= 1000) throw new Error('Límite de la demo: recarga para empezar de nuevo');
      let ticket, type;
      if (action === 'issue') {
        ticket = {id: ++this.number, label: 'A-' + String(this.number).padStart(3, '0'), state: 'waiting', counter_id: null};
        this.records.push(ticket);
        type = 'turnos.ticket_issued';
      } else if (action === 'call') {
        if (this.current) throw new Error('Finaliza la atención actual antes de llamar otro ticket');
        ticket = this.records.find(row => row.state === 'waiting');
        if (!ticket) throw new Error('No hay tickets en espera');
        ticket.state = 'called';
        ticket.counter_id = normalized.counter_id;
        this.current = ticket;
        ++this.callSequence;
        type = 'turnos.ticket_called';
      } else {
        ticket = this.current;
        if (!ticket) throw new Error('No hay un llamado activo');
        if (action === 'repeat') {
          if (ticket.state !== 'called') throw new Error('Solo se repite un ticket pendiente de atención');
          ++this.callSequence;
          type = 'turnos.call_repeated';
        } else if (action === 'start') {
          if (ticket.state !== 'called') throw new Error('El ticket ya está en atención');
          ticket.state = 'serving';
          type = 'turnos.service_started';
        } else {
          if (ticket.state !== 'serving') throw new Error('Inicia la atención antes de finalizar');
          ticket.state = 'finished';
          this.current = null;
          type = 'turnos.service_finished';
        }
      }
      const event = {
        schema: '2cam.events.v1', event_id: 'demo-event-' + (this.sequence + 1),
        sequence: ++this.sequence, occurred_at: this.clock(), source: 'turnos', type,
        tenant_ref: 'turnos:company:1', scope: {site_id: 2, area_id: 5},
        payload: {
          ticket_id: ticket.id, ticket_label: ticket.label, state: ticket.state,
          counter_id: ticket.counter_id,
          counter_label: ticket.counter_id ? 'Ventanilla ' + ticket.counter_id : null,
          call_id: ticket.counter_id ? 'demo-call-' + ticket.id : null,
          call_sequence: this.callSequence,
          sound: action === 'call' || action === 'repeat'
        }
      };
      this.log.push(event);
      this.log = this.log.slice(-this.retention);
      const result = {event: copy(event), ticket: copy(ticket)};
      this.receipts.set(key, {fingerprint, result});
      return copy(result);
    }
    snapshot() {
      return copy({schema: '2cam.snapshot.v1', tenant_ref: 'turnos:company:1',
        scope: {site_id: 2, area_id: 5}, sequence: this.sequence,
        generated_at: this.clock(), active_calls: this.current ? [{
          ticket_id: this.current.id, ticket_label: this.current.label,
          counter_id: this.current.counter_id, counter_label: 'Ventanilla ' + this.current.counter_id,
          state: this.current.state
        }] : [],
        recent_calls: this.log.filter(e => e.type === 'turnos.ticket_called').slice(-4).reverse().map(e => e.payload)
      });
    }
    events(after) {
      if (!Number.isSafeInteger(after) || after < 0 || after > this.sequence) throw new Error('Secuencia inválida');
      const oldest = this.log.length ? this.log[0].sequence : this.sequence + 1;
      if (after < oldest - 1) return {snapshot_required: true, events: [], sequence: this.sequence};
      return copy({snapshot_required: false, events: this.log.filter(e => e.sequence > after), sequence: this.sequence});
    }
  }
  class Display {
    constructor() {
      this.sequence = 0;
      this.connected = true;
      this.current = null;
      this.lastSync = null;
    }
    snapshot(snapshot) {
      if (snapshot.tenant_ref !== 'turnos:company:1' || snapshot.scope.area_id !== 5 || snapshot.scope.site_id !== 2) throw new Error('Snapshot fuera de ámbito');
      if (!Number.isSafeInteger(snapshot.sequence) || snapshot.sequence < 0) throw new Error('Secuencia inválida');
      if (snapshot.sequence < this.sequence) return false;
      this.sequence = snapshot.sequence;
      this.current = snapshot.active_calls.length ? copy(snapshot.active_calls[0]) : null;
      this.lastSync = snapshot.generated_at;
      return true;
    }
    accept(event) {
      if (!this.connected) return false;
      if (event.tenant_ref !== 'turnos:company:1' || event.scope.area_id !== 5 || event.scope.site_id !== 2) throw new Error('Evento fuera de ámbito');
      if (!Number.isSafeInteger(event.sequence) || event.sequence < 1) throw new Error('Secuencia inválida');
      if (event.sequence <= this.sequence) return false;
      if (event.sequence !== this.sequence + 1) throw new Error('Brecha: solicita replay o snapshot');
      if (['turnos.ticket_called', 'turnos.call_repeated', 'turnos.service_started'].includes(event.type)) this.current = copy(event.payload);
      if (event.type === 'turnos.service_finished') this.current = null;
      this.sequence = event.sequence;
      this.lastSync = event.occurred_at;
      return true;
    }
    reconnect(queue) {
      this.connected = true;
      const replay = queue.events(this.sequence);
      if (replay.snapshot_required) this.snapshot(queue.snapshot());
      else replay.events.forEach(event => this.accept(event));
      return replay.snapshot_required ? 'snapshot' : 'replay';
    }
  }
  return {Queue, Display};
}));
