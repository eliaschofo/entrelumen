# Compatibilidad de modelos del cliente

Revisión local del log del 12-09-2026 18:15 y JAR fijados: Mekanism 10.7.19.85, Mekanism Extras 1.4.1, MoreMachine 1.4.1 y Ars Nouveau 5.13.1. Sin supresión de ModelBakery ni cambios de mods.

## Correcciones

- Cuatro modelos ausentes `mekanism_extras:block/factory/front_led/active/{basic,advanced,elite,ultimate}`: overrides originales de una propiedad `parent` apuntan a los modelos homónimos existentes de Mekanism. Se conserva geometría/UV/textura nativa por herencia, sin copiar assets de Mekanism. Son referencias cargadas realmente por ModelBakery, no un filtro de logs.
- `mekmm:block/factory/centrifuging/base`: typo `block_block` resolvía al inexistente `minecraft:models/block_block.json`. Corregido a `minecraft:block/block`. Los 34 elementos, texturas, grupos y crédito permanecen idénticos al JAR. Este padre es utilizado por modelos de factories centrifugadoras, incluidos los modelos de inventario; corresponde repararlo.

## Ars: advertencia real, recurso auxiliar sin ruta normal encontrada

`planarium_projector` está registrado con block/item pero no posee JSON de modelo ni blockstate. Ningún JSON del JAR lo referencia, incluidas las recetas. `PlanariumProjectorRenderer` dibuja directamente el PLANARIUM invertido a escala32 y traslaciónY=-24, con bounding box infinito: su proyección no depende del modelo item ausente. No se reemplaza por un planarium normal ni un cubo arbitrario: alteraría la identidad de una pieza auxiliar. El warning de item es real y una entrega administrativa puede mostrar modelo ausente; no demuestra que el Planarium obtenible tenga un modelo roto. Queda revisión upstream para representación/intención del auxiliar. El blockstate también queda pendiente y fuera de estos overrides de models.

## Verificación y prueba pendiente

Parseo JSON aprobado; los cuatro padres LED existen en el JAR de Mekanism; comparación estructural demuestra que sólo parent y metadata de licencia cambiaron en centrifuging/base. Pendiente recarga real e inspección de factories centrifugadoras de todos los tiers, LEDs activos y Planarium normal por el integrador. No se declara validación visual desde fuente.

## Licencias y procedencia

MoreMachine declara MIT en META-INF/neoforge.mods.toml, autor Lost Myself; el modelo conserva literalmente `Made with Blockbench, made by TedXenon`. El JAR no contiene archivo LICENSE separado. El aviso MIT se incorpora como metadata ignorada por el cargador dentro del propio JSON para acompañar cada distribución del recurso. Esta excepción de tercero conserva MIT y no queda sometida a la restricción de redistribución del contenido original de ENTRELUMEN. Fuente: https://github.com/lostmyself8/Mekanism-MoreMachine

Mekanism y Extras declaran MIT; los cuatro alias sólo referencian modelos que continúa suministrando su JAR. Ars declara GNU GPL y no se copió contenido suyo.

MIT License

Original model credited to TedXenon; Mekanism: MoreMachine by Lost Myself.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
