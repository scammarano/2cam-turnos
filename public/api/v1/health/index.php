<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
echo json_encode([
    'application' => '2cam-turnos',
    'version' => '0.1.0-t0',
    'status' => 'prototype',
    'ready' => false,
    'storage' => 'not_configured',
    'events' => 'simulator_only',
    'occurred_at' => gmdate('Y-m-d\TH:i:s\Z'),
], JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);
