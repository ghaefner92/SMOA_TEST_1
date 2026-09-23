# Pipeline del avatar 3D

Android carga únicamente estos activos empaquetados:

- `app/src/main/assets/models/valence_face.glb`: rostro usado cuando no hay modo de transporte.
- `app/src/main/assets/models/imiq_character.glb`: personaje animado usado para caminar, bicicleta, coche y transporte público.

`Valence3DFace` conserva el fallback 2D si un GLB no puede cargarse. El personaje debe exponer los clips `walk`, `bike_ride`, `drive` y `transit_sit`, y los morph targets `valence_negative` y `valence_positive`.

## Regenerar el personaje de prueba

Con Blender instalado, desde la raíz del repositorio:

```powershell
blender --background --python tools/create_imiq_character.py
```

El script usa rutas relativas al repositorio y escribe solamente `imiq_character.glb`. No sobrescribas el activo aprobado sin revisarlo antes en un visor glTF y en un dispositivo Android.

## Fuentes locales

Archivos de autoría (`.blend`), vídeos y exportaciones candidatas pertenecen a `artifacts/` y están ignorados por Git. No deben situarse en `app/src/main/assets/models/`, porque Android empaqueta los assets de esa carpeta. Si se aprueba un nuevo asset, exporta un GLB autocontenido, sustitúyelo deliberadamente y comprueba los cuatro clips y los dos morph targets.
