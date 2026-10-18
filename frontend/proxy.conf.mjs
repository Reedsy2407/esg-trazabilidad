// ng serve only: forwards /svc/<service>/... to that Render service, so the
// browser stays same-origin and local development never depends on CORS.
// The Origin header is dropped: the backend would otherwise treat
// http://localhost:4200 as a foreign origin and refuse it (its allow-list is
// empty unless CORS_ALLOWED_ORIGINS is set).
const services = {
  auth: 'https://esg-auth-service.onrender.com',
  recycler: 'https://esg-recycler-service.onrender.com',
  collection: 'https://esg-collection-service.onrender.com',
  reporting: 'https://esg-reporting-service.onrender.com',
};

export default Object.fromEntries(
  Object.entries(services).map(([name, target]) => [
    `/svc/${name}`,
    {
      target,
      secure: true,
      changeOrigin: true,
      // A cold start on Render's free plan can take ~2 min.
      proxyTimeout: 180_000,
      timeout: 180_000,
      rewrite: (path) => path.replace(`/svc/${name}`, ''),
      configure: (proxy) => {
        proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
      },
    },
  ]),
);
