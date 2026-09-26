# Arte del libro de quests (25 de septiembre de 2026)

Adornos para poner **alrededor** de los nodos, como imágenes de capítulo de FTB Quests (`images` en el SNBT del capítulo). Así el libro deja de ser nodos sueltos, como pidió Elias en el playtest. Los genera `art/authoring/draw_quest_art.py` y viven en `entrelumen:textures/gui/quests/`. Referencias inspeccionadas: ATM10 8.1 «The ATM Star» y FTB Evolution «Create» (capturas en `research/quests`).

| Imagen | Tamaño | Uso sugerido |
|---|---|---|
| `sun_heliodor.png` | 128×128 | Centro del capítulo final y del hub: detrás del hito, de 6 a 8 celdas. |
| `numeral_1..6.png` | variable | Número de acto grande, arriba a la izquierda de cada capítulo de historia. No depende del idioma. |
| `act_1..6.png` | 32×32 | Emblema del acto: al lado del numeral y en el hub, como puerta a cada capítulo. |
| `medallion.png` | 64×64 | Disco de pergamino y cobre. Encima va la textura grande de un ítem o bloque del mod (por ejemplo `create:textures/item/wrench.png`), como el emblema de capítulo de FTB Evolution. |
| `frame_1x1`, `frame_2x1`, `frame_3x1`, `frame_3x2` | 64×64 a 192×128 | Paneles que agrupan nodos de un mismo tema. Se estiran sin deformar el borde sólo si se respeta la proporción. |
| `divider.png` | 96×9 | Separador entre ramas o bajo un título. |
| `corner.png` | 16×16 | Esquina superior izquierda. Rotada 90°, 180° y 270° para las otras. |

- Todo es simétrico salvo los numerales: IV y VI son espejo uno del otro.
- Las texturas de otros mods se referencian por su ruta dentro del JAR del jugador. No se copian al repo.
- Uso real desde el 25/9, colocado por el generador: [quest-book](quest-book.md). Los marcos `frame_*` no se usan: se estiran mal fuera de su proporción, y los paneles se arman con cuatro esquineros 1×, que calzan en cualquier tamaño.
