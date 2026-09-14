import crypto from 'crypto';

const DEFAULT_REDIRECT_URL = 'http://127.0.0.1:8787/ewelink/callback';

function cleanEnv(value) {
  return String(value || '')
    .trim()
    .replace(/^[\'\"]|[\'\"]$/g, '')
    .trim();
}

function nonce8() {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  const bytes = crypto.randomBytes(8);
  let out = '';
  for (let i = 0; i < 8; i++) out += alphabet[bytes[i] % alphabet.length];
  return out;
}

export default function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');

  const clientId = cleanEnv(process.env.EWELINK_APP_ID);
  const clientSecret = cleanEnv(process.env.EWELINK_APP_SECRET);
  const redirectUrl = cleanEnv(process.env.EWELINK_REDIRECT_URL) || DEFAULT_REDIRECT_URL;

  if (!clientId || !clientSecret) {
    return res.status(503).json({
      ok: false,
      configured: false,
      message: 'EWELINK_APP_ID ou EWELINK_APP_SECRET ausente no backend.'
    });
  }

  const state = String(req.query.state || 'chegada-casa');
  const seq = String(Date.now());
  const authorization = crypto.createHmac('sha256', clientSecret)
    .update(`${clientId}_${seq}`)
    .digest('base64');

  // Replica o formato da biblioteca oficial ewelink-api-next.
  // Importante: a página OAuth da CoolKit recebe a assinatura Base64 crua
  // na query; não usamos URLSearchParams/encodeURIComponent aqui.
  const params = {
    clientId,
    redirectUrl,
    grantType: 'authorization_code',
    state,
    nonce: nonce8(),
    seq,
    showQRCode: false,
    authorization
  };

  const query = Object.keys(params)
    .map((key) => `${key}=${params[key]}`)
    .join('&');

  const url = `https://c2ccdn.coolkit.cc/oauth/index.html?${query}`;

  if (String(req.query.open || '') === '1') {
    return res.redirect(302, url);
  }

  return res.status(200).json({
    ok: true,
    configured: true,
    clientId,
    redirectUrl,
    url
  });
}
