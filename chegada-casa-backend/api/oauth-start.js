import crypto from 'crypto';

// Este endereço precisa ser EXATAMENTE o mesmo cadastrado no app OAuth do eWeLink.
// O Chegada Casa já possui um servidor local nessa porta aguardando o retorno.
const REDIRECT_URL = 'http://127.0.0.1:8787/ewelink/callback';

function cleanEnv(value) {
  return String(value || '')
    .trim()
    .replace(/^['"]|['"]$/g, '')
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
  const clientId = cleanEnv(process.env.EWELINK_APP_ID);
  const clientSecret = cleanEnv(process.env.EWELINK_APP_SECRET);
  const redirectUrl = REDIRECT_URL;

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

  // Formato usado pelo OAuth2.0 do eWeLink. Os valores são codificados
  // individualmente para preservar +, / e = da assinatura Base64.
  const params = [
    ['clientId', clientId],
    ['seq', seq],
    ['authorization', authorization],
    ['redirectUrl', redirectUrl],
    ['grantType', 'authorization_code'],
    ['state', state],
    ['nonce', nonce]
  ];

  const query = params
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&');

  const url = `https://c2ccdn.coolkit.cc/oauth/index.html?${query}`;

  res.setHeader('Cache-Control', 'no-store');

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
