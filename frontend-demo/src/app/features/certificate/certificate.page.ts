import { Component, DestroyRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { CertificateDownloadService, UnexpectedDownloadError } from '../../core/api/certificate-download.service';
import { ApiError } from '../../core/http/api-error';
import { DistrictContext } from '../../shared/district-context';
import { DownloadKind } from '../../shared/download-filename';
import { loadErrorMessage, writeErrorText } from '../../shared/error-message';
import { formatInstantDate, formatKg, formatLocalDate, formatPercent } from '../../shared/format';
import { COMPLIANCE_LABEL, hierarchyNote } from '../sigersol/origin-label';
import { ReasonConfirm } from '../../shared/reason-confirm';

/** Opened right after issuing it (router state from CertificateIssuePage)? Read once, then dropped. */
function justIssued(): boolean {
  const state: unknown = history.state;
  if (typeof state !== 'object' || state === null || (state as Record<string, unknown>)['emitido'] !== true) {
    return false;
  }
  history.replaceState({ ...(state as Record<string, unknown>), emitido: false }, '');
  return true;
}

/**
 * Certificate detail, the brief's signature screen: a printed-document look
 * built only from real fields (header with company and RUC, the two figures,
 * period, issue date, the full certificate id). No status stamp and no
 * weighings table: the certificate has no status, and the weighings travel in
 * the CSV. Nothing here suggests third-party verification: there is no public
 * verification endpoint. A certificate is never deleted: an ISSUED one can be annulled, with a
 * required reason (who and when come from the session on the server), and then shows ANULADO with
 * its frozen numbers untouched.
 */
export const ANNUL_UNCERTAIN =
  'No pudimos confirmar si el certificado se anuló. Vuelve a cargar la página para ver su estado actual antes de reintentar.';

@Component({
  selector: 'app-certificate-page',
  imports: [RouterLink, ReasonConfirm, DistrictContext],
  templateUrl: './certificate.page.html',
  styleUrl: './certificate.page.css',
})
export class CertificatePage {
  private readonly api = inject(BackendApi);
  private readonly downloads = inject(CertificateDownloadService);
  private readonly destroyRef = inject(DestroyRef);

  readonly companyId = input.required<string>();
  readonly certificateId = input.required<string>();
  /**
   * "Certificado emitido.", once, for the certificate just issued. The page never links to another
   * certificate, so the component isn't reused for a different one while this is true.
   */
  protected readonly issued = justIssued();

  protected readonly certificate = rxResource({
    params: () => ({ companyId: this.companyId(), certificateId: this.certificateId() }),
    stream: ({ params: p }) => this.api.certificate(p.companyId, p.certificateId),
  });

  protected readonly complianceLabel = COMPLIANCE_LABEL;

  protected readonly view = computed(() => {
    if (!this.certificate.hasValue()) {
      return null;
    }
    const c = this.certificate.value();
    return {
      id: c.id,
      company: c.companyName,
      ruc: c.companyRuc,
      kilos: formatKg(c.kilosTrazados),
      compliance: formatPercent(c.hierarchyCompliancePercent),
      complianceNote: hierarchyNote(c),
      period: `${formatLocalDate(c.periodStart)} – ${formatLocalDate(c.periodEnd)}`,
      issued: formatInstantDate(c.issuedAt),
      districts: c.districtContext ?? null,
      annulled:
        c.status === 'ANNULLED'
          ? {
              at: c.annulledAt === null ? null : formatInstantDate(c.annulledAt),
              by: c.annulledByEmail ?? c.annulledById,
              reason: c.annulmentReason,
            }
          : null,
    };
  });

  // ---- Annulment: one panel, a reason and an explicit confirmation.
  protected readonly annulling = signal(false);
  protected readonly annulBusy = signal(false);
  protected readonly annulError = signal<string | null>(null);
  /** Polite announcement once it is done. */
  protected readonly annulStatus = signal('');
  private readonly reasonPanel = viewChild(ReasonConfirm);

  protected openAnnul(): void {
    this.annulling.set(true);
    this.annulError.set(null);
    this.annulStatus.set('');
    setTimeout(() => this.reasonPanel()?.focus());
  }

  protected closeAnnul(): void {
    this.annulling.set(false);
    this.annulError.set(null);
  }

  protected annul(reason: string): void {
    if (this.annulBusy()) {
      return;
    }
    this.annulError.set(null);
    this.annulBusy.set(true);
    this.api
      .annulCertificate(this.companyId(), this.certificateId(), reason)
      .pipe(
        finalize(() => this.annulBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updated) => {
          this.certificate.set(updated);
          this.annulling.set(false);
          this.annulStatus.set('Certificado anulado.');
        },
        error: (error: unknown) => {
          this.annulError.set(
            writeErrorText(error, ANNUL_UNCERTAIN, {
              'RPT-019': 'Este certificado ya estaba anulado. Vuelve a cargar la página para ver quién lo anuló y por qué.',
              'RPT-003': 'Este certificado no existe o no pertenece a esta empresa.',
            }),
          );
          this.reasonPanel()?.focusError();
        },
      });
  }

  /** Which download is in flight; one at a time. */
  protected readonly downloading = signal<DownloadKind | null>(null);
  protected readonly downloadError = signal<string | null>(null);
  /** Polite announcement for the copy button, set once per click. */
  protected readonly copyStatus = signal('');

  protected isNotFound(error: unknown): boolean {
    return error instanceof ApiError && error.status === 404;
  }

  protected errorText(error: unknown): string {
    return loadErrorMessage(error, 'el certificado');
  }

  protected download(kind: DownloadKind): void {
    if (this.downloading() !== null) {
      return;
    }
    this.downloadError.set(null);
    this.downloading.set(kind);
    this.downloads
      .download(this.companyId(), this.certificateId(), kind)
      .pipe(
        finalize(() => this.downloading.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        error: (error: unknown) => this.downloadError.set(downloadErrorText(error, kind)),
      });
  }

  protected async copy(code: string): Promise<void> {
    // Emptied first and refilled on a later task, so a second click is a new
    // change for screen readers instead of being batched into the same text.
    this.copyStatus.set('');
    let message: string;
    try {
      await navigator.clipboard.writeText(code);
      message = 'Código copiado.';
    } catch {
      message = 'No se pudo copiar. Selecciona el código y cópialo a mano.';
    }
    setTimeout(() => this.copyStatus.set(message), 100);
  }
}

/** 401 never reaches the page as text: errorInterceptor already ended the session and went to the login. */
export function downloadErrorText(error: unknown, kind: DownloadKind): string {
  const file = kind === 'pdf' ? 'el PDF' : 'el CSV';
  if (error instanceof UnexpectedDownloadError) {
    return `El servicio de reportes no devolvió un ${kind.toUpperCase()} válido. Inténtalo de nuevo.`;
  }
  if (!(error instanceof ApiError)) {
    return `No se pudo descargar ${file}. Inténtalo de nuevo.`;
  }
  switch (error.status) {
    case 0:
      return `No se pudo conectar con el servicio de reportes para descargar ${file}. Comprueba tu conexión.`;
    case 404:
      return `No se pudo descargar ${file}: el certificado ya no existe o no pertenece a esta empresa.`;
    case 429:
      return error.retryAfterSeconds === null
        ? `Demasiadas solicitudes. Espera un momento y vuelve a descargar ${file}.`
        : `Demasiadas solicitudes. Vuelve a descargar ${file} en ${error.retryAfterSeconds} s.`;
    default:
      return `No se pudo descargar ${file} (HTTP ${error.status}). Inténtalo de nuevo.`;
  }
}
