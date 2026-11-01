# Probar ScanFromCam

## 1. Compilar e instalar

Requisitos: JDK 21 y Android SDK 37. Indica dónde está el SDK con `local.properties`
(`sdk.dir=...`, lo escribe Android Studio) o con la variable `ANDROID_HOME`.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Para probar con el rendimiento real usa **staging** (R8 activo, firmado con la clave de
debug):

```bash
./gradlew :app:assembleStaging
adb install -r app/build/outputs/apk/staging/app-arm64-v8a-staging.apk
```

En un móvil físico hay que activar antes las opciones de desarrollador y la
depuración USB.

## 2. Qué probar

| # | Prueba | Qué debe pasar |
|---|---|---|
| 1 | Abrir la app | Biblioteca vacía con el botón **Escanear** |
| 2 | Escanear → elegir **PDF** | Se abre la cámara y pide permiso una vez |
| 3 | Apuntar a un folio | Aparece el marco siguiendo los bordes; se ilumina al estabilizarse |
| 4 | Quedarse quieto | Cuenta atrás en pantalla y disparo automático |
| 5 | Mover el móvil | Se pausa la detección y no dispara |
| 6 | Disparar a mano | Salto al editor con la página recortada y encarada |
| 7 | **Original / Optimizar** | Cambia el acabado; Optimizar aclara el papel y afila el texto |
| 8 | **Rotar**, **Recortar** | Gira la imagen; el recorte permite arrastrar esquinas o un lado entero, con lupa |
| 9 | **Texto** | Extrae el texto del folio (o dice que no hay texto) |
| 10 | **Agregar** → nuevo disparo | El editor muestra "Página 2" y la cámara cuenta las páginas |
| 11 | **Finalizar** con páginas | Abre la hoja de guardado sin perder lo capturado |
| 12 | Guardar con nombre | Vuelve a la Biblioteca con el documento |
| 13 | Abrir el documento | Carrusel de páginas, botón **Abrir PDF** y menú compartir/renombrar/borrar |
| 14 | Abrir el PDF en un visor | Se puede **seleccionar el texto** (capa OCR) |
| 15 | Ajustes → Idioma | Cambia entre español e inglés al instante |
| 16 | Cerrar y reabrir la app | El documento sigue ahí |

## 3. Comprobar el PDF por fuera

```bash
adb shell run-as com.scanfromcam ls -R files/documents
adb shell run-as com.scanfromcam cat files/documents/<id>/document.pdf > doc.pdf
```

## 4. Limitaciones conocidas

- Solo Android. Sin nube, sin cuentas y sin importar de galería.
- Detección pensada para folios sobre fondo contrastado; en páginas muy curvadas
  el recorte deja margen.
- El PDF de una página escaneada sin texto sale sin capa de texto (no hay nada
  que reconocer).
