# Revisión acotada de controles QoL

Propuesta para integración por el responsable del preset. No modifica el perfil activo. Se leyó el options.txt generado por ENTRELUMEN; todavía muestra quests en grave.accent, aunque el preset fuente ya fue movido a F8 por el integrador.

## Colisiones reales y contextos

V reúne minería, radial Ars, casco Mekanism, invisibilidad Aether, cambio de hotbar Twilight, carcaj Supplementaries, bolsa Occultism y ranura Tool Belt. Varias funciones dependen de equipo, pero esas condiciones pueden coexistir: no alcanza con ignorar la advertencia roja. B mezcla mochila, waypoint, botas Mekanism y mochila Occultism; J mezcla mapa, piernas Mekanism y limpieza de avisos. R de Tool Belt sí puede chocar con rango Building Gadgets en el mundo.

R de recetas y R del cinturón se conservan: Tool Belt comprueba screen == null antes de abrirse. La acción de recetas requiere pantalla. B dentro del mapa puede conservarse: el contexto de mochila sólo admite mundo o AbstractContainerScreen, no el mapa JourneyMap. No se trata toda repetición de tecla como conflicto.

## Valores exactos propuestos

La columna de destino contiene el valor después del primer separador de options.txt, incluidos modificadores. Ninguna función asignada se desactiva.

| Clave exacta | Valor leído | Valor propuesto |
|---|---|---|
| `key_key.ftbquests.quests` | `key.keyboard.grave.accent` | `key.keyboard.f8` |
| `key_key.ftbultimine` | `key.keyboard.v` | `key.keyboard.v` |
| `key_key.ars_nouveau.selection_hud` | `key.keyboard.v` | `key.keyboard.f6` |
| `key_key.sophisticatedbackpacks.open_backpack` | `key.keyboard.b` | `key.keyboard.b` |
| `key_key.journeymap.create_waypoint` | `key.keyboard.b` | `key.keyboard.m` |
| `key_key.journeymap.map_toggle_alt` | `key.keyboard.j` | `key.keyboard.j` |
| `key_key.journeymap.fullscreen_create_waypoint` | `key.keyboard.b` | `key.keyboard.b` |
| `key_key.mekanism.head_mode` | `key.keyboard.v` | `key.keyboard.up:ALT` |
| `key_key.mekanism.chest_mode` | `key.keyboard.g` | `key.keyboard.left:ALT` |
| `key_key.mekanism.legs_mode` | `key.keyboard.j` | `key.keyboard.down:ALT` |
| `key_key.mekanism.feet_mode` | `key.keyboard.b` | `key.keyboard.right:ALT` |
| `key_key.toolbelt.open` | `key.keyboard.r` | `key.keyboard.r` |
| `key_key.toolbelt.slot` | `key.keyboard.v` | `key.keyboard.r:SHIFT` |
| `key_key.aether.invisibility_toggle.desc` | `key.keyboard.v` | `key.keyboard.home` |
| `key_key.twilightforest.swap_hotbar` | `key.keyboard.v` | `key.keyboard.semicolon` |
| `key_supplementaries.keybind.quiver` | `key.keyboard.v` | `key.keyboard.apostrophe` |
| `key_key.occultism.ender_bag` | `key.keyboard.v` | `key.keyboard.o` |
| `key_key.occultism.backpack` | `key.keyboard.b` | `key.keyboard.insert` |
| `key_key.toastcontrol.clear` | `key.keyboard.j` | `key.keyboard.f10` |
| `key_key.buildinggadgets2.range` | `key.keyboard.r` | `key.keyboard.end` |

Descubrimiento: enseñar V minería, B mochila, J mapa, M marcador, R cinturón y F8 quests en la primera hora. La GUI del Atlas se abre desde el hito correspondiente. Introducir F6 radial cuando se entrega el primer libro Ars. Enseñar Alt+flechas al obtener armadura Mekanism: arriba casco, izquierda torso, abajo piernas, derecha botas. Shift+R abre la ranura del cinturón. Los controles especializados restantes se presentan en su capítulo y se pueden cambiar desde Opciones → Controles, buscando por mod/acción con Controlling.

Home (invisibilidad Aether), Insert (mochila Occultism) y End (rango Building Gadgets) no aparecen asignadas en el options.txt generado inspeccionado. Se proponen sin modificadores y sin depender de un teclado numérico. Semicolon/apostrophe son tokens físicos de GLFW; su etiqueta visible puede variar con distribución ES. No publicar instrucciones basadas sólo en el símbolo impreso.

## Evidencia de las versiones instaladas

- `ars_nouveau-1.21.1-5.13.1.jar`: `client.keybindings.KeyHandler` compara `KeyMapping.getKey().getValue()` en rutas de OPEN_RADIAL_HUD/NEXT_SLOT/PREVIOUS_SLOT/OPEN_BOOK. No se presupone que añadir un modificador a esos controles resuelva el conflicto: el radial recibe una tecla física exclusiva.
- `Mekanism-1.21.1-10.7.19.85.jar`: `mekanism.client.key.MekKeyHandler` consulta `getKeyModifier()` y `KeyModifier.isActive`; `MekanismKeyHandler` registra además una acción SHIFT. Fundamenta la sintaxis `:ALT` para sus modos, pendiente la prueba combinada real.
- `ToolBelt-1.21.1-2.2.10.jar`: `ClientEvents` abre menú/ranura sólo sin pantalla; su comprobación de tecla consulta contexto y `KeyModifier.isActive`. Probar R frente a Shift+R para confirmar que no abre ambos menús.
- `sophisticatedbackpacks-1.21.1-3.25.78.2107.jar`: `KeybindHandler$BackpackKeyConflictContext.isActive` admite mundo y AbstractContainerScreen; no cualquier GUI.
- `journeymap-neoforge-1.21.1-6.0.6.jar`: `KeybindingHandler` registra create_waypoint con contexto IN_GAME y distingue acciones de pantalla completa; minimap_toggle_alt usa Modifier.CTRL. El options generado serializa ese modificador como `:CONTROL`.
- `ftb-ultimine-neoforge-2101.1.15.jar`: `ClientPlatformUtilImpl` consulta el modificador NeoForge. La propuesta conserva V sin modificador.

Se inspeccionó bytecode con javap, sin parchear JAR ni utilizar configuraciones ATM. Las acciones de Aether/Twilight/Supplementaries/Occultism/Building Gadgets se proponen con teclas simples nuevas: sus condiciones de equipo y comportamiento combinado necesitan prueba en juego.

## Detector y comprobación

`python tools/audit_keybindings.py PATH/options.txt` informa colisiones sólo cuando interviene una de las claves auditadas. Devuelve 1 ante claves faltantes o solapamientos del mundo conocidos. `--simulate-preset` aplica el destino sólo en memoria; `--check-preset` exige que los valores estén instalados. El informe conserva casos de contexto desconocido y coincidencias de tecla base con modificador como `review-context`: no afirma que estén libres de colisión. No audita bindings internos de EMI ni todo el pack.

Prueba de aceptación en cliente: mantener V con libro Ars y equipo equipado; abrir B sin waypoint; usar M/J sin cambiar armadura; comprobar los cuatro Alt+flechas y repetir con mapa abierto; R/Shift+R con cinturón y gadget; R sobre una receta; B dentro del mapa. Confirmar que texto/búsqueda no activa funciones del mundo. Registrar configuración de teclado y poder reasignar cada función. Quedan fuera de esta corrección los grupos G/H/C y el solapamiento de ordenado central entre mods: requieren inspección contextual propia, no están declarados resueltos.

## Incorporación de Aircraft (23 de septiembre de 2026)

El JAR fijado Immersive Aircraft 1.5.2 registra `key.immersive_aircraft.dismount` en R. `VehicleEntity.tickPilot` procesa la acción del piloto local; `ClientMain` requiere tecla presionada y clic consumible. Esto coincide con el contexto de ToolBelt al montar con cinturón equipado. El preset cambia sólo la bajada a F12 (`key_key.immersive_aircraft.dismount`); R y Shift+R mantienen sus funciones. El perfil anterior todavía no serializaba esa tecla: se debe confirmar F12 en la primera apertura y probar bajada con cinturón. Una pulsación breve de R en el ensayo visual previo no demuestra compatibilidad. G/H/C y clic medio siguen pendientes; no se declara paridad QoL completa.

El primer perfil completo reveló `key_key.moreoverlays.lightoverlay.desc` en F7; se descartó esa propuesta. F12 no aparece en ninguna otra asignación del options generado de 460 líneas. La tecla final necesita todavía la prueba de bajada con cinturón; la ausencia de otra asignación registrada no demuestra todos los contextos.

F12 quedó comprobado en la pantalla de controles del perfil aislado, en español y sin otra asignación coincidente: [captura nativa](../verification/screenshots/automatic-defaults-f12-es.png). No se ensayó aún la maniobra con cinturón.

## Large-parity families (23 de septiembre de 2026)

Las familias nuevas se revisan con los defaults leídos del bytecode de cada JAR fijado; no hubo cliente lanzado. [Industrial](industrial-expansion-family.md#keybindings): motor de Iron Jetpacks en F4, vuelo estacionario Alt+H, Just Dire Things Alt+T/Shift+T, Draconic Alt+P y Alt+Y (no C: Ars lee C como código crudo) y Hostile Neural Networks Alt+U. `tools/audit_keybindings.py --simulate-preset` sobre las opciones del perfil más esos defaults no encontró solapamientos de mundo ni riesgo de código crudo; los modificadores quedan como review-context hasta la prueba en cliente.
[QoL](qol-functional-decor-family.md#keybindings): recogida de Easy Villagers en Shift+V e imán de Simple Magnets en Shift+M; el ciclo de ofertas conserva C porque actúa en la pantalla de comercio.
[Arcana](arcane-expansion-family.md#keybindings): rueda de Iron's Spells en Shift+G y lanzamiento en Alt+Q. `KeyMappingLookup.getAll` de NeoForge 21.1.249 solo agrega los bindings sin modificador cuando ninguno con el modificador activo coincide, por lo que Alt+Q no dispara también soltar objeto; queda la prueba en cliente.
[Exploración](exploration-structures-family.md#keybindings): Deeper and Darker en Alt+B/Alt+N y habilidades de armadura de Cataclysm en Alt+J/K/L/I.
[Apotheosis](apotheosis-family.md#keybindings): se conservan y fijan en el preset los valores nativos Ctrl+T (selector de World Tier), Ctrl+O (minería radial; ambas movidas a Alt+F5 y Alt+9 el 1 de octubre) y Shift+T (enlazar ítem al chat, sólo en inventarios). Ctrl+O de JEI y Ctrl+T del editor de FTB Quests actúan sólo en GUI; la comparación con Shift queda desactivada por config porque Equipment Compare ya la ofrece. La auditoría simulada terminó con código 0 y sin solapamientos de mundo.

## Revisión completa con los 316 mods (25 de septiembre de 2026)

Se partió del `options.txt` de un cliente recién arrancado con los 316 mods (59 teclas compartidas). Para cada tecla en conflicto se leyó en el JAR fijado cómo se registra (`KeyConflictContext`, `KeyModifier`) y cómo se lee (`consumeClick`/`isDown` en el tick, eventos de pantalla o código crudo). El resultado vive en `tools/keybind_contexts.json` y lo usa `python tools/check_keybinds.py`, que corre en CI y falla ante un choque real.

Regla de NeoForge 21.1.249 verificada en `KeyMappingLookup.getAll`: con un modificador apretado se disparan sólo las combinaciones con ese modificador y la tecla sola queda afuera; sin combinación registrada, se dispara la tecla sola. Por eso Alt+V no activa Ultimine. La contracara: mientras te agachás (Shift) o corrés con Ctrl, una combinación Shift+/Ctrl+ tapa a la tecla sola. Hasta el 30 de septiembre el checker sólo lo marcaba como choque si tapaba a un dueño fijo (G, V, B, K) y como aviso en los demás casos; desde el 1 de octubre es siempre un choque (sección al final). La pantalla de Controles pinta en rojo cualquier letra repetida entre mundo y combinación; es más conservadora que el despacho real.

Dueños fijos: G Curios (ahí van el Brazo de Terra y el curio de Ultimine), V Ultimine (mantener), B mochila, K reclamos de FTB Chunks. Ninguna otra acción del mundo usa esas letras, ni con Shift ni con Ctrl.

Contextos que dejaron teclas compartidas sin choque: GuideME G es `KeyConflictContext.GUI` y se mantiene sobre un ítem con guía; JEI R/U/A actúan sólo en pantallas y Tool Belt R sólo sin pantalla; Silent Gear C/X/Z y Modular Routers C/I son `GUI` sobre su propio ítem; el mapa completo de JourneyMap (B, C, O, flechas) sólo actúa en su pantalla; los modos de Mekanism (N) y el espejo de LittleTiles (N) o el deshacer de Building Gadgets (U) y la marca de LittleTiles (U) exigen herramientas distintas en la mano. El imán de Immersive Engineering en S declara `conflicts() = false` a propósito: se activa con doble toque de retroceder y un escudo con imán. Aether I (pantalla del Aether) queda; Accessories es otro framework, no la base de Curios: el pack no trae la capa de compatibilidad (ver `accessory-tooltip-compat.md`), así que Curios queda visible en G, el Aether en I y la pantalla genérica de Accessories sólo por su botón del inventario.

Teclas que leen el código crudo (Ars Nouveau: grimorio C, radial F6, corona 0, ranuras) chocan con cualquier combinación sobre su misma letra: Alt+C de Psi, Ctrl+C del lanzador de PneumaticCraft y Shift+C de Draconic abrían además el grimorio con el libro en mano. Por eso esas tres salieron de la C.

Quedan compartidas a propósito y documentadas en el JSON: salto con botas propulsoras, gravitita o Beehemoth; ataque con las armas de Mahou Tsukai; S con el imán de IE; C de vanilla (sólo creativo con 1-9) con el grimorio; C para rotar ofertas (sólo en comercio) con Silent Gear y Modular Routers (sólo sobre su ítem); P de salto de página del editor de quests con la vista de patrón de ExtendedAE; F1 de las quests con el manual de McJty; G de Curios con GuideME dentro de la pantalla de Curios (G la cierra y la guía no llega a abrirse).

Avisos: desde el 1 de octubre no quedan. Los 29 avisos de esta ronda (combinaciones Shift+/Ctrl+ que tapan la tecla sola mientras te agachás o corrés con Ctrl, entre ellos Ctrl+B, el narrador de vanilla, sobre la mochila) se resolvieron o se justificaron uno por uno en la sección del 1 de octubre.

Borde conocido: dos herramientas con teclas iguales en manos distintas (por ejemplo, una herramienta de LittleTiles en la mano principal y la Meka-Tool en la secundaria) disparan las dos con N. El checker asume una herramienta por vez.

### Cómo llega a un jugador existente

Default Options 21.1.8 (`KeyMappingDefaultsHandler`, POST_LOAD) hace dos cosas con `keybindings.txt`. Primero cambia el valor por defecto de cada tecla listada, así que el botón Restablecer de Controles ya lleva a la tecla del pack. Después asigna la tecla nueva sólo si esa tecla nunca apareció en el `options.txt` del jugador (`markUserSeenKeys` marca como vistas todas las líneas `key_` existentes) y si sigue en el valor por defecto original. Minecraft guarda todas las teclas al cerrar, así que en un perfil ya jugado ninguna tecla de esta revisión cambia sola: sólo llegan a instalaciones nuevas y a teclas de mods agregados después. El perfil de Elias, por ejemplo, tiene las 473 teclas guardadas y conserva sus 74 choques. Para adoptar el preset hace falta Restablecer (una tecla o todas) en Controles, que pisa también lo que el jugador cambió a mano. `python tools/check_keybinds.py --options RUTA/options.txt --diff` lista, sin escribir nada, qué teclas del preset le faltan. No se implementó un reseteo automático.

### Tabla final del preset

Tecla efectiva de cada línea de `pack/config/defaultoptions/keybindings.txt` (118). "Antes" y "Motivo" sólo en lo que movieron las revisiones del 25 de septiembre y del 1 de octubre; el resto viene de rondas anteriores y su motivo está arriba en este documento o en el doc de su familia. Las teclas que el preset no nombra conservan el valor del mod y pasan el checker.

| Tecla | Acción | Antes | Motivo |
|---|---|---|---|
| 0 | `key.ars_nouveau.head_curio_hotkey` | G | Ars lee el código crudo de la tecla e ignora modificadores: necesita una tecla base propia; nadie usa 0 en el mundo. |
| acento grave | `key.irons_spellbooks.spell_wheel` | Shift+G | Shift+G tapaba a Curios mientras te agachás. |
| Alt+, | `key.bridgingmod.toggle_bridging` |  |  |
| Alt+- | `key.enderio.toggle_magnet` | Ctrl+M | Ctrl+M tapaba el marcador de JourneyMap (M) mientras corrés. |
| Alt+. | `key.settingsGUI` |  |  |
| Alt+1 | `key.sophisticatedbackpacks.toggle_upgrade_1` | Alt+Z | Alt+1/Alt+2 nombran la ranura de mejora y liberan Alt+Z/Alt+X. |
| Alt+2 | `key.sophisticatedbackpacks.toggle_upgrade_2` | Alt+X | Ídem. |
| Alt+3 | `key.little.building_mode` | Ctrl+Inicio (antes Ctrl+B) | Ctrl+Inicio tapaba la invisibilidad del Aether (Inicio); las cinco de LittleTiles quedan juntas en Alt+3 a Alt+7. |
| Alt+4 | `key.little.config.item` | Ctrl+I (antes I) | Ctrl+I tapaba la pantalla de accesorios del Aether (I). |
| Alt+5 | `key.little.config_secondary.item` | Shift+I | Shift+I tapaba la misma I. |
| Alt+6 | `key.little.undo` | Ctrl+Z | Ctrl+Z tapaba el zoom de Just Zoom (Z) mientras corrés. |
| Alt+7 | `key.little.redo` | Ctrl+Y | Va con el deshacer. |
| Alt+8 | `key.selectiveDisplacement` | Ctrl+H | Ctrl+H tapaba la insignia de Eternal Starlight (H). |
| Alt+9 | `key.apotheosis.toggle_radial_mining` | Ctrl+O | Ctrl+O tapaba la bolsa de Occultism (O) mientras corrés. |
| Alt+= | `key.familiarslib.screen_key` | Shift+J | Shift+J tapaba el mapa de JourneyMap (J) mientras te agachás. |
| Alt+[ | `key.prevDestination` | [ | Corchete del tipo de minimapa de JourneyMap. |
| Alt+] | `key.nextDestination` | ] | Ídem, con su par. |
| Alt+AvPág | `key.modern_industrialization.toggle_flight` | Shift+F4 | Shift tapaba el motor (F4) mientras te agachás; los dos vuelos quedan juntos. |
| Alt+B | `key.deeperdarker.boost` |  |  |
| Alt+barra invertida | `key.mekanism.module_tweaker` | barra invertida | Barra invertida de JourneyMap y RFTools. |
| Alt+Down | `key.mekanism.legs_mode` |  |  |
| Alt+E | `key.enderio.travel_staff` | G | G es de Curios. |
| Alt+F | `psimisc.keybind` | Alt+C | Ars lee la C cruda aun con Alt: con el grimorio en mano abría las dos cosas. |
| Alt+F11 | `key.sophisticatedbackpacks.inventory_interaction` | Shift+X (antes C) | Shift+X tapaba la invocación de familiares (X) mientras te agachás. |
| Alt+F5 | `key.apotheosis.open_world_tier_select` | Ctrl+T | Ctrl+T tapaba el chat (T) mientras corrés. |
| Alt+F7 | `simplemagnets.keys.toggle` | Shift+M | Shift+M tapaba el marcador de JourneyMap (M) mientras te agachás. |
| Alt+F9 | `framedblocks.key.update_cull` | Ctrl+F9 (antes F9) | Ctrl+F9 tapaba los bordes de chunk (F9) mientras corrés. |
| Alt+Fin | `key.deep_aether.slider_eye_ability` | Shift+H | Shift+H tapaba la insignia de Eternal Starlight (H); queda junto a Alt+Inicio. |
| Alt+G | `key.deep_aether.stratus_dash_ability.desc` |  |  |
| Alt+H | `keybind.ironjetpacks.hover` |  |  |
| Alt+I | `key.cataclysm.boots_ability` |  |  |
| Alt+Inicio | `key.deep_aether.toggle_skyjade_transparency` | Shift+Inicio | Shift+Inicio tapaba la invisibilidad del Aether (Inicio) mientras te agachás. |
| Alt+Insert | `key.ars_elemental.open_pouch` | J | J es el mapa de JourneyMap. |
| Alt+J | `key.cataclysm.ability` |  |  |
| Alt+K | `key.cataclysm.helmet_ability` |  |  |
| Alt+L | `key.cataclysm.chestplate_ability` |  |  |
| Alt+Left | `key.mekanism.chest_mode` |  |  |
| Alt+M | `key.drawMahoujin` |  |  |
| Alt+N | `key.deeperdarker.transmit` |  |  |
| Alt+O | `key.easy_villagers.pick_up` | Shift+V | Shift+V tapaba Ultimine mientras te agachás. |
| Alt+P | `key.draconicevolution.place_item` |  |  |
| Alt+punto y coma | `key.neovitae.lex_mode` | Shift+punto | Shift+punto tapaba el acelerador de Iron Jetpacks (punto) mientras te agachás. |
| Alt+Q | `key.irons_spellbooks.spellbook_cast` |  |  |
| Alt+R | `key.ad_astra.open_radio` |  |  |
| Alt+RePág | `key.ad_astra.toggle_suit_flight` | Ctrl+F4 | Ctrl tapaba el motor de Iron Jetpacks (F4) mientras corrés. |
| Alt+Retroceso | `key.draconicevolution.tool_modules` | Shift+U (antes Shift+C) | Shift+U tapaba el deshacer de Building Gadgets y la marca de LittleTiles (U). |
| Alt+Right | `key.mekanism.feet_mode` |  |  |
| Alt+Supr | `justdirethings.key.toolUI` | Shift+T | Shift+T tapaba el chat (T) mientras te agachás. |
| Alt+T | `justdirethings.key.toggle_tool` |  |  |
| Alt+U | `key.hostilenetworks.open_deep_learner` |  |  |
| Alt+Up | `key.mekanism.head_mode` |  |  |
| Alt+V | `key.modern_industrialization.toggle_3x3` |  |  |
| Alt+X | `create.keyinfo.toolbelt` | Left.alt | Una pulsación de Alt sola abría la caja de herramientas cerca de una y pisaba todas las combinaciones Alt+. |
| Alt+Y | `key.draconicevolution.tool_config` |  |  |
| Alt+Z | `key.oritech.augment_screen` | Ctrl+G | Ctrl+G tapaba a Curios mientras corrés con Ctrl. |
| apóstrofo | `supplementaries.keybind.quiver` |  |  |
| AvPág | `key.gunDown` |  |  |
| B | `key.journeymap.fullscreen_create_waypoint` |  |  |
| B | `key.sophisticatedbackpacks.open_backpack` |  |  |
| botón 4 del ratón | `key.ars_affinity.ability` |  |  |
| Ctrl+] | `key.occultism.storage_remote` | Shift+O (antes N) | Shift+O tapaba la bolsa de Occultism (O) mientras te agachás. |
| Ctrl+barra invertida | `key.mekanism.key_hud` | H | H queda para Eternal Starlight, que enseña la guía; el HUD se agrupa con el ajustador de módulos. |
| Ctrl+F12 | `key.immersive_aircraft.boost` | B | B es la mochila; queda junto a F12 (bajarse). |
| Ctrl+Fin | `key.buildinggadgets2.anchor` | H | H queda para Eternal Starlight; grupo de Fin. |
| Ctrl+Intro | `pneumaticcraft.chestplate.launcher` | Ctrl+N (antes Ctrl+C) | Ctrl+N tapaba el modo de Mekanism y el espejo de LittleTiles (N). |
| Ctrl+Retroceso | `pneumaticcraft.armor.options` | Ctrl+U (antes U) | Ctrl+U tapaba el deshacer de Building Gadgets y la marca de LittleTiles (U). |
| Ctrl+Supr | `pneumaticcraft.boots.kick` | Ctrl+X | Ctrl+X (valor del mod) tapaba la invocación de familiares y la barra guardada (X). |
| F10 | `key.toastcontrol.clear` |  |  |
| F12 | `key.immersive_aircraft.dismount` |  |  |
| F4 | `keybind.ironjetpacks.engine` |  |  |
| F6 | `key.ars_nouveau.selection_hud` |  |  |
| F8 | `key.ftbquests.quests` |  |  |
| Fin | `key.buildinggadgets2.range` |  |  |
| G | `key.curios.open.desc` | G | Dueño fijo: se fija en el preset para que Restablecer y el checker lo sostengan (valor del mod). |
| H | `key.eternal_starlight.switch_crest` |  |  |
| Inicio | `key.aether.invisibility_toggle.desc` |  |  |
| Insert | `key.occultism.backpack` |  |  |
| J | `key.journeymap.map_toggle_alt` |  |  |
| K | `key.ftbchunks.claim_manager` |  |  |
| M | `key.journeymap.create_waypoint` |  |  |
| N | `key.little.mirror` |  |  |
| O | `key.occultism.ender_bag` |  |  |
| punto y coma | `key.twilightforest.swap_hotbar` |  |  |
| R | `key.toolbelt.open` |  |  |
| RePág | `key.gunUp` |  |  |
| Shift+] | `key.toolbelt.slot` | Shift+R | Shift+R tapaba el menú del cinturón (R) mientras te agachás; ] sólo tiene combinaciones en pantallas. |
| Shift+barra invertida | `key.unmountVehicle` | barra invertida | Barra invertida de JourneyMap y Mekanism. |
| Shift+clic central | `key.immersiveengineering.railgunZoom` | clic central | Con el riel en mano el clic central también elegía bloque (vanilla). |
| Shift+Fin | `key.buildinggadgets2.settings_menu` | G | G es de Curios; queda junto al alcance (Fin) y sólo existe con un gadget en mano. |
| Shift+Intro | `pneumaticcraft.helmet.hack` | Ctrl+P (antes H) | Ctrl+P tapaba las interacciones sociales (P) mientras corrés. |
| Shift+Supr | `key.twilightforest.item_display_map_cycle` | Shift+punto y coma (antes C) | Shift+; tapaba el cambio de barra de Twilight (;); Supr no tiene acción propia en el mundo. |
| Shift+T | `key.apotheosis.link_item_to_chat` |  |  |
| Shift+Y | `key.changeMysticCode` |  |  |
| sin asignar | `accessories.key.open_accessories_screen` | H | Pantalla genérica de Accessories (dentro del Aether), otro framework que Curios; queda el botón del inventario y la I del Aether. |
| sin asignar | `crafting_on_a_stick.key.open_curios` | V | Segunda tecla para la misma pantalla de Curios, sobre la V de Ultimine. |
| sin asignar | `iris.keybind.reload` |  |  |
| sin asignar | `iris.keybind.shaderPackSelection` |  |  |
| sin asignar | `iris.keybind.toggleShaders` |  |  |
| sin asignar | `key.ars_affinity.affinity_ui` |  |  |
| sin asignar | `key.ars_nouveau.next_slot` | X | Código crudo sobre la X de vanilla y las combinaciones con X; el radial de F6 elige ranuras. |
| sin asignar | `key.ars_nouveau.previous_slot` | Z | Código crudo sobre la Z del zoom; el radial de F6 elige ranuras. |
| sin asignar | `key.createrailgrinding.catch` |  |  |
| sin asignar | `key.evilcraft.exaltedCrafting` | C | C del grimorio de Ars y del guardado de barra; el Exalted Crafter se abre con clic derecho. |
| sin asignar | `key.evilcraft.fart` | P | Chiste de EvilCraft sobre la P de interacciones sociales de vanilla. |
| sin asignar | `key.ftbchunks.map` |  |  |
| sin asignar | `key.ftbchunks.minimap.zoomIn` | Equal | Minimapa de FTB apagado; = y - son del zoom de JourneyMap. |
| sin asignar | `key.ftbchunks.minimap.zoomOut` | Minus | Ídem. |
| sin asignar | `key.invtweaks_sort_either.desc` | clic central | Clic central ordenaba a la vez que Sophisticated y ExtendedAE en sus pantallas; quedan sus botones y teclas. |
| sin asignar | `key.journeymap.fullscreen_waypoints` | N | UNIVERSAL sobre N; el administrador de waypoints está en el mapa completo. |
| sin asignar | `key.journeymap.toggle_entity_names` | G | UNIVERSAL (mundo y mapa) sobre G; acción rara que sigue en las opciones de JourneyMap. |
| sin asignar | `key.keybindbundles.open_radial_menu` | Left.alt | Alt sola pisaba las combinaciones Alt+; la guía ya lo daba sin tecla. |
| sin asignar | `key.kubejs.kubedex` | K | Inspector de desarrollo sobre la K de reclamos. |
| sin asignar | `key.neovitae.edit_hud` |  |  |
| sin asignar | `key.twilightforest.zoom` | Z | Zoom de antiparras duplicado sobre la Z de Just Zoom. |
| sin asignar | `keybind.advancedperipherals.glasses_hotkey` |  |  |
| sin asignar | `options.narrator` | Ctrl+B | Rebind Narrator expone el atajo del narrador de vanilla en Ctrl+B y tapaba la mochila (B) mientras corrés; se apaga también `narratorHotkey` en las opciones. |
| U | `key.little.mark` |  |  |
| V | `key.ftbultimine` |  |  |

## Combinaciones Ctrl+ y Shift+ que tapan la tecla sola (1 de octubre de 2026)

Una revisión adversaria encontró que el pack, con `toggleSprint` en falso, tiene a Ctrl apretado mientras el jugador corre, y que NeoForge 21.1.249 (`KeyMappingLookup.getAll`) dispara entonces la combinación Ctrl+tecla en lugar de la tecla sola (lo mismo con Shift mientras te agachás). Casos reales: Ctrl+B es el narrador de vanilla en vez de la mochila; Ctrl+T abría el World Tier en vez del chat; Ctrl+F4 y Shift+F4 eran el vuelo de Ad Astra y de Modern Industrialization en vez del motor de Iron Jetpacks (F4); Ctrl+Z era el deshacer de LittleTiles en vez del zoom; Ctrl+O la bolsa de Occultism y Ctrl+M el marcador de JourneyMap. La ronda anterior ya los veía como avisos y los dejó pasar.

### Regla nueva

Las combinaciones propias del pack viven en Alt+ (Alt no se mantiene apretado jugando) o en Ctrl+/Shift+ sobre una tecla sin acción propia en el mundo (Supr, Retroceso, Intro y el corchete derecho: sólo tienen acciones en pantallas). Una combinación Ctrl+/Shift+ sobre una tecla que también tiene una acción sola en el mundo es un choque. Pasan sin más:

- la combinación por defecto de un mod sobre su propia tecla (JourneyMap trae Ctrl+J junto a J);
- Ctrl o Shift cuando el jugador puso `toggleSprint`/`toggleCrouch`, o movió el sprint/sneak a una tecla que no es Ctrl/Shift;
- las excepciones de `shadowAllowed` en `tools/keybind_contexts.json`, cada una con su razón (cuatro): Ctrl+F12 de Immersive Aircraft junto a F12 (las dos sólo montado en una nave); Ctrl+\ de Mekanism y Shift+\ de RFTools Builder sobre la `\` del preset de minimapa de JourneyMap (un diseño que se elige una vez); Shift+Y del código místico de Mahou sobre la depuración de drones de PneumaticCraft (vista de desarrollo). Un test falla si una excepción deja de corresponder a una sombra real.

### Qué se movió

La tabla de arriba lleva la tecla anterior y el motivo de cada una. Resumen de los casos encontrados:

| Antes | Ahora | Acción |
|---|---|---|
| Ctrl+B (narrador) | sin asignar y `narratorHotkey:false` | mochila de Sophisticated en B |
| Ctrl+T | Alt+F5 | World Tier de Apotheosis (el chat sigue en T) |
| Shift+T | Alt+Supr | interfaz de herramienta de Just Dire Things |
| Ctrl+F4 | Alt+RePág | vuelo con traje de Ad Astra (el motor de Iron Jetpacks sigue en F4) |
| Shift+F4 | Alt+AvPág | vuelo de Modern Industrialization |
| Ctrl+Z / Ctrl+Y | Alt+6 / Alt+7 | deshacer y rehacer de LittleTiles (zoom sigue en Z); config y modo de construcción en Alt+3 a Alt+5 |
| Ctrl+O | Alt+9 | minería radial de Apotheosis (la bolsa de Occultism sigue en O) |
| Ctrl+M | Alt+- | imán de Ender IO (el marcador de JourneyMap sigue en M) |

El narrador: Rebind Narrator (`rebind_narrator-1.21.1-neoforge-2025.12.23`) registra el atajo como la tecla `options.narrator`, por defecto Ctrl+B, y cambia por mixin la comprobación de `KeyboardHandler.keyPress` para usar esa tecla y sus modificadores. El preset la deja sin asignar (`key_options.narrator`) y además fija la opción de vanilla `narratorHotkey:false` en `options.txt` (la cadena existe en `Options` del cliente 1.21.1). Se hacen las dos cosas porque la segunda sola no se probó contra el mixin; quien quiera el narrador lo reasigna en Controles o en Accesibilidad.

### Comprobación

`tools/check_keybinds.py` informa la clase como CLASH, lee `toggleSprint`/`toggleCrouch` y `narratorHotkey` del `options.txt` del preset (o del que se audite con `--options`) y la tecla actual de sprint y sneak. `tools/test_check_keybinds.py` cubre los ocho casos de arriba, el efecto de `toggleSprint`/`toggleCrouch`, Alt como lugar seguro, el apagado del narrador, la regla de la combinación propia del mod y que cada excepción siga viva. El preset pasa con 0 choques y 24 usos compartidos intencionales. Como siempre, esto llega sólo a perfiles nuevos o al Restablecer de Controles.

### Lo que sólo se resuelve jugando

Que LittleTiles registre su contexto de teclas siempre activo (el hallazgo de la revisión; el checker lo toma así). Que las teclas nuevas lleguen al juego en el portátil (la fila de funciones con Fn, GeForce Experience y atajos de Windows sobre Alt+F5, F7, F9, F11, Alt+Supr o Alt+Retroceso). Que Rebind Narrator acepte la tecla sin asignar sin avisos en el log.
