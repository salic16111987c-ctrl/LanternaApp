import crypto from 'crypto';

function cleanEnv(value) {
  return String(value || '')
    .trim()
    .replace(/^['"]|['"]$/g, '')
    .trim();
}

function sign(secret, payload) {
  return crypto.createHmac('sha256', secret)
    .update(payload)
    .digest('base64');
}

export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');

  const clientId = cleanEnv(process.env.EWELINK_APP_ID);
  const clientSecret = cleanEnv(process.env.EWELINK_APP_SECRET);
  const countryCode = String(req.query.countryCode || '55').replace('+', '').trim();

  if (!clientId || !clientSecret) {
    return res.status(503).json({
      ok: false,
      configured: false,
      message: 'EWELINK_APP_ID ou EWELINK_APP_SECRET ausente.'
    });
  }

  const payload = `countryCode=${countryCode}`;
  const authorization = sign(clientSecret, payload);

  try {
    const url = `https://apia.coolkit.cn/v2/utils/get-region?countryCode=${encodeURIComponent(countryCode)}`;
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'X-CK-Appid': clientId,
        'Authorization': `Sign ${authorization}`,
        'Accept': 'application/json'
      }
    });

    let json;
    try {
      json = await response.json();
    } catch {
      json = { raw: await response.text() };
    }

    return res.status(200).json({
      ok: response.ok && Number(json?.error ?? -1) === 0,
      httpStatus: response.status,
      appIdLength: clientId.length,
      appIdSuffix: clientId.slice(-6),
      secretLength: clientSecret.length,
      countryCode,
      coolkit: json
    });
  } catch (error) {
    return res.status(500).json({
      ok: false,
      message: error?.message || 'Falha ao consultar região no CoolKit.'
    });
  }
}
