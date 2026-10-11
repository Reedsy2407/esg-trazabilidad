import { Component, ElementRef, computed, input, output, signal, viewChild } from '@angular/core';

export const REASON_MAX = 500;

/**
 * A required reason plus an explicit confirmation (a checkbox and a button that names the
 * action), the same shape as issuing a certificate. Used by the neighbour's deactivate,
 * reactivate and delete. Who does it is never asked: the backend takes it from the session.
 */
@Component({
  selector: 'app-reason-confirm',
  template: `
    <form class="reason" novalidate (submit)="submit($event)" [attr.aria-labelledby]="idPrefix() + '-title'">
      <h3 [id]="idPrefix() + '-title'">{{ title() }}</h3>
      <p class="explain" [id]="idPrefix() + '-explain'">{{ explanation() }}</p>
      <label [for]="idPrefix() + '-reason'">Motivo</label>
      <textarea
        #reasonField
        class="control"
        rows="3"
        [id]="idPrefix() + '-reason'"
        [attr.maxlength]="max"
        [value]="reason()"
        [disabled]="busy()"
        [attr.aria-invalid]="reasonMissing()"
        [attr.aria-describedby]="idPrefix() + '-explain ' + idPrefix() + '-count' + (reasonMissing() ? ' ' + idPrefix() + '-error' : '')"
        (input)="reason.set($any($event.target).value)"
      ></textarea>
      <p class="counter" [id]="idPrefix() + '-count'">{{ reason().length }} de {{ max }} caracteres</p>
      @if (reasonMissing()) {
        <p class="field-error" [id]="idPrefix() + '-error'">Escribe el motivo.</p>
      }
      <label class="check">
        <input type="checkbox" [checked]="confirmed()" [disabled]="busy()" (change)="confirmed.set($any($event.target).checked)" />
        {{ confirmLabel() }}
      </label>
      <div class="buttons">
        <button type="submit" class="btn btn-primary" [disabled]="!confirmed() || busy()">{{ busy() ? busyText() : actionText() }}</button>
        <button type="button" class="btn" [disabled]="busy()" (click)="cancelled.emit()">No, volver</button>
      </div>
      @if (error(); as message) {
        <p class="problem" role="alert" tabindex="-1" #problem>{{ message }}</p>
      }
    </form>
  `,
  styles: `
    .reason {
      padding: var(--space-4) var(--space-5);
      border: 2px solid var(--ink);
      background: var(--paper);
    }
    h3 {
      margin: 0 0 var(--space-2);
      font-size: 15px;
    }
    .explain {
      margin: 0 0 var(--space-4);
    }
    label {
      display: block;
      margin-bottom: var(--space-2);
      font-size: 14px;
      font-weight: 600;
    }
    textarea.control {
      height: auto;
      padding: var(--space-2) var(--space-3);
      resize: vertical;
    }
    .counter {
      margin: var(--space-1) 0 0;
      color: var(--ink-soft);
      font-size: 13px;
    }
    .check {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      min-height: 44px;
      margin: var(--space-3) 0 0;
      cursor: pointer;
    }
    .check input {
      width: 20px;
      height: 20px;
      margin: 0;
      accent-color: var(--ink);
    }
    .buttons {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      margin-top: var(--space-3);
    }
    .problem {
      margin: var(--space-3) 0 0;
    }
  `,
})
export class ReasonConfirm {
  readonly idPrefix = input.required<string>();
  readonly title = input.required<string>();
  readonly explanation = input.required<string>();
  readonly confirmLabel = input.required<string>();
  readonly actionText = input.required<string>();
  readonly busyText = input.required<string>();
  readonly busy = input(false);
  readonly error = input<string | null>(null);

  /** The reason, trimmed. */
  readonly confirm = output<string>();
  readonly cancelled = output<void>();

  protected readonly max = REASON_MAX;
  protected readonly reason = signal('');
  protected readonly confirmed = signal(false);
  private readonly tried = signal(false);
  protected readonly reasonMissing = computed(() => this.tried() && this.reason().trim() === '');

  private readonly reasonField = viewChild.required<ElementRef<HTMLTextAreaElement>>('reasonField');
  private readonly problem = viewChild<ElementRef<HTMLElement>>('problem');

  focus(): void {
    this.reasonField().nativeElement.focus();
  }

  /** Moves focus to the error, once the page has set it. */
  focusError(): void {
    setTimeout(() => this.problem()?.nativeElement.focus());
  }

  protected submit(event: Event): void {
    event.preventDefault();
    this.tried.set(true);
    if (this.busy() || !this.confirmed()) {
      return;
    }
    const reason = this.reason().trim();
    if (reason === '') {
      this.focus();
      return;
    }
    this.confirm.emit(reason);
  }
}
