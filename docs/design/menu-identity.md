# Identidad de menús: implementación mínima

Estado actual: ocho layouts aceptados por el parser de FancyMenu **3.9.12**; Drippy **3.1.5** está instalado. La captura real del título en español a 1024×768/GUI 3 muestra seis botones y cuatro iconos libres del pie; faltan otras escalas, inglés y la revisión completa de cargas. Los fondos ensayados fueron rechazados y no tienen aprobación estética. No se usó diseño, configuración ni arte de ATM. No constituye todavía la identidad completa de todas las cargas solicitada.

## Superficies y comportamiento

| Identificador verificado en el JAR | Uso | Contenido preservado |
|---|---|---|
| `title_screen` | Menú principal | Un jugador, multijugador, opciones, Mods, idioma, accesibilidad, salir y sus acciones originales |
| `connect_screen` | Conexión a servidor | Estado y cancelación |
| `level_loading_screen` | Preparación del mundo | Indicador real de generación |
| `progress_screen` | Operaciones con progreso | Título, estado y porcentaje originales |
| `receiving_level_screen` | Recepción de terreno | Mensaje original |
| `generic_dirt_message_screen` | Mensaje transitorio | Texto original |
| `drippy_loading_overlay` | Overlay de recursos Mojang | Progreso real |

Los controles conservan acciones, foco, teclado y traducciones de Minecraft/NeoForge; la posición usa anclaje mid-left y coordenadas enteras. La implementación mueve los ocho widgets nativos a una columna izquierda: seis filas de 160×20 con paso 24 y dos accesos de 20×20. No crea botones duplicados ni áreas invisibles que intercepten clics. No fija una escala global de GUI. El fondo conserva proporción; los widgets siguen respondiendo a la resolución nativa.

## Interfaz de assets para el integrador de arte

El generador consume archivos PNG originales en estas rutas cuando existen:

- `art/menu/title-background.png` → `pack/config/fancymenu/assets/entrelumen/title-background.png`.
- `art/menu/loading-background.png` → `pack/config/fancymenu/assets/entrelumen/loading-background.png`.
- `art/menu/logo.png` → `pack/config/fancymenu/assets/entrelumen/logo.png`.

`assets/entrelumen/manifest.json` registra disponibilidad y SHA256. Sin ilustraciones presentes se usa el fondo sólido `#18302FFF` mediante el tipo real `color_fancymenu`, evitando una imagen rota. El logo aprobado se coloca arriba de la columna a tamaño nativo (máximo 192×64), sin reescalado fraccional; sólo entonces se ocultan minecraft_logo_widget y minecraft_splash_widget. Su aplicación visual todavía requiere QA del cliente. No integrar texto traducible dentro de los fondos.

Dirección de composición: observatorio, vegetación y cobre en los márgenes; baja textura y contraste en el centro, donde siguen botones y progreso. Crear fondos como pixel art auténtico a baja resolución y paleta limitada. La ilustración se concentra a la derecha y el tercio izquierdo permanece oscuro y tranquilo. Nada de imagen generada usada directamente, antialiasing ni interpolación bilineal. El generador copia bytes, sin resampling; revisar el filtro nearest del renderer en QA. Mantener centro libre para progreso en fondos de carga. Revisar también 1028×768: el recorte de proporción no puede desplazar arte importante detrás de etiquetas.

## Activación y cierre visual a cargo del integrador

1. Ejecutar `python tools/build_menu_identity.py` después de recibir los assets; comprobar `--check`.
2. Instalar únicamente los nuevos archivos de `customization/entrelumen_*.txt` y `assets/entrelumen/` en el perfil propio con el cliente cerrado.
3. En el menú principal, abrir la barra de FancyMenu. Activar **Current Screen Customization** para `title_screen`. Usar **Customization → Layout Editor** para confirmar el fondo. No crear otro layout duplicado: abrir el `entrelumen_title_screen` existente.
4. Para pantallas transitorias, utilizar las pantallas dummy soportadas por FancyMenu/editor cuando estén disponibles; confirmar que el identificador coincide con la tabla. Activar cada pantalla mediante el editor y guardar su archivo de configuración generado, preservando las opciones existentes. Si el editor no permite activar una superficie, dejarla pendiente; no inventar archivos de screen settings.
5. El generador ya coloca el logo con el serializer image y oculta únicamente logo/splash vanilla al existir arte aprobado. Verificar en editor que la columna y el logo se aplican: x20; primer botón y=-40 respecto del centro vertical; logo termina en y=-52. Si una propiedad no se aplica, guardar una edición mínima real de ese widget para contrastar la serialización; no duplicar elementos.
6. Revisar 1028 y 1920, español e inglés, con ratón y navegación por teclado: cinco acciones principales, idiomas/accesibilidad, conexión y cancelación, carga/generación real, y retorno al menú. Confirmar legibilidad al inicio de cada transición, no sólo una captura posterior.

Las instrucciones de acceso al editor y activación provienen de [Getting Started](https://docs.fancymenu.net/docs/en-US/home); la distribución de archivos de [Modpacks](https://docs.fancymenu.net/docs/en-US/modpacks). Los nombres específicos del formato se comprobaron contra bytecode local, porque la documentación web puede describir versiones posteriores.

## Verificación y límites

`tools/build_menu_identity.py --verify-parser <java.exe>` ejecuta únicamente `PropertiesParser.deserializeSetFromFancyString` del JAR fijado, con Konkrete y Log4j API, sobre siete archivos temporales. No lanza cliente ni servidor. `--check` compara bytes de todos los productos contra generación determinista. Esto comprueba sintaxis real, no aplicación ni renderizado.

Fuente de formato inspeccionada: `Layout.serialize/deserialize`, `MenuBackgroundBuilder`, `ImageMenuBackgroundBuilder`, `ColorMenuBackgroundBuilder`, `PropertiesParser`, `UniversalScreenIdentifierRegistry`. Formato: `type = fancymenu_layout`; contenedores `layout-meta`, `customization`, `menu_background`; tipo `image` y prefijo local `[source:local]/config/fancymenu/assets/...`.

No se añadieron consejos bilingües nuevos. Los seis botones principales tienen texturas propias normal/hover/inactive y conservan sus acciones y etiquetas nativas. Drippy cubre el overlay de recursos de Mojang y fue observado funcionando con progreso real; no cubre la ventana temprana de NeoForge, launcher ni errores. La ubicación de los iconos pequeños continúa pendiente de QA. El hook de identificación de cuatro widgets del título está compilado en el cliente nuevo (hash prefijo `5AAF`), sin comprobación visual: la interacción quedó pausada.


## Verificación del origen pixel art

Historial del mecanismo técnico: el productor del arte emite `art/menu/pixel-art-approved.json` con `{"sha256":{"title-background.png":"HASH","loading-background.png":"HASH","logo.png":"HASH"}}` después de terminar el pixel art original. El generador sólo incorpora archivos cuyo hash coincide; una imagen de inspiración presente bajo la misma ruta no entra por accidente. Esto es procedencia técnica del arte, no un pedido de aprobación al usuario. No se añadieron toggles globales de suavizado.

El serializer real de `VanillaWidgetElementBuilder` admite `vanilla_button`, `is_hidden`; `ElementBuilder` lee `instance_identifier`, `anchor_point`, `sticky_anchor`, `x/y/width/height`. `TitleScreenWidgetIdentificationContext` resuelve IDs mediante claves traducibles, independientes del idioma. `AnchorMidLeft` obtiene altura/2; offsets y tamaños son enteros. Comprobar GUI de al menos 256 píxeles de alto; otras escalas pequeñas requieren compactar espaciado después de QA, nunca ocultar acciones.

## Drippy Loading Screen: instalado; límites de cobertura

[Drippy 3.1.5 para NeoForge 1.21.1](https://modrinth.com/mod/drippy-loading-screen/version/MVgsDsSB) declara FancyMenu >=3.9.9, Konkrete >=1.9.0 y NeoForge >=21.1.47. El catálogo actual satisface esos mínimos. Se inspeccionó `META-INF/neoforge.mods.toml` del JAR oficial en memoria; la investigación inicial no lo instaló; posteriormente el integrador incorporó esta versión al catálogo y al cliente. SHA1 oficial: `9d27991a7f5a551265692abf8ed40ece10ea1958`.

La [documentación oficial de carga](https://docs.fancymenu.net/docs/en-US/loading-screen) confirma que FancyMenu necesita este complemento para el splash/loading. Compatibilidad declarada no demuestra ausencia de conflictos: el integrador debe validar arranque y recarga F3+T, transición al menú y errores de recursos antes de usarlo. El módulo separado de carga temprana de NeoForge requiere verificación adicional; no se presume cubierto por Drippy básico.


## Muestreo pixel art y desenfoque: comprobación de fuente

El JAR fijado resuelve PNG mediante `PngTexture` → `TextureManagerEntry.dynamicTexture` → `new DynamicTexture(NativeImage)`. En Minecraft 1.21.1, `DynamicTexture.upload` llama a `NativeImage.upload` con blur=false; la implementación establece filtro OpenGL **9728 / GL_NEAREST**. No hace falta ni se inventa una propiedad filter. Un escalado de pantalla no entero puede producir bloques de píxeles de anchos alternados, pero no interpolación suave; el logo se dibuja a tamaño nativo con GUI scale entero.

FancyMenu inicializa `applyVanillaBackgroundBlur` en false. El formato serializa `apply_vanilla_background_blur` dentro del contenedor **scroll_list_customization**, aunque su nombre parezca referirse sólo a listas. Los siete layouts lo fijan explícitamente a false y no tocan opciones globales del usuario. También desactivan el overlay vanilla sobre su fondo personalizado.

El logo180×64 usa x10 y termina doce unidades antes del primer botón; los botones permanecen en x20/ancho160. Con GUI de257alto entra todo. Para alturas GUI inferiores a250, no considerar aprobada la composición: verificar un layout compacto específico antes de prometer soporte. No se escala texto ni se fuerzan tamaños fraccionales.

## Corte de evidencia actualizado — 12-09-2026

Los pasos de editor anteriores documentan el procedimiento de integración, no tareas todas pendientes: la activación ya permitió ver botones propios y overlay Drippy. No convertir esa observación en aprobación del diseño completo. El catálogo contiene140 dependencias de cliente y110 de servidor (companion aparte).

La dirección visual vigente exige textura y formas próximas a Minecraft, con paleta contenida. Se rechazaron tanto la escena programática anterior como los fondos derivados de ImageGen filtrados. Los archivos y hashes conservados son evidencia de iteraciones, no aprobación final; no presentar ninguno como fondo terminado. Logo/composición, posición de iconos y los cuatro IDs del hook nuevo requieren revisión del cliente5AAF cuando se reanude UI. Sin prueba simultánea de las siete pantallas ni cobertura de early-loading.

## Observación posterior y corrección — 19:20 Argentina

El cliente90A mostró el Atlas nativo y los cuatro widgets identificados correctamente, pero el pie de versión pisaba esos iconos y el botón inferior. El generador ahora usa seis filas separadas22px; los cuatro iconos se anclan a `mid-right` bajo el Atlas, conservando sus acciones. En GUI342×256, los controles terminan en Y220 y el pie dispone de32px desdeY224. El anclaje derecho mantiene alineación con el Atlas también en ventanas mayores. Falta comprobar la composición nueva dentro del cliente.

`select_world_screen` se añadió como octava superficie con fondo pino explícito: la pantalla de selección observada aún mostraba un fondo borroso no deseado. No se atribuye su causa a Cumulus sin prueba. Ocho layouts pasan el parser real. Este probe requiere un JDK completo: el JRE recortado de CurseForge no incluye `jdk.compiler` y no ejecuta el archivo fuente del probe.

`theme-policy.json` distingue aceptación artística de integridad por hash. Los fondos filtrados rechazados permanecen inactivos en las ocho superficies generadas. El Atlas del título debe proceder del icono nativo16×16 y se amplía8× entero; el nombre histórico `pixel-art-approved.json` sólo representa hashes de integridad y no aprobación del usuario. PixelLab se evalúa para el siguiente candidato, manteniendo las mismas restricciones.

## Evidencia posterior — 21:13–21:23 Argentina, 12-09-2026

Este corte actualiza los pendientes históricos anteriores sólo donde hay observación concreta. La [captura F2 del título de las 21:13](../verification/screenshots/title-pixellab-footer-fixed-es.png), en español, 1024×768 y GUI 3, corresponde al JAR previo con prefijo SHA256 `C064`: los seis botones y cuatro iconos quedan libres del pie. Queda comprobada esa composición en esa configuración, sin extender la prueba a otros idiomas o escalas.

El cliente actual usa SHA256 `AA3D45EA220B6CA060ED2B799E6D80F313CFB1491C37EA43E5BF2A98D9A2C7EF`; el integrador reporta test/build PASS en `work/atlas-readability-build.log`. Observó el Atlas nativo en inventario y en mano, y la GUI poblada en español. La [captura F2 de las 21:23](../verification/screenshots/atlas-pixellab-es-cost-visible.png) muestra Banco de precisión con icono de material de 16px, nombre, `0/1` y `faltan 1` visibles sin scroll. El libro mide 300×210, las filas 12px y la fuente nativa 9px; los nombres de materiales reservan dos filas y los costos aparecen antes de los prerrequisitos. El scroll izquierdo alcanzó los cinco proyectos y Archivo. La [captura anterior a la corrección de densidad](../verification/screenshots/atlas-pixellab-es-before-density-fix.png) conserva el fallo de costos ocultos como evidencia histórica.

La partida quedó pausada en singleplayer. La continuación de Computer Use se dejó pendiente por actividad del usuario en el escritorio. Siguen pendientes inglés, otras escalas, scroll derecho y fabricación en estación; no se afirma una prueba en inglés. No hubo nuevas generaciones de PixelLab en este tramo. La identidad completa de menú principal y cargas no está terminada; los fondos filtrados rechazados siguen inactivos y sus rechazos se conservan.
