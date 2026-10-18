import { Environment } from './environment.model';

/** Production: the four public Render services, called directly (needs CORS_ALLOWED_ORIGINS on them). */
export const environment: Environment = {
  services: {
    auth: 'https://esg-auth-service.onrender.com',
    recycler: 'https://esg-recycler-service.onrender.com',
    collection: 'https://esg-collection-service.onrender.com',
    reporting: 'https://esg-reporting-service.onrender.com',
  },
};
