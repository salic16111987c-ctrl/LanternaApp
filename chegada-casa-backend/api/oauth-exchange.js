import crypto from 'crypto';

function sign(secret, payload) {
  return crypto.createHmac('sha256', secret)
    .update(payload)
    .digest('base64');
}

function apiBase(region) {
  if (region === 'cn') return 'https://cn-apia.coolkit.cn';
  if (region === 'eu') return 'https://eu-apia.coolkit.cc';
  if (region === 'as') return 'https://as-apia.coolkit.cc';
  return 'https://us-apia.coolkit.cc';
}

async function exchangeOnce({ clientId, clientSecret, redirectUrl, code, region }) {
  const bodyObject = {
    redirectUrl,
    code,
    grantType: 'authorization_code'
  };
  const body = JSON.stringify(bodyObject);
  const authorization = sign(clientSecret, body);

  const response = await fetch(`${apiBase(region)}/v2/user/oauth/token`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'X-CK-Appid': clientId,
      'Authorization': `Sign ${authorization}`
    },
    body
  });

  let json;
  try {
    json = await response.json();
  } catch {
    json = { error: response.status, msg: `HTTP ${response.status}` };
  }

  return { response, json };
}

export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');

  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return res.status(405).json({ ok: false, message: 'Use POST.' });
  }

  const clientId = process.env.EWELINK_APP_ID;
  const clientSecret = process.env.EWELINK_APP_SECRET;
  const redirectUrl = process.env.EWELINK_REDIRECT_URL || 'https://chegada-casa-api.vercel.app/api/auth/callback';

  if (!clientId || !clientSecret) {
    return res.status(503).json({ ok: false, configured: false, message: 'Backend eWeLink não configurado.' });
  }

  const code = String(req.body?.code || '').trim();
  const requestedRegion = String(req.body?.region || '').toLowerCase().trim();

  if (!code) {
    return res.status(400).json({ ok: false, message: 'Código OAuth ausente.' });
  }

  const validRegions = ['us', 'eu', 'as', 'cn'];
  const regions = validRegions.includes(requestedRegion)
    ? [requestedRegion]
    : ['us', 'eu', 'as', 'cn'];

  let last = null;

  try {
    for (const region of regions) {
      const result = await exchangeOnce({
        clientId,
        clientSecret,
        redirectUrl,
        code,
        region
      });
      last = result;

      const error = Number(result.json?.error ?? -1);
      if (error !== 0) continue;

      const data = result.json?.data || {};
      const accessToken = data.at || data.accessToken || data.access_token || '';
      const refreshToken = data.rt || data.refreshToken || data.refresh_token || '';
      const finalRegion = String(data.region || region);

      if (!accessToken) {
        return res.status(502).json({
          ok: false,
          message: 'eWeLink respondeu sem Access Token.'
        });
      }

      return res.status(200).json({
        ok: true,
        accessToken,
        refreshToken,
        region: finalRegion,
        expiresIn: 2592000
      });
    }

    return res.status(502).json({
      ok: false,
      error: Number(last?.json?.error ?? -1),
      message: String(last?.json?.msg || 'Não foi possível trocar o código OAuth pelo token.')
    });
  } catch (error) {
    return res.status(500).json({
      ok: false,
      message: error?.message || 'Falha interna ao obter token do eWeLink.'
    });
  }
}
