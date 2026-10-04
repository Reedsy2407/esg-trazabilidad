import { DATE_PIPE_DEFAULT_OPTIONS, registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeEsPe from '@angular/common/locales/es-PE';
import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { errorInterceptor, jwtInterceptor } from './core/http/interceptors';

registerLocaleData(localeEsPe);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([jwtInterceptor, errorInterceptor])),
    // es-PE: 12,480.50; dates dd/MM/yyyy in Lima time (UTC-5 all year, no DST).
    { provide: LOCALE_ID, useValue: 'es-PE' },
    { provide: DATE_PIPE_DEFAULT_OPTIONS, useValue: { dateFormat: 'dd/MM/yyyy', timezone: '-0500' } },
  ],
};
