# Atlas: revisión de interfaz

Revisión acotada del 23 de septiembre de 2026. Se conserva el libro actual y se corrige únicamente la visibilidad del foco de teclado. No es una nueva aceptación visual dentro del juego.

## Referencias inspeccionadas visualmente

| Referencia local | Resolución | Observación aplicada |
| --- | --- | --- |
| `docs/verification/screenshots/minecraft-book-and-quill-native.png` | 1024×768 | Libro real de Minecraft: pergamino claro, tinta oscura, borde material y texto separado de los controles. El foco debe seguir siendo reconocible aunque los controles tengan tratamiento de libro. |
| `docs/verification/screenshots/atlas-pixellab-es-cost-visible.png` | 1024×768 | Atlas real en español: el libro de dos páginas, el título completo del proyecto en el detalle y el coste `0 / 1 · faltan 1` son legibles. El índice abrevia nombres largos, con detalle completo y tooltip. No se necesita otra composición. |
| `art/gui/atlas_book.png` | 300×210 | Fondo vigente con pliegue y márgenes de pergamino. Se conserva intacto, a escala GUI entera, sin agregar una paleta o arte alternativos. |

## Fallo y cambio único

Los dos `renderSelection` de `AtlasScreen` ignoraban `outerColor`. En Minecraft 1.21.1, `AbstractSelectionList.renderItem` calcula ese color según `isFocused()`: la selección nativa distingue la lista activa de otra que sólo conserva su selección. El Atlas descartaba esa señal; al alternar el foco entre índice y detalle ambas selecciones podían quedar visibles con exactamente el mismo tratamiento.

La comprobación se hizo contra el código de `net/minecraft/client/gui/components/AbstractSelectionList.java` y `ObjectSelectionList.java` del artefacto local `C:/Users/elias/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_447a22689bb98030d481b11ff9a729ce8e4aa204_output.jar`, identificado por su manifiesto como Minecraft 1.21.1 / NeoForge 21.1.249. `ObjectSelectionList.nextFocusPath` conserva la navegación nativa y `AbstractSelectionList.setFocused` mantiene visible la fila al navegar con flechas.

`renderPaperListFocus` dibuja un contorno de un píxel GUI con el cobre existente sólo cuando la lista tiene foco. Se dibuja fuera de su recorte, por lo que no cruza glifos, iconos de 16×16 ni barras de desplazamiento. La selección previa sigue visible al pasar a la otra página. No se cambian eventos de entrada, textos EN/ES, dimensiones, desplazamiento, peticiones, respuestas, pausa ni estado de botones.

Geometría relativa a la esquina del libro, con límites finales exclusivos:

| Superficie | X | Y | Ancho | Alto |
| --- | ---: | ---: | ---: | ---: |
| Foco del índice | 22 | 43 | 114 | 113 |
| Foco del detalle | 164 | 43 | 110 | 113 |

Los controles inferiores comienzan en Y=158; el contorno termina en Y=156. Los encabezados quedan por encima de Y=43. A 1024×768, GUI 2 deja un lienzo lógico de 512×384 y GUI 3 de 342×256; el libro de 300×210 y ambos contornos caben en los dos casos. Esta comprobación geométrica no sustituye la revisión renderizada.

## Validación y pendiente de integración

- `compileJava --offline --no-daemon` pasó con el JDK 21.0.12.1 local. Sólo aparecieron las advertencias existentes de `AtlasClient` y Gradle.
- `git diff --check` pasó para los archivos propios. No se agregan tests que repliquen dos llamadas de dibujo; la aceptación del cambio necesita ver el foco en Minecraft.
- No se abrió ni se lanzó un cliente desde este trabajo. Las capturas citadas son evidencia anterior al cambio.

### Control posterior del integrador

El integrador probó el JAR SHA-256 `c0046f479cb1d7ed83f41b6d92aa8d60bf5dd16e5c0528a17cb5e0deff3594da` en Minecraft real, un jugador, ES, GUI 2 y ventana de 1024×768. `/entrelumen` abrió el libro; Tab/Shift+Tab alternaron correctamente los marcos. Clic en el segundo proyecto seleccionó «Una mesa para viajeros» y cargó su detalle. Ratón y desplazamiento respondieron; el mundo permaneció pausado.

Las pulsaciones `Down` enviadas con `@oai/sky` no cambiaron la selección de ninguna lista. Se contrastó la ruta exacta `Screen.keyPressed(264)` → `ObjectSelectionList.nextFocusPath(ArrowNavigation)` → `AbstractSelectionList.setFocused`: no depende de los ticks del mundo y el detalle no redefine la selección. Como control fuera del Atlas, el integrador abrió el chat nativo con T y envió `Up`; tampoco recuperó `/entrelumen` del historial recién utilizado. Por tanto, estas pulsaciones no prueban un fallo del Atlas: la validación de flechas queda limitada por el transporte de teclas Sky/GLFW. No se identificó su causa interna ni se cambió la navegación para compensarlo.

El integrador debe comprobar en GUI 2 y 3, tanto EN como ES:

1. Con Tab/Shift+Tab, el contorno aparece únicamente en la lista activa y desaparece al enfocar un botón. Se conserva el foco visible de los botones.
2. Con flechas, el índice cambia el proyecto y el detalle permite llegar al último coste/prerrequisito. El contorno queda fuera del texto y de los iconos, también al desplazar parcialmente una fila.
3. En un proyecto con materiales y nombre largo, siguen legibles nombre completo, cantidades y estado; Revisar/Refresh sigue mostrando la respuesta del servidor. Una entrega autorizada sigue dependiendo de ese servidor.

### Cierre visual del integrador

El mismo JAR se revisó en juego a 1024×768, ES y EN, en GUI 2 y 3. Los márgenes, las cantidades y los nombres completos del detalle permanecen legibles; el índice abrevia nombres largos conservando su tooltip. Tab distingue la página activa y Shift+Tab vuelve al índice. El clic en Revisar recibe la respuesta y vuelve a habilitar el botón sin avanzar ni consumir materiales. El marco desaparece al enfocar ese botón. El clic y el desplazamiento del detalle funcionan.

Capturas F2 sin editar: [ES GUI 3](../verification/screenshots/atlas-focus-es-gui3.png), [EN GUI 3](../verification/screenshots/atlas-focus-en-gui3.png), [EN GUI 2](../verification/screenshots/atlas-focus-en-gui2.png). ES GUI 2 se observó directamente durante la misma sesión; no se guardó una captura F2 nueva de ese caso.

El log también registra cinco errores `Invalid scancode 256` durante las pruebas de flechas. La comprobación física de flechas sigue pendiente; no se presenta la inspección de código como prueba runtime. El cambio integrado sólo agrega el foco visible. Esta revisión no acepta el arte final del pack ni prueba ritmo de supervivencia.
