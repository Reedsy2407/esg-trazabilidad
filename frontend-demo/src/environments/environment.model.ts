export type ServiceKey = 'auth' | 'recycler' | 'collection' | 'reporting';

export interface Environment {
  /** Base URL per backend service. Public, not secrets. */
  readonly services: Readonly<Record<ServiceKey, string>>;
}
