// Prueba de humo del bucket de evidencia (Backblaze B2, API compatible con S3) con la MISMA forma de
// firma que usa collection-service: una URL firmada de subida que cubre el tipo, el tamaño y el SHA-256.
//
// Lo hace el humano, una vez, tras desplegar la etapa 1 y con las variables SOLO en su sesión de PowerShell:
//   $env:B2_BUCKET = "..."; $env:B2_KEY_ID = "..."; $env:B2_APPLICATION_KEY = "..."
//   (opcionales: B2_ENDPOINT, B2_REGION; por defecto us-east-005)
//   node scripts/smoke-b2.mjs
//   Remove-Item Env:B2_BUCKET, Env:B2_KEY_ID, Env:B2_APPLICATION_KEY
//
// Qué comprueba, en orden: (1) una subida con otro contenido que el firmado es rechazada; (2) la subida de un
// JPEG diminuto con la URL firmada; (3) el tamaño del objeto; (4) la URL firmada de lectura devuelve los mismos
// bytes y sin firma no se puede leer; (5) el borrado; (6) el bucket queda vacío.
//
// Garantías: no lee archivos .env ni ningún archivo; nunca imprime la clave, el identificador ni una URL
// firmada (solo estados y tamaños); si encuentra objetos ajenos al terminar, no los borra: lo dice y sale con 2.
// `node scripts/smoke-b2.mjs --selftest` comprueba la firma sin red: debe dar la misma que el firmador S3 del
// SDK de AWS para un GET y para un PUT con cabeceras firmadas (las fija SigV4CrossCheckTest en
// collection-service con las mismas entradas). No es el mismo objeto de código que S3Presigner (el que firma
// en producción), pero ambos usan el algoritmo SigV4 de S3; lo que cierra el círculo es el paso (2) de este smoke.
import crypto from 'node:crypto';
import http from 'node:http';
import https from 'node:https';

const sha256Hex = (data) => crypto.createHash('sha256').update(data).digest('hex');
const hmac = (key, data) => crypto.createHmac('sha256', key).update(data).digest();
const encode = (value) => encodeURIComponent(value).replace(/[!'()*]/g, (c) => `%${c.charCodeAt(0).toString(16).toUpperCase()}`);
const encodePath = (path) => path.split('/').map(encode).join('/');

/**
 * Firma por parámetros de consulta (AWS Signature Version 4). `headers` son los encabezados que la firma
 * cubre además de `host`; quien use la URL debe enviarlos con exactamente esos valores.
 * Devuelve la cadena de consulta completa, con `X-Amz-Signature` al final.
 */
export function presign({ method, host, path, query = {}, headers = {}, expires, region, keyId, secret, now = new Date() }) {
  const amzDate = now.toISOString().replace(/[:-]|\.\d{3}/g, '');
  const dateStamp = amzDate.slice(0, 8);
  const scope = `${dateStamp}/${region}/s3/aws4_request`;
  const signed = { host, ...Object.fromEntries(Object.entries(headers).map(([k, v]) => [k.toLowerCase(), String(v).trim()])) };
  const names = Object.keys(signed).sort();
  const params = {
    ...query,
    'X-Amz-Algorithm': 'AWS4-HMAC-SHA256',
    'X-Amz-Credential': `${keyId}/${scope}`,
    'X-Amz-Date': amzDate,
    'X-Amz-Expires': String(expires),
    'X-Amz-SignedHeaders': names.join(';'),
  };
  const canonicalQuery = Object.keys(params).sort().map((k) => `${encode(k)}=${encode(params[k])}`).join('&');
  const canonicalHeaders = names.map((n) => `${n}:${signed[n]}\n`).join('');
  const canonicalRequest = [method, encodePath(path), canonicalQuery, canonicalHeaders, names.join(';'), 'UNSIGNED-PAYLOAD'].join('\n');
  const stringToSign = ['AWS4-HMAC-SHA256', amzDate, scope, sha256Hex(canonicalRequest)].join('\n');
  const signingKey = hmac(hmac(hmac(hmac(`AWS4${secret}`, dateStamp), region), 's3'), 'aws4_request');
  const signature = crypto.createHmac('sha256', signingKey).update(stringToSign).digest('hex');
  return `${canonicalQuery}&X-Amz-Signature=${signature}`;
}

function selftest() {
  // Las mismas entradas que SigV4CrossCheckTest (reloj fijo, credenciales inventadas).
  const query = presign({
    method: 'GET',
    host: 'bucket.example.test',
    path: '/test-bucket/smoke-test/object.jpg',
    expires: 600,
    region: 'us-east-005',
    keyId: 'stub-id',
    secret: 'stub-secret',
    now: new Date('2026-01-02T03:04:05Z'),
  });
  const expected = '0b2b4a38d0d2245d2f0a513f8799b0803f77ccee1f2f48e6400692b238ed99af';
  // Un PUT cuya firma cubre tipo, tamaño y hash (las cabeceras de la URL de subida de collection-service).
  const put = presign({
    method: 'PUT',
    host: 'bucket.example.test',
    path: '/test-bucket/smoke-test/object.jpg',
    headers: { 'content-type': 'image/jpeg', 'content-length': 1234, 'x-amz-checksum-sha256': 'q83vEjRWeJA=' },
    expires: 600,
    region: 'us-east-005',
    keyId: 'stub-id',
    secret: 'stub-secret',
    now: new Date('2026-01-02T03:04:05Z'),
  });
  const expectedPut = '49f7f698e3fc67c7fc25bd4414e46524ba898f6bb19580103eb75ebb797b6c24';
  if (!query.endsWith(`X-Amz-Signature=${expected}`) || !put.endsWith(`X-Amz-Signature=${expectedPut}`)) {
    console.error('AUTOTEST FALLIDO: la firma no coincide con la del firmador S3 del SDK de AWS.');
    process.exit(1);
  }
  console.log('Autotest de la firma: correcto (GET y PUT con cabeceras firmadas coinciden con el firmador S3 del SDK de AWS).');
}

function request(method, url, headers = {}, body = null) {
  return new Promise((resolve, reject) => {
    const target = new URL(url);
    const client = target.protocol === 'http:' ? http : https;
    const req = client.request(target, { method, headers, timeout: 30000 }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks) }));
    });
    req.on('timeout', () => req.destroy(new Error('tiempo de espera agotado')));
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

// Unos pocos bytes con forma de JPEG mínimo: el bucket no valida el contenido, solo importan el tamaño y el hash.
const TINY_JPEG = Buffer.from(
  '/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////' +
    'wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=',
  'base64',
);

async function main() {
  if (process.argv.includes('--selftest')) {
    selftest();
    return;
  }
  const missing = ['B2_BUCKET', 'B2_KEY_ID', 'B2_APPLICATION_KEY'].filter((n) => !process.env[n]);
  if (missing.length) {
    console.error(`Faltan variables de entorno: ${missing.join(', ')}. Ponlas solo en tu sesión (ver el encabezado del script).`);
    process.exit(1);
  }
  const endpoint = new URL(process.env.B2_ENDPOINT || 'https://s3.us-east-005.backblazeb2.com');
  const region = process.env.B2_REGION || 'us-east-005';
  const bucket = process.env.B2_BUCKET;
  const keyId = process.env.B2_KEY_ID;
  const secret = process.env.B2_APPLICATION_KEY;
  const host = endpoint.host;
  const base = `${endpoint.protocol}//${host}`;
  const key = `smoke-test/${crypto.randomUUID()}.jpg`;
  const objectPath = `/${bucket}/${key}`;
  const sign = (method, path, extra = {}) => `${base}${encodePath(path)}?${presign({ method, host, path, region, keyId, secret, ...extra })}`;
  let failed = false;
  const check = (label, ok, detail = '') => {
    console.log(`${ok ? 'OK    ' : 'FALLÓ '} ${label}${detail ? ` (${detail})` : ''}`);
    if (!ok) failed = true;
    return ok;
  };

  const size = TINY_JPEG.length;
  const hashBase64 = crypto.createHash('sha256').update(TINY_JPEG).digest('base64');
  const uploadHeaders = { 'content-type': 'image/jpeg', 'content-length': size, 'x-amz-checksum-sha256': hashBase64 };
  const uploadUrl = sign('PUT', objectPath, { expires: 600, headers: uploadHeaders });

  // (1) Otro contenido (otro tamaño y otro hash) con la misma URL firmada: debe ser rechazado.
  const other = Buffer.concat([TINY_JPEG, Buffer.from('extra')]);
  const rejected = await request('PUT', uploadUrl, {
    'content-type': 'image/jpeg',
    'content-length': other.length,
    'x-amz-checksum-sha256': crypto.createHash('sha256').update(other).digest('base64'),
  }, other);
  check('una subida con otro contenido que el firmado es rechazada', rejected.status >= 400, `estado ${rejected.status}`);
  const stillMissing = await request('HEAD', sign('HEAD', objectPath, { expires: 300 }));
  check('el objeto rechazado no quedó en el bucket', stillMissing.status === 404, `estado ${stillMissing.status}`);

  // (2) La subida legítima.
  const uploaded = await request('PUT', uploadUrl, uploadHeaders, TINY_JPEG);
  check('subida del JPEG con la URL firmada', uploaded.status === 200, `estado ${uploaded.status}`);

  // (3) Tamaño.
  const head = await request('HEAD', sign('HEAD', objectPath, { expires: 300 }));
  check('el objeto existe con el tamaño subido', head.status === 200 && Number(head.headers['content-length']) === size,
    `estado ${head.status}, tamaño ${head.headers['content-length']} de ${size}`);

  // (4) Lectura firmada.
  const read = await request('GET', sign('GET', objectPath, { expires: 300 }));
  check('la URL firmada de lectura devuelve los mismos bytes', read.status === 200 && read.body.equals(TINY_JPEG), `estado ${read.status}`);
  const anonymous = await request('GET', `${base}${encodePath(objectPath)}`);
  check('sin firma el objeto no se puede leer (bucket privado)', anonymous.status >= 400, `estado ${anonymous.status}`);

  // (5) Borrado.
  const deleted = await request('DELETE', sign('DELETE', objectPath, { expires: 300 }));
  check('borrado del objeto', deleted.status === 204 || deleted.status === 200, `estado ${deleted.status}`);
  const gone = await request('HEAD', sign('HEAD', objectPath, { expires: 300 }));
  check('tras borrar, el objeto ya no existe', gone.status === 404, `estado ${gone.status}`);

  // (6) El bucket queda vacío.
  const listing = await request('GET', sign('GET', `/${bucket}`, { expires: 300, query: { 'list-type': '2', 'max-keys': '5' } }));
  const keys = [...listing.body.toString('utf8').matchAll(/<Key>([^<]*)<\/Key>/g)].map((m) => m[1]);
  check('el bucket queda vacío', listing.status === 200 && keys.length === 0,
    listing.status === 200 ? `${keys.length} objeto(s) encontrados` : `estado ${listing.status}`);
  if (listing.status === 200 && keys.length > 0) {
    console.log('Hay objetos que este script no creó; no se borran. Revísalos en el panel de Backblaze.');
    process.exit(2);
  }
  console.log(failed ? 'RESULTADO: hay comprobaciones fallidas.' : 'RESULTADO: todas las comprobaciones pasaron.');
  process.exit(failed ? 1 : 0);
}

if (process.argv[1] && process.argv[1].endsWith('smoke-b2.mjs')) {
  main().catch((error) => {
    console.error(`Error inesperado: ${error.message}`);
    process.exit(1);
  });
}
