# Lectura del Atlas — 2026-09-23

## Referencia inspeccionada antes del cambio

Se abrieron con `view_image` estas imágenes reales, sin modificarlas:

- `docs/verification/screenshots/minecraft-book-and-quill-native.png` (1024×768): libro nativo de una página vertical. El pergamino visible mide aproximadamente 250×325 píxeles dentro de una cubierta de unos 290×356; conserva márgenes claros, tinta oscura, contador arriba y controles separados abajo. La referencia informa la lectura y la separación entre texto y controles, no un reemplazo del arte del Atlas.
- `docs/verification/screenshots/atlas-focus-es-gui3.png` (1024×768): Atlas a GUI 3. El libro de 300×210 unidades GUI ocupa 900×630 píxeles. Se ven «Una mesa para v…», «Ensamblar la len…» y estados abreviados. En entregas, «Libro» reserva dos filas de 12 unidades antes de las cantidades; el icono tiene 16×16 unidades. El marco de foco del detalle queda fuera del texto.
- También se inspeccionó `art/gui/atlas_book.png` (300×210): dos páginas de pergamino, pliegue central y bordes ornamentados. La textura y los controles inferiores conservan sus posiciones; el contenido sólo comienza tres unidades más arriba, todavía debajo del encabezado.

## Ajuste acotado

`AtlasScreen` mide títulos y estados traducidos con `Font.split`; el índice dibuja todas sus líneas sin elipsis. Cada acto usa una altura de fila suficiente para su entrada más larga: `10 + líneas × font.lineHeight`. Las filas siguen siendo uniformes para conservar los hit targets, la selección y la navegación de `ObjectSelectionList`. Esto puede dejar menos proyectos simultáneamente visibles; todos se alcanzan mediante el desplazamiento nativo. No se aplica un máximo de líneas que vuelva a ocultar texto.

| Geometría, en unidades GUI | Antes | Ahora |
| --- | ---: | ---: |
| Libro | 300×210 | 300×210 |
| Altura del área desplazable | 109 | 112 |
| Ancho de título/estado en el índice | 90 | 96 |
| Ancho de texto completo en entregas | 88 | 92 |
| Ancho del nombre junto al icono | 69 | 73 |
| Interlínea del detalle con fuente nativa de 9 | 12 | 10 |
| Iconos de materiales | 16×16 | 16×16 |

El detalle usa la fuente nativa más una unidad de interlínea. Un material con nombre de una o dos líneas y cantidades de una línea pasa de 36 a 30 unidades de altura. Las cantidades siguen usando todo el ancho; nombres y estados largos se envuelven. Las filas necesarias para alojar el icono se calculan desde sus 16 unidades, sin escalarlo. El resaltado de una línea de continuación evita el icono, y su parte inferior sigue dibujándose al desplazar parcialmente la primera fila.

La respuesta del servidor conserva el widget enfocado, la posición relativa de fila del índice y el desplazamiento/selección del detalle cuando continúan la misma campaña, acto y proyecto. El desplazamiento se restaura después del foco nativo y se limita al rango actual. Volver a seleccionar el mismo proyecto conserva la lectura; seleccionar otro empieza arriba. Una campaña, acto o selección que desaparece abre su nueva selección visible. Repoblar el detalle elimina la referencia de foco antigua y enfoca una entrada nueva cuando corresponde, también ante espera o timeout. Los clics en la barra no seleccionan una fila por el solapamiento del hit target nativo.

Las traducciones se resuelven al construir las filas; cada reconstrucción vuelve a medir con el idioma y la fuente actuales. Se conservan las narraciones, los tooltips completos, Tab/Shift+Tab, las flechas nativas, el estado autoritativo de entrega y la pausa de un jugador.

## Verificación y límite

Se contrastaron los recorridos de foco, hit testing, clipping y scroll contra `AbstractSelectionList`, `ObjectSelectionList`, `AbstractContainerWidget` y `Screen` de las fuentes locales fijadas de NeoForge 21.1.249. `git diff --check` no detectó errores de whitespace. El integrador ejecutará build y tests; este trabajo no abrió un cliente ni generó una captura sintética. Las imágenes anteriores documentan la referencia y el defecto, no prueban el resultado nuevo.

La aceptación renderizada queda pendiente en Minecraft real, EN/ES y GUI 2/3:

1. Leer completos «Una mesa para viajeros», «Ensamblar la lente», un título posterior largo y todos los estados, recorriendo el índice hasta el último proyecto.
2. Leer materiales de una y varias líneas, cantidades y prerrequisitos; desplazar un icono parcialmente fuera del borde superior y enfocar su segunda línea sin taparlo.
3. Probar Tab/Shift+Tab, flechas físicas, tooltips y arrastre de ambas barras. La incidencia histórica de transporte `Invalid scancode 256` no cuenta como prueba de las flechas.
4. Refrescar mientras se lee una parte baja, volver a seleccionar el mismo proyecto, cambiar a otro y recibir una respuesta/timeout con el detalle enfocado. La posición y el marco deben corresponder a entradas actuales.
5. Confirmar respuesta autoritativa, habilitación de botones y pausa. Las pruebas anteriores de entrega no aceptan por sí solas este render ni el arte final del pack.
