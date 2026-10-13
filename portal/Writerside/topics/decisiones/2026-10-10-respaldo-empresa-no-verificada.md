# La empresa se registra como no verificada cuando SUNAT no responde

- **Estado:** Implementada en M4 (2026-10-12): `POST /tracked-companies/unverified`, `POST /tracked-companies/{id}/sunat-refresh` y `RPT-014`/`RPT-016`.
- **Fecha:** 2026-10-10
- **Dónde se aplica:** reporting-service, alta de empresa no verificada
- **Reemplaza a:** —
- **Reemplazada por:** —

## Contexto

Desde v3, la razón social, el estado y la condición de una empresa vienen de una
consulta a SUNAT por RUC (proveedor propuesto: Decolecta, a confirmar en M4 al
revisar sus términos y cuota). El registro normal no acepta
la razón social del cliente. Pero el proveedor puede no responder (caída, cuota
agotada, red), y el personal tiene que poder registrar la empresa igual.

## Decisión

- Un endpoint aparte, `POST /tracked-companies/unverified`, recibe la razón social
  escrita a mano y guarda la empresa como `UNVERIFIED` (en pantalla, "No verificada").
- Antes de aceptar, el servidor vuelve a consultar al proveedor. Si responde, rechaza
  el respaldo (RPT-014) y hay que usar el registro normal. Nadie puede saltarse la
  consulta solo porque lo diga.
- Una empresa `UNVERIFIED` no puede recibir certificados (RPT-016) hasta que
  "Verificar con SUNAT" (`POST /tracked-companies/{id}/sunat-refresh`) funcione y
  reemplace los datos escritos a mano. Si al verificar SUNAT dice que el RUC no existe
  (RPT-012), que está en baja definitiva (RPT-013) o sigue sin responder (RPT-011), la
  empresa no cambia y sigue `UNVERIFIED`.
- `RUC_LOOKUP_PROVIDER=none`, que hace que toda consulta "no responda", existe solo
  para desarrollo y pruebas: el servicio no arranca con ese valor en Render
  (`RENDER=true`).

## Consecuencias

- Hay dos caminos de alta, y el segundo queda marcado y sin certificados hasta
  verificarse.
- Una empresa no verificada puede quedar así si nadie la verifica. La pantalla lo
  muestra y ofrece la acción.

## Alternativas descartadas

- **Rechazar el registro mientras SUNAT no responda:** bloquea al personal por una
  caída de un tercero.
- **Aceptar la razón social en el registro normal cuando la consulta falle:** mezcla
  en un solo endpoint un dato verificado con uno escrito a mano, y el cliente podría
  forzar la ruta manual.
