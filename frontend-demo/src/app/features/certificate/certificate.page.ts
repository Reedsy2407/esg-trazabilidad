import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { BackendApi } from '../../core/api/backend.api';
import { CertificateDownloadService, UnexpectedDownloadError } from '../../core/api/certificate-download.service';
import { ApiError } from '../../core/http/api-error';
import { DownloadKind } from '../../shared/download-filename';
import { loadErrorMessage } from '../../shared/error-message';
import { formatInstantDate, formatKg, formatLocalDate, formatPercent } from '../../shared/format';

/**
 * Certificate detail, the brief's signature screen: a printed-document look
 * built only from real fields (header with company and RUC, the two figures,
 * period, issue date, the full certificate id). No status stamp and no
 * weighings table: the certificate has no status, and the weighings travel in
 * the CSV. Nothing here suggests third-party verification: there is no public
 * verification endpoint.
 */
@Component({
  selector: 'app-certificate-page',
  imports: [RouterLink],
  templateUrl: './certificate.page.html',
  styleUrl: './certificate.page.css',
})
export class CertificatePage {
  private readonly api = inject(BackendApi);
  private readonly downloads = inject(CertificateDownloadService);
  private readonly destroyRef = inject(DestroyRef);

  readonly companyId = input.required<string>();
  readonly certificateId = input.required<string>();

  protected readonly certificate = rxResource({
    params: () => ({ companyId: this.companyId(), certificateId: this.certificateId() }),
    stream: ({ params: p }) => this.api.certificate(p.companyId, p.certificateId),
  });

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
      period: `${formatLocalDate(c.periodStart)} – ${formatLocalDate(c.periodEnd)}`,
      issued: formatInstantDate(c.issuedAt),
    };
  });

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
