// The demo data approved by Angel (tasks/todo.md, "Handoff v2"). Fictitious on purpose: every
// name carries "(demo)" or "(ficticia)", RUCs start with 20999. No real person or company.

export const ASSOCIATION = {
  name: 'Asociación de Recicladores Demo Norte (ficticia)',
  ruc: '20999000011',
};

/** Valid on the run date, so the association shows as "vigente". */
export const CERTIFICATION = {
  certificationType: 'Registro municipal (demo)',
  issuedDate: '2026-01-15',
  expirationDate: '2027-01-15',
};

/** `schedule`: the neighbour's collection schedule; every collection below falls on that weekday. */
export const NEIGHBORS = [
  {
    fullName: 'Carmen Flores (demo)',
    address: 'Jr. Demo 101',
    district: 'Comas',
    schedule: { dayOfWeek: 'MONDAY', time: '08:00' },
    collections: [
      { collectionDate: '2026-09-07', weightKg: 12.5 },
      { collectionDate: '2026-09-14', weightKg: 10.75 },
      { collectionDate: '2026-09-21', weightKg: 11.2 },
    ],
  },
  {
    fullName: 'Jorge Salas (demo)',
    address: 'Av. Demo 202',
    district: 'Comas',
    schedule: { dayOfWeek: 'WEDNESDAY', time: '09:30' },
    collections: [
      { collectionDate: '2026-09-09', weightKg: 8.4 },
      { collectionDate: '2026-09-23', weightKg: 9.1 },
    ],
  },
  {
    fullName: 'Lucía Paz (demo)',
    address: 'Calle Demo 303',
    district: 'Independencia',
    schedule: { dayOfWeek: 'FRIDAY', time: '07:00' },
    collections: [{ collectionDate: '2026-09-18', weightKg: 15 }],
  },
];

export const COMPANY = {
  name: 'Empresa Demo Envases S.A.C. (ficticia)',
  ruc: '20999000029',
};

export const SIGERSOL = {
  periodStart: '2026-09-01',
  periodEnd: '2026-09-30',
  hierarchyCompliancePercent: 85.5,
  officialKilosDeclared: 70,
  sourceNote: 'Dato de demostración, no oficial',
};

/** What the certificate must come out with; the script refuses to issue it otherwise. */
export const CERTIFICATE = {
  periodStart: '2026-09-01',
  periodEnd: '2026-09-30',
  expectedKilos: 66.95,
  expectedCompliancePercent: 85.5,
};

/**
 * --extra-association (off by default): a second association whose certification expires in
 * EXTRA_EXPIRES_IN_DAYS days from the run date, so the "por vencer" chip (< 30 days) shows.
 */
export const EXTRA_ASSOCIATION = {
  name: 'Asociación de Recicladores Demo Sur (ficticia)',
  ruc: '20999000037',
};
export const EXTRA_CERTIFICATION_TYPE = 'Registro municipal (demo)';
export const EXTRA_EXPIRES_IN_DAYS = 20;
