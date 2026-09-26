# Guías del pack (capítulos informativos)

Capítulos de quests que documentan el pack para quien recién empieza: cada mod importante, QoL, logística, construcción, granjas, tips y los sistemas propios de ENTRELUMEN. Son opcionales y no mueven la historia. El formato está en `tools/check_guides.py`; se valida con `python tools/check_guides.py`. `tools/generate_quests.py` los compila al libro en cinco grupos de FTB (ver [quest-book](../../docs/design/quest-book.md)), con el `layout` y `optional` tal como están escritos.

- `emblem`: textura del ítem clave, dibujada 4× sobre el medallón del capítulo. Tiene que existir en un JAR fijado, ser cuadrada (16 o 32 px) y quieta.
- Una tarea con `tag` también nombra el `item` concreto que FTB va a pedir: no hay mod de filtros. Para metales unificados, el que elige Almost Unified.
- Una tarea de ítem pide el ítem que el jugador realmente recibe: si Almost Unified lo reemplaza, va el unificado.
