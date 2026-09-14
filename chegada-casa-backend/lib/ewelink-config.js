const FALLBACK_APP_ID = 'd4m62Z1qSN0le69A1rJqhUd7al3eA7qg';

function clean(value) {
  return String(value || '')
    .trim()
    .replace(/^['"]|['"]$/g, '')
    .trim();
}

function validAppId(value) {
  const v = clean(value);
  return /^[A-Za-z0-9]{6,128}$/.test(v);
}

function validSecret(value) {
  const v = clean(value);
  return v.length >= 16;
}

function firstValid(entries, validator) {
  for (const [name, value] of entries) {
    const v = clean(value);
    if (validator(v)) return { name, value: v };
  }
  return null;
}

export function resolveEwelinkCredentials(env = process.env) {
  const explicitIds = [
    ['EWELINK_APP_ID', env.EWELINK_APP_ID],
    ['EWELINK_APPID', env.EWELINK_APPID],
    ['EWELINK_CLIENT_ID', env.EWELINK_CLIENT_ID],
    ['EWELINK_API_KEY', env.EWELINK_API_KEY],
    ['COOLKIT_APP_ID', env.COOLKIT_APP_ID],
    ['COOLKIT_CLIENT_ID', env.COOLKIT_CLIENT_ID]
  ];

  const explicitSecrets = [
    ['EWELINK_APP_SECRET', env.EWELINK_APP_SECRET],
    ['EWELINK_SECRET', env.EWELINK_SECRET],
    ['EWELINK_CLIENT_SECRET', env.EWELINK_CLIENT_SECRET],
    ['EWELINK_API_SECRET', env.EWELINK_API_SECRET],
    ['COOLKIT_APP_SECRET', env.COOLKIT_APP_SECRET],
    ['COOLKIT_CLIENT_SECRET', env.COOLKIT_CLIENT_SECRET]
  ];

  let id = firstValid(explicitIds, validAppId);
  let secret = firstValid(explicitSecrets, validSecret);

  const related = Object.entries(env)
    .filter(([name]) => /EWELINK|COOLKIT/i.test(name));

  if (!id) {
    id = firstValid(
      related.filter(([name]) => /(APP.*ID|APPID|CLIENT.*ID|API.*KEY)/i.test(name) && !/SECRET/i.test(name)),
      validAppId
    );
  }

  if (!id && validAppId(FALLBACK_APP_ID)) {
    id = { name: 'FALLBACK_APP_ID_FROM_EWELINK_PORTAL', value: FALLBACK_APP_ID };
  }

  if (!secret) {
    secret = firstValid(
      related.filter(([name]) => /SECRET/i.test(name)),
      validSecret
    );
  }

  return {
    clientId: id?.value || '',
    clientSecret: secret?.value || '',
    clientIdSource: id?.name || null,
    clientSecretSource: secret?.name || null
  };
}
