// Monsters Island per Windows: all'avvio controlla GitHub, scarica la nuova versione del gioco se c'è, poi parte.
const { app, BrowserWindow, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const https = require('https');

const BASE = 'https://github.com/jobbyboy/monsters-island/releases/download/ultima/';
const SHELL = 2; // versione del programma Windows: aumentarla solo quando cambia questo file

function get(url, onProgress, redirects = 0) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': 'MonstersIsland' } }, res => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location && redirects < 6) {
        res.resume(); return resolve(get(res.headers.location, onProgress, redirects + 1));
      }
      if (res.statusCode !== 200) { res.resume(); return reject(new Error('http ' + res.statusCode)); }
      const total = +res.headers['content-length'] || 0; let got = 0; const parts = [];
      res.on('data', c => { parts.push(c); got += c.length; if (onProgress && total) onProgress(Math.round(got * 100 / total)); });
      res.on('end', () => { const b = Buffer.concat(parts); if (total && b.length !== total) reject(new Error('incompleto')); else resolve(b); });
      res.on('error', reject);
    }).on('error', reject).setTimeout(20000, function () { this.destroy(new Error('timeout')); });
  });
}

let win;
function say(t, p) { if (win) win.webContents.executeJavaScript(`window.postMessage(${JSON.stringify({ t, p })},'*')`).catch(() => {}); }

async function updateThenStart() {
  const dir = path.join(app.getPath('userData'), 'gioco');
  fs.mkdirSync(dir, { recursive: true });
  const verFile = path.join(dir, 'versione.txt');
  const local = fs.existsSync(verFile) ? parseInt(fs.readFileSync(verFile, 'utf8')) || 0 : 0;
  let askShell = false;
  try {
    const v = JSON.parse((await get(BASE + 'version.json')).toString());
    askShell = (v.win || 0) > SHELL;
    if ((v.game || 0) > local || !fs.existsSync(path.join(dir, 'index.html'))) {
      say('Scarico la nuova versione del gioco…', 0);
      if (!fs.existsSync(path.join(dir, 'three.min.js'))) fs.writeFileSync(path.join(dir, 'three.min.js'), await get(BASE + 'three.min.js'));
      const html = await get(BASE + 'index.html', p => say('Scarico l\'aggiornamento… ' + p + '%', p));
      fs.writeFileSync(path.join(dir, 'index.html'), html);
      fs.writeFileSync(verFile, String(v.game || 0));
      say('Aggiornamento completato!', 100);
    } else say('Hai già l\'ultima versione', 100);
  } catch (e) {
    say('Nessuna connessione: avvio la versione salvata', 100);
  }
  const downloaded = path.join(dir, 'index.html');
  const target = fs.existsSync(downloaded) && fs.existsSync(path.join(dir, 'three.min.js')) ? downloaded : path.join(__dirname, 'game', 'index.html');
  setTimeout(() => {
    win.loadFile(target);
    if (askShell) setTimeout(() => {
      const { dialog } = require('electron');
      dialog.showMessageBox(win, { type: 'info', buttons: ['Aggiorna all\'ultima versione', 'Più tardi'], title: 'Nuova versione', message: 'È disponibile un nuovo programma di installazione di Monsters Island. Vuoi scaricarlo? I salvataggi restano.' })
        .then(r => { if (r.response === 0) shell.openExternal(BASE + 'MonstersIsland-Setup.exe'); });
    }, 3000);
  }, 700);
}

app.whenReady().then(() => {
  win = new BrowserWindow({
    width: 1280, height: 720, minWidth: 800, minHeight: 450, backgroundColor: '#1a1008',
    icon: path.join(__dirname, 'icon.ico'), autoHideMenuBar: true, title: 'Monsters Island',
    webPreferences: { contextIsolation: true, webSecurity: false }
  });
  win.setMenuBarVisibility(false);
  win.webContents.on('before-input-event', (e, input) => { if (input.type === 'keyDown' && input.key === 'F11') win.setFullScreen(!win.isFullScreen()); });
  win.loadFile(path.join(__dirname, 'splash.html')).then(updateThenStart);
});
app.on('window-all-closed', () => app.quit());
