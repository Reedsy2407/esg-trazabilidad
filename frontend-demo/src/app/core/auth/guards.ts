import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SessionService } from './session.service';

/** Screens behind the login. An expired token counts as signed out. */
export const authGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  if (session.isAuthenticated()) {
    return true;
  }
  session.clear();
  return inject(Router).createUrlTree(['/login']);
};

/** The login itself: someone already signed in goes straight to the companies. */
export const guestGuard: CanActivateFn = () =>
  inject(SessionService).isAuthenticated() ? inject(Router).createUrlTree(['/empresas']) : true;
