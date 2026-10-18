import { Component, DestroyRef, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { ApiError } from '../../core/http/api-error';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';
import { generateInitialPassword } from './initial-password';
import { SecretBox } from './secret-box';

type Outcome =
  /** 204: the password is set. */
  | 'applied'
  /** No answer, 408 or 5xx: it may or may not be set. */
  | 'uncertain';

/**
 * Restablecer la contraseña de otra persona (POST /auth/staff-users/{id}/password), only for an
 * ADMIN. The password is generated here, like the initial one, and exists only in this component's
 * memory: never in storage, the URL, router state, the console or a DOM attribute. The person is
 * asked to choose their own at the next sign-in. A retry after an unknown outcome reuses the same
 * password, because the first attempt may have set it.
 */
@Component({
  selector: 'app-staff-reset-page',
  imports: [RouterLink, SecretBox],
  templateUrl: './staff-reset.page.html',
  styleUrls: ['./staff.css', './staff-new.page.css'],
})
export class StaffResetPage {
  private readonly api = inject(BackendApi);
  private readonly destroyRef = inject(DestroyRef);

  /** The :id of the route. */
  readonly id = input.required<string>();

  protected readonly accounts = rxResource({ stream: () => this.api.staffUsers() });
  protected readonly account = computed(() =>
    this.accounts.hasValue() ? (this.accounts.value().find((a) => a.id === this.id()) ?? null) : null,
  );

  /** The new password: only here, in memory, from the first attempt until it is handed over. */
  protected readonly password = signal<string | null>(null);
  protected readonly confirmed = signal(false);
  protected readonly submitted = signal(false);
  protected readonly sending = signal(false);
  protected readonly outcome = signal<Outcome | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly status = signal('');

  private readonly resultTitle = viewChild<ElementRef<HTMLElement>>('resultTitle');

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'la cuenta', 'el servicio de autenticación');
  }

  protected setConfirmed(event: Event): void {
    this.confirmed.set((event.target as HTMLInputElement).checked);
  }

  protected submit(): void {
    this.submitted.set(true);
    const account = this.account();
    if (account === null || this.sending() || this.outcome() === 'applied') {
      return;
    }
    if (!this.confirmed()) {
      return;
    }
    const password = this.password() ?? generateInitialPassword();
    this.password.set(password);
    this.error.set(null);
    this.sending.set(true);
    this.api
      .resetStaffPassword(account.id, password)
      .pipe(
        finalize(() => this.sending.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.settle('applied'),
        error: (error: unknown) => {
          const uncertain = !(error instanceof ApiError) || error.status === 0 || error.status === 408 || error.status >= 500;
          if (uncertain) {
            this.settle('uncertain');
            return;
          }
          // Refused: nothing was set with this password (unless an earlier attempt is still unknown).
          if (this.outcome() !== 'uncertain') {
            this.password.set(null);
          }
          this.error.set(
            writeErrorText(error, '', {
              'AUTH-009': 'No puedes restablecer tu propia contraseña así: usa «Cambiar mi contraseña».',
              'AUTH-006': 'Solo un administrador puede hacer esto.',
              'AUTH-003': 'La cuenta ya no existe.',
              'AUTH-007': 'El servicio no aceptó la contraseña generada. Vuelve a intentarlo: se generará otra.',
            }),
          );
        },
      });
  }

  private settle(outcome: Outcome): void {
    this.outcome.set(outcome);
    this.confirmed.set(false);
    this.status.set(
      outcome === 'applied'
        ? 'Contraseña restablecida.'
        : 'No pudimos confirmar si se restableció la contraseña.',
    );
    setTimeout(() => this.resultTitle()?.nativeElement.focus());
  }

  /** "Ya la entregué": the password leaves memory and screen. */
  protected handedOver(): void {
    this.password.set(null);
    this.outcome.set(null);
    this.submitted.set(false);
    this.status.set('La contraseña se borró de esta pantalla.');
  }
}
