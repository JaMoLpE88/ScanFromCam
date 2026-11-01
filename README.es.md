<p align="center">
  <img src="docs/images/logo.png" alt="ScanFromCam" width="560">
</p>

<p align="center">
  <a href="https://github.com/JaMoLpE88/ScanFromCam"><img alt="Plataforma" src="https://img.shields.io/badge/plataforma-Android%207%2B-3DDC84"></a>
  <a href="LICENSE"><img alt="Licencia" src="https://img.shields.io/badge/licencia-Apache--2.0-blue"></a>
  <img alt="Sin anuncios" src="https://img.shields.io/badge/anuncios-ninguno-brightgreen">
  <img alt="Sin red" src="https://img.shields.io/badge/red-ninguna-brightgreen">
  <img alt="Sin marca de agua" src="https://img.shields.io/badge/marca%20de%20agua%20forzada-ninguna-brightgreen">
</p>

<h1 align="center">ScanFromCam</h1>

<p align="center">
  <a href="README.md">English</a> · <b>Español</b>
</p>

<p align="center">
  Convierte la cámara del móvil en un escáner de documentos: captura, endereza,
  limpia y exporta a imagen o a PDF con texto. Libre, open source, sin conexión,
  sin anuncios, sin cuentas y sin límites.
</p>

---

## Qué hace

Apuntas la cámara a un folio. ScanFromCam encuentra sus cuatro esquinas, espera
a que el encuadre esté quieto antes de disparar y te deja la página en un lienzo
oscuro donde puedes corregir el recorte, girarla, reconocer su texto y elegir el
acabado: **Original** (tal cual se capturó) u **Optimizar** (limpio como un
escaneo real). Las páginas se apilan en un documento que se guarda como un JPG
por página o como un PDF con capa de texto seleccionable.

Todo ocurre en el dispositivo. La app ni siquiera pide el permiso
`INTERNET`.

<p align="center">
  <img src="docs/images/library-es.jpg" width="240" alt="Biblioteca">
  <img src="docs/images/capture-es.jpg" width="240" alt="Captura con detección en vivo">
  <img src="docs/images/editor-es.jpg" width="240" alt="Editor con el acabado Optimizar">
</p>
<p align="center">
  <em>Biblioteca · detección de bordes en vivo y auto-disparo · editor con el acabado optimizado</em>
</p>

## Pantallas

<table>
  <tr>
    <td align="center"><img src="docs/images/format.jpg" width="220" alt=""><br><sub>Imagen o PDF</sub></td>
    <td align="center"><img src="docs/images/crop.jpg" width="220" alt=""><br><sub>Arrastra un vértice o un lado entero</sub></td>
    <td align="center"><img src="docs/images/magnifier.jpg" width="220" alt=""><br><sub>Lupa de 2,5× bajo el dedo</sub></td>
    <td align="center"><img src="docs/images/save.jpg" width="220" alt=""><br><sub>Ponle nombre y guarda</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/viewer.jpg" width="220" alt=""><br><sub>Lee, comparte o abre el PDF</sub></td>
    <td align="center"><img src="docs/images/options.jpg" width="220" alt=""><br><sub>Renombrar, compartir, borrar</sub></td>
    <td align="center"><img src="docs/images/settings-es.jpg" width="220" alt=""><br><sub>Idioma, sonido, auto-disparo</sub></td>
    <td align="center"><img src="docs/images/original-vs-optimize.jpg" width="220" alt=""><br><sub>Original vs Optimizar</sub></td>
  </tr>
</table>

## Funciones

**Captura**

- Detección de bordes en vivo sobre el fotograma completo, con un cuadrilátero
  suavizado por esquina que no tiembla.
- Auto-disparo: el marco se pone verde cuando la página está encuadrada y con
  cobertura suficiente, espera la demora que elijas (0–3 s) y dispara. Se pausa
  mientras el móvil se mueve, así que nunca hace una foto borrosa.
- Obturador manual siempre disponible; el beep y el sonido de obturador se
  pueden apagar.

**Edición**

- Recorte con cuatro vértices o arrastrando un lado entero, moviendo el borde que
  corresponde, con una lupa de 2,5× que sigue al dedo.
- Rotar, retomar, añadir más páginas y quitar las que no quieras.
- Original / Optimizar por página, calculado al pedirlo para que el editor abra
  al instante.
- Extracción de texto en el dispositivo, para copiarlo o para el PDF.

**Optimizar** — el pase de foto a escaneo, en este orden:

1. estimar la iluminación de la escena y dividirla;
2. fijar el negro y el blanco al papel y la tinta de esta página, lo que además
   neutraliza la dominante de color;
3. estirar la curva de tonos, recuperar altas luces y afilar sólo donde hay un
   borde, para que el papel liso siga liso;
4. devolver el croma, porque aplanar la luminancia lava el color.

**Guardar y compartir**

- Un JPG por página, o un PDF cuya capa de texto es realmente seleccionable y
  buscable (texto invisible sobre la imagen).
- Nombrar el documento, compartirlo, renombrarlo, abrir el PDF y borrarlo.

**Privacidad**

- Sin permiso de `INTERNET`: la app no puede subir nada, por construcción.
- Sin cuentas, sin analítica, sin informes de fallos y sin SDK de anuncios.
- Los documentos viven en el almacenamiento privado de la app y sólo salen del
  dispositivo si los compartes tú.

## Gratis es gratis

- **Sin anuncios.** Ni banner, ni splash, ni "mira un vídeo para desbloquear".
- **Sin suscripción, sin créditos y sin muro de pago.** Todo lo que hay en este
  repositorio está disponible para todo el mundo.
- **Sin límite de páginas ni de documentos.** Escanea 2 páginas o 200.
- **Sin marca de agua forzada.** Hay un sello diagonal opcional, con el texto que
  tú escribas, que viene **desactivado**; la página es tuya.
- **Código abierto.** Apache-2.0: léelo, compílalo, hazle un fork, publícalo.

## Idiomas

La interfaz viene en **español** e **inglés**, y se cambia en caliente desde
Ajustes. Añadir otro idioma es implementar `Texts`
(`core/Texts.kt`).

<table>
  <tr>
    <td align="center"><img src="docs/images/library.jpg" width="240" alt=""><br><sub>English</sub></td>
    <td align="center"><img src="docs/images/editor.jpg" width="240" alt=""><br><sub>English</sub></td>
    <td align="center"><img src="docs/images/capture.jpg" width="240" alt=""><br><sub>English</sub></td>
  </tr>
</table>

## Descargar

Los APK firmados van adjuntos en cada [release](https://github.com/JaMoLpE88/ScanFromCam/releases):

- `ScanFromCam-1.0.0-arm64-v8a.apk` — móviles y tablets (casi cualquier dispositivo actual).
- `ScanFromCam-1.0.0-x86_64.apk` — emuladores y tablets x86.

Descarga el APK y ábrelo en el móvil; Android pedirá permiso para instalar desde esa
fuente. Sin cuenta de tienda y sin registro.

## Compilar

Requisitos: JDK 21 y Android SDK 37. Indica dónde está el SDK con `sdk.dir` en
`local.properties` (lo escribe Android Studio) o con la variable `ANDROID_HOME`.

```bash
git clone https://github.com/JaMoLpE88/ScanFromCam.git
cd ScanFromCam
./gradlew :app:assembleDebug
```

La build de release va minificada y genera un APK por ABI (la librería nativa de
OpenCV pesa, así que `arm64-v8a` y `x86_64` se compilan por separado). Para una build
instalable rápida, firmada con la clave de debug:

```bash
./gradlew :app:assembleStaging
```

## Pruebas

- `./gradlew :app:testDebugUnitTest` — 54 tests unitarios: geometría y orden del
  cuadrilátero, suavizado, ventana de estabilidad, decisión de auto-disparo,
  manejo del recorte, rotación, maquetación de la marca de agua, nombres de
  fichero y detección de idioma.
- `./gradlew :app:connectedDebugAndroidTest` — 15 tests instrumentados: detección
  sobre fotos sintéticas, refinado de esquinas, el pipeline completo de captura,
  recorte y optimización, PDF con y sin capa de texto, y PDFs grandes en
  streaming.

## Estructura

```
app/src/main/java/com/scanfromcam/
  core/            nombres, imágenes, idioma, textos, marca de agua, sonidos
  domain/          modelos, geometría del cuadrilátero, contrato del repositorio
  data/            pipeline OpenCV, SQLite, OCR, PDF
  feature/
    library/       documentos y miniaturas
    format/        imagen o PDF
    capture/       cámara, detección en vivo, auto-disparo
    editor/        recorte, lupa, acabados, texto
    viewer/        leer, compartir, renombrar, borrar
  ui/theme/        tokens de diseño
```

## Cómo funciona el escaneo

- **Detección** (`data/Cv.kt`): dos generadores de candidatos independientes, los
  contornos de borde y la mancha brillante del papel, puntuados por área,
  rectangularidad, soporte real de borde, brillo del papel y contraste con el
  entorno; se descartan los cuadriláteros cortados por el encuadre, porque un
  folio se ve entero.
- **Rectificación**: ajuste de mínimos cuadrados por lado, re-intersección de las
  cuatro rectas y deformación de perspectiva con prefiltro al reducir, para que
  los trazos finos no hagan aliasing.
- **Optimizar**: el pase de iluminación / punto blanco / contraste local / croma
  descrito arriba.
- **OCR**: reconocimiento de texto de ML Kit en el dispositivo y capa de texto
  invisible en el PDF.

## Terceros

AndroidX, Jetpack Compose, CameraX (Apache-2.0) · OpenCV 4 (Apache-2.0) ·
ML Kit Text Recognition (términos de Google) · Coil (Apache-2.0).

## Pruebas manuales

Hay una guía paso a paso para probar la app en un móvil real en
[docs/TESTING.md](docs/TESTING.md).

## Licencia

[Licencia Apache 2.0](LICENSE) © 2026 JaMoLpE88.
