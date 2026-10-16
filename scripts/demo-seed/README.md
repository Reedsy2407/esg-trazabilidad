# demo-seed: datos de demo ficticios

Carga, a través de las APIs públicas, la lista de datos de demo aprobada (ver `tasks/todo.md`, "Handoff v2"):

- la asociación "Asociación de Recicladores Demo Norte (ficticia)" y su certificación;
- 3 vecinos con sus cronogramas y 6 recojos de setiembre de 2026 (66.95 kg en total; el certificado cubre solo del 17 al 30, 35.30 kg);
- la empresa "Empresa Demo Envases S.A.C. (ficticia)";
- el dato SIGERSOL de setiembre (ingreso manual);
- y, solo si se pide, el certificado del 17 al 30 de setiembre de 2026 (el 16 de setiembre hay un recojo de prueba ajeno al seed, fuera del período).

Todo es ficticio, con "(demo)" o "(ficticia)" en el nombre. No crea cuentas del personal.

- **Simulación por defecto.** Sin `--apply` solo lee y muestra, paso a paso, qué crearía (`+`) y qué ya existe (`=`).
- **Idempotente.** Antes de cada alta busca si ya existe (por RUC, nombre, fecha o período) y la omite. Se puede volver a correr sin duplicar nada. Si encuentra un conflicto (por ejemplo, otro registro SIGERSOL que se superpone), se detiene sin tocarlo.
- **El certificado es irreversible.** Solo se emite con `--apply --emit-certificate`, y solo si el borrador (17 al 30 de setiembre) da exactamente 35.30 kg y 85.5 %. Los kilos llegan a reportes por evento, unos segundos después de cada recojo; si aún no cuadran, el script no emite y pide volver a correr en un minuto.
- **Solo habla con los cuatro servicios conocidos** (`esg-auth-service`, `esg-recycler-service`, `esg-collection-service` y `esg-reporting-service` en onrender.com), los mismos del frontend. Se niega a cualquier otra URL y no sigue redirecciones.
- **Credenciales solo por variables de entorno** (`ESG_STAFF_EMAIL`, `ESG_STAFF_PASSWORD`). Nunca se aceptan como argumento. La contraseña y el token nunca se imprimen ni se guardan.
- Requiere Node 22 o superior. No usa dependencias.

## Cómo correrlo (PowerShell)

Desde la raíz del repo:

```powershell
cd "C:\Proyectos Reedsy\esg-trazabilidad"

# 1. Credenciales, solo para esta ventana. La contraseña se pide sin mostrarla y no queda en el historial.
$env:ESG_STAFF_EMAIL = "tu.correo@dominio"
$secure = Read-Host "Contraseña" -AsSecureString
$env:ESG_STAFF_PASSWORD = [System.Net.NetworkCredential]::new("", $secure).Password
Remove-Variable secure

# 2. Simulación (no escribe nada). Revisa la salida.
node scripts/demo-seed/seed.mjs

# 3. Crear lo que falta.
node scripts/demo-seed/seed.mjs --apply

# 4. Comprobar: debe decir "0 altas pendientes".
node scripts/demo-seed/seed.mjs

# 5. Certificado (IRREVERSIBLE). Primero en simulación: debe decir "se emitiría con 35.3 kg y 85.5 %".
node scripts/demo-seed/seed.mjs --emit-certificate
node scripts/demo-seed/seed.mjs --apply --emit-certificate

# 6. Al terminar, borrar las variables de entorno (o cerrar la ventana).
Remove-Item Env:ESG_STAFF_EMAIL, Env:ESG_STAFF_PASSWORD
```

La primera llamada puede tardar hasta ~2 minutos: los servicios del plan gratuito de Render se duermen, y el script espera a que despierten, avisando del progreso.

Opcional: `--extra-association` agrega una segunda asociación ("Asociación de Recicladores Demo Sur (ficticia)", RUC 20521286769, el de una entidad pública real; sin validez legal). Su certificación vence 20 días después de la fecha en que se corre, así que sale con el chip "por vencer". Está apagada por defecto y también pasa por la simulación. Si esa asociación ya tiene una certificación "Registro municipal (demo)", no se crea otra, aunque ya haya vencido:

```powershell
node scripts/demo-seed/seed.mjs --extra-association
node scripts/demo-seed/seed.mjs --apply --extra-association
```

Códigos de salida: `0` terminó, `1` se detuvo por un conflicto o un error del servicio (la salida dice cuál), y `2` hay un problema de uso (faltan variables, opción desconocida o URL no permitida).

## Pruebas

```powershell
node --test scripts/demo-seed/seed.test.mjs
```

Usan un `fetch` simulado en memoria: nunca tocan la red ni producción.
