import crypto from 'crypto';
import { resolveEwelinkCredentials } from '../lib/ewelink-config.js';

const REDIRECT_URL = 'https://chegada-casa-api.vercel.app/api/auth/callback';

function nonce8() {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  const bytes = crypto.randomBytes(8);
  let out = '';
  for (let i = 0; i < 8; i++) out += alphabet[bytes[i] % alphabet.length];
  return out;
}

export default function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');

  const { clientId, clientSecret, clientIdSource, clientSecretSource } = resolveEwelinkCredentials();
  const redirectUrl = REDIRECT_URL;

  if (!clientId || !clientSecret) {
    return res.status(503).json({
      ok: false,
      configured: false,
      message: 'Não encontrei um App ID/Secret eWeLink válido no backend.',
      clientIdSource,
      clientSecretSource
    });
  }

  const state = String(req.query.state || 'chegada-casa');
  const seq = String(Date.now());
  const authorization = crypto.createHmac('sha256', clientSecret)
    .update(`${clientId}_${seq}`)
    .digest('base64');

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
    clientIdSource,
    clientSecretSource,
    redirectUrl,
    url
  });
}
