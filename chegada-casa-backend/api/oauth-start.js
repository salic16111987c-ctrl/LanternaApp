import crypto from 'crypto';

export default function handler(req, res) {
  const clientId = process.env.EWELINK_APP_ID;
  const clientSecret = process.env.EWELINK_APP_SECRET;
  const redirectUrl = process.env.EWELINK_REDIRECT_URL || 'http://127.0.0.1:8787/ewelink/callback';

  if (!clientId || !clientSecret) {
    return res.status(503).json({ ok: false, configured: false });
  }

  const state = String(req.query.state || 'chegada-casa');
  const seq = String(Date.now());
  const nonce = crypto.randomBytes(6).toString('hex').slice(0, 8);
  const authorization = crypto.createHmac('sha256', clientSecret)
    .update(`${clientId}_${seq}`)
    .digest('base64');

  const url = new URL('https://c2ccdn.coolkit.cc/oauth/index.html');
  url.searchParams.set('state', state);
  url.searchParams.set('clientId', clientId);
  url.searchParams.set('authorization', authorization);
  url.searchParams.set('seq', seq);
  url.searchParams.set('redirectUrl', redirectUrl);
  url.searchParams.set('nonce', nonce);
  url.searchParams.set('grantType', 'authorization_code');
  url.searchParams.set('showQRCode', 'false');

  return res.status(200).json({ ok: true, url: url.toString() });
}
