# Fase final de pruebas

Desde el 29/9, todo lo que pide abrir el juego, un servidor o GameTests queda para el final del árbol de progreso (Elias: «ponemos los tests ingame a lo ultimo de todo el progress tree, una vez este el 99%»). Mientras tanto se integra con validación estática y compilación. Esta es la lista de lo que quedó pendiente, por frente; cada rama nueva que difiera algo lo suma acá.

La fase arranca con la PC libre: un solo controlador, sin otras sesiones ni agentes, con toda la RAM para el servidor y el cliente.

## Orden sugerido

1. Servidor: arranque limpio con el pack entero, sin errores de carga ni de recetas.
2. GameTests pendientes, en una sola corrida guardada (`scratchpad/guarded_gametests.sh` como base).
3. Auditoría de runtime de recetas y balance (`entrelumen_runtime_audit.js` y los `*_balance.js` con su chequeo `loaded-ingredient-check`). La auditoría viene con las líneas por ítem apagadas (eran 6.030 por carga): para esta fase prendé `{"full": true}` en `kubejs/config/entrelumen_audit.json` del servidor o generá el script con `python tools/check_runtime_content.py --sync --full` (README de `pack/kubejs`) y verificá con `--log`. En el log mirá también `[ENTRELUMEN_RECIPE_INDEX]` (`indexed` y `scans: 0`; con `fallback-to-scans` o `indexed-outputs-by-scan`, anotar el motivo) y cualquier `failed-row`: una fila que KubeJS rechazó, con su receta nativa todavía en pie.
4. Cliente: el libro de quests, capítulo por capítulo, y lo visual de cada frente.
5. Rendimiento con el pack entero.
6. Playtest de Elias.
7. Instalación en su cliente, con backup y su sí.

## Mods y catálogo

- Rondas 5 a 7 (Neo Vitae y complementos, Iris, Hardcore Revival, Mystical Agradditions, ME Beam Former, Squat Grow, Industrialization Overdrive, Extended Industrialization y el resto): QA de servidor con `runtime_all.sh` y sus 15 GameTests, y rendimiento.
- Aeronautics: decidir con sus números en mano.
- Dynamic FPS: sin foco (Alt+Tab) baja a 15 FPS y vuelve al recuperar el foco. Con el rastreo de batería apagado, `idle.condition: on_battery` deja inerte el modo inactivo, también en una notebook: tras 6 minutos mirando una fábrica sin tocar nada, F3 no debe mostrar un tope de 10 FPS.
- Mystical Customization: el cultivo del lingote luminoso (nivel 5, supremium) y su receta de esencia.

## Balance y loot

- Datos de mods con derechos reservados reemplazados por stubs y archivos propios: los augments de Eterna de Apotheosis en `entrelumen/tier_augments`, el ritual de la estela eterna en `entrelumen/forbidden_arcanus`, las recetas de tiza de Malum y los stubs apagados. Confirmar que el juego los lee como se leyó en los JAR (la lista está en `docs/design/mod-pingpong.md`).
- Varita del Tiempo de Just Dire Things al 32.º: tope ×8.
- Squat Grow: 5 veces más lento y sin tocar Mystical, AE2 ni los cultivos excluidos.
- Tablas de loot nuevas y alias (`check_loot_tables.py` pasa estático; falta verlas caer).

## El Envés

- Jugarlo entero: pisos, ecos, campeones, santuarios, sellos, bóvedas, la Luz Agria y la bolsa.
- El Grillete Agrio: las marcas flotando en la muñeca con y sin ítem en la mano, el estallido y el 300 % contra jefes.
- Canje de esquirlas con Cenit en Solsticio.

## Sistemas propios

- Jardín de Terra: el motor de 3×2×2 que se arma en un modelo, la lámpara 3D, los cristales de terraluz (4 h, lluvia ×8), las 900 cosechas por segundo y que la lámpara vuelva al romperlo.
- Ruinas de Heliodor (las 11) y Solsticio: verlas generadas.

## Libro de quests (V2)

Nada del libro nuevo se vio todavía en el cliente. Revisar en cada capítulo:
- las fuentes `quest_big` y `quest_icons`, los glifos `[li:…]` y los sprites animados (llamas, Luminosidades, terraluz);
- los `glow` teñidos y el orden en que se dibuja la escena al avanzar;
- las notas con `hover`, que dependen de que FTB las muestre al hacer clic;
- las teclas `[key:…]`, que en las vistas previas salen como `[?]`;
- los ítems con modelo 3D que el renderizador de vistas previas no dibuja (mochila, cabeza de dragón, cajas, estandartes, cabezas de jugador);
- la interferencia `[glitch|…]` de los actos I a IV.

Datos leídos del código que conviene confirmar jugando:
- Neo Vitae: el nivel de orbe de la Tabula Vitae (el código pide el de la receta), los fragmentos de hierro que el mod genera al arrancar, el costo del meteoro y las tareas de mirar la demonita y matar Daemonium.
- Complementos de Modern Industrialization: los pulsos de fertilizante, las unidades del tope de transferencia de los bobinados, la duración de la celda fotovoltaica, los números del Vajra y el enlace del constructor con un punto de acceso inalámbrico.
- Tombstone: que no haya tumba mientras estás caído con Hardcore Revival.

## Chequeos de la revisión adversarial del 1/10

Salen de la revisión adversarial del 1 de octubre (`boot_checks`). Todo cambio de Java de esa revisión se verificó sólo de forma estática: nada de esto corrió todavía. Cada punto dice qué hacer y cuándo pasa.

- Antes de cualquier prueba jugando: compilá el companion con Gradle, corré todos los GameTests y los tests de Python (`test_generate_quests`, `test_sector_book`, `test_quest_book`, `test_family_balance`, `check_guides`, `build_menu_identity --check`). Pasa si todo termina en verde.
- F1: en un servidor dedicado limpio, con una semilla de spawn en bosque y `max-tick-time` en 60000, medí la colocación de la ruina inicial dentro de `ServerStartedEvent`. Pasa si termina muy por debajo de 30 s y el `ServerHangWatchdog` no mata el servidor.
- F4: en un servidor dedicado con `spawn-protection=16` por defecto y un solo op, un jugador sin op reclama la brújula, abre el corazón y hace clic en la puerta del Envés. Pasa si las tres cosas funcionan.
- F5: como jugador A, probá `/ftbchunks claim` junto a la ruina inicial, en Solsticio y dentro de un slot del Envés; como jugador B (sin alianza), hacé clic derecho en el pedestal, el corazón, la puerta, el portal y los comerciantes de Solsticio. Pasa si todos los claims de A se rechazan y B interactúa con todo (`claim_dimension_blacklist` de `ftbchunks-world.snbt` cubre el Envés y Solsticio; la ruina inicial y el portal de la superficie dependen del veto del companion).
- F2: al arrancar el servidor, buscá en el log `Parsing error loading custom advancement productivebees:`. Pasa si no aparece. Con `/advancement grant @s only productivebees:husbandry/bee_cage/catch_any_bee` tiene que marcarse «Bee in a box»; criá una abeja de hierro y confirmá que se dispara la condición `configurable_bee`.
- F7: matá al Ojo de la Tormenta. Pasa si se otorga `deep_aether:brass_dungeon` y se desbloquea «Cover me in sunshine».
- F9 y F10: creá mundos Classic Flat y Snowy Kingdom. Pasa si el origen de la ruina inicial queda en `minBuild` o por encima, con la escalera sellada abajo y una puerta que existe, y si el Taller Sumergido y el Invernadero Cúpula se colocan sin un bucle de reintentos «not placed».
- F28: probá 5 semillas. Pasa si el bioma del Santuario de Twilight nunca cae en un bioma restringido de Twilight Forest.
- F37 y F36: pasa si el sitio de la Fundición del Nether no corta una fortaleza, un bastión ni una estructura de Repurposed, y si las ruinas de superficie dejan 10 bloques libres de margen respecto de las aldeas.
- F59: Taller Sumergido en un bioma nevado con el foso congelado. Pasa si al drenar se quita el hielo y, con la superficie del reservorio de y=14 congelada, abrir una compuerta sigue llevando la rueda hidráulica grande a su RPM nominal.
- F8: en el Envés, pasá a Pacífico. Pasa si los guardianes y campeones no desaparecen y abrir, entrar y encender sellos se rechaza con el mensaje de pacífico; con `/kill` a un guardián generado, reaparece en unos 5 s.
- F19 y F20: en el Envés, un blink de Psi hacia abajo atravesando un piso y un Ars Blink hacia una bóveda tienen que volver al punto de partida. Pasa también si comer chorus sobre una bóveda con puerta sin resolver (`dome_greenhouse`, `nether_foundry`) se rechaza.
- F11: Alt+O y clic derecho agachado sobre Cenit y sobre un nativo. Pasa si se rechazan; un nativo llevado con Carry On o en vagoneta conserva su rol y despierta en su bioma, y un comerciante capturado con la herramienta de encarcelamiento de Industrial Foregoing se desvanece al soltarlo.
- F12 y F24: en un motor de Terra despierto, `nether_star_seeds` no produce estrellas, esencia ni semillas de estrella. Pasa si un altar de Crecimiento junto al motor deja la salida igual que sin altar, y un trigo maduro roto dentro del mismo altar sigue dando x2.
- F13: vara de terraluz. Cosechá con un cable quitado, rebrotá con una segunda vara y restituí el cable. Pasa si el cristal queda en etapa 0, no en 3.
- F54: Time Wand x8 sobre un altar de Renovación. Pasa si spark muestra unos 2 ms/tick, no unos 18, y la varita rechaza altares y bloques del jardín.
- F14 y F39: un jugador solo completa quests, crea un grupo, reclama y se va. Pasa si no queda un segundo claim. El jugador B (acto III) se une a A (acto I): pasa si los hitos se reinician una sola vez, sin parpadeo, y A recibe las recompensas de hitos cuando el grupo los alcanza.
- F22 y F31: un jugador solo con un Arca, un comercio conocido, un intento abierto del Envés y una parcela de Solsticio crea un grupo. Pasa si el Arca sigue activa, el comercio sigue conocido, el jugador sigue dentro del intento y la parcela es del grupo; al disolver, el fundador recupera todo y no se pierde ninguna parcela.
- F25: llevá el Brazo de Terra en una ranura de Curios cuando se desbloquea «Three Blocks Closer». Pasa si se completa en cerca de 1 s y de nuevo tras reconectarte.
- F56: en el acto I, un ítem con afijos muestra su nombre y sus stats reales. Pasa si Alt+F5 abre la pantalla de nivel sin tutorial y sin desconexión.
- F52: pasa si la raíz de la pestaña de logros de Apotheosis muestra «Alt + F5» a través del componente de tecla.
- F35: entrá a un servidor armado con `build_server_pack.py` con el cliente completo de 377 mods. Mantené un Immersive Aircraft en vuelo nivelado más de 10 s. Pasa si no te patea.
- F55: en `debug.log`, en una segunda entrada con el mundo ya caliente, pasa si `Registering recipes: jei:internal` tarda milisegundos, no hay líneas `[JEMI] Collecting data for Block/Item/Fluid Tags` y `Reloaded plugin from jemi` tarda unos 3 s; anotá `Starting JEI took` y `Reloaded EMI in`. Pensá en sacar EMI sólo si la recarga en caliente sigue pasando de unos 2 minutos.
- F62: pasa si el log muestra `Adding complex exclusion` de `emi_loot`, la cantidad de recetas de EMI baja unas 5.000 y los drops de minerales, cultivos y hojas se siguen viendo.
- F68: probá `ingredientDedupe=true` (`pack/config/alltheleaks.json`). Navegá las recetas y máquinas de EMI (`emi_loot`, `emi_ores`), NERB, Create Central Kitchen, Croptopia, Copycats+, LittleTiles, Malum, Psi y Mekanism. Buscá en el log `Cannot set count`, `ATLUnsupportedOperation` y `errors related to Ingredient Dedupe`, y compará el heap tras entrar con true y con false. Pasa, y recién ahí se entrega el archivo, si los logs quedan limpios.
- F69 y F88: tiempo de cuadro durante 30 s caminando, volando en creativo con equipo luminoso y navegando una nave de Aeronautics (LambDynamicLights fancy contra fast, más la cola de chunks de Sodium), y en una zona densa de Create o Aeronautics (Flywheel SMOOTH contra TRI_LINEAR). Pasa si no hay una penalización medible; sólo entonces se cambia, y por defecto se mantienen `fancy` y `SMOOTH`.
- F67: tiempo de cuadro en F3 en `sector_twilight`, `sector_create_kinetics` y `sector_adastra` con el zoom totalmente alejado, contra un capítulo liviano. Se actúa sólo si queda por debajo de unos 60 FPS.
- F64, F65 y F63: spark en el servidor. (a) Ultimine más Vein Resonator rompiendo unos 1.000 bloques: mirá la parte de `CommonHooks.modifyLoot`. (b) Forjá la Llave de Luz mientras se coloca una ruina de acto: mirá el MSPT. (c) Unos 20 jardines de Terra exportando a un controlador de almacenamiento grande después de un reinicio: buscá un pico a 1 Hz. Pasa si ninguno de los tres se nota en el MSPT; si alguno duele, se mide antes de tocar código (no hay override de loot modifiers ni TickGovernor todavía).
- F66: medí `loaded translation tables` al arrancar el servidor, el payload de entrada de un cliente `es_ar` y el congelamiento de `/ftbquests reload`. Se acepta el costo de las siete tablas españolas idénticas salvo que estos números duelan.
- F21 y F46: `/rtp` en el End antes de matar al dragón. Pasa si se rechaza; iniciá un `/rtp` y recibí daño: pasa si la búsqueda se cancela y no se gasta el enfriamiento.
- F38: entrá a Solsticio desde el Nether con la Llave de Luz rota y volvé a casa. Pasa si aparecés junto al portal del Overworld, no dentro.
- F26: un deployer sin dueño impreso por un Schematicannon coloca un módulo del Arca y usa un Atlas. Pasa si no hay excepción y Neruina no reporta nada.
- F70: antes de sincronizar con una instancia de prueba que ya arrancó alguna vez, fusioná a mano tres configs: `commoncapabilities-common.toml` y `integratedtunnels-common.toml` (el pack trae sólo `analytics = false` y `versionChecker = false` bajo `[core.general]`; en la instancia ya existe el archivo completo, así que `sync_pack.py` corta con «Locally changed file needs review») e `integrateddynamics-common.toml` (corta con «TOML update would remove additional keys» porque la instancia tiene más claves). Dejá el archivo de la instancia y sumale esas dos claves, o borrá los tres y dejá que `sync_pack.py` los copie antes del arranque. Pasa si el log no muestra el chequeo de versión de NeoForge ni la analítica de CyclopsCore.
- F85: pasa si el título muestra cinco botones, sin el botón de Realms ni los iconos de notificación de Realms.
- F71: Dynamic FPS. Pasa si sin foco baja a 15 FPS y no hay un tope de 10 FPS tras 6 minutos inactivo.
- F49: controles de la cámara de Exposure. Pasa si no caen a agacharse mientras están sin asignar; si caen, ajustá la quest.

## Instalación en tu cliente

Con todo lo anterior en verde, el sí de Elias y un backup de su instancia.
