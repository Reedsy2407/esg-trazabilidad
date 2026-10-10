const KEY = 'esg.recojo.asociacion';

/**
 * The association last used on the collection form, remembered on this device so the next round
 * starts with it chosen. Only an id, never personal data; a convenience, so any storage failure
 * just means nothing is remembered.
 */
export function rememberedAssociation(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function rememberAssociation(id: string): void {
  try {
    localStorage.setItem(KEY, id);
  } catch {
    // Blocked storage: nothing is remembered.
  }
}
