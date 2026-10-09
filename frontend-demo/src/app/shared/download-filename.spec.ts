import { MAX_BASE_LENGTH, filenameFrom, safeDownloadName } from './download-filename';

const ID = '0192f3a8-c000-7000-8000-000000000013';
const name = (header: string | null, kind: 'pdf' | 'csv' = 'pdf') => safeDownloadName(header, ID, kind);

describe('safeDownloadName', () => {
  it('keeps the backend\'s own name (attachment; filename="certificado-{id}.pdf")', () => {
    expect(name(`attachment; filename="certificado-${ID}.pdf"`)).toBe(`certificado-${ID}.pdf`);
    expect(name(`attachment; filename="certificado-${ID}.csv"`, 'csv')).toBe(`certificado-${ID}.csv`);
  });

  it('falls back to certificado-{id} when the header is missing, empty or has no filename', () => {
    expect(name(null)).toBe(`certificado-${ID}.pdf`);
    expect(name('')).toBe(`certificado-${ID}.pdf`);
    expect(name('attachment')).toBe(`certificado-${ID}.pdf`);
    expect(name('attachment; filename=""')).toBe(`certificado-${ID}.pdf`);
  });

  it('never lets a path escape: ../ and backslashes are cut down to the last segment', () => {
    expect(name('attachment; filename="../../etc/passwd"')).toBe('passwd.pdf');
    expect(name('attachment; filename="..\\..\\Windows\\system32\\evil.exe"')).toBe('evil.pdf');
    expect(name('attachment; filename=C:\\Users\\x\\Desktop\\a.pdf')).toBe('a.pdf');
    expect(name('attachment; filename="/var/tmp/x.pdf"')).toBe('x.pdf');
    expect(name('attachment; filename=".."')).toBe(`certificado-${ID}.pdf`);
    expect(name('attachment; filename="../"')).toBe(`certificado-${ID}.pdf`);
  });

  it('forces the expected extension, whatever the server says', () => {
    expect(name('attachment; filename="report.exe"')).toBe('report.pdf');
    expect(name('attachment; filename="report.pdf.exe"')).toBe('report.pdf.pdf');
    expect(name('attachment; filename="report.html"', 'csv')).toBe('report.csv');
    expect(name('attachment; filename="no-extension"')).toBe('no-extension.pdf');
  });

  it('turns quotes, spaces, control and null characters into plain dashes', () => {
    expect(name('attachment; filename="a\\"b\'c d.pdf"')).toBe('a-b-c-d.pdf');
    expect(name('attachment; filename="nul\u0000byte.pdf"')).toBe('nul-byte.pdf');
    expect(name('attachment; filename="tab\there\r\nnew.pdf"')).toBe('tab-here-new.pdf');
    expect(name('attachment; filename="<script>.pdf"')).toBe('script.pdf');
  });

  it("decodes filename*=UTF-8'' and still keeps only safe characters", () => {
    expect(name(`attachment; filename*=UTF-8''certificado-${ID}.pdf`)).toBe(`certificado-${ID}.pdf`);
    expect(name("attachment; filename*=UTF-8''Certificaci%C3%B3n%20octubre.pdf")).toBe('Certificaci-n-octubre.pdf');
    expect(name("attachment; filename*=UTF-8''..%2F..%2Fetc%2Fpasswd")).toBe('passwd.pdf');
    // filename* wins over filename when both are present
    expect(name('attachment; filename="plain.pdf"; filename*=UTF-8\'\'fancy.pdf')).toBe('fancy.pdf');
    // malformed percent-encoding falls back to the plain filename
    expect(name("attachment; filename=\"plain.pdf\"; filename*=UTF-8''bad%E0%A4%A")).toBe('plain.pdf');
  });

  it('removes hidden-file dots and caps very long names', () => {
    expect(name('attachment; filename=".htaccess"')).toBe('htaccess.pdf');
    expect(name('attachment; filename="...secret.pdf"')).toBe('secret.pdf');
    const long = name(`attachment; filename="${'a'.repeat(5000)}.pdf"`);
    expect(long).toBe(`${'a'.repeat(MAX_BASE_LENGTH)}.pdf`);
  });

  it('refuses Windows device names', () => {
    expect(name('attachment; filename="CON.pdf"')).toBe(`certificado-${ID}.pdf`);
    expect(name('attachment; filename="nul"')).toBe(`certificado-${ID}.pdf`);
    expect(name('attachment; filename="com1.csv"', 'csv')).toBe(`certificado-${ID}.csv`);
    // Windows reads CON.x and lpt1.tar as the device too
    expect(name('attachment; filename="CON.x.pdf"')).toBe(`certificado-${ID}.pdf`);
    expect(name('attachment; filename="lpt1.tar.pdf"')).toBe(`certificado-${ID}.pdf`);
    // ...but a name that only starts with those letters is fine
    expect(name('attachment; filename="console.pdf"')).toBe('console.pdf');
  });

  it('a hostile fallback id is cleaned too', () => {
    const fallback = safeDownloadName(null, '../x"y\\z', 'csv');
    expect(fallback).toMatch(/^certificado[A-Za-z0-9._-]*\.csv$/);
    expect(fallback).not.toMatch(/[\\/"]/);
  });
});

describe('filenameFrom', () => {
  it('reads quoted, token and extended forms', () => {
    expect(filenameFrom('attachment; filename="a.pdf"')).toBe('a.pdf');
    expect(filenameFrom('attachment; filename=a.pdf')).toBe('a.pdf');
    expect(filenameFrom("attachment; filename*=UTF-8''a%20b.pdf")).toBe('a b.pdf');
    expect(filenameFrom('inline')).toBeNull();
  });
});
