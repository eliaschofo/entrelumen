# Plan de cobertura del libro: todos los mods, a la escala de ATM10 (26 de septiembre de 2026)

Pedido de Elias al ver la vista previa de Create · Cinética: «faltan nodos; el libro tiene que igualar o superar a ATM10 en nodos, sin relleno». Este plan reparte **todos los mods del pack** (`catalog/curated.json`, 316 IDs) en cadenas del libro v3, con la cuenta de ATM10 para el mismo mod, la meta nuestra y los hitos que cada cadena tiene que enseñar. Lo escriben los redactores en oleadas de unas seis cadenas, sobre el motor y el estándar de [quest-book-v3](quest-book-v3.md), con la voz de [quest-copy](quest-copy.md).

## Criterio

- **Referencia:** `research/quests/chapter-stats.json` (ATM10 8.1: 66 capítulos, 4.790 quests; FTB Evolution: 2.072; Craftoria: 1.136). De ATM10 se toma sólo la **cuenta** por mod; nada de sus quests, textos ni diseños (AGENTS.md y licencia).
- **Meta por mod:** igual o mayor que ATM10 cuando ATM10 tiene el mod; si no lo tiene, un número razonado con lo que el mod trae en los JAR fijados (ítems, máquinas, jefes, dimensiones, logros; `questbook-v3/facts/mod-sizes.json`).
- **Sin relleno:** cada nodo enseña, habilita, premia o muestra algo real. Nada de «fabricá uno de cada color» ni de chequeos casi repetidos: una colección es **un** nodo con varias tareas. Las escaleras de niveles (Powah, Mystical Agriculture, Productive Bees) van **agrupadas por escalón**, por decisión de Elias del 27/9: un nodo por nivel con varias recetas adentro. Esas tres metas bajan y el total igual supera 4.790 (ver «Totales»).
- **Hechos de los JAR fijados:** recetas, cantidades, rangos y nombres salen de los JAR (`tools/check_guides.py` valida ítems, logros, estructuras, criaturas y teclas; Almost Unified incluido). Las herramientas de investigación de la fase 1 quedan para los redactores: `tools/mod_facts.py` (ítems con nombre EN/ES, logros, recetas por tipo, estructuras y documentación del mod, a un JSON fuera del repo) y `tools/recipe_of.py` (las recetas nativas de cualquier ítem en los JAR fijados; las que cambia el pack están en `pack/kubejs` y los generadores de balance).
- **IDs:** una cadena que reemplaza guías reusa sus claves; el progreso de quien ya las tenía sigue valiendo.

## Cadenas

| Ola | Cadena | Grupo | Acto | Mods | ATM10 | Otras referencias | Hoy | Meta | División | Hitos que tiene que enseñar |
|---|---|---|---|---|---|---|---|---|---|---|
| 0 | Ars Nouveau / Ars Nouveau · Complementos | magia | II-III | `ars_nouveau`, `ars_elemental`, `ars_technica`, `ars_creo`, `ars_ocultas`, `arseng`, `ars_additions`, `ars_controle`, `not_enough_glyphs`, `starbunclemania` | ars_nouveau 130 | FTB Evo 57, Craftoria 91 | 162 | **162** | — | Hecha: 162 quests en dos capítulos. |
| 0 | Create · Cinética / Vías y energía / Complementos | tecnología | I-II | `create`, `createaddition`, `create_new_age`, `create_enchantment_industry`, `create_connected`, `create_dragons_plus`, `copycats`, `createdeco`, `create_hypertube`, `bellsandwhistles`, `sliceanddice`, `create_central_kitchen`, `rechiseledcreate`, `sophisticatedstorageinmotion`, `sophisticatedbackpackscreateintegration`, `sophisticatedstoragecreateintegration` | create 89 | FTB Evo 102, Craftoria 109 | 140 | **140** | — | Hecha: 140 quests en tres capítulos. |
| 1 | Applied Energistics 2 | tecnología | II-IV | `ae2`, `extendedae`, `advanced_ae`, `megacells`, `ae2wtlib`, `merequester`, `ae2importexportcard`, `ae2netanalyser` | applied_energistics_2 74 + extended__advanced_ae 56 | FTB Evo 46, Craftoria 120 | 140 | **140** | Red 75 · Automatización y complementos 65 | Hecha: 140 quests. |
| 1 | Granja · Cocina y cultivos | exploración | I-II | `farmersdelight`, `croptopia`, `pamhc2crops`, `pamhc2trees`, `pamhc2foodcore`, `herbsandharvest`, `sushigocrafting`, `cookingforblockheads`, `farmingforblockheads`, `mynethersdelight`, `ends_delight`, `aethersdelight`, `twilightdelight`, `solcarrot` | food_and_farming 35 | FTB Evo 44 | 120 | **120** | Cocina 60 · Cultivos y árboles 60 | Hecha: 120 quests. |
| 1 | Immersive Engineering | tecnología | I-III | `immersiveengineering` | immersive_engineering 127 | FTB Evo 46 | 130 | **130** | Taller 70 · Industria 60 | Hecha: 130 quests. |
| 1 | Mekanism | tecnología | II-V | `mekanism`, `mekanismgenerators`, `mekanismtools`, `mekanism_extras`, `mekmm`, `mekanismcovers`, `mekanisticrouters`, `appmek`, `refinedstorage_mekanism_integration` | mekanism 90 + mekanism_reactors 94 + generators 47 | FTB Evo 82, Craftoria 54 | 240 | **240** | Básico 75 · Energía y reactores 90 · Logística y equipo 75 | Hecha: 240 quests. |
| 1 | Almacenamiento y mochilas | tecnología | I-III | `sophisticatedbackpacks`, `sophisticatedstorage`, `functionalstorage`, `enderstorage`, `storagedelight`, `trashcans`, `toolbelt` | storage 90 | FTB Evo 36 | 90 | **90** | — | Hecha: 90 quests. |
| 1 | The Twilight Forest | exploración | II-IV | `twilightforest` | twilight_forest 56 | — | 90 | **90** | — | Hecha: 90 quests. |
| 2 | Iron's Spells 'n Spellbooks | magia | II-V | `irons_spellbooks`, `irons_jewelry` | iron_spells_and_spellbooks 103 | FTB Evo 59, Craftoria 92 | 115 | **110** | — | Hecha: 115 quests. |
| 2 | Occultism | magia | II-IV | `occultism` | occultism 64 | FTB Evo 113, Craftoria 66 | 115 | **115** | Rituales 60 · Espíritus y almacenamiento 55 | Hecha: 115 quests. |
| 2 | PneumaticCraft: Repressurized | tecnología | II-IV | `pneumaticcraft` | pneumaticcraft 127 | FTB Evo 36, Craftoria 76 | 130 | **130** | Presión 70 · Drones y armadura 60 | Hecha: 130 quests. |
| 2 | Refined Storage | tecnología | II-III | `refinedstorage` | refined_storage 72 | FTB Evo 56 | 75 | **75** | — | Hecha: 75 quests. |
| 2 | The Aether | exploración | III-IV | `aether`, `deep_aether` | aether 73 | — | 100 | **100** | Aether 70 · Deep Aether 30 | Hecha: 100 quests. |
| 3 | Industrial Foregoing | tecnología | II-IV | `industrialforegoing` | industrial_foregoing 78 | FTB Evo 46 | 80 | **80** | — | Hecha: 80 quests. Las unidades de agujero negro no existen en IF 3.6.39: la cadena lo dice y manda a los cajones de Functional Storage. |
| 3 | Modern Industrialization | tecnología | III-VI | `modern_industrialization` | mi_steam 30 + mi_electric 50 + mi_digital 66 + mi_endgame 25 | FTB Evo 145 | 175 | **175** | Vapor 40 · Eléctrica 55 · Digital 50 · Final 30 | Hecha: 175 quests en cuatro capítulos. |
| 3 | Mystical Agriculture | exploración | II-VI | `mysticalagriculture` | elmystical_agriculturerr 191 | — | 112 | **195** | — | Hecha: 112 quests en dos capítulos, escaleras y familias agrupadas; insanium no existe sin Mystical Agradditions. |
| 3 | Oritech | tecnología | III-V | `oritech` | oritech 56 | FTB Evo 88 | 70 | **70** | — | Hecha: 70 quests. |
| 3 | Powah | tecnología | II-V | `powah` | powah 109 | Craftoria 15 | 56 | **110** | — | Hecha: 56 quests, niveles agrupados. |
| 3 | The Bumblezone | exploración | IV | `the_bumblezone` | bumblezone 114 | — | 119 | **115** | — | Hecha: 119 quests en dos capítulos. |
| 4 | Apotheosis | magia | II-V | `apotheosis`, `apothic_enchanting`, `apothic_spawners` | apothic_enchanting 58 + apotheosis_2 36 + apotheosis_gear 30 | Craftoria 27 | 50 | **125** | Encantamiento 60 · Aventura 40 · Generadores 25 | Eterna, quanta y arcana; estantes hasta el estante dracónico; biblioteca; gemas, afijos y reciclado; niveles de jefe y rareza; modificadores de generadores. |
| 4 | Draconic Evolution | tecnología | IV-VI | `draconicevolution` | draconic_evolution 140 | FTB Evo 41 | 25 | **140** | — | Draconio y el núcleo de fusión; núcleo de energía y pilones; niveles wyvern, dracónico y caótico; herramientas y armaduras modulares; reactor; el Guardián del Caos; dislocadores. |
| 4 | Just Dire Things | tecnología | III-V | `justdirethings` | justdirethings 154 | FTB Evo 89, Craftoria 58 | 20 | **155** | — | Niveles de goo, de ferricore a eclipse alloy; rompedores, colocadores y cliqueadores; generadores; la máquina de paradojas y la varita del tiempo; la pistola de portales; mejoras y habilidades. |
| 4 | L_Ender's Cataclysm | exploración | IV-VI | `cataclysm` | cataclysm 110 | — | 22 | **110** | — | Ocho jefes (Guardián del End, Monstruosidad de Netherita, Ignis, el Heraldo, el Leviatán, el Remanente Antiguo, Maledictus, Scylla); sus estructuras; el equipo de cada uno; witherita y cursium. |
| 4 | Productive Bees | exploración | III-V | `productivebees`, `modularbees` | productive_bees 252 | — | 0 | **255** | — | Nidos y jaulas; colmenas avanzadas, centrífugas y mejoras; árboles de cría por familia; mutación; panales a recursos; manipulación de genes; colmenares de Modular Bees. |
| 5 | Eternal Starlight | exploración | IV-V | `eternal_starlight` | eternal_starlight 193 | — | 21 | **195** | — | Entrar a Starlight; biomas y árboles; sus jefes; plata de pantano, springstone termal y aethersent; el Crest; armas y armaduras. |
| 5 | Forbidden and Arcanus | magia | III-V | `forbidden_arcanus` | forbidden__arcanus 62 | FTB Evo 28 | 19 | **65** | — | Cristal arcano y runas; la forja de Hefesto y sus rituales; potenciadores; horno clibano; polvo Mundabitur; el mazo del herrero y sus modificadores; stella eterna. |
| 5 | Malum | magia | III-V | `malum` | — | FTB Evo 35 | 22 | **70** | — | Cosecha de espíritus con la guadaña; altar y crisol de espíritus; runewood y soulwood; arcanas, éteres y tótems; acero manchado de almas y oro consagrado; el pedestal de rituales; el vacío y el pozo que llora. |
| 5 | Nature's Aura | magia | II-IV | `naturesaura` | natures_aura 25 | — | 16 | **45** | — | El aura y el ojo ambiental; soporte de madera y altar natural; ofrendas; aparatos que gastan aura; hierro infundido y lingotes del cielo. |
| 5 | The Undergarden | exploración | III-IV | `undergarden` | undergarden 82 | — | 21 | **85** | — | Portal del catalizador; cloggrum, froststeel y utherium; el Guardián Olvidado; el Muncher (el Masticador no existe en Undergarden 0.9.6); regalium. |
| 5 | Theurgy | magia | III-V | `theurgy` | theurgy 45 | Craftoria 38 | 18 | **60** | — | Vara de adivinación; brasero piromántico; calcinación, destilación, incubación y fermentación; mercurio y sales; reformación y flujo calórico; emisor de flujo sulfúrico. |
| 6 | Actually Additions | tecnología | II-IV | `actuallyadditions` | — | FTB Evo 46 | 15 | **60** | — | Reconstructor atómico y cristales; empoderador; caras fantasma; relés láser; granjeros y rompedores; café; taladros y mejoras; el folleto. |
| 6 | Ender IO | tecnología | II-V | `enderio` | — | FTB Evo 27 | 15 | **70** | — | Fundidora de aleaciones y aglutinantes; conductos de ítems, fluidos, energía y redstone; molino SAG y bolas de molienda; capacitores y mejoras de máquina; aglutinador de almas y generadores; anclas y bastón de viaje; equipo de acero oscuro. |
| 6 | Integrated Dynamics y Tunnels | tecnología | III-V | `integrateddynamics`, `integratedtunnels`, `integrateddynamicscompat`, `integratedtunnelscompat` | integrated_dynamics 28 | FTB Evo 41 | 15 | **60** | — | Menril y el exprimidor; cables, lectores y escritores; tarjetas de variable y operadores; el programador lógico; importadores y exportadores de Integrated Tunnels; retardadores y proxies. |
| 6 | Redes de logística | tecnología | I-IV | `laserio`, `pipez`, `fluxnetworks`, `rangedpumps`, `itemcollectors`, `energymeter` | basic_logistics 40 | FTB Evo 54 | 55 | **70** | — | Pipez por tipo y mejoras; nodos, tarjetas y filtros de LaserIO; puntos y enchufes de Flux Networks; bombas de rango; recolectores de ítems; medidores de energía. |
| 6 | Modular Routers | tecnología | II-IV | `modularrouters` | modular_router 55 | — | 0 | **55** | — | El router y sus mejoras; módulos emisor, extractor, soltador, colocador, rompedor, extrusor y activador; filtros y regulación; módulos de Mekanistic Routers. |
| 6 | RFTools y XNet | tecnología | III-V | `rftoolsbase`, `rftoolsbuilder`, `rftoolspower`, `rftoolsutility`, `xnet` | — | — | 15 | **55** | — | Marcos de máquina y fragmentos dimensionales; el constructor y las tarjetas de forma; celdas y generadores; pantallas; teletransporte; controladores, conectores y canales de XNet. |
| 7 | Artifacts | exploración | any | `artifacts` | artifacts 50 | Craftoria 71 | 16 | **50** | — | Encontrar artefactos en cofres, mímicos y campamentos; el efecto de cada uno por ranura (cabeza, cuello, cinturón, manos, pies); combinaciones que valen la pena. |
| 7 | EvilCraft | magia | II-IV | `evilcraft`, `evilcraftcompat` | evilcraft 45 | — | 20 | **50** | — | Sangre y el infusor de sangre; gemas oscuras; el acumulador ambiental; espíritus de venganza; la escoba; tanque oscuro y horno espiritual; la Caja del Encierro Eterno. |
| 7 | Hostile Neural Networks | tecnología | III-V | `hostilenetworks` | hostile_neural_networks 50 | — | 0 | **50** | — | Aprendiz profundo y modelos de datos; cámara de simulación y fabricador de botín; niveles de defectuoso a autoconsciente; los modelos que conviene entrenar. |
| 7 | Mahou Tsukai | magia | II-IV | `mahoutsukai` | mahou_tsukai 35 | — | 20 | **40** | — | Círculos de maná y el compendio del conocimiento; hechizos de proyección, refuerzo y alteración; códigos místicos; el final de Unlimited Blade Works. |
| 7 | Psi | magia | III-V | `psi` | — | — | 19 | **40** | — | Ensamblador de CAD y sus partes; programar hechizos con trucos, selectores y operadores; balas y encastres; ébano y marfil; exotrajes. |
| 7 | Reliquary | magia | II-IV | `reliquary` | — | — | 20 | **45** | — | Partes de criaturas y el altar de alcahestría; la pistola y sus cargadores; tomos, pedestales y el boticario; las reliquias únicas (cáliz del emperador, piedra de Midas, medallón del héroe...). |
| 7 | Silent Gear | tecnología | I-V | `silentgear` | silent_gear 69 | — | 20 | **70** | — | Planos y partes; materiales y rasgos; el reciclador; la mesa de herrería; mejoras y gemas; partes de accesorios; materiales tardíos. |
| 8 | Ad Astra | exploración | V-VI | `ad_astra` | — | — | 25 | **60** | — | Niveles de cohete; distribuidor de oxígeno y trajes espaciales; refinado de combustible; la Luna, Marte, Venus, Mercurio y Glacio; desh, ostrum y calorita; la mesa de la NASA. |
| 8 | ComputerCraft | tecnología | III-V | `computercraft` | — | — | 23 | **35** | — | Computadoras, monitores y disqueteras; tortugas y sus mejoras; módems y rednet; computadoras de bolsillo; impresoras; periféricos de otros mods. |
| 8 | Criaturas y jefes | exploración | any | `mowziesmobs`, `friendsandfoes`, `creeperoverhaul`, `endermanoverhaul`, `naturalist`, `dummmmmmy` | — | Craftoria 21 | 41 | **60** | — | Jefes de Mowzie's (Ferrous Wroughtnaut, Frostmaw, Umvuthi, el Escultor) y su equipo; criaturas de Friends&Foes; creepers y endermen por bioma; fauna de Naturalist; el muñeco de práctica. |
| 8 | Mundos profundos | exploración | IV-V | `deeperdarker`, `jamd` | deeper_and_darker 45 | — | 15 | **50** | — | Ciudades antiguas y el Warden; el Otherside; equipo de sculk; los portales de la dimensión minera. |
| 8 | Aparatos y ayudantes | tecnología | I-IV | `mininggadgets`, `buildinggadgets2`, `constructionstick`, `laserbridges`, `simplemagnets`, `ironjetpacks`, `torchmaster`, `utilitarian`, `elevatorid`, `ironfurnaces`, `compactmachines`, `easy_villagers` | — | — | 31 | **70** | — | Aparato minero y mejoras; aparatos de construcción; varas de construcción; puentes láser; imanes; niveles de Iron Jetpacks; megaantorchas; hornos de hierro; Compact Machines; Easy Villagers. |
| 8 | Immersive Aircraft | exploración | III-V | `immersive_aircraft` | — | Craftoria 6 | 18 | **25** | — | Dirigible, dirigible de carga, biplano, girodino, cuadricóptero, buque de guerra y saltamontes de bambú; motores, calderas y mejoras; combustible. |
| 9 | Construcción | calidad de vida | any | `chipped`, `rechiseled`, `rechiseled_chipped`, `mcwbridges`, `mcwdoors`, `mcwfences`, `mcwlights`, `mcwpaths`, `mcwroofs`, `mcwstairs`, `mcwtrpdoors`, `mcwwindows`, `handcrafted`, `another_furniture`, `refurbished_furniture`, `framedblocks`, `littletiles`, `supplementaries`, `amendments`, `glassential`, `simplylight` | building_tips 91 | — | 35 | **100** | Paleta 35 · Muebles 30 · Framed y Little 15 · Supplementaries 20 | Mesas de Chipped y Rechiseled; familias de Macaw's; mods de muebles; FramedBlocks y LittleTiles; Supplementaries y Amendments; vidrios y luces. |
| 9 | Energía, menas y automatización básica | tecnología | I-II | (varios) | basic_power 34 | FTB Evo 51 | 45 | **60** | — | Qué generador conviene cuándo; rutas de duplicado de menas entre mods; los primeros circuitos de automatización. |
| 9 | Granja · Macetas y automatización | exploración | II-IV | `botanypots`, `botanypotstiers`, `botanytrees` | — | — | 16 | **35** | — | Niveles, suelos y tolvas de Botany Pots; árboles en maceta; cultivos de recursos en macetas. |
| 9 | Equipo y armadura | calidad de vida | any | (varios) | basic_armor 128 + basic_tools 77 | FTB Evo 77 | 15 | **80** ⚠ | — | Niveles vanilla hasta netherita; la progresión de armaduras y herramientas del pack en nodos de colección que apuntan al capítulo de cada mod; plantillas de herrería. ATM10 cuenta cada pieza vanilla por separado; acá las armaduras de mods viven en su propia cadena. |
| 9 | Estructuras y exploración | exploración | any | `betterdeserttemples`, `betterdungeons`, `betterendisland`, `betterjungletemples`, `bettermineshafts`, `betterfortresses`, `betteroceanmonuments`, `betterstrongholds`, `betterwitchhuts`, `yungsextras`, `repurposed_structures`, `t_and_t`, `mr_dungeons_andtaverns`, `explorify`, `lootr`, `hellish_trials`, `trenzalore` | — | — | 35 | **70** | — | Una tarea de estructura por familia que valga la visita (YUNG's, Repurposed, Towns and Towers, Dungeons and Taverns, Explorify), lo mejor del Nether y el End, los desafíos; Lootr. |
| 9 | Tombstone | calidad de vida | any | `tombstone` | — | — | 15 | **20** | — | Tumbas y recuperación; el alma y los familiares; tumbas decorativas; ventajas. |
| 9 | Viaje y navegación | exploración | any | `waystones`, `explorerscompass`, `naturescompass`, `fireproofboats`, `jumpboat`, `exposure`, `aquaculture`, `carryon`, `comforts` | — | Craftoria 11 | 32 | **70** | — | Waystones y piedras de teletransporte; brújulas de exploración y de naturaleza; botes; fotografía con Exposure; cañas, anzuelos y peces de Aquaculture; bolsas de dormir y hamacas. |

Capítulos que no entran en las olas (se mantienen, con sus dueños):

| Bloque | Capítulos | Quests hoy | Nota |
|---|---|---|---|
| Story acts I-VI and Solsticio | 8 | 172 | Elias and the story worker; unchanged by this plan. |
| ENTRELUMEN guides | 8 | 138 | Pack mechanics; converted to the sector grammar with the owners of each system. |
| Quality of life | 6 | 105 | Controls, recipes, inventory, teams, world and client; no inflation. |
| Magic overview | 1 | 17 | The map of the magic schools; stays short. |

## Totales

- Cadenas (ola 0 hecha y olas 1 a 8): **5142** quests, de las que **302** ya están en el libro (Create y Ars).
- Historia, ENTRELUMEN, calidad de vida y panorama de magia, como están hoy: **432**.
- **Total planeado: 5574** (ATM10: 4.790; FTB Evolution: 2.072; Craftoria: 1.136). Hoy el libro tiene 1992.
- Si las tres escaleras de niveles (Powah, Mystical Agriculture y Productive Bees, 560 quests) se agruparan a la mitad, el total quedaría en 5294.
- Falta escribir: **4840** quests en 54 cadenas (algunas se parten en varios capítulos, columna «División»). 1258 de ellas ya existen en las guías v2 que esas cadenas reemplazan: se reusan sus claves y se reescriben con el estándar nuevo.

## Esfuerzo

Medido en esta fase: la ampliación de Ars (68 quests nuevas y 14 reubicadas, dos capítulos) llevó una sesión larga de un redactor con la investigación de los JAR incluida; Create · Complementos (6 quests) menos de una hora. Con el motor, las herramientas de hechos y el estándar ya hechos:

- **Una cadena de 50 a 90 quests ≈ una sesión de redactor**: investigación en los JAR (un tercio), texto EN/ES y diseño (la mitad), validación y vista previa (el resto).
- **Una ola de seis cadenas ≈ 450 a 700 quests**, seis redactores en paralelo, uno por cadena, y un revisor que corre `check_guides`, las pruebas y mira las vistas previas.
- **4840 quests en 9 olas.** Cada ola cierra con: generador y pruebas en verde, vista previa de cada capítulo nuevo, revisión de texto con [quest-copy](quest-copy.md) y un arranque de QA (un servidor por vez, 4 GB) con la auditoría KubeJS de los ítems nuevos.

## Olas

- **Ola 0** (hecha, 302 quests): Create · Cinética / Vías y energía / Complementos; Ars Nouveau / Ars Nouveau · Complementos.
- **Ola 1** (6 cadenas, 810 quests): Mekanism; Immersive Engineering; Applied Energistics 2; Almacenamiento y mochilas; Granja · Cocina y cultivos; The Twilight Forest.
- **Ola 2** (5 cadenas, 530 quests): Refined Storage; PneumaticCraft: Repressurized; Occultism; Iron's Spells 'n Spellbooks; The Aether.
- **Ola 3** (6 cadenas, 745 quests): Modern Industrialization; Oritech; Industrial Foregoing; Powah; Mystical Agriculture; The Bumblezone.
- **Ola 4** (5 cadenas, 785 quests): Draconic Evolution; Just Dire Things; Productive Bees; Apotheosis; L_Ender's Cataclysm.
- **Ola 5** (6 cadenas, 520 quests): Eternal Starlight; The Undergarden; Theurgy; Malum; Forbidden and Arcanus; Nature's Aura.
- **Ola 6** (6 cadenas, 370 quests): Ender IO; Actually Additions; RFTools y XNet; Redes de logística; Modular Routers; Integrated Dynamics y Tunnels.
- **Ola 7** (7 cadenas, 345 quests): Artifacts; Hostile Neural Networks; Silent Gear; EvilCraft; Mahou Tsukai; Psi; Reliquary.
- **Ola 8** (6 cadenas, 300 quests): Ad Astra; Immersive Aircraft; ComputerCraft; Aparatos y ayudantes; Mundos profundos; Criaturas y jefes.
- **Ola 9** (7 cadenas, 435 quests): Estructuras y exploración; Viaje y navegación; Construcción; Granja · Macetas y automatización; Equipo y armadura; Tombstone; Energía, menas y automatización básica.

El orden sigue a la historia: primero lo que un jugador toca en los actos I y II (Mekanism, Immersive Engineering, AE2, almacenamiento, cocina, Twilight), después la segunda línea técnica y mágica, y al final las dimensiones tardías, la exploración y la construcción. Cada redactor recibe: la ficha de su cadena de esta tabla, los hechos de su mod (`tools/mod_facts.py`), el estándar de cadena, la guía de texto y los ejemplares de Create y Ars como modelo.

## Qué necesita cada ola del motor

- **Presupuesto de XP.** Hoy el libro reparte 17.654 puntos contra un tope de 18.000 (`test_quest_book.py`). Con 4.800 quests el tope se rompe en la primera ola. Propuesta: pasar de XP por quest a XP por hito y cumbre (los pasos y ramas sin XP, sólo el aviso) o escalar el tope; es una decisión de Elias.
- **Formas y arte.** Las cinco formas propias y las placas de título sirven para todas las cadenas; cada cadena nueva puede pedir su placa de título y su emblema (lista en [quest-book-v3](quest-book-v3.md)).
- **Tamaño del generador.** 97 capítulos generan en segundos; 150 no cambian eso. Las pruebas de contratos ya aceptan cadenas nuevas sin tocar código (rango por defecto de 20 a 90 quests).

## Todos los mods

Cada ID de `catalog/curated.json` y dónde vive. Los que no tienen quests son librerías, herramientas de rendimiento o de servidor, o del cliente, y los cubre, cuando importa, la guía de calidad de vida.

| Mod | Nombre | Lado | Ítems | Cadena |
|---|---|---|---|---|
| `accelerateddecay` | Accelerated Decay | both | 0 | sin quests: rendimiento o servidor |
| `actuallyadditions` | Actually Additions | both | 294 | Actually Additions |
| `ad_astra` | Ad Astra | both | 419 | Ad Astra |
| `advanced_ae` | Advanced AE | both | 68 | Applied Energistics 2 |
| `ae2` | Applied Energistics 2 | both | 378 | Applied Energistics 2 |
| `ae2importexportcard` | AE2 Import Export Card | both | 2 | Applied Energistics 2 |
| `ae2jeiintegration` | AE2 JEI Integration | client | 0 | sin quests: sólo del cliente |
| `ae2netanalyser` | AE2NetworkAnalyzer | both | 2 | Applied Energistics 2 |
| `ae2wtlib` | AE2WTLib | both | 8 | Applied Energistics 2 |
| `aether` | The Aether | both | 612 | The Aether |
| `aethersdelight` | Aethers Delight | both | 75 | Granja · Cocina y cultivos |
| `aiimprovements` | AI-Improvements | both | 0 | sin quests: rendimiento o servidor |
| `akashictome` | Akashic Tome | both | 1 | sin quests: libro, equipos y herramientas (guías de calidad de vida) |
| `alltheleaks` | All The Leaks | both | 0 | sin quests: rendimiento o servidor |
| `almostunified` | AlmostUnified | both | 0 | sin quests: rendimiento o servidor |
| `amendments` | Amendments | both | 59 | Construcción |
| `another_furniture` | Another Furniture | both | 189 | Construcción |
| `apotheosis` | Apotheosis | both | 150 | Apotheosis |
| `apothic_attributes` | Apothic Attributes | both | 0 | sin quests: librería |
| `apothic_enchanting` | Apothic Enchanting | both | 59 | Apotheosis |
| `apothic_spawners` | Apothic Spawners | both | 1 | Apotheosis |
| `appleskin` | AppleSkin | client | 0 | sin quests: sólo del cliente |
| `appmek` | Applied Mekanistics | both | 12 | Mekanism |
| `aquaculture` | Aquaculture 2 | both | 136 | Viaje y navegación |
| `architectury` | Architectury | both | 0 | sin quests: librería |
| `ars_additions` | Ars Additions | both | 103 | Ars Nouveau / Ars Nouveau · Complementos |
| `ars_controle` | Ars Controle | both | 17 | Ars Nouveau / Ars Nouveau · Complementos |
| `ars_creo` | Ars Creo | both | 1 | Ars Nouveau / Ars Nouveau · Complementos |
| `ars_elemental` | Ars Elemental | both | 170 | Ars Nouveau / Ars Nouveau · Complementos |
| `ars_nouveau` | Ars Nouveau | both | 481 | Ars Nouveau / Ars Nouveau · Complementos |
| `ars_ocultas` | Ars Ocultas | both | 1 | Ars Nouveau / Ars Nouveau · Complementos |
| `ars_polymorphia` | Ars Polymorphia | both | 0 | sin quests: compatibilidad sin contenido propio |
| `ars_technica` | Ars Technica | both | 39 | Ars Nouveau / Ars Nouveau · Complementos |
| `arseng` | Ars Énergistique | both | 16 | Ars Nouveau / Ars Nouveau · Complementos |
| `artifacts` | Artifacts | both | 54 | Artifacts |
| `athena` | Athena | both | 0 | sin quests: librería |
| `atlas_api` | Atlas API | both | 0 | sin quests: librería |
| `attributefix` | AttributeFix | both | 0 | sin quests: rendimiento o servidor |
| `balm` | Balm | both | 0 | sin quests: librería |
| `bellsandwhistles` | Create: Bells & Whistles | both | 38 | Create · Cinética / Vías y energía / Complementos |
| `betteradvancedtooltips` | Better Advanced Tooltips | both | 0 | sin quests: libro, equipos y herramientas (guías de calidad de vida) |
| `betteradvancements` | Better Advancements | client | 0 | sin quests: sólo del cliente |
| `betterdeserttemples` | YUNG's Better Desert Temples | both | 0 | Estructuras y exploración |
| `betterdungeons` | YUNG's Better Dungeons | both | 0 | Estructuras y exploración |
| `betterendisland` | YUNG's Better End Island | both | 0 | Estructuras y exploración |
| `betterfortresses` | YUNG's Better Nether Fortresses | both | 0 | Estructuras y exploración |
| `betterjungletemples` | YUNG's Better Jungle Temples | both | 0 | Estructuras y exploración |
| `bettermineshafts` | YUNG's Better Mineshafts | both | 0 | Estructuras y exploración |
| `betteroceanmonuments` | YUNG's Better Ocean Monuments | both | 0 | Estructuras y exploración |
| `betterpingdisplay` | Better Ping Display | client | 0 | sin quests: sólo del cliente |
| `betterstrongholds` | YUNG's Better Strongholds | both | 0 | Estructuras y exploración |
| `betterwitchhuts` | YUNG's Better Witch Huts | both | 0 | Estructuras y exploración |
| `bookshelf` | Bookshelf | both | 0 | sin quests: librería |
| `botanypots` | BotanyPots | both | 183 | Granja · Macetas y automatización |
| `botanypotstiers` | BotanyPotsTiers | both | 552 | Granja · Macetas y automatización |
| `botanytrees` | BotanyTrees | both | 0 | Granja · Macetas y automatización |
| `brandonscore` | Brandon's Core | both | 0 | sin quests: librería |
| `buildinggadgets2` | Building Gadgets 2 | both | 31 | Aparatos y ayudantes |
| `carryon` | Carry On | both | 0 | Viaje y navegación |
| `cataclysm` | L_Ender's Cataclysm 1.21.1 | both | 387 | L_Ender's Cataclysm |
| `chat_heads` | Chat Heads | client | 0 | sin quests: sólo del cliente |
| `cherishedworlds` | Cherished Worlds | client | 0 | sin quests: sólo del cliente |
| `chipped` | Chipped | both | 6993 | Construcción |
| `cleanswing` | Clean Swing | both | 0 | sin quests: rendimiento o servidor |
| `cloth_config` | Cloth Config v15 API | both | 0 | sin quests: librería |
| `clumps` | Clumps | both | 0 | sin quests: rendimiento o servidor |
| `codechickenlib` | CodeChicken Lib | both | 0 | sin quests: librería |
| `colorfulhearts` | Colorful Hearts | client | 0 | sin quests: sólo del cliente |
| `comforts` | Comforts | both | 48 | Viaje y navegación |
| `common_storage_lib` | Common Storage Lib: Core | both | 0 | sin quests: librería |
| `commoncapabilities` | CommonCapabilities | both | 0 | sin quests: librería |
| `compactmachines` | Compact Machines | both | 8 | Aparatos y ayudantes |
| `computercraft` | CC: Tweaked | both | 36 | ComputerCraft |
| `constructionstick` | Construction Sticks | both | 20 | Aparatos y ayudantes |
| `controlling` | Controlling | client | 0 | sin quests: sólo del cliente |
| `cookingforblockheads` | Cooking for Blockheads | both | 148 | Granja · Cocina y cultivos |
| `copycats` | Create: Copycats+ | both | 48 | Create · Cinética / Vías y energía / Complementos |
| `cosmeticarmorreworked` | CosmeticArmorReworked | both | 0 | sin quests: libro, equipos y herramientas (guías de calidad de vida) |
| `crafting_on_a_stick` | Crafting On A Stick | both | 9 | sin quests: libro, equipos y herramientas (guías de calidad de vida) |
| `craftingtweaks` | Crafting Tweaks | both | 0 | sin quests: rendimiento o servidor |
| `create` | Create | both | 993 | Create · Cinética / Vías y energía / Complementos |
| `create_central_kitchen` | Create: Central Kitchen | both | 0 | Create · Cinética / Vías y energía / Complementos |
| `create_connected` | Create: Connected | both | 202 | Create · Cinética / Vías y energía / Complementos |
| `create_dragons_plus` | Create: Dragons Plus | both | 121 | Create · Cinética / Vías y energía / Complementos |
| `create_enchantment_industry` | Create: Enchantment Industry | both | 38 | Create · Cinética / Vías y energía / Complementos |
| `create_hypertube` | Create Hypertube | both | 8 | Create · Cinética / Vías y energía / Complementos |
| `create_new_age` | Create: New Age | both | 59 | Create · Cinética / Vías y energía / Complementos |
| `createaddition` | Create Crafts & Additions | both | 144 | Create · Cinética / Vías y energía / Complementos |
| `createdeco` | Create Deco | both | 403 | Create · Cinética / Vías y energía / Complementos |
| `creativecore` | CreativeCore | both | 0 | sin quests: librería |
| `creeperoverhaul` | Creeper Overhaul | both | 19 | Criaturas y jefes |
| `cristellib` | Cristel Lib | both | 0 | sin quests: librería |
| `croptopia` | Croptopia | both | 505 | Granja · Cocina y cultivos |
| `cucumber` | Cucumber Library | both | 0 | sin quests: librería |
| `curios` | Curios API | both | 0 | sin quests: librería |
| `cyclopscore` | Cyclops Core | both | 1 | sin quests: librería |
| `deep_aether` | Deep Aether | both | 625 | The Aether |
| `deeperdarker` | Deeper and Darker | both | 373 | Mundos profundos |
| `defaultoptions` | Default Options | client | 0 | sin quests: sólo del cliente |
| `deimos` | Deimos | both | 0 | sin quests: librería |
| `draconicevolution` | Draconic Evolution | both | 182 | Draconic Evolution |
| `drippyloadingscreen` | Drippy Loading Screen | client | 0 | sin quests: sólo del cliente |
| `dummmmmmy` | MmmMmmMmmMmm | both | 1 | Criaturas y jefes |
| `easy_villagers` | Easy Villagers | both | 10 | Aparatos y ayudantes |
| `elevatorid` | ElevatorMod | both | 16 | Aparatos y ayudantes |
| `emi` | EMI | client | 3 | sin quests: sólo del cliente |
| `enchdesc` | EnchantmentDescriptions | client | 0 | sin quests: sólo del cliente |
| `enderio` | Ender IO | both | 955 | Ender IO |
| `endermanoverhaul` | Enderman Overhaul | both | 36 | Criaturas y jefes |
| `enderstorage` | EnderStorage | both | 55 | Almacenamiento y mochilas |
| `ends_delight` | End's Delight | both | 64 | Granja · Cocina y cultivos |
| `energymeter` | Energy Meter | both | 4 | Redes de logística |
| `epherolib` | EpheroLib | both | 0 | sin quests: librería |
| `equipmentcompare` | Equipment Compare | client | 0 | sin quests: sólo del cliente |
| `eternal_starlight` | Eternal Starlight | both | 1312 | Eternal Starlight |
| `evilcraft` | EvilCraft | both | 258 | EvilCraft |
| `evilcraftcompat` | EvilCraft-Compat | both | 0 | EvilCraft |
| `explorerscompass` | Explorer's Compass | both | 32 | Viaje y navegación |
| `explorify` | Explorify | both | 0 | Estructuras y exploración |
| `exposure` | Exposure | both | 86 | Viaje y navegación |
| `extendedae` | ExtendedAE | both | 82 | Applied Energistics 2 |
| `extremesoundmuffler` | Extreme Sound Muffler | client | 0 | sin quests: sólo del cliente |
| `fancymenu` | FancyMenu | client | 0 | sin quests: sólo del cliente |
| `farmersdelight` | Farmer's Delight | both | 237 | Granja · Cocina y cultivos |
| `farmingforblockheads` | Farming for Blockheads | both | 11 | Granja · Cocina y cultivos |
| `fastbench` | Fast Workbench | both | 0 | sin quests: rendimiento o servidor |
| `fastfurnace` | FastFurnace | both | 0 | sin quests: rendimiento o servidor |
| `fastsuite` | Fast Suite | both | 0 | sin quests: rendimiento o servidor |
| `ferritecore` | Ferrite Core | both | 0 | sin quests: rendimiento o servidor |
| `fireproofboats` | Fireproof Boats | both | 4 | Viaje y navegación |
| `fluxnetworks` | Flux Networks | both | 11 | Redes de logística |
| `forbidden_arcanus` | Forbidden Arcanus | both | 284 | Forbidden and Arcanus |
| `framedblocks` | FramedBlocks | both | 245 | Construcción |
| `framework` | Framework | both | 0 | sin quests: librería |
| `friendsandfoes` | Friends&Foes | both | 46 | Criaturas y jefes |
| `ftbchunks` | FTB Chunks | both | 0 | sin quests: libro, equipos y herramientas (guías de calidad de vida) |
| `ftblibrary` | FTB Library | both | 3 | sin quests: librería |
| `ftbquests` | FTB Quests | both | 59 | sin quests: libro, equipos y herramientas (guías de calidad de vida) |
| `ftbteams` | FTB Teams | both | 0 | sin quests: libro, equipos y herramientas (guías de calidad de vida) |
| `ftbultimine` | FTB Ultimine | both | 2 | sin quests: libro, equipos y herramientas (guías de calidad de vida) |
| `fuelgoeshere` | Fuel Goes Here | both | 0 | sin quests: rendimiento o servidor |
| `functionalstorage` | Functional Storage | both | 66 | Almacenamiento y mochilas |
| `fusion` | Fusion | both | 0 | sin quests: librería |
| `geckolib` | GeckoLib 4 | both | 0 | sin quests: librería |
| `glassential` | Glassential-renewed | both | 115 | Construcción |
| `glodium` | Glodium | both | 0 | sin quests: librería |
| `guideme` | GuideME | both | 2 | sin quests: librería |
| `handcrafted` | Handcrafted | both | 285 | Construcción |
| `hellish_trials` | Hellish Trials | both | 0 | Estructuras y exploración |
| `herbsandharvest` | Mama's Herbs and Harvest | both | 429 | Granja · Cocina y cultivos |
| `heyberryshutup` | Hey Berry! SHUT UP | both | 0 | sin quests: rendimiento o servidor |
| `hostilenetworks` | Hostile Neural Networks | both | 24 | Hostile Neural Networks |
| `iceberg` | Iceberg | client | 0 | sin quests: sólo del cliente |
| `immediatelyfast` | ImmediatelyFast | client | 0 | sin quests: sólo del cliente |
| `immersive_aircraft` | Immersive Aircraft | both | 40 | Immersive Aircraft |
| `immersiveengineering` | Immersive Engineering | both | 818 | Immersive Engineering |
| `industrialforegoing` | Industrial Foregoing | both | 250 | Industrial Foregoing |
| `integrateddynamics` | IntegratedDynamics | both | 121 | Integrated Dynamics y Tunnels |
| `integrateddynamicscompat` | IntegratedDynamics-Compat | both | 0 | Integrated Dynamics y Tunnels |
| `integratedtunnels` | IntegratedTunnels | both | 22 | Integrated Dynamics y Tunnels |
| `integratedtunnelscompat` | IntegratedTunnels-Compat | both | 0 | Integrated Dynamics y Tunnels |
| `invtweaks` | Inventory Tweaks Refoxed | both | 0 | sin quests: rendimiento o servidor |
| `ironfurnaces` | Iron Furnaces | both | 44 | Aparatos y ayudantes |
| `ironjetpacks` | Iron Jetpacks | both | 9 | Aparatos y ayudantes |
| `irons_jewelry` | Iron's Gems 'n Jewelry | both | 14 | Iron's Spells 'n Spellbooks |
| `irons_lib` | Iron's Lib | both | 30 | sin quests: librería |
| `irons_spellbooks` | Iron's Spells 'n Spellbooks | both | 412 | Iron's Spells 'n Spellbooks |
| `itemcollectors` | Item Collectors | both | 2 | Redes de logística |
| `jade` | Jade | both | 0 | sin quests: libro, equipos y herramientas (guías de calidad de vida) |
| `jadeaddons` | Jade Addons | both | 0 | sin quests: libro, equipos y herramientas (guías de calidad de vida) |
| `jamd` | Just Another Mining Dimension | both | 3 | Mundos profundos |
| `jearchaeology` | Just Enough Archaeology | client | 0 | sin quests: sólo del cliente |
| `jeed` | Just Enough Effects Descriptions | client | 0 | sin quests: sólo del cliente |
| `jei` | Just Enough Items | client | 0 | sin quests: sólo del cliente |
| `jei_mekanism_multiblocks` | Just Enough Mekanism Multiblocks | client | 0 | sin quests: sólo del cliente |
| `journeymap` | Journeymap | client | 0 | sin quests: sólo del cliente |
| `jumpboat` | Jumpy Boats | both | 0 | Viaje y navegación |
| `justdirethings` | Just Dire Things | both | 411 | Just Dire Things |
| `justenoughbreeding` | Just Enough Breeding | client | 0 | sin quests: sólo del cliente |
| `justenoughprofessions` | Just Enough Professions (JEP) | client | 0 | sin quests: sólo del cliente |
| `justzoom` | Just Zoom | client | 0 | sin quests: sólo del cliente |
| `keybindbundles` | KeyBind Bundles | client | 0 | sin quests: sólo del cliente |
| `konkrete` | Konkrete | client | 0 | sin quests: sólo del cliente |
| `kubejs` | KubeJS | both | 1 | sin quests: librería |
| `lambdynlights` | LambDynamicLights | client | 0 | sin quests: sólo del cliente |
| `laserbridges` | Laser Bridges | both | 6 | Aparatos y ayudantes |
| `laserio` | LaserIO | both | 33 | Redes de logística |
| `lionfishapi` | lionfishapi | both | 0 | sin quests: librería |
| `littletiles` | LittleTiles | both | 87 | Construcción |
| `lodestone` | Lodestone | both | 0 | sin quests: librería |
| `lootr` | Lootr | both | 10 | Estructuras y exploración |
| `mahoutsukai` | Mahou Tsukai | both | 174 | Mahou Tsukai |
| `malum` | Malum | both | 690 | Malum |
| `mcjtylib` | McJtyLib | both | 0 | sin quests: librería |
| `mcwbridges` | Macaw's Bridges | both | 152 | Construcción |
| `mcwdoors` | Macaw's Doors | both | 283 | Construcción |
| `mcwfences` | Macaw's Fences and Walls | both | 180 | Construcción |
| `mcwlights` | Macaw's Lights and Lamps | both | 140 | Construcción |
| `mcwpaths` | Macaw's Paths and Pavings | both | 315 | Construcción |
| `mcwroofs` | Macaw's Roofs | both | 607 | Construcción |
| `mcwstairs` | Macaw's Stairs and Balconies | both | 224 | Construcción |
| `mcwtrpdoors` | Macaw's Trapdoors | both | 199 | Construcción |
| `mcwwindows` | Macaw's Windows | both | 326 | Construcción |
| `measurements` | Measurements | both | 1 | sin quests: rendimiento o servidor |
| `megacells` | MEGA Cells | both | 110 | Applied Energistics 2 |
| `mekanism` | Mekanism | both | 422 | Mekanism |
| `mekanism_extras` | MekanismExtras | both | 207 | Mekanism |
| `mekanismcovers` | Mekanism Covers | both | 2 | Mekanism |
| `mekanismgenerators` | Mekanism: Generators | both | 37 | Mekanism |
| `mekanismtools` | Mekanism: Tools | both | 414 | Mekanism |
| `mekanisticrouters` | Mekanistic Routers | both | 4 | Mekanism |
| `mekmm` | Mekanism: MoreMachine | both | 201 | Mekanism |
| `melody` | Melody | client | 0 | sin quests: sólo del cliente |
| `merequester` | ME Requester | both | 3 | Applied Energistics 2 |
| `mininggadgets` | Mining Gadgets | both | 31 | Aparatos y ayudantes |
| `modelfix` | Model Gap Fix | client | 0 | sin quests: sólo del cliente |
| `modern_industrialization` | Modern Industrialization | both | 921 | Modern Industrialization |
| `modernfix` | ModernFix | both | 0 | sin quests: rendimiento o servidor |
| `modonomicon` | Modonomicon | both | 6 | sin quests: librería |
| `modularbees` | ModularBees | both | 32 | Productive Bees |
| `modularrouters` | Modular Routers | both | 58 | Modular Routers |
| `moonlight` | Moonlight Lib | both | 2 | sin quests: librería |
| `moreoverlays` | More Overlays Updated | client | 0 | sin quests: sólo del cliente |
| `mousetweaks` | Mouse Tweaks | client | 0 | sin quests: sólo del cliente |
| `mowziesmobs` | Mowzie's Mobs | both | 144 | Criaturas y jefes |
| `mr_dungeons_andtaverns` | Dungeons and Taverns | both | 0 | Estructuras y exploración |
| `mynethersdelight` | My Nether's Delight | both | 123 | Granja · Cocina y cultivos |
| `mysticalagriculture` | Mystical Agriculture | both | 665 | Mystical Agriculture |
| `naturalist` | Naturalist | both | 212 | Criaturas y jefes |
| `naturesaura` | NaturesAura | both | 159 | Nature's Aura |
| `naturescompass` | Nature's Compass | both | 32 | Viaje y navegación |
| `netherportalfix` | NetherPortalFix | both | 0 | sin quests: rendimiento o servidor |
| `nochatreports` | No Chat Reports | both | 0 | sin quests: rendimiento o servidor |
| `not_enough_glyphs` | Not Enough Glyphs | both | 43 | Ars Nouveau / Ars Nouveau · Complementos |
| `notenoughanimations` | NotEnoughAnimations | client | 0 | sin quests: sólo del cliente |
| `occultism` | Occultism | both | 1301 | Occultism |
| `oritech` | Oritech | both | 335 | Oritech |
| `overloadedarmorbar` | OverloadedArmorBar | client | 0 | sin quests: sólo del cliente |
| `owo` | oωo | both | 0 | sin quests: librería |
| `pamhc2crops` | Pam's HarvestCraft - Crops | both | 319 | Granja · Cocina y cultivos |
| `pamhc2foodcore` | Pam's HarvestCraft - Food Core | both | 209 | Granja · Cocina y cultivos |
| `pamhc2trees` | Pam's HarvestCraft - Trees | both | 158 | Granja · Cocina y cultivos |
| `patchouli` | Patchouli | both | 13 | sin quests: librería |
| `pipez` | Pipez | both | 12 | Redes de logística |
| `placebo` | Placebo | both | 0 | sin quests: librería |
| `playeranimator` | Player Animator | both | 0 | sin quests: librería |
| `pneumaticcraft` | PneumaticCraft: Repressurized | both | 338 | PneumaticCraft: Repressurized |
| `polymorph` | Polymorph | both | 0 | sin quests: rendimiento o servidor |
| `ponderjs` | PonderJS | both | 0 | sin quests: librería |
| `powah` | Powah | both | 153 | Powah |
| `prickle` | PrickleMC | both | 0 | sin quests: librería |
| `productivebees` | Productive Bees | both | 422 | Productive Bees |
| `psi` | Psi | both | 112 | Psi |
| `rangedpumps` | Ranged Pumps | both | 8 | Redes de logística |
| `rebind_narrator` | Rebind Narrator | client | 0 | sin quests: sólo del cliente |
| `rechiseled` | Rechiseled | both | 3628 | Construcción |
| `rechiseled_chipped` | Rechiseled: Chipped | both | 0 | Construcción |
| `rechiseledcreate` | Rechiseled: Create | both | 242 | Create · Cinética / Vías y energía / Complementos |
| `refinedstorage` | Refined Storage | both | 500 | Refined Storage |
| `refinedstorage_emi_integration` | Refined Storage - EMI Integration | client | 0 | sin quests: sólo del cliente |
| `refinedstorage_mekanism_integration` | Refined Storage - Mekanism Integration | both | 18 | Mekanism |
| `refurbished_furniture` | MrCrayfish's Furniture Mod: Refurbished | both | 476 | Construcción |
| `reliquary` | Reliquary Reincarnations | both | 274 | Reliquary |
| `repurposed_structures` | Repurposed Structures | both | 0 | Estructuras y exploración |
| `resourcefulconfig` | Resourcefulconfig | both | 0 | sin quests: librería |
| `resourcefullib` | Resourceful Lib | both | 0 | sin quests: librería |
| `rftoolsbase` | RFToolsBase | both | 24 | RFTools y XNet |
| `rftoolsbuilder` | RFToolsBuilder | both | 43 | RFTools y XNet |
| `rftoolspower` | RFToolsPower | both | 23 | RFTools y XNet |
| `rftoolsutility` | RFToolsUtility | both | 98 | RFTools y XNet |
| `rhino` | Rhino | both | 0 | sin quests: librería |
| `searchables` | Searchables | client | 0 | sin quests: sólo del cliente |
| `shulkerboxtooltip` | ShulkerBoxTooltip | client | 0 | sin quests: sólo del cliente |
| `silentgear` | Silent Gear | both | 400 | Silent Gear |
| `silentlib` | Silent Lib | both | 0 | sin quests: librería |
| `simplebackups` | Simple Backups | both | 0 | sin quests: rendimiento o servidor |
| `simplemagnets` | Simple Magnets | both | 4 | Aparatos y ayudantes |
| `simplylight` | Simply Light | both | 181 | Construcción |
| `sliceanddice` | Create Slice & Dice | both | 5 | Create · Cinética / Vías y energía / Complementos |
| `smartbrainlib` | SmartBrainLib | both | 0 | sin quests: librería |
| `smithingtemplateviewer` | SmithingTemplateViewer | client | 0 | sin quests: sólo del cliente |
| `sodium` | Sodium | client | 0 | sin quests: sólo del cliente |
| `solcarrot` | Spice of Life: Carrot Edition | both | 1 | Granja · Cocina y cultivos |
| `sophisticatedbackpacks` | Sophisticated Backpacks | both | 149 | Almacenamiento y mochilas |
| `sophisticatedbackpackscreateintegration` | Sophisticated Backpacks Create Integration | both | 0 | Create · Cinética / Vías y energía / Complementos |
| `sophisticatedcore` | Sophisticated Core | both | 12 | sin quests: librería |
| `sophisticatedstorage` | Sophisticated Storage | both | 237 | Almacenamiento y mochilas |
| `sophisticatedstoragecreateintegration` | Sophisticated Storage Create Integration | both | 0 | Create · Cinética / Vías y energía / Complementos |
| `sophisticatedstorageinmotion` | Sophisticated Storage In Motion | both | 3 | Create · Cinética / Vías y energía / Complementos |
| `spark` | spark | both | 0 | sin quests: rendimiento o servidor |
| `starbunclemania` | Starbunclemania | both | 41 | Ars Nouveau / Ars Nouveau · Complementos |
| `storagedelight` | Storage Delight | both | 169 | Almacenamiento y mochilas |
| `supermartijn642configlib` | SuperMartijn642's Config Library | both | 0 | sin quests: librería |
| `supermartijn642corelib` | SuperMartijn642's Core Lib | both | 0 | sin quests: librería |
| `supplementaries` | Supplementaries | both | 364 | Construcción |
| `sushigocrafting` | Sushi Go Crafting | both | 77 | Granja · Cocina y cultivos |
| `t_and_t` | Towns and Towers | both | 0 | Estructuras y exploración |
| `the_bumblezone` | The Bumblezone | both | 388 | The Bumblezone |
| `theurgy` | Theurgy | both | 1832 | Theurgy |
| `titanium` | Titanium | both | 8 | sin quests: librería |
| `toastcontrol` | Toast Control | client | 0 | sin quests: sólo del cliente |
| `tombstone` | Corail Tombstone | both | 143 | Tombstone |
| `toolbelt` | Tool Belt | both | 3 | Almacenamiento y mochilas |
| `torchmaster` | Torchmaster | both | 9 | Aparatos y ayudantes |
| `trashcans` | Trash Cans | both | 4 | Almacenamiento y mochilas |
| `trashslot` | TrashSlot | client | 0 | sin quests: sólo del cliente |
| `trenzalore` | Trenzalore | both | 0 | Estructuras y exploración |
| `twilightdelight` | Twilight Flavors & Delight | both | 77 | Granja · Cocina y cultivos |
| `twilightforest` | The Twilight Forest | both | 1153 | The Twilight Forest |
| `undergarden` | The Undergarden | both | 382 | The Undergarden |
| `utilitarian` | Utilitarian | both | 67 | Aparatos y ayudantes |
| `valhelsia_core` | Valhelsia Core | both | 0 | sin quests: librería |
| `waystones` | Waystones | both | 63 | Viaje y navegación |
| `xnet` | XNet | both | 35 | RFTools y XNet |
| `yungsapi` | YUNG's API | both | 0 | sin quests: librería |
| `yungsextras` | YUNG's Extras | both | 0 | Estructuras y exploración |

Tablas generadas por `questbook-v3/tools/coverage_plan.py` (fuera del repo) desde `catalog/curated.json`, el libro generado y `research/quests/chapter-stats.json`.
