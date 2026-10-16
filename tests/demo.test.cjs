const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {Queue, Display} = require('../public/demo/engine.js');
const make = options => new Queue({clock: () => '2026-10-16T21:35:00Z', ...options});
test('emisión FIFO y ciclo completo sin saltarse el inicio de atención', () => {
  const q = make();
  assert.equal(q.execute('i1', 'issue').ticket.label, 'A-001');
  assert.equal(q.execute('i2', 'issue').ticket.label, 'A-002');
  assert.equal(q.execute('c1', 'call').ticket.label, 'A-001');
  assert.throws(() => q.execute('bad', 'finish'), /Inicia/);
  assert.throws(() => q.execute('bad2', 'call'), /Finaliza/);
  q.execute('s1', 'start'); q.execute('f1', 'finish');
  assert.equal(q.execute('c2', 'call', {counter_id: 2}).ticket.label, 'A-002');
  assert.equal(q.current.counter_id, 2);
});
test('idempotencia de emisión, llamado y repetición; conflicto de clave', () => {
  const q = make();
  q.execute('i', 'issue'); q.execute('i', 'issue');
  const first = q.execute('c', 'call');
  assert.deepEqual(q.execute('c', 'call'), first);
  const repeated = q.execute('r', 'repeat');
  assert.deepEqual(q.execute('r', 'repeat'), repeated);
  assert.equal(q.records.length, 1);
  assert.equal(q.sequence, 3); assert.equal(q.callSequence, 2);
  assert.equal(first.event.payload.call_id, repeated.event.payload.call_id);
  assert.notEqual(first.event.event_id, repeated.event.event_id);
  assert.throws(() => q.execute('c', 'call', {counter_id: 2}), /reutilizada/);
});
test('transiciones inválidas y entrada fuera de ámbito no generan eventos', () => {
  const q = make();
  assert.throws(() => q.execute('a', 'call'), /No hay tickets/);
  assert.throws(() => q.execute('b', 'repeat'), /No hay un llamado/);
  assert.throws(() => q.execute('c', 'issue', {area_id: 99}), /ámbito/);
  assert.throws(() => q.execute('d', 'issue', {counter_id: 99}), /Puesto/);
  assert.throws(() => q.execute('', 'issue'), /Clave/);
  assert.throws(() => q.execute('e', 'delete'), /Acción/);
  assert.equal(q.sequence, 0);
});
test('dos consumidores reciben el mismo llamado sin doble aplicación', () => {
  const q = make(), a = new Display(), b = new Display();
  const issued = q.execute('i', 'issue').event;
  const called = q.execute('c', 'call').event;
  for (const display of [a, b]) {
    display.accept(issued); assert.equal(display.accept(called), true);
    assert.equal(display.accept(called), false);
    assert.equal(display.sequence, 2);
  }
  assert.deepEqual(a.current, b.current);
});
test('offline conserva último estado, luego recupera por replay', () => {
  const q = make(), display = new Display();
  display.accept(q.execute('i', 'issue').event);
  display.accept(q.execute('c', 'call').event);
  display.connected = false;
  assert.equal(display.accept(q.execute('s', 'start').event), false);
  assert.equal(display.current.state, 'called');
  q.execute('f', 'finish');
  assert.equal(display.reconnect(q), 'replay');
  assert.equal(display.current, null); assert.equal(display.sequence, 4);
});
test('retención agotada exige snapshot y recupera sin inventar eventos', () => {
  const q = make({retention: 2}), display = new Display();
  display.connected = false;
  q.execute('i', 'issue'); q.execute('c', 'call'); q.execute('s', 'start');
  assert.equal(q.events(0).snapshot_required, true);
  assert.equal(display.reconnect(q), 'snapshot');
  assert.equal(display.sequence, 3); assert.equal(display.current.state, 'serving');
});
test('consumidor rechaza brechas, scopes incorrectos y secuencias inválidas', () => {
  const q = make(), display = new Display();
  const issued = q.execute('i', 'issue').event, called = q.execute('c', 'call').event;
  assert.throws(() => display.accept(called), /Brecha/);
  assert.throws(() => display.accept({...issued, tenant_ref: 'turnos:company:2'}), /ámbito/);
  assert.throws(() => display.accept({...issued, scope: {site_id: 99, area_id: 5}}), /ámbito/);
  assert.throws(() => display.accept({...issued, sequence: NaN}), /Secuencia/);
  assert.throws(() => display.snapshot({...q.snapshot(), scope: {site_id: 2, area_id: 99}}), /ámbito/);
  assert.equal(display.sequence, 0);
});
test('snapshot viejo no hace retroceder el estado; respuestas no mutan la autoridad', () => {
  const q = make(), display = new Display(), old = q.snapshot();
  q.execute('i', 'issue'); q.execute('c', 'call');
  display.snapshot(q.snapshot());
  assert.equal(display.snapshot(old), false);
  const result = q.execute('c', 'call'); result.ticket.label = 'MUTADO';
  assert.equal(q.current.label, 'A-001');
  const snapshot = q.snapshot(); snapshot.active_calls[0].ticket_label = 'MUTADO';
  assert.equal(q.snapshot().active_calls[0].ticket_label, 'A-001');
});
test('replay incremental valida cursor y entrega orden ascendente', () => {
  const q = make(); q.execute('i', 'issue'); q.execute('c', 'call');
  assert.deepEqual(q.events(1).events.map(e => e.sequence), [2]);
  assert.equal(q.events(2).events.length, 0);
  for (const value of [-1, 1.5, 3, '1']) assert.throws(() => q.events(value), /Secuencia/);
});
test('eventos públicos no contienen datos personales y snapshot no expone cola interna', () => {
  const q = make(); q.execute('i', 'issue'); q.execute('c', 'call');
  assert.deepEqual(Object.keys(q.log[1].payload).sort(), ['ticket_id','ticket_label','state','counter_id','counter_label','call_id','call_sequence','sound'].sort());
  assert.equal(q.snapshot().records, undefined);
  assert.equal(q.log[1].payload.ticket_label, 'A-001');
});
test('numeración y secuencia crecen sin colisión en el simulador', () => {
  const q = make();
  for (let i = 0; i < 300; i++) q.execute('i' + i, 'issue');
  assert.equal(new Set(q.records.map(t => t.label)).size, 300);
  assert.equal(q.sequence, 300); assert.equal(q.log.length, 100);
});
test('HTML no depende de CDN y render de campos utiliza textContent', () => {
  const base = path.join(__dirname, '../public/demo');
  const html = fs.readFileSync(path.join(base, 'index.html'), 'utf8');
  const js = fs.readFileSync(path.join(base, 'app.js'), 'utf8');
  assert.doesNotMatch(html, /(?:src|href)="https?:/);
  assert.doesNotMatch(js, /innerHTML|insertAdjacentHTML|eval\(/);
  for (const asset of ['engine.js', 'app.js', 'style.css']) assert.ok(fs.existsSync(path.join(base, asset)));
});
