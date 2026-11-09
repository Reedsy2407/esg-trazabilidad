import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/http/interceptors';
import { RecyclerAccessPage } from './recycler-access.page';

const { auth, recycler } = environment.services;
const RECYCLER_URL = `${recycler}/associations/a-1/recyclers/r-1`;
const ACCOUNTS_URL = `${auth}/auth/recycler-accounts`;

const RECYCLER = { id: 'r-1', fullName: 'Rosa Quispe Huamán', dni: '00000000', phone: null, associationId: 'a-1', status: 'ACTIVE' };
// A made-up value that cannot be a real activation code.
const SHOWN_ONCE = 'PRUEBA0001';

describe('RecyclerAccessPage', () => {
  let backend: HttpTestingController;
  let page: HTMLElement;
  let fixture: ComponentFixture<RecyclerAccessPage>;

  const settle = async () => {
    await Promise.resolve();
    TestBed.tick();
  };
  const flushUrl = async (url: string, body: object) => {
    backend.expectOne((r) => r.url === url).flush(body);
    await settle();
  };
  const buttonByText = (text: string) =>
    [...page.querySelectorAll('button')].find((b) => b.textContent?.trim() === text) as HTMLButtonElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [RecyclerAccessPage],
      providers: [provideRouter([]), provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RecyclerAccessPage);
    fixture.componentRef.setInput('associationId', 'a-1');
    fixture.componentRef.setInput('recyclerId', 'r-1');
    page = fixture.nativeElement as HTMLElement;
    TestBed.tick();
    await flushUrl(RECYCLER_URL, RECYCLER);
  });

  it('without an account, creating one shows the code once with its warning, and "Ya lo entregué" drops it', async () => {
    await flushUrl(ACCOUNTS_URL, []);
    expect(page.querySelector('h1')?.textContent).toBe('Rosa Quispe Huamán');
    expect(page.textContent).toContain('Sin cuenta');
    expect(page.textContent).not.toContain('dni');

    buttonByText('Crear cuenta y generar código').click();
    TestBed.tick();
    const post = backend.expectOne(ACCOUNTS_URL);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ recyclerId: 'r-1' });
    post.flush({ accountId: 'acc-1', activationCode: SHOWN_ONCE, expiresAt: '2026-11-12T10:00:00Z' });
    await settle();
    // The refreshed list the page asks for next never carries the code.
    await flushUrl(ACCOUNTS_URL, [
      { accountId: 'acc-1', recyclerId: 'r-1', status: 'PENDING_ACTIVATION', activatedAt: null, devices: 0 },
    ]);

    expect(page.querySelector('.handover code')?.textContent).toContain(SHOWN_ONCE);
    expect(page.querySelector('.handover .warning')?.textContent).toContain('una sola vez');
    expect(buttonByText('Copiar')).toBeTruthy();
    expect(page.querySelector('[role="group"] #code-title')?.getAttribute('tabindex')).toBe('-1');
    await vi.waitFor(() => expect(document.activeElement?.id).toBe('code-title'));

    buttonByText('Ya lo entregué').click();
    TestBed.tick();
    expect(page.textContent).not.toContain(SHOWN_ONCE);
    expect(page.querySelector('.handover')).toBeNull();
    // Focus goes to a stable place, not to <body>.
    await vi.waitFor(() => expect(document.activeElement?.hasAttribute('data-generate')).toBe(true));
  });

  describe('clipboard', () => {
    let writeText: ReturnType<typeof vi.fn>;

    const issueCode = async () => {
      await flushUrl(ACCOUNTS_URL, []);
      buttonByText('Crear cuenta y generar código').click();
      TestBed.tick();
      backend
        .expectOne(ACCOUNTS_URL)
        .flush({ accountId: 'acc-1', activationCode: SHOWN_ONCE, expiresAt: '2026-11-12T10:00:00Z' });
      await settle();
      await flushUrl(ACCOUNTS_URL, [
        { accountId: 'acc-1', recyclerId: 'r-1', status: 'PENDING_ACTIVATION', activatedAt: null, devices: 0 },
      ]);
    };

    beforeEach(() => {
      writeText = vi.fn().mockResolvedValue(undefined);
      Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    });

    it('Copiar puts the code on the clipboard and dismissing it overwrites the clipboard', async () => {
      await issueCode();
      buttonByText('Copiar').click();
      await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith(SHOWN_ONCE));
      buttonByText('Ya lo entregué').click();
      TestBed.tick();
      expect(writeText).toHaveBeenLastCalledWith('');
    });

    it('leaving the page after copying overwrites the clipboard', async () => {
      await issueCode();
      buttonByText('Copiar').click();
      await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith(SHOWN_ONCE));
      fixture.destroy();
      expect(writeText).toHaveBeenLastCalledWith('');
    });

    it('leaving the page without copying never touches the clipboard', async () => {
      await issueCode();
      fixture.destroy();
      expect(writeText).not.toHaveBeenCalled();
    });
  });

  it('an unsynced recycler (AUTH-020) gets a retry message and no code', async () => {
    await flushUrl(ACCOUNTS_URL, []);
    buttonByText('Crear cuenta y generar código').click();
    TestBed.tick();
    backend
      .expectOne(ACCOUNTS_URL)
      .flush({ code: 'AUTH-020', detail: 'x', status: 409, title: 'x' }, { status: 409, statusText: 'Conflict' });
    await settle();
    expect(page.querySelector('.problem')?.textContent).toContain('aún no está sincronizado');
    expect(page.querySelector('.handover')).toBeNull();
  });

  describe('with an active account and devices', () => {
    const ACCOUNT = { accountId: 'acc-1', recyclerId: 'r-1', status: 'ACTIVE', activatedAt: '2026-11-01T10:00:00Z', devices: 2 };
    const DEVICES = `${ACCOUNTS_URL}/acc-1/devices`;

    beforeEach(async () => {
      await flushUrl(ACCOUNTS_URL, [{ accountId: 'zzz', recyclerId: 'otro', status: 'ACTIVE', activatedAt: null, devices: 0 }, ACCOUNT]);
      await flushUrl(DEVICES, [
        { deviceId: 'd-1', label: 'Honor X7b', createdAt: '2026-11-01T10:00:00Z', lastSeenAt: '2026-11-05T10:00:00Z', revokedAt: null },
        { deviceId: 'd-2', label: 'Teléfono viejo', createdAt: '2026-10-01T10:00:00Z', lastSeenAt: null, revokedAt: '2026-10-20T10:00:00Z' },
      ]);
    });

    it('shows this recycler\'s account, lists devices and offers revoke only for the one in use', () => {
      expect(page.textContent).toContain('Activa');
      const entries = [...page.querySelectorAll('.devices .entry')];
      expect(entries.length).toBe(2);
      expect(entries[0].textContent).toContain('En uso');
      expect(entries[0].querySelector('button')?.getAttribute('aria-label')).toBe('Revocar Honor X7b');
      expect(entries[1].textContent).toContain('Revocado el');
      expect(entries[1].querySelector('button')).toBeNull();
    });

    it('revoking needs a second click, then DELETEs and reloads', async () => {
      (page.querySelector('button[aria-label="Revocar Honor X7b"]') as HTMLElement).click();
      TestBed.tick();
      backend.expectNone((r) => r.method === 'DELETE');
      expect(page.querySelector('.confirm')?.textContent).toContain('No se puede');
      // The pressed button is gone: focus moves to the confirmation, not to <body>.
      await vi.waitFor(() => expect(document.activeElement?.hasAttribute('data-confirm')).toBe(true));

      buttonByText('Sí, revocar').click();
      TestBed.tick();
      const del = backend.expectOne(`${DEVICES}/d-1`);
      expect(del.request.method).toBe('DELETE');
      del.flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      expect(page.querySelector('[role="status"]')?.textContent).toContain('Honor X7b');
      backend.expectOne(DEVICES).flush([]);
      backend.expectOne((r) => r.url === ACCOUNTS_URL).flush([ACCOUNT]);
      await vi.waitFor(() => expect(document.activeElement?.id).toBe('devices-title'));
    });

    it('cancelling the confirmation returns focus to that device\'s Revocar button', async () => {
      (page.querySelector('button[aria-label="Revocar Honor X7b"]') as HTMLElement).click();
      TestBed.tick();
      buttonByText('No, volver').click();
      TestBed.tick();
      backend.expectNone((r) => r.method === 'DELETE');
      await vi.waitFor(() => expect(document.activeElement?.getAttribute('data-revoke')).toBe('d-1'));
    });

    it('a new code for an account with a device in use explains the conflict', async () => {
      buttonByText('Generar código nuevo').click();
      TestBed.tick();
      backend
        .expectOne(`${ACCOUNTS_URL}/acc-1/activation-code`)
        .flush({ code: 'DATA_CONFLICT', detail: 'x', status: 409, title: 'x' }, { status: 409, statusText: 'Conflict' });
      await settle();
      expect(page.querySelector('.problem')?.textContent).toContain('Revoca sus dispositivos');
    });
  });
});
