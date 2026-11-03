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
| 17 | Mantener pulsado un documento | Se activa la selección; tocar otros añade o quita; la barra superior ofrece **Compartir** y **Borrar** |
| 18 | Con varios seleccionados, **Compartir** | Sale la hoja nativa (WhatsApp, correo, Drive…) con el PDF o los JPG |
| 19 | Deslizar un documento a la izquierda | Queda desplazado mostrando la papelera y pide confirmación; al cancelar vuelve a su sitio |
| 20 | **Nueva carpeta** y entrar en ella | La carpeta aparece en la biblioteca y dentro se puede escanear: el documento se guarda en esa carpeta |
| 21 | Seleccionar documentos → **Cortar**, entrar en una carpeta y **Pegar** | Los documentos se mueven; su PDF y sus páginas siguen abriéndose |
| 22 | Seleccionar documentos → **Copiar** y **Pegar** | Aparecen duplicados independientes: borrar el original no toca la copia |
| 23 | Mantener pulsada una carpeta | Permite renombrarla o borrarla; borrarla elimina también todos sus documentos, y el aviso lo dice |
| 24 | Botón de vista (cuadrícula / lista) | Cambia el diseño, y se recuerda al cerrar y reabrir la app; el deslizar para borrar solo funciona en lista |
| 25 | Contadores | La portada muestra el total de documentos del dispositivo (con carpetas incluidas) y cada carpeta muestra su número entre paréntesis |
| 26 | Ajustes → Bloqueo → activar **Pedir el bloqueo del móvil** | Sale el diálogo del sistema pidiendo huella o el PIN/patrón del teléfono (no se crea un PIN nuevo) |
| 27 | Cerrar y volver a abrir la app con el bloqueo activo | Pide el desbloqueo del móvil antes de enseñar nada; si se cancela, no se entra |
| 28 | Desbloquear con huella o PIN del móvil | Entra directamente a la biblioteca con todo disponible |
| 29 | Desactivar la opción en Ajustes | La app deja de pedir el desbloqueo |

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
