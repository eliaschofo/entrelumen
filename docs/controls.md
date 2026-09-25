# First-launch controls / Controles iniciales

This preset is applied once to a fresh profile. Player changes are preserved. The
in-game Controls search (Controlling) remains the authority for your keyboard.
Keys below are a starting layout; the full 316-mod conflict review is in
docs/design/keybinding-review.md and `python tools/check_keybinds.py` guards it.
Players who already launched the pack keep their saved keys; Controls → Reset
adopts the preset.

Este preset se aplica una sola vez a un perfil nuevo y conserva los cambios del
jugador. Buscá cada acción en Controles (Controlling), especialmente con un teclado
español. La revisión de conflictos con los 316 mods está en
docs/design/keybinding-review.md. Un perfil ya jugado conserva sus teclas
guardadas; Restablecer en Controles adopta el preset.

| Action / Acción | Initial key / Tecla inicial |
|---|---|
| Quests / Misiones | F8, or inventory book icon / F8 o icono de libro del inventario |
| Curios (Terra Arm, Ultimine curio / Brazo de Terra, curio de Ultimine) | G |
| Ultimine | V |
| Map / Mapa | J |
| Waypoint / Marcador | M (world / mundo), B (inside map / dentro del mapa) |
| Backpack / Mochila | B |
| Claims / Parcelas | K |
| Tool belt / Cinturón | R (world / mundo) |
| Tool belt slot / Ranura del cinturón | Shift+R |
| Ars spell selection / Selección de hechizos Ars | F6 |
| Mekanism armor modes / Modos de armadura Mekanism | Alt+arrows / Alt+flechas |
| Villager pickup / Levantar aldeano | Alt+O |
| Ender IO travel staff / Bastón de viaje | Alt+E |
| Iron's Spells wheel / Rueda de Iron's Spells | key left of 1 / tecla a la izquierda del 1 |
| Psi | Alt+F |

Recipe and use keys are handled by the recipe viewer in inventory context; do not
use its inventory shortcuts as global world actions. FTB Chunks' extra full map
shortcut starts unbound because JourneyMap supplies navigation; its claim screen
remains available. The shipped FTB minimap is disabled. The first client entry
displayed JourneyMap; combined controls with equipment still require verification.

Las teclas de recetas y usos funcionan en el inventario. El acceso al mapa extra
de FTB Chunks comienza sin tecla asignada: JourneyMap se ocupa de la navegación y
el menú de parcelas sigue disponible. El minimapa de FTB viene desactivado y
JourneyMap se vio en la primera entrada. Falta probar los atajos con equipo puesto.

No FPS or memory claim is inferred from these settings. Benchmark resolution is
1080p, without shaders; actual window size and memory allocation are measured at
test time. No se infieren resultados de rendimiento a partir de este preset.

## Atlas and chapter navigation / Atlas y navegación

Open quests with F8 or through the book icon in the inventory. In the opening chapter, open
the Atlas milestone and click its task to open the Atlas interface. This task
entry requires the matching companion build with the FTB task-opening bridge.
The first project supplies the portable Atlas, avoiding a separate bootstrap
craft. Afterwards, use that item to open the interface. Select a project, inspect
its prerequisites and available/missing materials, then Deliver. Advance opens
the next act after the current projects are complete. Refresh updates a delayed
response before retrying. These are server-validated actions, not quest checkmarks.

Abrí las misiones con F8 o el icono de libro del inventario. En el primer capítulo,
abrí el hito del Atlas y hacé clic en su tarea para abrir la interfaz. Esta entrada
requiere el companion actualizado con el puente de apertura desde FTB. El primer
proyecto entrega el Atlas portátil, sin exigir una fabricación previa. Después,
usá ese objeto para abrir la interfaz. Elegí un proyecto, revisá sus requisitos y
materiales disponibles/faltantes y pulsá Entregar. Avanzar abre el próximo acto
cuando terminás los proyectos actuales. Actualizar permite consultar una respuesta
demorada antes de reintentar. Las acciones las valida el servidor.

Diagnostic fallback only: `/entrelumen status`, `/entrelumen deliver <project>` and
`/entrelumen advance`. The normal walkthrough teaches the interface. Sólo como
alternativa de diagnóstico; el recorrido normal enseña la interfaz.

F8 is the agreed fresh-profile binding, applied by the root-owned preset. The
inventory icon was verified in the client. F8 interaction and the new Atlas-task
click still need verification on the updated installed build. Player-rebound
keys remain authoritative. F8 es la tecla acordada para perfiles nuevos; el
preset lo aplica el integrador. El icono del inventario está verificado. Falta
comprobar F8 y el clic de la tarea del Atlas con la versión actualizada instalada.

For zoom/recenter, use FTB's displayed control help and actual key bindings.
The Spanish keyboard mapping has not been proven by the configured key names;
do not assume grave/minus injection is portable. Para zoom y centrado, usá la
ayuda mostrada por FTB y las teclas efectivas de Controles. No se da por validado
el mapeo del teclado español a partir del nombre de una tecla en options.txt.

## Verified serialization capabilities (FTB Quests 2101.1.34)

Inspected the installed `ftb-quests-neoforge-2101.1.34.jar` read-only using JDK21
`javap -c -p`; no guessed configuration keys:

- `Chapter.writeData/readData` stores `autofocus_id` as a string. `getAutofocus`
  parses its hex ID and accepts a movable in that same chapter. `QuestScreen.tick`
  invokes it when choosing the first visible chapter. The generator points it at
  the stable `arrival` ID. Existing client-persisted view state may restore an old
  location; verify first opening with fresh quest-view state, not by deleting a
  player's settings automatically.
- `Quest.writeData/readData` stores `hide_dependent_lines` as a boolean;
  `shouldHideDependentLines` returns it. Only workbench and furnace hide their
  outgoing visual fan-out. Dependencies, completion rules and IDs remain intact;
  cooking, surveying and final convergence retain their lines.
- Existing serialized `size` now uses 1.1 for the five hexagonal milestones,
  0.85 for normal tasks and 0.75 for optional readings. Coordinates remain stable.
  No invented global zoom/pan setting is written and no client preset is changed.

Generator validation passes for 25 quests, locale parity, IDs, dependency DAG,
nonoverlap and welcome autofocus. Rendered first-open/recenter behavior and the
new companion task-opening interaction still require root's client QA. This
change never writes the active instance.
