import { Component, DestroyRef, inject, input, signal } from '@angular/core';

/**
 * A password generated in this browser, shown on request and copyable: hidden until "Mostrar", and
 * the clipboard this component filled is overwritten when it goes away. The password is only ever a
 * text node while shown, never an attribute, a URL, storage or a log line. The page that owns the
 * password decides when to drop it (it stops passing it in).
 */
@Component({
  selector: 'app-secret-box',
  template: `
    <div class="secret">
      @if (shown()) {
        <code class="mono">{{ password() }}</code>
      } @else {
        <code class="mono" aria-label="Contraseña oculta">•••• •••• •••• ••••</code>
      }
      <div class="actions">
        <button type="button" class="btn" [attr.aria-pressed]="shown()" (click)="shown.set(!shown())">Mostrar</button>
        <button type="button" class="btn" (click)="copy()">Copiar</button>
      </div>
    </div>
    <p class="visually-hidden" role="status">{{ status() }}</p>
  `,
  styles: `
    .secret {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3) var(--space-5);
      margin: 0 0 var(--space-5);
      padding: var(--space-4) 0;
      border-top: 2px solid var(--ink);
      border-bottom: 2px solid var(--ink);
    }
    code {
      font-size: 22px;
      font-weight: 600;
      letter-spacing: 0.04em;
      overflow-wrap: anywhere;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
    }
  `,
})
export class SecretBox {
  readonly password = input.required<string>();

  protected readonly shown = signal(false);
  protected readonly status = signal('');
  private copied = false;

  constructor() {
    // Leaving the page overwrites the clipboard if this box filled it. Best effort: the browser may
    // refuse without focus; the status text already asked to overwrite it.
    inject(DestroyRef).onDestroy(() => {
      if (this.copied) {
        navigator.clipboard?.writeText('').catch(() => undefined);
      }
    });
  }

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.password());
      this.copied = true;
      this.status.set('Contraseña copiada. Después de pegarla, copia otra cosa para que no quede en el portapapeles.');
    } catch {
      this.status.set('No se pudo copiar. Muéstrala y cópiala a mano.');
    }
  }
}
