function esc(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export default function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');

  const code = String(req.query.code || '').trim();
  const region = String(req.query.region || req.query.regin || '').trim();
  const state = String(req.query.state || '').trim();
  const error = String(req.query.error || '').trim();

  if (error) {
    return res.status(400).send(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Chegada Casa</title></head><body style="font-family:sans-serif;padding:24px"><h2>Autorização eWeLink não concluída</h2><p>${esc(error)}</p></body></html>`);
  }

  if (!code) {
    return res.status(400).send('<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Chegada Casa</title></head><body style="font-family:sans-serif;padding:24px"><h2>Retorno eWeLink inválido</h2><p>O código de autorização não foi recebido.</p></body></html>');
  }

  const app = new URL('chegadacasa://oauth');
  app.searchParams.set('code', code);
  if (region) app.searchParams.set('region', region);
  if (state) app.searchParams.set('state', state);
  const target = app.toString();

  return res.status(200).send(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Chegada Casa — autorizado</title></head><body style="font-family:sans-serif;padding:24px;line-height:1.5"><h2>eWeLink autorizado</h2><p>A autorização foi recebida. Abrindo o aplicativo Chegada Casa...</p><p><a style="display:inline-block;padding:14px 18px;background:#111;color:#fff;text-decoration:none;border-radius:8px" href="${esc(target)}">ABRIR CHEGADA CASA</a></p><p>Se o aplicativo não abrir sozinho, toque no botão acima.</p><script>setTimeout(function(){window.location.href=${JSON.stringify(target)};},250);</script></body></html>`);
}
