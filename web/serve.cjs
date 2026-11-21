#!/usr/bin/env node
'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');

const ROOT = __dirname;
const REFEREE_HTML_PATH = path.resolve(ROOT, '../app/src/main/assets/referee/index.html');
const TV_PORT = Number(process.env.PORT || 8788);
const REFEREE_PORT = Number(process.env.REFEREE_PORT || 7777);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.mp3': 'audio/mpeg',
  '.wav': 'audio/wav',
};

function findLanIp() {
  const nets = os.networkInterfaces();
  const candidates = [];
  for (const [name, list] of Object.entries(nets)) {
    for (const info of list || []) {
      if (info.family === 'IPv4' && !info.internal) {
        const prio = name.startsWith('en') || name.startsWith('wlan') ? 0 : 1;
        candidates.push([prio, info.address]);
      }
    }
  }
  candidates.sort((a, b) => a[0] - b[0]);
  return candidates[0]?.[1] || '127.0.0.1';
}

// Shared real-time referee state & WebSocket hub between TV Simulator (:8788) and Mobile Referee (:7777)
const tvSockets = new Set();
const refereeSockets = new Set();
let activeRefereePort = REFEREE_PORT;

let fallbackState = {
  screen: 'SCOREBOARD',
  isRunning: false,
  remainingSeconds: 300,
  formattedClock: '05:00',
  durationSeconds: 300,
  fighterAName: '',
  fighterBName: '',
  category: '',
  aPoints: 0,
  aAdvantages: 0,
  aPenalties: 0,
  bPoints: 0,
  bAdvantages: 0,
  bPenalties: 0,
  computedWinner: 'DRAW',
  defaultMethod: 'Empate',
  results: [],
};

function formatMmSs(sec) {
  const s = Math.max(0, Math.floor(sec));
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

function recomputeWinner(st) {
  if (st.aPoints !== st.bPoints) {
    st.computedWinner = st.aPoints > st.bPoints ? 'FIGHTER_A' : 'FIGHTER_B';
    st.defaultMethod = 'Pontos';
  } else if (st.aAdvantages !== st.bAdvantages) {
    st.computedWinner = st.aAdvantages > st.bAdvantages ? 'FIGHTER_A' : 'FIGHTER_B';
    st.defaultMethod = 'Vantagens';
  } else if (st.aPenalties !== st.bPenalties) {
    st.computedWinner = st.aPenalties < st.bPenalties ? 'FIGHTER_A' : 'FIGHTER_B';
    st.defaultMethod = 'Punições';
  } else {
    st.computedWinner = 'DRAW';
    st.defaultMethod = 'Empate';
  }
}

function applyCommandToFallback(cmdObj) {
  const cmd = cmdObj?.cmd || '';
  const sideA = cmdObj?.side !== 'B';
  if (cmd === 'set_name') {
    if (sideA) fallbackState.fighterAName = String(cmdObj.name || '').slice(0, 40);
    else fallbackState.fighterBName = String(cmdObj.name || '').slice(0, 40);
  } else if (cmd === 'set_category') {
    fallbackState.category = String(cmdObj.category || '').slice(0, 48);
  } else if (cmd === 'score_delta') {
    const k = sideA ? 'a' : 'b';
    const prop = cmdObj.field === 'advantages' ? `${k}Advantages` : cmdObj.field === 'penalties' ? `${k}Penalties` : `${k}Points`;
    fallbackState[prop] = Math.max(0, Math.min(999, (fallbackState[prop] || 0) + (Number(cmdObj.delta) || 0)));
  } else if (cmd === 'score_set') {
    const k = sideA ? 'a' : 'b';
    const prop = cmdObj.field === 'advantages' ? `${k}Advantages` : cmdObj.field === 'penalties' ? `${k}Penalties` : `${k}Points`;
    fallbackState[prop] = Math.max(0, Math.min(999, Number(cmdObj.value) || 0));
  } else if (cmd === 'swap_sides') {
    [fallbackState.fighterAName, fallbackState.fighterBName] = [fallbackState.fighterBName, fallbackState.fighterAName];
    [fallbackState.aPoints, fallbackState.bPoints] = [fallbackState.bPoints, fallbackState.aPoints];
    [fallbackState.aAdvantages, fallbackState.bAdvantages] = [fallbackState.bAdvantages, fallbackState.aAdvantages];
    [fallbackState.aPenalties, fallbackState.bPenalties] = [fallbackState.bPenalties, fallbackState.aPenalties];
  } else if (cmd === 'set_duration') {
    const s = Math.max(10, Math.min(3600, Number(cmdObj.seconds) || 300));
    fallbackState.durationSeconds = s;
    fallbackState.remainingSeconds = s;
    fallbackState.formattedClock = formatMmSs(s);
    fallbackState.isRunning = false;
  } else if (cmd === 'time_delta') {
    const s = Math.max(1, Math.min(7200, fallbackState.remainingSeconds + (Number(cmdObj.delta) || 0)));
    fallbackState.remainingSeconds = s;
    fallbackState.formattedClock = formatMmSs(s);
  } else if (cmd === 'time_set') {
    const s = Math.max(1, Math.min(7200, Number(cmdObj.seconds) || 300));
    fallbackState.remainingSeconds = s;
    fallbackState.formattedClock = formatMmSs(s);
  } else if (cmd === 'play_pause') {
    fallbackState.isRunning = !fallbackState.isRunning;
  } else if (cmd === 'reset_match') {
    if (!cmdObj.keepNames) {
      fallbackState.fighterAName = '';
      fallbackState.fighterBName = '';
      fallbackState.category = '';
    }
    fallbackState.aPoints = fallbackState.aAdvantages = fallbackState.aPenalties = 0;
    fallbackState.bPoints = fallbackState.bAdvantages = fallbackState.bPenalties = 0;
    fallbackState.remainingSeconds = fallbackState.durationSeconds;
    fallbackState.formattedClock = formatMmSs(fallbackState.durationSeconds);
    fallbackState.isRunning = false;
  } else if (cmd === 'save_result') {
    recomputeWinner(fallbackState);
    const now = new Date();
    const ts = `${String(now.getDate()).padStart(2, '0')}/${String(now.getMonth() + 1).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
    fallbackState.results.unshift({
      id: Math.random().toString(36).slice(2, 10),
      timestamp: ts,
      category: fallbackState.category,
      fighterAName: fallbackState.fighterAName || 'Atleta 1 (Branco)',
      fighterAPoints: fallbackState.aPoints,
      fighterAAdvantages: fallbackState.aAdvantages,
      fighterAPenalties: fallbackState.aPenalties,
      fighterBName: fallbackState.fighterBName || 'Atleta 2 (Vermelho)',
      fighterBPoints: fallbackState.bPoints,
      fighterBAdvantages: fallbackState.bAdvantages,
      fighterBPenalties: fallbackState.bPenalties,
      winnerSide: cmdObj.winner || fallbackState.computedWinner,
      method: cmdObj.method || fallbackState.defaultMethod,
      durationSeconds: fallbackState.durationSeconds,
      remainingSeconds: fallbackState.remainingSeconds,
    });
  } else if (cmd === 'delete_result') {
    fallbackState.results = fallbackState.results.filter((r) => r.id !== cmdObj.id);
  } else if (cmd === 'clear_results') {
    fallbackState.results = [];
  }
  recomputeWinner(fallbackState);
}

function encodeWsText(str) {
  const payload = Buffer.from(str, 'utf8');
  const len = payload.length;
  let header;
  if (len <= 125) {
    header = Buffer.from([0x81, len]);
  } else if (len <= 65535) {
    header = Buffer.from([0x81, 126, (len >> 8) & 0xff, len & 0xff]);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x81;
    header[1] = 127;
    header.writeBigUInt64BE(BigInt(len), 2);
  }
  return Buffer.concat([header, payload]);
}

function sendJson(sock, obj) {
  try {
    sock.write(encodeWsText(JSON.stringify(obj)));
  } catch (_) {}
}

function broadcastLanMetaToTvs() {
  const ip = findLanIp();
  const msg = {
    type: 'lan_meta',
    lanUrl: `${ip}:${activeRefereePort}`,
    connectedClients: refereeSockets.size,
  };
  for (const s of tvSockets) sendJson(s, msg);
}

// The leader is the TV page the user touched last (ties: newest). It alone speaks and feeds the phone its state, so a
// stale background tab can neither layer a second voice nor overwrite live names/points with its own empty state.
let lastLeader = null;
function currentLeader() {
  let best = null;
  for (const s of tvSockets) if (!best || (s.feuActiveAt || 0) >= (best.feuActiveAt || 0)) best = s;
  return best;
}
function syncAudioLeader(force) {
  const leader = currentLeader();
  if (!force && leader === lastLeader) return;
  lastLeader = leader;
  for (const s of tvSockets) sendJson(s, { type: 'audio_leader', leader: s === leader });
}

function dispatchRefereeCommand(cmdObj) {
  applyCommandToFallback(cmdObj);
  if (tvSockets.size > 0) {
    // Every TV page applies the command (state never diverges between tabs); only the audio leader makes sound.
    for (const tv of tvSockets) sendJson(tv, { type: 'referee_cmd', payload: cmdObj });
  } else {
    for (const ref of refereeSockets) sendJson(ref, fallbackState);
  }
}

function attachWebSocketHandler(server, defaultRole) {
  server.on('upgrade', (req, socket) => {
    const key = req.headers['sec-websocket-key'];
    if (!key) {
      socket.destroy();
      return;
    }
    socket.setNoDelay(true);
    const accept = crypto
      .createHash('sha1')
      .update(key.trim() + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11')
      .digest('base64');

    socket.write(
      'HTTP/1.1 101 Switching Protocols\r\n' +
      'Upgrade: websocket\r\n' +
      'Connection: Upgrade\r\n' +
      `Sec-WebSocket-Accept: ${accept}\r\n\r\n`
    );

    const isTv = (req.url || '').includes('role=tv') || defaultRole === 'tv-only';
    const pool = isTv ? tvSockets : refereeSockets;
    pool.add(socket);
    if (isTv) { socket.feuActiveAt = 0; syncAudioLeader(true); }
    broadcastLanMetaToTvs();

    if (!isTv) {
      sendJson(socket, fallbackState);
    }

    let buf = Buffer.alloc(0);
    socket.on('data', (chunk) => {
      buf = Buffer.concat([buf, chunk]);
      while (buf.length >= 2) {
        const opcode = buf[0] & 0x0f;
        const masked = (buf[1] & 0x80) !== 0;
        let len = buf[1] & 0x7f;
        let offset = 2;
        if (len === 126) {
          if (buf.length < 4) return;
          len = buf.readUInt16BE(2);
          offset = 4;
        } else if (len === 127) {
          if (buf.length < 10) return;
          len = Number(buf.readBigUInt64BE(2));
          offset = 10;
        }
        const totalFrame = offset + (masked ? 4 : 0) + len;
        if (buf.length < totalFrame) return;

        let payload = buf.subarray(offset + (masked ? 4 : 0), totalFrame);
        if (masked) {
          const mask = buf.subarray(offset, offset + 4);
          const unmasked = Buffer.alloc(payload.length);
          for (let i = 0; i < payload.length; i++) unmasked[i] = payload[i] ^ mask[i & 3];
          payload = unmasked;
        }
        buf = buf.subarray(totalFrame);

        if (opcode === 0x8) {
          socket.end();
          return;
        }
        if (opcode === 0x9) {
          socket.write(Buffer.concat([Buffer.from([0x8a, payload.length]), payload]));
          continue;
        }
        if (opcode === 0x1) {
          try {
            const msg = JSON.parse(payload.toString('utf8'));
            if (isTv && msg.type === 'tv_active') {
              socket.feuActiveAt = Date.now();
              syncAudioLeader(false);
            } else if (isTv && msg.type === 'tv_state' && msg.state) {
              if (socket === currentLeader()) {
                fallbackState = { ...fallbackState, ...msg.state };
                for (const ref of refereeSockets) sendJson(ref, fallbackState);
              }
            } else if (isTv) {
              // TV pages never issue referee commands
            } else {
              dispatchRefereeCommand(msg);
            }
          } catch (_) {}
        }
      }
    });

    const cleanup = () => {
      pool.delete(socket);
      if (isTv) syncAudioLeader(false);
      broadcastLanMetaToTvs();
    };
    socket.on('close', cleanup);
    socket.on('error', cleanup);
  });
}

function handleApiOrReferee(req, res, urlPath) {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
    });
    res.end();
    return true;
  }

  if (req.method === 'GET' && urlPath === '/api/lan-info') {
    const ip = findLanIp();
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify({ lanUrl: `${ip}:${activeRefereePort}`, connectedClients: refereeSockets.size }));
    return true;
  }

  if (req.method === 'GET' && urlPath === '/api/state') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'Access-Control-Allow-Origin': '*' });
    res.end(JSON.stringify(fallbackState));
    return true;
  }

  if (req.method === 'POST' && urlPath === '/api/cmd') {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      try {
        const cmdObj = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        dispatchRefereeCommand(cmdObj);
      } catch (_) {}
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'Access-Control-Allow-Origin': '*' });
      res.end(JSON.stringify(fallbackState));
    });
    return true;
  }

  return false;
}

function serveRefereeHtml(res) {
  fs.readFile(REFEREE_HTML_PATH, (err, data) => {
    if (err) {
      res.writeHead(500);
      res.end('Referee console missing');
      return;
    }
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(data);
  });
}

// 1. Main TV Simulator Server (:8788, plus /r shortcut for Mobile Referee)
const tvServer = http.createServer((req, res) => {
  const urlPath = decodeURIComponent((req.url || '/').split('?')[0]);
  if (handleApiOrReferee(req, res, urlPath)) return;
  if (urlPath === '/r' || urlPath === '/referee') return serveRefereeHtml(res);

  const relPath = urlPath === '/' ? 'index.html' : urlPath.replace(/^\/+/, '');
  const fullPath = path.resolve(ROOT, relPath);
  if (!fullPath.startsWith(ROOT)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }

  fs.readFile(fullPath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('Not found');
      return;
    }
    const ext = path.extname(fullPath).toLowerCase();
    res.writeHead(200, {
      'Content-Type': MIME[ext] || 'application/octet-stream',
      'Cache-Control': 'no-store',
    });
    res.end(data);
  });
});
attachWebSocketHandler(tvServer, 'mixed');

// 2. Dedicated Short-URL Mobile Referee Server (:7777, matching Android TV ScoreboardLanServer)
const refereeServer = http.createServer((req, res) => {
  const urlPath = decodeURIComponent((req.url || '/').split('?')[0]);
  if (handleApiOrReferee(req, res, urlPath)) return;
  serveRefereeHtml(res);
});
attachWebSocketHandler(refereeServer, 'referee');

tvServer.listen(TV_PORT, '0.0.0.0', () => {
  const ip = findLanIp();
  console.log(`FEU BJJ Android TV Simulator running at http://127.0.0.1:${TV_PORT}/`);
  refereeServer.listen(REFEREE_PORT, '0.0.0.0', () => {
    activeRefereePort = REFEREE_PORT;
    console.log(`FEU BJJ Mobile Portrait Referee Console live at http://${ip}:${REFEREE_PORT}/`);
  }).on('error', () => {
    activeRefereePort = TV_PORT;
    console.log(`FEU BJJ Mobile Portrait Referee Console live at http://${ip}:${TV_PORT}/r`);
  });
});
