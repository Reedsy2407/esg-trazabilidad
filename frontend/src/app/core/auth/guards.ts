import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, Router } from '@angular/router';

import { SessionService } from './session.service';

/** Where a person whose password was chosen by someone else must go before anything else. */
export const CHANGE_PASSWORD_URL = '/mi-sesion/contrasena';

/** Screens behind the login. An expired token counts as signed out. */
export const authGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  if (session.isAuthenticated()) {
    return true;
  }
  session.clear();
  return inject(Router).createUrlTree(['/login']);
};

/**
 * A new account, or one an admin reset, starts with a password somebody else chose: until the
 * person picks their own, the only screen open is the one that changes it (and "Cerrar sesión").
 * It only steers the screens: the services keep accepting the token, by design.
 */
export const passwordChangeGuard: CanActivateChildFn = (_route, state) => {
  const session = inject(SessionService);
  if (session.mustChangePassword() && state.url.split('?')[0] !== CHANGE_PASSWORD_URL) {
    return inject(Router).createUrlTree([CHANGE_PASSWORD_URL]);
  }
  return true;
};

/**
 * The account administration screens. auth-service refuses a STAFF with AUTH-006 whatever the
 * screens do; this only keeps them from opening a page that cannot work.
 */
export const adminGuard: CanActivateFn = () =>
  inject(SessionService).isAdmin() ? true : inject(Router).createUrlTree(['/mi-sesion']);

/** The login itself: someone already signed in goes straight to the companies. */
export const guestGuard: CanActivateFn = () =>
  inject(SessionService).isAuthenticated() ? inject(Router).createUrlTree(['/empresas']) : true;
