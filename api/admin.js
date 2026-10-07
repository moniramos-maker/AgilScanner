export default function handler(req, res) {
  res.setHeader("Content-Type", "text/html; charset=utf-8");
  res.end(`<!doctype html>
<html lang="pt-BR">
<head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Painel Mestre Ágil</title>
<style>
body{font-family:Arial,sans-serif;background:#f4f6f9;color:#101722;margin:0}
.wrap{max-width:1100px;margin:auto;padding:24px}
.card{background:#fff;border-radius:16px;padding:18px;margin:12px 0;box-shadow:0 2px 10px #0001}
input,button{font:inherit;padding:10px;border-radius:10px;border:1px solid #ccd3dd}
button{cursor:pointer}.primary{background:#101722;color:#fff}.muted{color:#687384;font-size:13px}
.row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.item{display:grid;grid-template-columns:1.2fr 1fr 1fr 1fr 1fr;gap:12px;align-items:center}
.badge{padding:5px 9px;border-radius:20px;background:#eaf2ff;display:inline-block}
@media(max-width:850px){.item{grid-template-columns:1fr}}
</style>
</head>
<body>
<div class="wrap">
  <h1>Painel Mestre Ágil</h1>
  <div class="muted">Controle de acesso ao ÁGIL Scanner</div>

  <div id="loginCard" class="card">
    <h3>Acesso administrativo</h3>
    <div class="row">
      <input id="key" type="password" placeholder="Chave administrativa" autocomplete="off">
      <button id="loginBtn" class="primary" type="button">Entrar</button>
    </div>
    <div id="loginMsg" class="muted" style="margin-top:10px"></div>
  </div>

  <div id="panel" style="display:none">
    <div class="card row">
      <button id="refreshBtn" class="primary" type="button">Atualizar lista</button>
      <button id="recoverBtn" type="button">Recuperar cadastros</button>
      <button id="logoutBtn" type="button">Sair</button>
      <span id="count" class="muted"></span>
    </div>
    <div id="list"></div>
  </div>
</div>

<script>
(function(){
  var adminKey = sessionStorage.getItem('agilAdminKey') || '';
  var loginCard = document.getElementById('loginCard');
  var panel = document.getElementById('panel');
  var msg = document.getElementById('loginMsg');
  var keyInput = document.getElementById('key');
  var loginBtn = document.getElementById('loginBtn');
  var refreshBtn = document.getElementById('refreshBtn');
  var recoverBtn = document.getElementById('recoverBtn');
  var logoutBtn = document.getElementById('logoutBtn');
  var list = document.getElementById('list');
  var count = document.getElementById('count');

  function esc(v){
    return String(v == null ? '' : v)
      .replace(/&/g,'&amp;').replace(/</g,'&lt;')
      .replace(/>/g,'&gt;').replace(/"/g,'&quot;')
      .replace(/'/g,'&#039;');
  }

  async function request(path, options){
    options = options || {};
    options.headers = Object.assign({}, options.headers || {}, {
      'X-Admin-Key': adminKey,
      'Content-Type': 'application/json'
    });
    return fetch(path, options);
  }

  function showPanel(){
    loginCard.style.display = 'none';
    panel.style.display = 'block';
  }

  async function doLogin(){
    adminKey = keyInput.value.trim();
    if(!adminKey){
      msg.textContent = 'Digite a chave administrativa.';
      return;
    }
    loginBtn.disabled = true;
    msg.textContent = 'Verificando...';
    try{
      var r = await request('/api/admin-auth', {method:'POST', body:'{}'});
      var j = {};
      try { j = await r.json(); } catch(e) {}
      if(r.ok){
        sessionStorage.setItem('agilAdminKey', adminKey);
        msg.textContent = 'Acesso liberado.';
        showPanel();
        await loadDevices();
      }else{
        msg.textContent = j.error || ('Não autorizado (' + r.status + ').');
      }
    }catch(e){
      msg.textContent = 'Erro ao conectar ao servidor.';
    }finally{
      loginBtn.disabled = false;
    }
  }

  async function loadDevices(){
    list.innerHTML = '<div class="card">Carregando cadastros...</div>';
    try{
      var r = await request('/api/admin-devices');
      if(r.status === 401){
        sessionStorage.removeItem('agilAdminKey');
        location.reload();
        return;
      }
      var j = await r.json();
      if(!r.ok){
        list.innerHTML = '<div class="card"><b>Erro ao carregar.</b><div class="muted">' + esc(j.error || '') + '</div></div>';
        return;
      }
      count.textContent = j.devices.length + ' cadastro(s)';
      list.innerHTML = '';
      if(j.devices.length === 0){
        list.innerHTML = '<div class="card">Nenhum técnico cadastrado ainda.</div>';
        return;
      }
      j.devices.forEach(function(d){
        var card = document.createElement('div');
        card.className = 'card';
        var status = d.effectiveStatus || d.status || '';
        card.innerHTML =
          '<div class="item">' +
            '<div><b>' + esc(d.name) + '</b><div class="muted">' + esc(d.company || '') + '</div></div>' +
            '<div>' + esc(d.region) + '<div class="muted">' + esc(d.project) + '</div></div>' +
            '<div>' + esc(d.phone || '') + '<div class="muted">' + esc(d.deviceModel || '') + '</div></div>' +
            '<div><span class="badge">' + esc(status) + '</span><div class="muted">Validade: ' + esc(d.expiresAt || 'sem prazo') + '</div></div>' +
            '<div class="row"></div>' +
          '</div>';

        var actions = card.querySelector('.row');
        [
          ['Aprovar','approve'],
          ['Bloquear','block'],
          ['Rejeitar','reject']
        ].forEach(function(pair){
          var b = document.createElement('button');
          b.type = 'button';
          b.textContent = pair[0];
          b.addEventListener('click', function(){ changeStatus(d.deviceId, pair[1]); });
          actions.appendChild(b);
        });
        list.appendChild(card);
      });
    }catch(e){
      list.innerHTML = '<div class="card"><b>Erro de conexão.</b><div class="muted">Atualize a página e tente novamente.</div></div>';
    }
  }


  async function recoverDevices(){
    recoverBtn.disabled = true;
    recoverBtn.textContent = 'Recuperando...';
    try{
      var r = await request('/api/admin-recover-devices', {method:'POST', body:'{}'});
      var j = {};
      try { j = await r.json(); } catch(e) {}
      if(!r.ok){
        alert(j.error || 'Não foi possível recuperar os cadastros.');
        return;
      }
      alert('Recuperação concluída. Cadastros encontrados: ' + (j.devices ? j.devices.length : 0));
      await loadDevices();
    }catch(e){
      alert('Erro de conexão durante a recuperação.');
    }finally{
      recoverBtn.disabled = false;
      recoverBtn.textContent = 'Recuperar cadastros';
    }
  }

  async function changeStatus(deviceId, action){
    var expiresAt = null;
    if(action === 'approve'){
      var v = prompt('Validade opcional (AAAA-MM-DD). Deixe vazio para sem prazo:','');
      if(v) expiresAt = v + 'T23:59:59-03:00';
    }
    try{
      var r = await request('/api/admin-devices', {
        method:'POST',
        body:JSON.stringify({deviceId:deviceId, action:action, expiresAt:expiresAt})
      });
      if(!r.ok){
        alert('Não foi possível alterar o acesso.');
        return;
      }
      await loadDevices();
    }catch(e){
      alert('Erro de conexão.');
    }
  }

  loginBtn.addEventListener('click', doLogin);
  keyInput.addEventListener('keydown', function(e){
    if(e.key === 'Enter'){ e.preventDefault(); doLogin(); }
  });
  refreshBtn.addEventListener('click', loadDevices);
  recoverBtn.addEventListener('click', recoverDevices);
  logoutBtn.addEventListener('click', function(){
    sessionStorage.removeItem('agilAdminKey');
    location.reload();
  });

  if(adminKey){
    showPanel();
    loadDevices();
  }
})();
</script>
</body>
</html>`);
}
