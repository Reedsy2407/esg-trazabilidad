import { Component, DestroyRef, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { IssuedActivationCode, RecyclerAccountStatus, RecyclerDevice } from '../../core/api/api.types';
import { BackendApi } from '../../core/api/backend.api';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';
import { formatInstantDate } from '../../shared/format';

const CODE_UNCERTAIN =
  'No pudimos confirmar si el código se generó. Recarga la página y mira el estado de la cuenta antes de volver a intentarlo: un código nuevo reemplaza al anterior.';
const REVOKE_UNCERTAIN =
  'No pudimos confirmar si el dispositivo se revocó. Recarga la lista de dispositivos antes de volver a intentarlo.';

const ACCOUNT_TEXT: Readonly<Record<RecyclerAccountStatus, string>> = {
  PENDING_ACTIVATION: 'Esperando que active la app',
  ACTIVE: 'Activa',
  INACTIVE: 'Inactiva',
};

/**
 * One recycler's access to the app (auth-service): the account and its state, the activation code
 * (generated here, shown once, never stored by this page) and the phones signed in, each of which
 * can be revoked. The code lives only in a signal while it is on screen: not in the URL, storage, an
 * attribute or a log, and the clipboard this page filled is overwritten when it goes away.
 */
@Component({
  selector: 'app-recycler-access-page',
  imports: [RouterLink],
  templateUrl: './recycler-access.page.html',
  styleUrl: './recycler-access.page.css',
})
export class RecyclerAccessPage {
  private readonly api = inject(BackendApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly associationId = input.required<string>();
  readonly recyclerId = input.required<string>();

  protected readonly recycler = rxResource({
    params: () => ({ a: this.associationId(), r: this.recyclerId() }),
    stream: ({ params }) => this.api.recycler(params.a, params.r),
  });

  protected readonly accounts = rxResource({
    params: () => this.associationId(),
    stream: ({ params }) => this.api.recyclerAccounts(params),
  });

  /** null: loaded and this recycler has no account yet. undefined: still loading or failed. */
  protected readonly account = computed(() =>
    this.accounts.hasValue() ? (this.accounts.value().find((a) => a.recyclerId === this.recyclerId()) ?? null) : undefined,
  );

  protected readonly devices = rxResource({
    params: () => this.account()?.accountId,
    stream: ({ params }) => this.api.recyclerDevices(params),
  });

  protected readonly status = signal('');

  protected readonly accountText = computed(() => {
    const a = this.account();
    return a ? ACCOUNT_TEXT[a.status] : 'Sin cuenta';
  });

  /**
   * Moves keyboard and screen-reader focus after the element that had it was replaced. A control can appear in the
   * table and in the narrow-screen cards, and only the visible one can take focus: the first that does is used.
   */
  private focus(selector: string): void {
    setTimeout(() => {
      for (const element of this.host.nativeElement.querySelectorAll<HTMLElement>(selector)) {
        element.focus();
        if (document.activeElement === element) return;
      }
    });
  }

  protected loadError(error: unknown, what: string): string {
    return loadErrorMessage(error, what, 'el servicio de autenticación');
  }

  protected date(instant: string): string {
    return formatInstantDate(instant);
  }

  protected deviceState(device: RecyclerDevice): string {
    return device.revokedAt === null ? 'En uso' : `Revocado el ${formatInstantDate(device.revokedAt)}`;
  }

  // --- activation code ---------------------------------------------------------------------

  /** The code just issued: in memory only, dropped by "Ya lo entregué" or by leaving the page. */
  protected readonly issued = signal<IssuedActivationCode | null>(null);
  protected readonly codeBusy = signal(false);
  protected readonly codeError = signal<string | null>(null);
  protected readonly copyStatus = signal('');
  private copied = false;

  private readonly codeTitle = viewChild<ElementRef<HTMLElement>>('codeTitle');

  constructor() {
    // Leaving the page overwrites the clipboard if this page filled it (best effort).
    this.destroyRef.onDestroy(() => {
      if (this.copied) {
        navigator.clipboard?.writeText('').catch(() => undefined);
      }
    });
  }

  protected codeActionText(): string {
    const a = this.account();
    if (a === null || a === undefined) {
      return 'Crear cuenta y generar código';
    }
    return 'Generar código nuevo';
  }

  protected generateCode(): void {
    if (this.codeBusy() || this.account() === undefined) {
      return;
    }
    const account = this.account();
    this.codeBusy.set(true);
    this.codeError.set(null);
    this.copyStatus.set('');
    const call =
      account === null || account === undefined
        ? this.api.createRecyclerAccount(this.recyclerId())
        : this.api.reissueActivationCode(account.accountId);
    call
      .pipe(
        finalize(() => this.codeBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (code) => {
          this.issued.set(code);
          this.accounts.reload();
          setTimeout(() => this.codeTitle()?.nativeElement.focus());
        },
        error: (error: unknown) =>
          this.codeError.set(
            writeErrorText(error, CODE_UNCERTAIN, {
              DATA_CONFLICT:
                'La cuenta ya tiene un teléfono en uso. Revoca sus dispositivos antes de generar un código nuevo.',
            }),
          ),
      });
  }

  protected async copyCode(): Promise<void> {
    const code = this.issued();
    if (code === null) {
      return;
    }
    try {
      await navigator.clipboard.writeText(code.activationCode);
      this.copied = true;
      this.copyStatus.set('Código copiado. Después de pegarlo, copia otra cosa para que no quede en el portapapeles.');
    } catch {
      this.copyStatus.set('No se pudo copiar. Cópialo a mano.');
    }
  }

  protected dismissCode(): void {
    this.issued.set(null);
    this.copyStatus.set('');
    this.focus('[data-generate]');
    if (this.copied) {
      this.copied = false;
      navigator.clipboard?.writeText('').catch(() => undefined);
    }
  }

  // --- revoke a device ---------------------------------------------------------------------

  /** The device waiting for its second click: revoking cannot be undone. */
  protected readonly confirmingDevice = signal<string | null>(null);
  /** The device whose revocation is being confirmed, if it is still in the list. */
  protected readonly confirming = computed(() => {
    const id = this.confirmingDevice();
    if (id === null || !this.devices.hasValue()) {
      return null;
    }
    return this.devices.value().find((d) => d.deviceId === id) ?? null;
  });
  protected readonly revokeBusy = signal(false);
  protected readonly revokeError = signal<string | null>(null);

  protected askRevoke(device: RecyclerDevice): void {
    this.revokeError.set(null);
    this.confirmingDevice.set(device.deviceId);
    this.focus('[data-confirm]');
  }

  protected cancelRevoke(): void {
    const id = this.confirmingDevice();
    this.confirmingDevice.set(null);
    this.focus(`[data-revoke="${id}"]`);
  }

  protected revoke(device: RecyclerDevice): void {
    const account = this.account();
    if (this.revokeBusy() || !account) {
      return;
    }
    this.revokeBusy.set(true);
    this.revokeError.set(null);
    this.api
      .revokeRecyclerDevice(account.accountId, device.deviceId)
      .pipe(
        finalize(() => this.revokeBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.confirmingDevice.set(null);
          this.status.set(`Dispositivo «${device.label}» revocado. Su sesión termina en la próxima conexión.`);
          this.devices.reload();
          this.accounts.reload();
          this.focus('#devices-title');
        },
        error: (error: unknown) => this.revokeError.set(writeErrorText(error, REVOKE_UNCERTAIN)),
      });
  }
}
