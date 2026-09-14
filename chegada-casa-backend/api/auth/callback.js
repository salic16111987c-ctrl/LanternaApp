export default function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');

  const code = String(req.query.code || '').trim();
  const region = String(req.query.region || '').trim();
  const state = String(req.query.state || '').trim();
  const error = String(req.query.error || '').trim();

  if (error) {
    return res.status(400).send(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Chegada Casa</title></head><body style="font-family:sans-serif;padding:24px"><h2>Autorização eWeLink não concluída</h2><p>${error}</p></body></html>`);
  }

  if (!code) {
    return res.status(400).send('<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Chegada Casa</title></head><body style="font-family:sans-serif;padding:24px"><h2>Retorno eWeLink inválido</h2><p>O código de autorização não foi recebido.</p></body></html>');
  }

  const local = new URL('http://127.0.0.1:8787/ewelink/callback');
  local.searchParams.set('code', code);
  if (region) local.searchParams.set('region', region);
  if (state) local.searchParams.set('state', state);

  const target = local.toString();

  return res.status(200).send(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Chegada Casa — autorizado</title></head><body style="font-family:sans-serif;padding:24px;line-height:1.5"><h2>eWeLink autorizado</h2><p>A autorização pelo site foi concluída.</p><p>Voltando para o aplicativo Chegada Casa...</p><p><a href="${target}">TOQUE AQUI SE NÃO VOLTAR AUTOMATICAMENTE</a></p><script>setTimeout(function(){window.location.href=${JSON.stringify(target)};},700);</script></body></html>`);
}
