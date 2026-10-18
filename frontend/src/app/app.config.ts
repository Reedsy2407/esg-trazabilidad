import { DATE_PIPE_DEFAULT_OPTIONS, registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeEsPe from '@angular/common/locales/es-PE';
import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';

import { routes } from './app.routes';
import { errorInterceptor, jwtInterceptor } from './core/http/interceptors';

registerLocaleData(localeEsPe);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      // Between routes the page fades (CSS in styles.css); a change inside the same route (another id, a filter,
      // a page) is not a transition: it skips it and updates at once.
      withViewTransitions({
        onViewTransitionCreated: ({ transition, from, to }) => {
          if (from.routeConfig === to.routeConfig) {
            transition.skipTransition();
          }
        },
      }),
    ),
    provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])),
    // es-PE: 12,480.50; dates dd/MM/yyyy in Lima time (UTC-5 all year, no DST).
    { provide: LOCALE_ID, useValue: 'es-PE' },
    { provide: DATE_PIPE_DEFAULT_OPTIONS, useValue: { dateFormat: 'dd/MM/yyyy', timezone: '-0500' } },
  ],
};
