# IMIQ backend

Servicios de despliegue para IMIQ. El motor HOTCO-CT y el flujo de Passport adaptativo viven en `services/dyconet`; el cliente Android está en `../app-frontend`.

## Servicios

- `proxy`: Traefik y rutas públicas.
- `dyconet`: onboarding adaptativo, bootstrap HOTCO-CT, actualización de perfil, análisis contextual y XAI.
- `graphhopper`: geometría de rutas.
- `routing` y `dashbot`: imágenes publicadas usadas por el entorno Compose.
- `web`: página de entrada para Dashbot.

## Desarrollo local

Desde este directorio:

```powershell
docker compose up --build
```

El frontend `localUsbDebug` consume DYCONET en el puerto `8077` expuesto mediante `adb reverse tcp:8077 tcp:8077`.

## Contratos principales de DYCONET

- `POST /api/dyconet/adaptive-passport/start`
- `POST /api/dyconet/adaptive-passport/complete`
- `POST /api/dyconet/adaptive-passport/bootstrap`
- `POST /api/dyconet/adaptive-passport/profile-update`
- `POST /api/dyconet/contextual-deliberation`
- `POST /api/dyconet/contextual-explanation`
- `POST /api/dyconet/contextual-explanation/narrate`

Los tests y contratos específicos se encuentran en `services/dyconet/tests`. La documentación de la app, los flavors Android y el contrato de routing están en `../app-frontend/README.md`.
