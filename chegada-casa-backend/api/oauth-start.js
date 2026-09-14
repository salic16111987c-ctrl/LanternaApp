import crypto from 'crypto';

function nonce8() {
  const alphabet = 'abcdefghijklmnopqrstuvwxyz0123456789';
  const bytes = crypto.randomBytes(8);
  let out = '';
  for (let i = 0; i < 8; i++) out += alphabet[bytes[i] % alphabet.length];
  return out;
}

export default function handler(req, res) {
  const clientId = process.env.EWELINK_APP_ID;
  const clientSecret = process.env.EWELINK_APP_SECRET;
  const redirectUrl = process.env.EWELINK_REDIRECT_URL || 'http://127.0.0.1:8787/ewelink/callback';

  if (!clientId || !clientSecret) {
    return res.status(503).json({
      ok: false,
      configured: false,
      message: 'EWELINK_APP_ID ou EWELINK_APP_SECRET ausente no backend.'
    });
  }

  const state = String(req.query.state || 'chegada-casa');
  const seq = String(Date.now());
  const nonce = nonce8();
  const authorization = crypto.createHmac('sha256', clientSecret)
    .update(`${clientId}_${seq}`)
    .digest('base64');

  // IMPORTANTE: a biblioteca oficial ewelink-api-next monta esta URL
  // por concatenação simples. Não usar URLSearchParams aqui, pois ele
  // codifica caracteres do HMAC Base64 (+, /, =) e o login OAuth pode
  // ficar preso no carregamento.
  const params = {
    clientId,
    redirectUrl,
    grantType: 'authorization_code',
    state,
    nonce,
    seq,
    showQRCode: false,
    authorization
  };

  const query = Object.keys(params)
    .map((key) => `${key}=${params[key]}`)
    .join('&');

  const url = `https://c2ccdn.coolkit.cc/oauth/index.html?${query}`;

  res.setHeader('Cache-Control', 'no-store');

  if (String(req.query.open || '') === '1') {
    return res.redirect(302, url);
  }

  return res.status(200).json({ ok: true, url });
}
