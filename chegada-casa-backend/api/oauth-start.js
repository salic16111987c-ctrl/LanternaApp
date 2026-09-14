import crypto from 'crypto';

const REDIRECT_URL = 'https://chegada-casa-api.vercel.app/api/auth/callback';

function cleanEnv(value) {
  return String(value || '')
    .trim()
    .replace(/^['"]|['"]$/g, '')
    .trim();
}

function nonce8() {
  return Math.random().toString(36).slice(-8);
}

export default function handler(req, res) {
  // Limpa espaços/aspas acidentais das variáveis da Vercel.
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
  const authorization = crypto.createHmac('sha256', clientSecret)
    .update(`${clientId}_${seq}`)
    .digest('base64');

  // Mesmo formato da biblioteca ewelink-api-next usada no exemplo oficial.
  const params = {
    clientId,
    redirectUrl,
    grantType: 'authorization_code',
    state,
    nonce: nonce8(),
    seq,
    showQRCode: null,
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

  // Não expõe o segredo. O retorno ajuda a conferir se a configuração
  // ativa é a mesma cadastrada no portal do eWeLink.
  return res.status(200).json({
    ok: true,
    configured: true,
    clientId,
    redirectUrl,
    url
  });
}
