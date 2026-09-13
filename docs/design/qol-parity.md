# Paridad funcional QoL — propuesta de selección

Fecha: 2026-09-12. **No se declara paridad terminada ni ausencia de conflictos.** La meta es igualar/superar las experiencias útiles de los ejemplos con un responsable por función, no acumular mods equivalentes. Esta tarea sólo escribió este documento y `content/qol-matrix.json`; no instaló ni configuró nada.

## Evidencia y límites

- ATM10 8.1: inventario local y metadata de los JAR candidatos, más installedAddons de minecraftinstance.json. Confirmar presencia no demuestra preset o comportamiento. No se copiaron configuraciones, recetas ni texto.
- Craftoria 1.36.0: [export oficial 8850018](https://www.curseforge.com/minecraft/modpacks/craftoria/files/8850018), manifest leído en memoria. Se cruzaron project IDs de los mods actuales y candidatos locales. `not established` significa que este cruce no demuestra la función, no que Craftoria carezca de ella.
- FTB Evolution 1.43.1 / 100487: [página oficial](https://www.feed-the-beast.com/modpacks/125-ftb-evolution) y [Mods](https://www.feed-the-beast.com/modpacks/125-ftb-evolution/mods) consultadas. MC1.21.1/NeoForge verificados; el HTML leído muestra versiones pero no entradas de mods. No se atribuyen funciones QoL ni se usa un manifest antiguo.
- Candidatos externos: API oficial Modrinth filtrada por loaders=[neoforge] y game_versions=[1.21.1], registrando versión y URL exactas. IDs de registro permanecen null donde no se abrió el JAR: el slug no se presenta como mod ID. Dependencias son índice para revisión, no cierre verificado.
- La matriz registra 49 funciones, 36 registros con disponibilidad comprobada (uno ya seleccionado) y dos búsquedas no resueltas/no compatibles. No son 36 recomendaciones simultáneas. Sin pruebas nuevas de cliente: `runtime_conflicts_verified=[]`.

## Decisiones que evitan superposición

1. Jade es el HUD de inspección, con Jade Addons; no sumar WTHIT/Top. AppleSkin sólo alimentación y tooltip mods sólo cuando el cursor los pide.
2. JourneyMap es el mapa visible. FTB Chunks administra claims/equipos: su minimapa no debe competir. La protección debe probarse con compañeros, invitados y máquinas.
3. EMI es el navegador visible. JEI permanece como compatibilidad para plugins que lo requieran; comprobar que las categorías reales se exponen en EMI y que no aparecen dos buscadores/listas. No esconder contenido faltante detrás de una preferencia estética.
4. Inventory Tweaks es el único sorter/restocker. Mouse Tweaks gestiona arrastre. Inventory Profiles Next es alternativa de sustitución, no incorporación acumulativa. Inventory Essentials sólo si puede ofrecer un gesto ausente sin duplicar handlers.
5. Tool Belt posee el radial de herramientas; Just Zoom posee zoom. Mapa, hechizos, Curios y mochila reciben contextos separados. Las teclas sugeridas en la matriz NO son defaults verificados: se aplican tras observar opciones reales.
6. Tombstone es la única recuperación de muerte; no añadir Corpse. Loom/loot y regalos siguen las reglas de campaña, sin recompensas poderosas que salten actos.

## Próxima selección recomendada

Primera tanda de pruebas pequeñas: Clean Swing, Crafting On A Stick, Smithing Template Viewer, Akashic Tome, Chat Heads, Better Ping Display y Cherished Worlds. Aportan interacción o lectura sin producción automática. Shulker Box Tooltip y FindMe también son prioritarios, pero primero comprobar preview existente de mochilas y permisos de búsqueda de contenedores.

Segunda tanda opcional: Equipment Compare, BetterF3, Better Third Person, KeyBind Bundles y Jumpy Boats. Activación contextual; cámara y debug optativos. No dar por hecho que hace falta Durability Tooltip si Better Advanced Tooltips ya cubre la lectura. Pick Up Notifier puede crear más ruido del que resuelve; defecto discreto/off hasta QA. Legendary Tooltips es presentación, no una mejora funcional obligatoria.

Balance separado: Carry On necesita respetar claims, spawners y NBT; Easy Anvils/Easy Magic alteran economía de encantamiento; Easy Shulker Boxes cambia acceso a almacenamiento; Easy Villagers puede automatizar trades; Torchmaster neutraliza hostiles; Item Collectors y Charging Gadgets se solapan con logística existente. Son candidatos vigentes, no defaults aprobados. Nunca añadirlos como regalos early para inflar comodidad.

Diferir KeybindsPurger automático, Transfer Labels/Step Crafter hasta leer su comportamiento concreto y cualquier segunda implementación de función ya cubierta. FTB JEI Extras sólo por categorías útiles comprobadas. Observable es herramienta opt-in de diagnóstico, no QoL que deba funcionar permanentemente.

## Matriz funcional

La comparación por pack, fuentes, dependencias y pendientes completos están en JSON. Aquí se muestran responsables y fronteras; todas las pruebas runtime listadas siguen pendientes en esta auditoría.

| Función | Responsable | Evitar duplicación | Contexto propuesto / QA |
|---|---|---|---|
| Inspección bloques y máquinas | Jade | WTHIT,The One Probe | HUD pasivo; toggle reservado; HUD solapado y lectura a escala 2/3 |
| Búsqueda de recetas | EMI visible; JEI backend | Dos paneles visibles | R/U sólo hover; búsqueda Ctrl+F en inventario; plugins de JEI visibles en EMI y foco texto |
| Árbol de ingredientes | EMI | Planner competidor | clicks interfaz; autocrafting no otorga materiales |
| Recetas ambiguas | Polymorph | ocultar recetas alternativas | selector crafting; recetas del mismo patrón |
| Transferencia a crafting | EMI transferencia; Crafting Tweaks rejilla | Inventory Essentials duplicando gestos | Shift-click contexto rejilla; faltantes y slots bloqueados |
| Ordenar inventario | Inventory Tweaks único | Inventory Profiles Next,Inventory Sorter | botón ordenar; medio sólo contenedor; mochilas AE2 y pantallas especiales |
| Arrastrar stacks | Mouse Tweaks | Mouse Wheelie | gestos ratón inventario; no activar dos drag handlers |
| Reposición hotbar | Inventory Tweaks | Inventory Profiles Next | automático sólo ítems equivalentes; no cambiar herramienta sin intención |
| Mesa portátil | Crafting On A Stick candidato | doble mesa portátil early | uso de objeto; receta simple; no autocraft universal |
| Vista de contenedores | Shulker Box Tooltip candidato | Easy Shulker Boxes previews duplicados | Shift-hover; Sophie previews existentes antes de sumar |
| Hambre y saturación | AppleSkin | segundo overlay de comida | HUD contextual al comer; escala y accesibilidad color |
| Explicación encantamientos | Enchantment Descriptions | tooltips redundantes | hover; idiomas mods externos no prometidos |
| Explicación efectos | JEED si ya seleccionado | otro panel pociones | hover efecto; JEI/EMI puente |
| Plantillas herrería | Smithing Template Viewer candidato | tooltips que tapan patrón | hover plantilla; recetas nativas visibles |
| Comparar equipo | Equipment Compare candidato | panel atributos duplicado | Shift-hover configurable; ancho tooltip con Curios |
| Durabilidad legible | Durability Tooltip candidato | Better Advanced Tooltips misma línea | hover; ver cobertura actual antes de añadir |
| IDs y tags técnicos | Better Advanced Tooltips | tooltips siempre expandidos | F3+H vanilla contexto; por defecto compacto |
| Biblioteca de manuales | Akashic Tome candidato | segundo libro campaña | uso objeto; Atlas conserva narrativa; no copiar libros de otros packs |
| Ayuda multiblocks | JEI Mekanism Multiblocks | tareas sin diagramas | interfaz recetas; EMI visibilidad |
| Profesiones y trades | Just Enough Professions | FTB JEI Extras si misma categoría | interfaz recetas; categorías duplicadas |
| Cría de animales | Just Enough Breeding | tooltips duplicados | interfaz recetas; integraciones reales |
| Arqueología | JE Archaeology | otra lista idéntica | interfaz recetas; loot dimensiones |
| Mapa y waypoints | JourneyMap único visible | Xaero y minimapa FTB simultáneo | M mundo; J configurar propuesta; no capturar M mientras se escribe |
| Claims cooperativos | FTB Chunks sólo claims | segundo minimapa | acceso botón equipo; sin M; claims no bloquean regalos autorizados |
| Viajes descubiertos | Waystones | teleport mapa gratuito | uso bloque; costos early y permisos |
| Buscar biomas | Nature's Compass | buscador global sin costo | uso brújula; radio/costo exploración |
| Buscar estructuras | Explorer's Compass | mapa revelado | uso brújula mid; no saltar expediciones early |
| Minería conectada | FTB Ultimine único | Ore Excavation | mantener tecla mundo; no toggle; hambre herramientas límites claims |
| Construcción repetitiva | Stick temprano; Gadgets energía después | varios builders tempranos | uso herramienta; radial sólo equipada; material consumido y undo |
| Acceso herramientas | Tool Belt | Utility Vest misma rueda | radial equipada; evitar zoom C; hotbar/mochila colisiones |
| Silenciar máquinas | Extreme Sound Muffler | mute global | botón ajustes audio; mantener avisos de combate |
| Notificaciones | Toast Control | Pick Up Notifier siempre activo | sin tecla frecuente; no ocultar tutorial necesario |
| Zoom accesible | Just Zoom | segunda cámara zoom | C mundo propuesto; evitar rueda toolbelt y spell radial |
| Narración voluntaria | Rebind Narrator + vanilla | desactivar accesibilidad | combinación explícita; texto no dispara Ctrl+B |
| Buscar conflictos teclas | Controlling | purga automática de preferencias | pantalla controles; prueba teclado ES/EN |
| Presets de controles | KeyBind Bundles candidato | Default Options pisando usuario | pantalla configuración; import opt-in y perfil nuevo |
| Golpear tras hierba | Clean Swing candidato | otro tweak hit detection | ataque mundo; protección claims y plantas |
| Navegación de botes | Jumpy Boats candidato | movimiento permanente alterado | saltar montado; no ventaja vuelo |
| Cámara tercera persona | Better Third Person optativo | otra cámara libre | sólo F5; mareo accesibilidad |
| Identidad chat coop | Chat Heads candidato | múltiples overlays chat | chat abierto; chat seguro sin cambios de permisos |
| Latencia por jugador | Better Ping Display candidato | HUD red permanente | Tab; no medir TPS con ping |
| Recuperación al morir | Tombstone único | Corpse + segundo sistema tumbas | interacción tumba; recompensas mágicas balanceadas |
| Loot por jugador | Lootr | duplicador global loot | interacción cofre; coop seis y reinicios |
| Dormir de expedición | Comforts | cambiar spawn con sleeping bag | uso objeto; dimensiones y voto coop |
| Respaldo local | Simple Backups | dos schedulers backup | administración no tecla; retención tiempo IO |
| Diagnóstico base | spark; Observable optativo | dos perfiles permanentes | comando op2 opt-in; sin uploads automáticos |
| Iluminación y chunks | More Overlays | overlays duplicados | F7/F9 propuestos; no solapar mapas/claims |
| Encontrar ítems cercanos | FindMe candidato | xray de inventarios ajenos | tecla hover sólo inventario; claims y rango |
| Evitar abrir save equivocado | Cherished Worlds candidato | menús redundantes | pantalla mundos; FancyMenu navegación |

## Candidatos con versión vigente comprobada

La lista contiene opciones de selección, sustitución y diferimiento; no es una orden de instalar todos. Las dependencias exactas extraídas figuran por candidato en JSON. No se instalaron bibliotecas para hacer la investigación.

| Candidato | Versión examinada / fuente primaria | Decisión |
|---|---|---|
| Better Advanced Tooltips | [2101.1.0-build.5](https://www.curseforge.com/minecraft/mc-mods/better-advanced-tooltips) | already selected; verify coverage |
| Clean Swing | [1.9](https://www.curseforge.com/minecraft/mc-mods/clean-swing-through-grass) | priority next isolated trial |
| Crafting On A Stick | [1.21.0.6](https://www.curseforge.com/minecraft/mc-mods/crafting-on-a-stick) | priority next isolated trial |
| KeyBind Bundles | [1.4.0](https://www.curseforge.com/minecraft/mc-mods/keybind-bundles) | priority next isolated trial |
| KeybindsPurger | [1.4.0](https://www.curseforge.com/minecraft/mc-mods/keybindspurger) | defer: must not silently remove player key preferences |
| FTB Jei Extras | [21.1.7](https://www.curseforge.com/minecraft/mc-mods/ftb-jei-extras) | conditional: audit each category for duplication and EMI display |
| Jumpy Boats | [1.21.0-1.0.5](https://www.curseforge.com/minecraft/mc-mods/jumpy-boats) | optional; compare existing owner first |
| SmithingTemplateViewer | [1.0.4](https://www.curseforge.com/minecraft/mc-mods/smithing-template-viewer) | priority next isolated trial |
| Transfer Labels | [0.1.9](https://www.curseforge.com/minecraft/mc-mods/transfer-labels) | optional; compare existing owner first |
| Akashic Tome | [1.8-30](https://www.curseforge.com/minecraft/mc-mods/akashic-tome) | priority next isolated trial |
| Observable | [5.4.4](https://www.curseforge.com/minecraft/mc-mods/observable) | optional; compare existing owner first |
| Item Collectors | [1.1.10](https://www.curseforge.com/minecraft/mc-mods/item-collectors) | conditional on balance/overlap review |
| Torchmaster | [21.1.9](https://www.curseforge.com/minecraft/mc-mods/torchmaster) | conditional on balance/overlap review |
| Easy Villagers | [1.21.1-1.1.42](https://www.curseforge.com/minecraft/mc-mods/easy-villagers) | conditional on balance/overlap review |
| No Villager Death Messages | [6.0.0](https://www.curseforge.com/minecraft/mc-mods/no-villager-death-messages) | optional; compare existing owner first |
| Charging Gadgets | [1.14.1](https://www.curseforge.com/minecraft/mc-mods/charging-gadgets) | conditional on balance/overlap review |
| Utility vest | [1.3.0](https://www.curseforge.com/minecraft/mc-mods/utility-vest) | conditional on balance/overlap review |
| Step Crafter | [1.21.1-0.1.8](https://www.curseforge.com/minecraft/mc-mods/step-crafter) | conditional on balance/overlap review |
| Shulker Box Tooltip | [5.1.9+1.21.1-neoforge](https://modrinth.com/mod/shulkerboxtooltip/version/IuqNIoAi) | priority next isolated trial |
| Chat Heads | [0.15.7](https://modrinth.com/mod/chat-heads/version/ZPylso9i) | priority next isolated trial |
| BetterF3 | [11.0.3](https://modrinth.com/mod/betterf3/version/maXNB1dn) | optional; compare existing owner first |
| Easy Anvils | [v21.1.0-1.21.1-NeoForge](https://modrinth.com/mod/easy-anvils/version/fSQSKhdF) | conditional on balance/overlap review |
| Easy Magic | [v21.1.4-1.21.1-NeoForge](https://modrinth.com/mod/easy-magic/version/MxbfrOEv) | conditional on balance/overlap review |
| Easy Shulker Boxes | [v21.1.3-1.21.1-NeoForge](https://modrinth.com/mod/easy-shulker-boxes/version/OBp8ltOS) | conditional on balance/overlap review |
| Carry On | [2.2.6](https://modrinth.com/mod/carry-on/version/PV8oLZ1q) | conditional on balance/overlap review |
| Better Third Person | [1.9.0](https://modrinth.com/mod/better-third-person/version/aG5y4JUQ) | optional; compare existing owner first |
| FindMe | [1.21-3.3.3](https://modrinth.com/mod/findme/version/XqFomM19) | priority next isolated trial |
| Inventory Profiles Next | [neoforge-1.21.1-2.2.5](https://modrinth.com/mod/inventory-profiles-next/version/vjuNnHLv) | do not add beside invtweaks; replacement option only |
| Equipment Compare | [1.3.13](https://modrinth.com/mod/equipment-compare/version/efoMHHTh) | priority next isolated trial |
| Legendary Tooltips | [1.5.5](https://modrinth.com/mod/legendary-tooltips/version/BabRJO04) | optional; compare existing owner first |
| Durability Tooltip | [1.2.0-neoforge-mc1.21](https://modrinth.com/mod/durability-tooltip/version/lVrBx0o4) | optional; compare existing owner first |
| Better Ping Display [Forge/NeoForge] | [1.21.1-1.1](https://modrinth.com/mod/better-ping-display/version/AzKubcBR) | priority next isolated trial |
| Cherished Worlds | [10.1.1+1.21.1](https://modrinth.com/mod/cherished-worlds/version/o5lwJaRU) | priority next isolated trial |
| Default Options | [21.1.8+neoforge-1.21.1](https://modrinth.com/mod/default-options/version/1zt17WsC) | optional; compare existing owner first |
| Pick Up Notifier | [v21.1.1-1.21.1-NeoForge](https://modrinth.com/mod/pick-up-notifier/version/5NZounJc) | optional; compare existing owner first |
| Inventory Essentials | [21.1.18+neoforge-1.21.1](https://modrinth.com/mod/inventory-essentials/version/kSYg2mEh) | conditional on balance/overlap review |

## Aceptación siguiente

Para cada tanda: instancia aislada, conflictos de teclas por contexto real, mundo y GUI con escritura de texto, inventario vanilla/mochila/AE2, Tool Belt + zoom + spell radial, escalas GUI 2/3, EN/ES, reingreso sin perder preferencias. En coop: búsqueda/traslado de contenedores dentro y fuera de claims, invitado sin permisos y regalos legítimos. Comprobar HUD/mapa único y categorías de recetas con máquinas reales. Medir rendimiento con el mismo recorrido después de cambios, no convertir cantidad de mods en evidencia.

Sólo se marca una función como verificada cuando ese escenario tiene evidencia de ejecución. Verificación disponible ahora: metadata oficial/JAR, JSON parseable, 49 IDs de función únicos, referencias de candidatos y ausencia de escrituras de catálogo/config. La paridad con FTB permanece parcial hasta disponer de inventario actual; no bloquea avanzar en las mejoras demostrables.
