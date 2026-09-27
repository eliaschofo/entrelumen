# Censo de mods: ATM10, FTB Evolution y Craftoria

27/9/2026. Pedido de Elias: todos los mods de los tres packs, sin duplicados, con el porqué de cada uno, y sumar sólo lo que mejore la experiencia o robustezca el pack. El detalle de cada mod (propósito, motivo, versión fijada, licencia y riesgos) está en [mod-census.json](mod-census.json).

**En una línea:** 958 proyectos únicos → 40 no son mods → 294 ya los tenemos → 6 fuera de los límites → 13 propios de cada pack → 18 herramientas de armado → 58 librerías → 2 ya en curso → **527 juzgados: 31 para sumar, 3 preguntas y 493 descartes**.

## Cómo se hizo

- Uní los tres manifiestos (ATM10 491, FTB Evolution 520 + 3 archivos sueltos, Craftoria 560) y saqué duplicados por proyecto de CurseForge. 207 proyectos están en los tres packs.
- Filtré antes de leer nada más: lo que ya tenemos (también los JAR anidados), la familia All the Mods (ProjectE no aparece en ninguno), lo propio de cada pack, las herramientas de armado y las librerías.
- Recién ahí leí los candidatos: primero los JAR de ATM10, después Modrinth (11 consultas, casi todas en bloque por hash) y CurseForge sólo para 115 que no estaban en Modrinth. Sin bajar JAR ni mandar datos personales.
- Cada candidato se juzgó contra el lock, los límites del catálogo, las rondas con Elias, las familias y la paridad QoL.

## Para sumar, en lotes

El número es la prioridad. Formato: **mod**: qué agrega · *etiqueta* · acto.

### Lote 1 · Robustez y rendimiento

1. **Entity Culling**: no dibuja criaturas ni máquinas ocultas tras paredes; más FPS en bases grandes · *rendimiento* · todos los actos
2. **Structure Layout Optimizer**: genera más rápido las estructuras armadas por piezas (aldeas, mazmorras, ruinas) · *rendimiento* · todos los actos
3. **I'm Fast**: el servidor deja de echar o frenar a quien se mueve rápido · *compat* · todos los actos
4. **Packet Fixer**: sube el límite de datos que viajan al servidor, para que nadie quede afuera por una mochila llena · *compat* · todos los actos
5. **Load My F\*\*\*ing Tags**: una entrada mal escrita en una lista de ítems (tag) ya no rompe la lista entera · *compat* · todos los actos
6. **Crash Assistant**: si el juego crashea, muestra los logs y los sube con un clic para pedir ayuda · *QoL* · todos los actos
7. **Not Enough Recipe Book [NERB]**: saca el libro de recetas vanilla; menos datos por jugador y entradas más rápidas · *rendimiento* · todos los actos
8. **NaNny**: repara la vida rota que dejan algunos mods y que vuelve inmortal a un jugador o lo mata · *compat* · todos los actos
9. **Better Compatibility Checker**: la lista de servidores muestra si el servidor corre la misma versión del pack · *servidor/admin* · todos los actos
10. **Sodium Extra**: más opciones de video para apagar efectos caros en PCs modestas · *rendimiento* · todos los actos
11. **Compact Machines Preview Fixer**: tapa una fuga de memoria al previsualizar salas de Compact Machines · *rendimiento* · acto III
12. **Draconic Evolution Render Patcher**: corrige cómo se dibujan piezas de Draconic Evolution con Sodium · *compat* · actos IV-V

- **Dependencia nueva:** ShatterLib | OctoLib, para Not Enough Recipe Book.
- **Escalonado:** ninguno; son arreglos y optimizaciones sin ítems.
- **Config:** nombre y versión del pack en Better Compatibility Checker y Crash Assistant; lista blanca de Entity Culling si algo nuestro desaparece.
- **Medir:** la generación de mundo antes y después de Structure Layout Optimizer, y los FPS en cliente con Entity Culling y Sodium Extra.
- **Guías:** `guide_qol_client`, `guide_qol_recipes`.

### Lote 2 · Información y comodidad

13. **EMI Loot**: muestra en EMI qué suelta cada criatura, bloque y cofre · *QoL* · acto I
14. **FindMe**: busca un ítem en los cofres cercanos y los marca · *QoL* · acto I
15. **EMI Ores**: muestra en EMI en qué altura y bioma aparece cada mineral · *QoL* · acto I
16. **RightClickHarvest**: cosechar y replantar con clic derecho · *QoL* · acto I
17. **AE2: Crafting Tree**: muestra el árbol completo de un pedido de AE2 y qué patrones faltan · *QoL* · acto III
18. **Bridging Mod**: colocar bloques «hacia afuera» al construir puentes y bordes · *QoL* · acto I
19. **WITS (What Is This Structure?)**: un comando dice en qué estructura estás parado · *QoL* · acto I
20. **Bad Wither No Cookie - Reloaded**: los sonidos globales del Wither y del Dragón se oyen sólo cerca · *QoL* · acto III
21. **Yeetus Experimentus**: quita el aviso de «ajustes experimentales» al crear o abrir un mundo · *QoL* · acto I

- **Dependencias nuevas:** Fzzy Config (EMI Loot), JamLib (RightClickHarvest) y YetAnotherConfigLib (Bridging Mod).
- **Escalonado:** ninguno; es información y comodidad.
- **Config:** EMI Loot oculta las tablas `entrelumen:` (ruinas, Envés, jefes); FindMe y RightClickHarvest tienen que respetar los reclamos de FTB Chunks; tecla de Bridging Mod en el preset.
- **Guías y quests:** `guide_qol_recipes`, `guide_ore_processing`, `guide_qol_inventory`, `guide_farming_resources`, `guide_building_tools`, `guide_structures`, `sector_ae2_automation`.

### Lote 3 · Compat entre sistemas que ya tenemos

22. **Apothic Category Compat**: armas de Cataclysm, Twilight Forest y Forbidden Arcanus entran en las categorías de loot de Apotheosis · *compat* · actos I-V
23. **Apotheosis x Iron's Spellbooks Compat**: el equipo de Iron's Spells recibe afijos y gemas de Apotheosis · *compat* · actos II-IV
24. **Polymorphic Energistics**: deja elegir la salida cuando dos recetas chocan dentro de AE2 · *compat* · acto III
25. **Ad-Astra: Giselle Addon**: mejoras de espacio (respirar, fuego, lluvia ácida, gravedad) para la MekaSuit y otras armaduras, más cargador de combustible y sensor de cohete · *compat* · acto V
26. **Advanced Peripherals**: periféricos para ComputerCraft (puente a AE2 y RS, detectores de jugador, energía y ambiente, chat) · *compat* · actos III-V

- **Sin dependencias nuevas.** Advanced Peripherals pide en CurseForge otra copia de CC:Tweaked; se resuelve en el catálogo declarando que la nuestra sirve, como hace ATM10.
- **Escalonado:** mesa NASA automática de Giselle con el Bus del Arca (V); puente ME de Advanced Peripherals con la Matriz de Enrutamiento (III).
- **Config:** revisar afijos de hechizos frente a los World Tiers; limitar el rango del detector de jugadores; Carry On para los bloques nuevos.
- **Quests:** `sector_irons_spellbooks`, `sector_apotheosis_adventure`, `sector_cataclysm_armory`, `sector_twilight`, `guide_ad_astra`, `guide_computercraft`.

### Lote 4 · Tecnología y redstone

27. **Extended Industrialization**: amplía Modern Industrialization con calderas y paneles solares, energía inalámbrica tesla, máquinas grandes, granjero eléctrico y un encadenador de máquinas · *contenido* · actos II-V
28. **Dyson Cube Project**: una esfera de Dyson; lanzás velas solares a órbita y cobrás su energía con un rayo · *factor X* · actos V-VI
29. **More Red**: compuertas lógicas de redstone y cables de colores o agrupados · *contenido* · actos II-VI
30. **More Red x CC:Tweaked Compat**: deja que ComputerCraft lea y escriba los cables agrupados de More Red · *compat* · actos III-V
31. **Industrialization Overdrive**: constructor automático de multibloques de MI, procesamiento en paralelo, horno de pirólisis y la herramienta Vajra · *contenido* · actos III-V

- **Dependencia nueva:** Tesseract API, para Extended Industrialization e Industrialization Overdrive.
- **Escalonado:** paneles solares LV con Marco de Calibración (II) y HV con Lente Espectral (IV); tesla y lanzador de Dyson con Bus del Arca (V); constructor de multibloques con Regulador de Energía (III); Vajra con Motor de Renovación (V).
- **Config:** Almost Unified y Carry On para los bloques y materiales nuevos; balance de energía de Dyson frente a Powah, reactores y New Age.
- **Quests:** `sector_mi_steam`, `sector_mi_electric`, `sector_mi_endgame`, `guide_energy`, el capítulo del acto V y las misiones de Terra en Solsticio (circuitos rotos con piezas de More Red).

## Preguntas para Elias

1. **Neo Vitae.** Blood Magic no existía para 1.21.1 y quedó afuera. Neo Vitae es su sucesor (altar de sangre, sigilos, una dimensión de demonios) y está en los tres packs. ¿Lo sumamos en los actos III-IV?
2. **Create Aeronautics.** Es el factor X de FTB Evolution: naves y vehículos armados con Create. En la ronda 4 dijiste «transporte: ninguno», y trae un motor de física (Sable) que pesa. ¿Lo reabrimos?
3. **Iris Shaders.** Los tres packs lo traen para quien quiera shaders; apagado, el look sigue vanilla. ¿Lo incluimos?

## Ya en curso

- **FTB Filter System** y **FTB XMod Compat** (están en los tres packs) ya los fija la rama `feature/ftb-filter-system` para las tareas de quests que aceptan varios ítems. No se duplican acá.

## Descartes por motivo (493)

- Relleno o cosmético, sin aporte claro: 192
- Ya lo cubre un mod nuestro: 94
- Addon de algo que no tenemos ni sumamos: 79
- Choca con el diseño o los límites del pack: 78
- Ya se descartó antes (Elias o una familia): 40
- Riesgo técnico sin una necesidad medida: 10

De los juzgados, 117 tienen un equivalente nuestro (el JSON lo nombra) y 410 no tienen nada parecido en el pack.

Casos que conviene saber aunque se descarten:

- **Relics, Super Factory Manager, When Dungeons Arise y Gateways to Eternity** están en los tres packs, pero ya se descartaron con motivo en sus familias.
- **Steam 'n' Rails** (port beta) lo usa FTB Evolution; Elias lo dejó afuera en la ronda 4.
- **XyCraft, Little Big Redstone, Pylons, Shrink, Entangled y Mob Grinding Utils** también están en los tres; no suman algo que no tengamos.
- **C2ME** acelera la generación de chunks pero puede romper el worldgen de mods y las ruinas: queda como experimento aparte si Structure Layout Optimizer no alcanza.
- **Oh The Biomes We've Gone, Terralith, Tectonic y Regions Unexplored**: sin pila de biomas extra.

## Lo que no pude verificar

- Nada se instaló ni se ejecutó: compatibilidad, rendimiento y los arreglos puntuales (fuga de Compact Machines, render de Draconic, aviso experimental) hay que comprobarlos en nuestras versiones.
- Mixins, peso y worldgen sólo se midieron en los JAR de ATM10; para lo que sólo está en FTB o Craftoria vienen de Modrinth o CurseForge, sin abrir el JAR.
- allowModDistribution sólo se conoce para proyectos de ATM10 (metadatos de la instancia); en el resto queda null.
- Los mod IDs de Craftoria salen del snapshot de Crash Assistant del ZIP; 41 entradas del manifiesto no aparecen ahí: 38 son resource packs o shaders y 3 son JAR con otro nombre (EMIffect, Better P2P, Kotlin for Forge).
- Las categorías y descripciones de CurseForge y Modrinth las declara cada autor.
