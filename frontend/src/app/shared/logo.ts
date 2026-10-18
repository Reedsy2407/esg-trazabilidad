import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

let nextId = 0;

export type LogoVariant = 'color' | 'mono' | 'symbol';

/**
 * The logo, redrawn as SVG (same geometry as public/brand/*.svg: a white «T» with its top-right corner folded,
 * a leaf, on a rounded square of the logo's petrol blue). Rules of use are in DESIGN-BRIEF.md: minimum 24 px,
 * clear space equal to the leaf's width, only these three variants, never stretched or recoloured.
 *
 * `informative` puts `aria-label="Trazabilidad ESG"` on it (login, 404); otherwise it is decorative
 * (next to the name in text, as in the top bar and the footer).
 */
@Component({
  selector: 'app-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      [attr.viewBox]="viewBox()"
      [attr.width]="size()"
      [attr.height]="size()"
      [attr.role]="informative() ? 'img' : null"
      [attr.aria-label]="informative() ? 'Trazabilidad ESG' : null"
      [attr.aria-hidden]="informative() ? null : 'true'"
      focusable="false"
    >
      <defs>
        <mask [attr.id]="maskId" maskUnits="userSpaceOnUse" x="0" y="0" width="512" height="512">
          <rect width="512" height="512" fill="#fff" />
          <path
            d="M285 396C272 345 320 300 391 294C392 345 370 395 300 400C295 400 289 398 285 396Z"
            fill="#000"
            stroke="#000"
            stroke-width="16"
            stroke-linejoin="round"
          />
        </mask>
      </defs>
      @if (variant() !== 'symbol') {
        <rect x="27" y="27" width="458" height="458" rx="68" style="fill: var(--brand)" />
      }
      <g [style.fill]="variant() === 'symbol' ? 'var(--brand)' : '#fff'" [attr.mask]="'url(#' + maskId + ')'">
        <path d="M118.3 111.9H329.8L297.6 186.4V399.7H216.2V191.1H118.3Z" />
        <g class="logo-fold">
          <path d="M331.6 111.9L392.5 178.2H331.6Z" />
          <path d="M338.4 190.4L392.5 180V189.3Z" />
        </g>
      </g>
      <g class="leaf">
        <path
          d="M285 396C272 345 320 300 391 294C392 345 370 395 300 400C295 400 289 398 285 396Z"
          [style.fill]="variant() === 'mono' ? '#fff' : 'var(--leaf)'"
        />
        <path
          d="M349.5 335.2C330 358 312 372 297.5 385.4"
          fill="none"
          [style.stroke]="variant() === 'symbol' ? '#fff' : 'var(--brand)'"
          stroke-width="5"
          stroke-linecap="round"
        />
      </g>
    </svg>
  `,
  styles: `
    :host {
      display: inline-block;
      line-height: 0;
    }
    svg {
      display: block;
    }
    /* «Preparando el sistema»: la esquina se dobla y la hoja aparece. Máximo 1,2 s; con reduced-motion no se anima. */
    @media (prefers-reduced-motion: no-preference) {
      :host(.animated) .logo-fold {
        transform-origin: 331.6px 111.9px;
        animation: fold-in 0.6s var(--ease, ease-out) 0.15s both;
      }
      :host(.animated) .leaf {
        transform-origin: 300px 399px;
        animation: leaf-in 0.6s var(--ease, ease-out) 0.55s both;
      }
      @keyframes fold-in {
        from {
          transform: scaleY(0);
        }
      }
      @keyframes leaf-in {
        from {
          transform: scale(0);
          opacity: 0;
        }
      }
    }
  `,
})
export class Logo {
  readonly variant = input<LogoVariant>('color');
  readonly size = input(32);
  readonly informative = input(false);

  protected readonly maskId = `logo-cut-${nextId++}`;
  protected readonly viewBox = computed(() => (this.variant() === 'symbol' ? '104 104 304 304' : '0 0 512 512'));
}
