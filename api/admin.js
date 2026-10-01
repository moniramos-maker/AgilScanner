export default function handler(req, res) {
  res.setHeader("Content-Type", "text/html; charset=utf-8");
  res.end(`<!doctype html><html lang="pt-BR"><head>
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Painel Mestre Ágil</title>
<style>
body{font-family:Arial,sans-serif;background:#f4f6f9;color:#101722;margin:0}.wrap{max-width:1100px;margin:auto;padding:24px}
.card{background:#fff;border-radius:16px;padding:18px;margin:12px 0;box-shadow:0 2px 10px #0001}
input,button{font:inherit;padding:10px;border-radius:10px;border:1px solid #ccd3dd}button{cursor:pointer}.primary{background:#101722;color:#fff}
.grid{display:grid;grid-template-columns:repeat(6,1fr);gap:8px;align-items:center}.muted{color:#687384;font-size:13px}
.badge{padding:5px 9px;border-radius:20px;background:#eaf2ff;display:inline-block}.top{display:flex;justify-content:space-between;gap:10px;align-items:center}
@media(max-width:850px){.grid{grid-template-columns:1fr}.top{display:block}}
</style></head><body><div class="wrap">
<div class="top"><div><h1>Painel Mestre Ágil</h1><div class="muted">Controle de acesso ao ÁGIL Scanner</div></div><button onclick="logout()">Sair</button></div>
<div id="login" class="card"><h3>Acesso administrativo</h3>
<form id="loginForm">
  <input id="key" type="password" placeholder="Chave administrativa" autocomplete="current-password">
  <button id="loginBtn" class="primary" type="submit">Entrar</button>
</form>
<div id="loginMsg" class="muted" style="margin-top:10px"></div></div>
<div id="panel" style="display:none"><div class="card"><button class="primary" onclick="load()">Atualizar lista</button><span id="count" class="muted"></span></div><div id="list"></div></div>
</div><script>
let adminKey=sessionStorage.getItem('agilAdminKey')||'';
function esc(s){return String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[m]))}
async function api(path,opt={}){opt.headers={...(opt.headers||{}),'X-Admin-Key':adminKey,'Content-Type':'application/json'};return fetch(path,opt)}
async function login(){
  const msg=document.getElementById('loginMsg');
  const btn=document.getElementById('loginBtn');
  adminKey=document.getElementById('key').value.trim();
  if(!adminKey){msg.textContent='Digite a chave administrativa.';return;}
  msg.textContent='Verificando...';
  btn.disabled=true;
  try{
    const r=await api('/api/admin-auth',{method:'POST',body:'{}'});
    const j=await r.json().catch(()=>({}));
    if(r.ok){
      sessionStorage.setItem('agilAdminKey',adminKey);
      msg.textContent='Acesso liberado.';
      show();
      await load();
    }else{
      msg.textContent=j.error||('Não autorizado ('+r.status+').');
    }
  }catch(e){
    msg.textContent='Erro ao conectar ao servidor.';
  }finally{
    btn.disabled=false;
  }
}
function logout(){sessionStorage.removeItem('agilAdminKey');location.reload()}
function show(){document.getElementById('login').style.display='none';document.getElementById('panel').style.display='block'}
async function act(id,action){let expiresAt=null;if(action==='approve'){const v=prompt('Validade opcional (AAAA-MM-DD). Deixe vazio para sem prazo:','');if(v) expiresAt=v+'T23:59:59-03:00'}const r=await api('/api/admin-devices',{method:'POST',body:JSON.stringify({deviceId:id,action,expiresAt})});if(!r.ok)alert('Não foi possível alterar.');await load()}
async function load(){const el=document.getElementById('list');el.innerHTML='<div class="card">Carregando cadastros...</div>';try{const r=await api('/api/admin-devices');if(r.status===401){logout();return}const j=await r.json();if(!r.ok){el.innerHTML='<div class="card"><b>Erro ao carregar cadastros.</b><div class="muted">'+esc(j.error||'Tente novamente.')+'</div></div>';return}document.getElementById('count').textContent=' '+j.devices.length+' cadastro(s)';el.innerHTML='';for(const d of j.devices){const c=document.createElement('div');c.className='card';c.innerHTML='<div class="grid"><div><b>'+esc(d.name)+'</b><div class="muted">'+esc(d.company||'')+'</div></div><div>'+esc(d.region)+'<div class="muted">'+esc(d.project)+'</div></div><div>'+esc(d.phone||'')+'<div class="muted">'+esc(d.deviceModel||'')+'</div></div><div><span class="badge">'+esc(d.effectiveStatus||d.status)+'</span><div class="muted">Validade: '+esc(d.expiresAt||'sem prazo')+'</div></div><div class="muted">ID: '+esc(String(d.deviceId).slice(0,12))+'…<br>Último acesso: '+esc(d.lastSeenAt||'')+'</div><div><button onclick="act(\''+esc(d.deviceId)+'\',\'approve\')">Aprovar</button> <button onclick="act(\''+esc(d.deviceId)+'\',\'block\')">Bloquear</button> <button onclick="act(\''+esc(d.deviceId)+'\',\'reject\')">Rejeitar</button></div></div>';el.appendChild(c)}}catch(e){el.innerHTML='<div class="card"><b>Erro de conexão.</b><div class="muted">Atualize a página e tente novamente.</div></div>'}}
document.getElementById('loginForm').addEventListener('submit',function(ev){ev.preventDefault();login();});
document.getElementById('key').addEventListener('keydown',function(ev){if(ev.key==='Enter'){ev.preventDefault();login();}});
if(adminKey){show();load()}
</script></body></html>`);
}
