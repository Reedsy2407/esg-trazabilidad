export type DownloadKind = 'pdf' | 'csv';

/** Longest base name kept (without the extension). */
export const MAX_BASE_LENGTH = 100;

const SAFE_CHAR = /[A-Za-z0-9._-]/;
/** Windows device names: valid characters, but unusable as a file name there. */
const RESERVED = /^(con|prn|aux|nul|com[0-9]|lpt[0-9])$/i;

/**
 * The file name to save a download under, from the server's
 * Content-Disposition, made safe whatever the header says:
 * - only [A-Za-z0-9._-] survive (everything else becomes "-"), so no path
 *   separators, quotes, control or null characters, spaces or Unicode tricks;
 * - only the last path segment is kept, so "../" and "\" can't escape;
 * - no leading dots or dashes (no hidden files, no ".." left over);
 * - the base is capped at MAX_BASE_LENGTH characters;
 * - the extension is always the expected one (.pdf or .csv), never the
 *   server's;
 * - a missing, unparseable, empty-after-cleaning or Windows-reserved name
 *   (CON, NUL, COM1...) falls back to "certificado-{id}".
 */
export function safeDownloadName(contentDisposition: string | null, fallbackId: string, kind: DownloadKind): string {
  const fallback = cleanBase(`certificado-${fallbackId}`) || 'certificado';
  const raw = contentDisposition === null ? null : filenameFrom(contentDisposition);
  const base = raw === null ? '' : cleanBase(stripExtension(lastSegment(raw)));
  // Windows treats CON.x like CON: the device check looks at the part before the first dot
  return `${base && !RESERVED.test(base.split('.')[0]) ? base : fallback}.${kind}`;
}

/** filename* (RFC 5987/6266, UTF-8 percent-encoded) wins over filename. Null if neither parses. */
export function filenameFrom(header: string): string | null {
  const extended = /filename\*\s*=\s*([^;]+)/i.exec(header);
  if (extended !== null) {
    const value = extended[1].trim();
    const match = /^([A-Za-z0-9!#$&+^`{}~-]+)'[^']*'(.*)$/.exec(value);
    if (match !== null) {
      try {
        return decodeURIComponent(match[2]);
      } catch {
        // Malformed percent-encoding: fall through to the plain filename.
      }
    }
  }
  const quoted = /filename\s*=\s*"((?:[^"\\]|\\.)*)"/i.exec(header);
  if (quoted !== null) {
    // Only \" is unescaped: any other backslash is kept and treated as a path
    // separator below, which is how Windows would read it.
    return quoted[1].replace(/\\"/g, '"');
  }
  const token = /filename\s*=\s*([^;\s]+)/i.exec(header);
  return token === null ? null : token[1];
}

function lastSegment(name: string): string {
  const parts = name.split(/[/\\]/);
  return parts[parts.length - 1];
}

function stripExtension(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot > 0 ? name.slice(0, dot) : name;
}

function cleanBase(name: string): string {
  const replaced = Array.from(name, (ch) => (SAFE_CHAR.test(ch) ? ch : '-')).join('');
  return replaced
    .replace(/-{2,}/g, '-')
    .replace(/\.{2,}/g, '.')
    .replace(/^[.-]+/, '')
    .slice(0, MAX_BASE_LENGTH)
    .replace(/[.-]+$/, '');
}
