import { Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { StaffUser } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { SessionService } from '../../core/auth/session.service';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';
import { formatInstantDate } from '../../shared/format';
import { ReasonConfirm } from '../../shared/reason-confirm';

export const STATUS_UNCERTAIN =
  'No pudimos confirmar si se aplicó el cambio. Vuelve a cargar la lista para ver el estado actual antes de intentarlo otra vez.';

interface Panel {
  readonly kind: 'deactivate' | 'reactivate';
  readonly id: string;
}

/**
 * Cuentas del personal (GET /auth/staff-users), only for an ADMIN: who has an account, its role and
 * state, and who last deactivated or reactivated it and why. Deactivating and reactivating ask for a
 * reason (the service keeps it with who did it and when). The admin's own row has neither action:
 * auth-service refuses them (AUTH-009), which is what keeps the system from losing its last admin.
 */
@Component({
  selector: 'app-staff-list-page',
  imports: [RouterLink, ReasonConfirm],
  templateUrl: './staff-list.page.html',
  styleUrls: ['./staff.css', './staff-list.page.css'],
})
export class StaffListPage {
  private readonly api = inject(BackendApi);
  private readonly session = inject(SessionService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly accounts = rxResource({ stream: () => this.api.staffUsers() });
  protected readonly panel = signal<Panel | null>(null);
  protected readonly panelBusy = signal(false);
  protected readonly panelError = signal<string | null>(null);
  protected readonly status = signal('');

  private readonly reasonPanel = viewChild(ReasonConfirm);
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'las cuentas del personal', 'el servicio de autenticación');
  }

  protected isMe(account: StaffUser): boolean {
    return account.email === this.session.email();
  }

  protected roleText(account: StaffUser): string {
    return account.role === 'ADMIN' ? 'Administrador' : 'Personal';
  }

  protected date(instant: string): string {
    return formatInstantDate(instant);
  }

  protected open(kind: Panel['kind'], account: StaffUser): void {
    this.panel.set({ kind, id: account.id });
    this.panelError.set(null);
    this.status.set('');
    setTimeout(() => this.reasonPanel()?.focus());
  }

  protected close(): void {
    const closed = this.panel();
    this.panel.set(null);
    this.panelError.set(null);
    if (closed !== null) {
      setTimeout(() =>
        this.list()?.nativeElement.querySelector<HTMLElement>(`[data-open="${closed.kind}-${closed.id}"]`)?.focus(),
      );
    }
  }

  protected change(account: StaffUser, reason: string): void {
    const panel = this.panel();
    if (panel === null || this.panelBusy()) {
      return;
    }
    this.panelError.set(null);
    this.panelBusy.set(true);
    const call =
      panel.kind === 'deactivate'
        ? this.api.deactivateStaffUser(account.id, reason)
        : this.api.reactivateStaffUser(account.id, reason);
    call
      .pipe(
        finalize(() => this.panelBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.panel.set(null);
          this.status.set(
            panel.kind === 'deactivate'
              ? `Cuenta de ${account.email} desactivada. Ya no puede iniciar sesión.`
              : `Cuenta de ${account.email} reactivada.`,
          );
          this.accounts.reload();
          setTimeout(() => this.list()?.nativeElement.focus());
        },
        error: (error: unknown) => {
          this.panelError.set(
            writeErrorText(error, STATUS_UNCERTAIN, {
              'AUTH-009': 'No puedes hacer esto con tu propia cuenta.',
              'AUTH-010': 'La cuenta ya estaba en ese estado. Vuelve a cargar la lista para ver el actual.',
              'AUTH-006': 'Solo un administrador puede hacer esto.',
            }),
          );
          this.reasonPanel()?.focusError();
        },
      });
  }
}
