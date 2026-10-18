import { Environment } from './environment.model';

/** ng serve: same-origin paths that proxy.conf.mjs forwards to the Render services, so local dev needs no CORS. */
export const environment: Environment = {
  services: {
    auth: '/svc/auth',
    recycler: '/svc/recycler',
    collection: '/svc/collection',
    reporting: '/svc/reporting',
  },
};
