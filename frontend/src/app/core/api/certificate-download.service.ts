import { HttpClient } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { DownloadKind, safeDownloadName } from '../../shared/download-filename';

const { reporting } = environment.services;

const EXPECTED_TYPE: Record<DownloadKind, string> = { pdf: 'application/pdf', csv: 'text/csv' };

/**
 * How long the object URL outlives the click. Revoking it at once can cancel
 * the download in Firefox and Safari; FileSaver.js waits 40 s for the same reason.
 */
export const REVOKE_AFTER_MS = 40_000;

/** A 200 answer that is not the file asked for: empty, or another media type. */
export class UnexpectedDownloadError extends Error {
  constructor(readonly kind: DownloadKind) {
    super(`The ${kind} download answered with an empty body or another media type`);
    this.name = 'UnexpectedDownloadError';
  }
}

/**
 * Downloads a certificate's PDF or CSV. The endpoints need the bearer token,
 * so this can't be a plain <a href>: the file comes through HttpClient (the
 * same interceptors as every other call: the token goes only to our service
 * bases, a 401 ends the session, errors become ApiError), then is saved from
 * a Blob under a sanitized name. A 200 that is empty or of the wrong type
 * (say, an HTML fallback page from a proxy) is refused instead of saved.
 */
@Injectable({ providedIn: 'root' })
export class CertificateDownloadService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);

  /** Emits the saved file name once the browser has been handed the file. */
  download(companyId: string, certificateId: string, kind: DownloadKind): Observable<string> {
    const url =
      `${reporting}/tracked-companies/${encodeURIComponent(companyId)}` +
      `/certificates/${encodeURIComponent(certificateId)}/${kind}`;
    return this.http.get(url, { observe: 'response', responseType: 'blob' }).pipe(
      map((response) => {
        const blob = response.body;
        const type = (response.headers.get('Content-Type') ?? '').split(';')[0].trim().toLowerCase();
        if (blob === null || blob.size === 0 || type !== EXPECTED_TYPE[kind]) {
          throw new UnexpectedDownloadError(kind);
        }
        const name = safeDownloadName(response.headers.get('Content-Disposition'), certificateId, kind);
        this.save(blob, name);
        return name;
      }),
    );
  }

  private save(blob: Blob, name: string): void {
    const objectUrl = URL.createObjectURL(blob);
    try {
      const link = this.document.createElement('a');
      link.href = objectUrl;
      link.download = name;
      link.rel = 'noopener';
      link.style.display = 'none';
      this.document.body.appendChild(link);
      link.click();
      link.remove();
    } finally {
      setTimeout(() => URL.revokeObjectURL(objectUrl), REVOKE_AFTER_MS);
    }
  }
}
