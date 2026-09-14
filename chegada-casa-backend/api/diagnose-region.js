import crypto from 'crypto';
import { resolveEwelinkCredentials } from '../lib/ewelink-config.js';

function sign(secret, payload) {
  return crypto.createHmac('sha256', secret)
    .update(payload)
    .digest('base64');
}

export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');

  const { clientId, clientSecret, clientIdSource, clientSecretSource } = resolveEwelinkCredentials();
  const countryCode = String(req.query.countryCode || '55').replace('+', '').trim();

  if (!clientId || !clientSecret) {
    return res.status(200).json({
      ok: false,
      configured: false,
      clientIdSource,
      clientSecretSource,
      message: 'Nenhum App ID/Secret eWeLink válido foi encontrado nas variáveis da Vercel.'
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
      json = { error: response.status, msg: `HTTP ${response.status}` };
    }

    return res.status(200).json({
      ok: response.ok && Number(json?.error ?? -1) === 0,
      configured: true,
      clientIdSource,
      clientSecretSource,
      appIdLength: clientId.length,
      secretLength: clientSecret.length,
      countryCode,
      coolkit: {
        error: json?.error,
        msg: json?.msg,
        region: json?.data?.region || null
      }
    });
  } catch (error) {
    return res.status(500).json({
      ok: false,
      message: error?.message || 'Falha ao consultar região no CoolKit.'
    });
  }
}
