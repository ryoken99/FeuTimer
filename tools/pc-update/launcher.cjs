/* Local PC launcher. Keep the browser origin while swapping verified release servers. */
const http = require('node:http');
const net = require('node:net');
const fs = require('node:fs');
const path = require('node:path');
const { spawn, execFile } = require('node:child_process');
const { promisify } = require('node:util');
const exec = promisify(execFile);
const delay = promisify(setTimeout);
const root = path.resolve(process.argv[2]);
const port = Number(process.argv[3]);
const host = '127.0.0.1';
const origin = 'http://' + host + ':' + port;
const manifest = path.join(root, 'active.json');
const client = fs.readFileSync(path.join(__dirname, 'client.js'));
let active, busy = false;
const children = new Set();

async function prepare(channel, current) {
  const { stdout } = await exec('python', [path.join(__dirname, 'package.py'), root, channel, current], { windowsHide: true, timeout: 180000 });
  return JSON.parse(stdout);
}

async function freePort() {
  const s = net.createServer();
  await new Promise((resolve, reject) => s.once('error', reject).listen(0, host, resolve));
  const p = s.address().port;
  await new Promise(resolve => s.close(resolve));
  return p;
}

async function start(pkg) {
  const p = await freePort();
  const child = spawn(process.execPath, ['serve.cjs'], {
    cwd: path.join(pkg.dir, 'web'), windowsHide: true, stdio: 'ignore',
    env: { ...process.env, PORT: String(p), REFEREE_PORT: String(await freePort()), FEU_BIND_HOST: host }
  });
  children.add(child);
  child.once('exit', () => children.delete(child));
  try {
    for (let i = 0; i < 80 && child.exitCode === null; i++) {
      const base = 'http://' + host + ':' + p;
      try {
        const page = await fetch(base);
        if (page.ok && (await page.text()).includes('id="tv-viewport"') && (await fetch(base+'/js/app.js')).ok)
          return { pkg, child, port: p };
      } catch {}
      await delay(100);
    }
    throw new Error('O servidor da nova versão não arrancou.');
  } catch (error) { child.kill(); throw error; }
}

function save(pkg) {
  fs.writeFileSync(manifest+'.tmp', JSON.stringify(pkg));
  fs.renameSync(manifest+'.tmp', manifest);
}
function json(res, status, body) {
  res.writeHead(status, {'Content-Type':'application/json', 'Cache-Control':'no-store'});
  res.end(JSON.stringify(body));
}

async function update(req, res) {
  if (req.method !== 'POST' || req.headers.origin !== origin) return json(res, 403, {});
  if (busy) return json(res, 409, {error:'Uma atualização já está em curso.'});
  busy = true;
  try {
    const channel = new URL(req.url, origin).searchParams.get('channel');
    if (!['test','stable'].includes(channel)) return json(res, 400, {});
    const pkg = await prepare(channel, active.pkg.tag);
    if (pkg.tag === active.pkg.tag) return json(res, 200, {current:true});
    const next = await start(pkg);
    try { save(pkg); } catch (err) { next.child.kill(); throw err; }
    const previous = active;
    active = next;
    previous.child.kill();
    json(res, 200, {layout:pkg.layout, tag:pkg.tag});
  } catch (err) {
    console.error(err.message);
    json(res, 502, {error:'A atualização falhou. A versão anterior continua disponível.'});
  } finally { busy = false; }
}

function openBrowser() {
  spawn('rundll32.exe', ['url.dll,FileProtocolHandler', origin], { windowsHide: true, stdio: 'ignore' }).unref();
}

async function main() {
  const openWindow = process.argv.includes('--open') && process.platform === 'win32';
  if (openWindow) {
    try {
      const existing = await fetch(origin + '/pc/status', {signal: AbortSignal.timeout(1000)});
      const status = await existing.json();
      if (existing.ok && typeof status.tag === 'string' && typeof status.layout === 'boolean') {
        openBrowser();
        return;
      }
    } catch {}
  }
  const pkg = fs.existsSync(manifest) ? JSON.parse(fs.readFileSync(manifest)) : await prepare('stable', '');
  active = await start(pkg);
  save(pkg);
  const server = http.createServer((req,res) => {
    if (req.headers.host !== host + ':' + port) return json(res,403,{});
    const route = new URL(req.url,origin).pathname;
    if (route === '/pc/install') return void update(req,res);
    if (route === '/pc/status') return json(res,200,{tag:active.pkg.tag, layout:active.pkg.layout});
    if (route === '/pc/client.js') {
      res.writeHead(200, {'Content-Type':'application/javascript','Cache-Control':'no-store'});
      return res.end(client);
    }
    if ((route === '/' || route === '/index.html') && req.method === 'GET') {
      const html = fs.readFileSync(path.join(active.pkg.dir,'web/index.html'),'utf8');
      res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'});
      return res.end(html.replace('</body>','<script src="/pc/client.js"></script></body>'));
    }
    const peer = http.request({hostname:host,port:active.port,path:req.url,method:req.method,headers:req.headers}, r => {
      res.writeHead(r.statusCode,r.headers); r.pipe(res);
    });
    peer.on('error',() => res.destroy()); req.pipe(peer);
  });
  server.on('upgrade',(req,socket,head) => {
    if (req.headers.host !== host+':'+port || req.headers.origin !== origin) return socket.destroy();
    const peer = net.connect(active.port, host, () => {
      peer.write(req.method+' '+req.url+' HTTP/'+req.httpVersion+'\r\n'+
        req.rawHeaders.reduce((s,v,i)=>s+v+(i%2?'\r\n':': '),'')+'\r\n');
      if (head.length) peer.write(head);
      socket.pipe(peer).pipe(socket);
    });
    socket.on('error',()=>peer.destroy()); peer.on('error',()=>socket.destroy());
    socket.on('close',()=>peer.destroy()); peer.on('close',()=>socket.destroy());
  });
  server.listen(port, host, () => {
    console.log('FeuTimer: ' + origin);
    if (openWindow) openBrowser();
  });
}
process.on('exit',()=>{ for (const child of children) child.kill(); });
main().catch(err=>{console.error(err);process.exit(1);});
