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
