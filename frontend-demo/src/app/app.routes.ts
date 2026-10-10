import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/guards';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Preparando el sistema · Trazabilidad ESG',
    loadComponent: () => import('./features/warmup/warmup.page').then((m) => m.WarmupPage),
  },
  {
    path: 'login',
    title: 'Iniciar sesión · Trazabilidad ESG',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shell/shell').then((m) => m.Shell),
    children: [
      {
        path: 'empresas',
        title: 'Empresas · Trazabilidad ESG',
        loadComponent: () => import('./features/companies/companies.page').then((m) => m.CompaniesPage),
      },
      {
        path: 'empresas/nueva',
        title: 'Registrar empresa · Trazabilidad ESG',
        loadComponent: () => import('./features/companies/company-new.page').then((m) => m.CompanyNewPage),
      },
      {
        path: 'empresas/:id',
        title: 'Empresa · Trazabilidad ESG',
        loadComponent: () => import('./features/companies/company.page').then((m) => m.CompanyPage),
      },
      {
        path: 'asociaciones',
        title: 'Asociaciones · Trazabilidad ESG',
        loadComponent: () => import('./features/associations/associations.page').then((m) => m.AssociationsPage),
      },
      {
        path: 'asociaciones/:id',
        title: 'Asociación · Trazabilidad ESG',
        loadComponent: () => import('./features/associations/association.page').then((m) => m.AssociationPage),
      },
      {
        path: 'vecinos',
        title: 'Vecinos · Trazabilidad ESG',
        loadComponent: () => import('./features/neighbors/neighbors.page').then((m) => m.NeighborsPage),
      },
      {
        path: 'vecinos/nuevo',
        title: 'Registrar vecino · Trazabilidad ESG',
        loadComponent: () => import('./features/neighbors/neighbor-new.page').then((m) => m.NeighborNewPage),
      },
      {
        path: 'vecinos/:id',
        title: 'Vecino · Trazabilidad ESG',
        loadComponent: () => import('./features/neighbors/neighbor.page').then((m) => m.NeighborPage),
      },
      {
        path: 'sigersol',
        title: 'Registros SIGERSOL · Trazabilidad ESG',
        loadComponent: () => import('./features/sigersol/sigersol.page').then((m) => m.SigersolPage),
      },
      {
        path: 'sigersol/nuevo',
        title: 'Registrar dato SIGERSOL · Trazabilidad ESG',
        loadComponent: () => import('./features/sigersol/sigersol-new.page').then((m) => m.SigersolNewPage),
      },
      {
        path: 'recojos/nuevo',
        title: 'Registrar recojo · Trazabilidad ESG',
        loadComponent: () =>
          import('./features/collection-record/collection-record.page').then((m) => m.CollectionRecordPage),
      },
      {
        path: 'empresas/:companyId/certificados/:certificateId',
        title: 'Certificado · Trazabilidad ESG',
        loadComponent: () => import('./features/certificate/certificate.page').then((m) => m.CertificatePage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
