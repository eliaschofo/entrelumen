# Auditoría de profundidad por mod (27 de septiembre de 2026)

Regla de Elias del 27/9, escrita como «Profundidad según el mod» en [quest-book-v3](quest-book-v3.md): la cantidad de nodos sigue la complejidad de cada mod, ninguna faceta queda sin explorar y para completar una cadena alcanza el tronco. Esta auditoría mide las 56 cadenas del [plan de cobertura](quest-coverage-plan.md) contra esa regla: el nivel de cada mod, qué facetas enseña el libro, si sólo obliga el tronco y qué cadenas piden más profundidad. Las columnas «Nivel», «Facetas», «Faltan» y «Meta por nivel» del plan salen de acá.

Base: `origin/main` 886f8c1, con 4.818 quests: 61 capítulos de cadena (3.907), 44 guías v2 y la historia; las olas 0 a 6 están escritas. No se tocó contenido.

## En corto

- **Niveles:** 4 mods S (Create, Mekanism, Applied Energistics 2, Modern Industrialization), 17 A, 43 B y 114 C. Se juzgan por cantidad de facetas, cómo se encadenan y cuánto lleva dominarlas, sin mirar la cifra de ATM10. Anclas de Elias: Create y Mekanism son S, Just Dire Things queda muy por debajo de Mekanism (B) e Iron Furnaces es C.
- **Facetas:** las 36 cadenas hechas enseñan 798 de 828 facetas (757 con un nodo propio y 41 sólo en el texto de otro nodo). Mekanism es el estándar: S, 240 quests, 53 de 55 facetas con nodo.
- **El hueco grande es Create.** Es S y tiene 140 quests para 94 facetas: 1,5 quests por faceta, la cadena más finita del libro. Le faltan 17 facetas (contraptions, lógica redstone, esquemas, buceo) y el libro nombra 87 de las 167 escenas de Ponder. Pide unas 110 quests más y dos capítulos nuevos.
- **Después:** Applied Energistics 2 (S, +50), Malum (A, +45), Oritech (A, +40) y Ender IO (A, +19). El resto de la ola es chico (ver «Ola de profundización»).
- **Sobre-profundidad:** Just Dire Things (B) tiene 155 quests, un 55 % sobre el techo de su nivel, por unas 40 ramas de una habilidad cada una; agrupadas por nivel quedaría en ~110 sin perder facetas. Eternal Starlight (195) está en el techo del nivel A y Mystical Agriculture (112) apenas sobre B; ninguna es relleno.
- **Metas del plan que bajan** con la regla: Artifacts 50 → 30, Hostile Neural Networks 50 → 25, Equipo y armadura 80 → 40, Construcción 100 → 75, Estructuras 70 → 50, Viaje 70 → 50. **Suben:** Create 140 → 250, AE2 140 → 190, Malum 70 → 115, Oritech 70 → 110, Ender IO 70 → 95. Sumadas, las metas por nivel dan 5.043 quests en cadenas (el plan: 5.142).
- **Sólo el tronco obliga:** 36 de 61 capítulos obligan al menos un nodo opcional. En 8 son sólo jefes, casi siempre porque el propio mod los exige; en 27, una rama o un consejo del que cuelga un nodo del tronco, que se arregla apuntando la dependencia a otro lado; uno es una elección del jugador. Contradicen la regla de fondo tres casos: las 16 tizas de Occultism en el tronco, la línea de energía de complementos en el tronco de Create · Vías y energía y la cumbre de Cataclysm que pide los ocho jefes.

## Niveles

Un nivel mide la complejidad del mod, no su cantidad de ítems ni la cifra de ATM10:

| Nivel | Qué es | Presupuesto |
|---|---|---|
| S | Columna del pack: muchos sistemas que se encadenan y llevan semanas | 200–400, de 3 a 5 capítulos |
| A | Sistema grande con varias ramas | 100–200, de 2 a 3 capítulos |
| B | Un sistema con variantes | 40–100, de 1 a 2 capítulos |
| C | Mod chico o utilidad | 5–30, dentro de una guía o de un paraguas |

La «Meta por nivel» del plan sale de cinco reglas, en orden:

1. **Facetas que faltan o que sólo se mencionan:** de uno a tres nodos cada una, según el nivel.
2. **Debajo del piso de su nivel y con pocas quests por faceta** frente a sus pares: crece hacia el piso (Create, AE2, Malum, Oritech, Ender IO).
3. **Encima del techo:** se marca y se propone agrupar, nunca cortar lo que no es relleno (Just Dire Things).
4. **Completa y dentro del presupuesto:** se queda como está, aunque quede cerca del piso (Twilight, Modern Industrialization).
5. **Sin escribir:** unas cuatro quests por faceta (tres en un C), más donde una faceta junta varias familias (estructuras, jefes), dentro del presupuesto del nivel.

«Quests por faceta» es un indicador, no una fórmula: la finura de las facetas sigue la del índice de cada mod (Immersive Engineering tiene una por máquina, como su manual; Bumblezone agrupa decenas de logros en doce). Sirve para comparar cadenas del mismo nivel. Una cadena **paraguas** junta varios mods (Granja, Construcción, Viaje): su nivel es el del mod más grande y su meta, la suma de sus mods. Dos cadenas no tienen mod propio (Energía y menas; Equipo y armadura) y figuran como **transversales**.

Nivel de cada mod con facetas propias (los mods de compatibilidad, las librerías y las estructuras sin ítems van con su familia):

- **S** (4): Applied Energistics 2 · Create · Mekanism · Modern Industrialization.
- **A** (17): Apotheosis · Ars Nouveau · Draconic Evolution · Ender IO · Eternal Starlight · Immersive Engineering · Iron's Spells 'n Spellbooks · L_Ender's Cataclysm 1.21.1 · Malum · Mekanism: Generators · Occultism · Oritech · PneumaticCraft: Repressurized · Productive Bees · The Aether · The Bumblezone · The Twilight Forest.
- **B** (43): Actually Additions · Ad Astra · Advanced AE · Apothic Enchanting · Aquaculture 2 · Ars Elemental · CC: Tweaked · Create Crafts & Additions · Create: Connected · Create: Enchantment Industry · Create: New Age · Deep Aether · Deeper and Darker · EvilCraft · ExtendedAE · Farmer's Delight · Forbidden Arcanus · Functional Storage · Industrial Foregoing · IntegratedDynamics · Just Dire Things · LaserIO · MEGA Cells · Mahou Tsukai · Mekanism: MoreMachine · MekanismExtras · Modular Routers · Mowzie's Mobs · Mystical Agriculture · NaturesAura · Powah · Psi · RFToolsBuilder · RFToolsUtility · Refined Storage · Reliquary Reincarnations · Silent Gear · Sophisticated Backpacks · Sophisticated Storage · Supplementaries · The Undergarden · Theurgy · XNet.
- **C** (114): AE2 Import Export Card · AE2NetworkAnalyzer · AE2WTLib · Aethers Delight · Amendments · Another Furniture · Apothic Spawners · Applied Mekanistics · Ars Additions · Ars Controle · Ars Creo · Ars Ocultas · Ars Technica · Ars Énergistique · Artifacts · BotanyPots · BotanyPotsTiers · BotanyTrees · Building Gadgets 2 · Carry On · Chipped · Comforts · Compact Machines · Construction Sticks · Cooking for Blockheads · Corail Tombstone · Create Deco · Create Hypertube · Create Slice & Dice · Create: Bells & Whistles · Create: Central Kitchen · Create: Copycats+ · Create: Dragons Plus · Creeper Overhaul · Croptopia · Dungeons and Taverns · Easy Villagers · ElevatorMod · End's Delight · EnderStorage · Enderman Overhaul · Energy Meter · Explorer's Compass · Explorify · Exposure · Farming for Blockheads · Fireproof Boats · Flux Networks · FramedBlocks · Friends&Foes · Glassential-renewed · Handcrafted · Hellish Trials · Hostile Neural Networks · Immersive Aircraft · IntegratedTunnels · Iron Furnaces · Iron Jetpacks · Iron's Gems 'n Jewelry · Item Collectors · Jumpy Boats · Just Another Mining Dimension · Laser Bridges · LittleTiles · Lootr · ME Requester · Macaw's Bridges · Macaw's Doors · Macaw's Fences and Walls · Macaw's Lights and Lamps · Macaw's Paths and Pavings · Macaw's Roofs · Macaw's Stairs and Balconies · Macaw's Trapdoors · Macaw's Windows · Mama's Herbs and Harvest · Mekanism Covers · Mekanism: Tools · Mekanistic Routers · Mining Gadgets · MmmMmmMmmMmm · ModularBees · MrCrayfish's Furniture Mod: Refurbished · My Nether's Delight · Naturalist · Nature's Compass · Not Enough Glyphs · Pam's HarvestCraft - Crops · Pam's HarvestCraft - Food Core · Pam's HarvestCraft - Trees · Pipez · RFToolsBase · RFToolsPower · Ranged Pumps · Rechiseled · Rechiseled: Chipped · Rechiseled: Create · Refined Storage - Mekanism Integration · Repurposed Structures · Simple Magnets · Simply Light · Sophisticated Storage In Motion · Spice of Life: Carrot Edition · Starbunclemania · Storage Delight · Sushi Go Crafting · Tool Belt · Torchmaster · Towns and Towers · Trash Cans · Twilight Flavors & Delight · Utilitarian · Waystones · YUNG's Better Dungeons.

Por qué los S y los A:

- **Create (S):** 18 etiquetas y 167 escenas de Ponder, 17 tipos de receta propios; cinética, procesado, fluidos, contraptions, lógica, trenes y la red de paquetes de Create 6 se apoyan unos en otros.
- **Mekanism (S):** procesado de menas en cinco escalones, química, fábricas y niveles, energía con turbina, fisión, fusión y SPS, logística, QIO y equipo modular; 97 logros.
- **Applied Energistics 2 (S):** canales, celdas, autocrafting, P2P, subredes, espacial y cuántico; la guía de AE2 tiene 125 páginas, y ExtendedAE, AdvancedAE y MEGA suman 66 más.
- **Modern Industrialization (S):** cuatro eras (vapor, eléctrica, digital, final), 24 tipos de receta propios, multibloques con escotillas, petroquímica, nuclear y fusión.
- **A:** Immersive Engineering (111 entradas de manual), Ars Nouveau (269), Occultism (230), PneumaticCraft (215), Oritech (104), Malum (178 entradas de la Encyclopedia Arcana), Apotheosis (107), Productive Bees, Draconic Evolution, Iron's Spells, Ender IO y Mekanism: Generators; y cinco dimensiones o mods de jefes con varias ramas: The Twilight Forest, The Aether, The Bumblezone, Eternal Starlight y L_Ender's Cataclysm.

Estado de cada cadena (las planeadas se miden sobre lo que el libro enseña hoy en cualquier capítulo):

| Cadena | Nivel | Hoy | Meta del plan | Meta por nivel | Facetas | Con nodo | Quests por faceta |
|---|---|---|---|---|---|---|---|
| Ars Nouveau / Ars Nouveau · Complementos | A | 162 | 162 | 170 | 49/51 | 43 | 3,2 |
| Create · Cinética / Vías y energía / Complementos | S | 140 | 140 | 250 | 77/94 | 65 | 1,5 |
| Applied Energistics 2 | S | 140 | 140 | 190 | 59/62 | 55 | 2,3 |
| Granja · Cocina y cultivos | B, paraguas | 120 | 120 | 120 | 20/21 | 19 | 5,7 |
| Immersive Engineering | A | 130 | 130 | 130 | 49/49 | 46 | 2,7 |
| Mekanism | S | 240 | 240 | 240 | 53/55 | 53 | 4,4 |
| Almacenamiento y mochilas | B, paraguas | 90 | 90 | 90 | 16/16 | 16 | 5,6 |
| The Twilight Forest | A | 90 | 90 | 90 | 19/19 | 19 | 4,7 |
| Iron's Spells 'n Spellbooks | A | 115 | 110 | 120 | 14/15 | 14 | 7,7 |
| Occultism | A | 115 | 115 | 115 | 17/17 | 15 | 6,8 |
| PneumaticCraft: Repressurized | A | 130 | 130 | 130 | 24/24 | 23 | 5,4 |
| Refined Storage | B | 75 | 75 | 75 | 13/13 | 13 | 5,8 |
| The Aether | A | 100 | 100 | 100 | 18/18 | 17 | 5,6 |
| Industrial Foregoing | B | 80 | 80 | 80 | 18/18 | 16 | 4,4 |
| Modern Industrialization | S | 175 | 175 | 175 | 30/30 | 30 | 5,8 |
| Mystical Agriculture | B | 112 | 195 | 112 | 13/13 | 13 | 8,6 |
| Oritech | A | 70 | 70 | 110 | 24/25 | 21 | 2,8 |
| Powah | B | 56 | 110 | 56 | 9/9 | 9 | 6,2 |
| The Bumblezone | A | 119 | 115 | 119 | 12/12 | 12 | 9,9 |
| Apotheosis | A | 125 | 125 | 130 | 15/16 | 14 | 7,8 |
| Draconic Evolution | A | 140 | 140 | 140 | 16/16 | 16 | 8,8 |
| Just Dire Things | B | 155 | 155 | 110 | 18/18 | 18 | 8,6 |
| L_Ender's Cataclysm | A | 115 | 110 | 115 | 16/16 | 16 | 7,2 |
| Productive Bees | A | 131 | 255 | 131 | 14/14 | 14 | 9,4 |
| Eternal Starlight | A | 195 | 195 | 195 | 23/23 | 23 | 8,5 |
| Forbidden and Arcanus | B | 73 | 65 | 73 | 14/14 | 14 | 5,2 |
| Malum | A | 71 | 70 | 115 | 21/23 | 19 | 3,1 |
| Nature's Aura | B | 76 | 45 | 76 | 12/12 | 12 | 6,3 |
| The Undergarden | B | 85 | 85 | 85 | 11/11 | 11 | 7,7 |
| Theurgy | B | 61 | 60 | 61 | 13/13 | 12 | 4,7 |
| Actually Additions | B | 79 | 60 | 79 | 16/16 | 16 | 4,9 |
| Ender IO | A | 76 | 70 | 95 | 22/22 | 22 | 3,5 |
| Integrated Dynamics y Tunnels | B | 64 | 60 | 64 | 13/13 | 13 | 4,9 |
| Redes de logística | B, paraguas | 70 | 70 | 70 | 10/10 | 9 | 7,0 |
| Modular Routers | B | 65 | 55 | 65 | 8/8 | 8 | 8,1 |
| RFTools y XNet | B, paraguas | 67 | 55 | 67 | 22/22 | 21 | 3,0 |
| Artifacts | C | 16 (guía) | 50 | 30 | 7/8 | 5 | — |
| EvilCraft | B | 20 (guía) | 50 | 50 | 10/13 | 8 | — |
| Hostile Neural Networks | C | 16 (guía) | 50 | 25 | 4/5 | 3 | — |
| Mahou Tsukai | B | 20 (guía) | 40 | 45 | 5/11 | 4 | — |
| Psi | B | 19 (guía) | 40 | 45 | 8/9 | 6 | — |
| Reliquary | B | 20 (guía) | 45 | 45 | 7/10 | 7 | — |
| Silent Gear | B | 20 (guía) | 70 | 70 | 7/15 | 6 | — |
| Ad Astra | B | 25 (guía) | 60 | 70 | 12/16 | 6 | — |
| ComputerCraft | B | 23 (guía) | 35 | 35 | 10/10 | 8 | — |
| Criaturas y jefes | B, paraguas | 41 (guía) | 60 | 50 | 10/10 | 6 | — |
| Mundos profundos | B | 15 (guía) | 50 | 45 | 7/7 | 4 | — |
| Aparatos y ayudantes | C, paraguas | 31 (guía) | 70 | 60 | 10/12 | 10 | — |
| Immersive Aircraft | C | 18 (guía) | 25 | 25 | 4/4 | 3 | — |
| Construcción | B, paraguas | 35 (guía) | 100 | 75 | 17/23 | 15 | — |
| Energía, menas y automatización básica | transversal | 45 (guía) | 60 | 50 | 3/3 | 0 | — |
| Granja · Macetas y automatización | C | 8 (guía) | 35 | 20 | 2/3 | 2 | — |
| Equipo y armadura | transversal | 15 (guía) | 80 | 40 | 3/3 | 2 | — |
| Estructuras y exploración | C, paraguas | 35 (guía) | 70 | 50 | 7/7 | 1 | — |
| Tombstone | C | 15 (guía) | 20 | 20 | 4/4 | 3 | — |
| Viaje y navegación | B, paraguas | 32 (guía) | 70 | 50 | 10/14 | 8 | — |

## Ola de profundización

En orden de urgencia: primero el nivel S, después las cadenas A con menos quests por faceta. Las cantidades son orientativas y salen de la meta por nivel.

1. **Create (S): 140 → 250, dos capítulos nuevos y ramas en los tres actuales.**
   - **Capítulo nuevo «Create · Contraptions» (~45):** rodamientos mecánico, de reloj y estabilizado; pistones y postes; pórticos, también en cascada; poleas de soga y ascensores con sus contactos; vagonetas con ensamblador; chasis lineal y radial, pegamento y sticker; actores en movimiento (taladro, sierra, cosechadora, arado, rodillo y desplegador); controles de contraption; interfaces portátiles de almacenamiento y de fluidos; control remoto (linked controller y atril). Hoy todo eso vive en una rama («Things that move») y otra de taladro y sierra.
   - **Capítulo nuevo «Create · Lógica y medición» (~30):** enlaces y palanca analógica; latches y pulsos (repetidor, extensor, temporizador); contacto redstone; observador inteligente e interruptor de umbral; enlace de pantalla, tableros y nixies; caja secuenciada; controlador de velocidad; medidores de velocidad y de estrés; y de Create: Connected, el generador de pulsos secuenciado, los transmisores enlazados y los tableros.
   - **Ramas en Cinética (~15):** buceo y tanque de aire (cobre y netherita), extendo grip, cañón de papas, caja de herramientas y portapapeles, mesa y cañón de esquemas, plano de fabricación, comida de Create (chocolate, té del constructor, bayas glaseadas), reloj cucú y campanas, zinc y las piedras decorativas.
   - **Ramas en Vías y energía (~8):** la red de paquetes a fondo (direcciones del stock ticker, reabastecedores del medidor de fábrica, filtros de paquete, tiendas con manteles) y, en energía, la interfaz portátil y el control por ComputerCraft de Crafts & Additions.
   - **Ramas en Complementos (~12):** infusor y bolsa tejida del End (Enchantment Industry); catalizadores de congelar, lijar, teñir y End, tanque frágil y aliento de dragón (Dragons Plus); puente de inventarios, puerto de acceso y silo (Connected); olla y cortes automáticos (Central Kitchen); la escalera de imanes de New Age con nodo propio.
2. **Applied Energistics 2 (S): 140 → 190, un capítulo nuevo y ramas.**
   - **Capítulo nuevo «AE2 · Complementos» (~60):** ExtendedAE, AdvancedAE, MEGA, AE2WTLib, ME Requester y las tarjetas, como pide el estándar para los complementos. Se mudan con sus claves los 47 nodos de complementos que hoy ocupan casi todo Automatización (65 quests) y se suman ~15: planos activos y de aniquilación inteligente, mejoras de dispositivo en el lugar, celdas infinita y de vacío, celdas MEGA de químicos (con Applied Mekanistics), de fuente (con Ars Énergistique), de almas, de experiencia y radiactivas, monitor de rendimiento y buses precisos, por etiqueta y por mod.
   - **Ramas en Red y Automatización (~30):** Automatización queda con el autocrafting de AE2 (18 nodos) y crece con los montajes de la guía como consejos y ramas: subredes y «caños» de ítems y fluidos con planos, autostock con emisor de nivel o con interfaz, granja de certus semiautomática y automática, prensas e inscriptores automáticos, fabricación recursiva, horno automático, vaciado y llenado de celdas y orden por tipos de almacenamiento.
3. **Malum (A): 71 → 115, un capítulo nuevo.** El actual queda como la columna de espíritus y un capítulo nuevo, «Malum · Ritos y artificio» (~45), abre lo que hoy tiene uno o dos nodos: los 16 ritos del tótem y sus versiones corruptas, las runas de la mesa rúnica, los anillos, collares, cinturones y broches (como colecciones), geas, pactos y juramentos (el sistema nuevo de Malum 1.8, hoy sólo mencionado), la transmutación desencadenada y el pozo que llora, la magia de espejos, vudú y muñecos, y el afinado del crisol.
4. **Oritech (A): 70 → 110, un capítulo nuevo o ramas.** Un segundo capítulo, «Oritech · Núcleo y partículas» (~40), con el reactor nuclear pieza por pieza (barras dobles y cuádruples, absorbedores, reflectores, caños de calor, respiraderos, puerto redstone y manejo del calor), el acelerador de partículas y sus colisiones, los aumentos cibernéticos de las tres estaciones, los explosivos nucleares, el lado arcano (encantador, catalizador, jaula de spawner y refinería corrupta), los cabezales de marco, los drones y los bloques de construcción.
5. **Ender IO (A): 76 → 95, ramas.** Tiene sus 22 facetas con nodo, pero 3,5 quests por faceta en un mod A. Faltan nodos para la estación de granja, los obeliscos que hoy van juntos (inhibidor, reubicación, atracción), el selector de coordenadas y la impresión de ubicación para el viaje, el encendedor de fuego frío, las palancas con retorno y las placas silenciosas.
6. **Ars Nouveau (A): 162 → 170, ramas en Complementos.** Ars Elemental (armaduras por escuela, brazaletes, torretas y prismas elementales), Ars Technica (armaduras de tecnomante, fábrica de bolsillo) y el códice de Ars Additions; en el capítulo base, las formas de hechizo con nodo propio.
7. **Apotheosis (A): 125 → 130, ramas en Aventura.** Jefes, invasores y élites de Apotheosis con nodo propio.
8. **Iron's Spells 'n Spellbooks (A): 115 → 120, una rama.** La dimensión de bolsillo.
9. **Immersive Engineering (A): 130.** Tres facetas que hoy sólo aparecen en texto merecen su nodo (vagonetas de almacenamiento, ingenieros aldeanos con su asalto, shaders): tres ramas, o tareas nuevas en nodos que ya existen.
10. **Mekanism (S): 240.** Dos ramas para el reciclador y el recolector de gases de MoreMachine.

Chicos y sin apuro: Occultism (fuego espiritual y el gólem de iesnio, hoy en texto), Aether (mímicos y centinelas) y Granja · Cocina (canasta y estantes de Farmer's Delight). Modern Industrialization es S pero ya tiene sus 30 facetas con nodo y 5,8 quests por faceta: queda en 175 aunque el piso orientativo del nivel sea 200.

## Sobre-profundidad

No se propone recortar nada que no sea relleno; las marcas son para decidir.

- **Just Dire Things (B): 155 quests para un techo de 100.** En Goo y equipo hay 56 ramas; unas 40 son una mejora de habilidad cada una (Mind Fog, Jump Boost, Step Assist, Ore Miner…). No son relleno, pero son el «un nodo por ítem» que el plan trata como colección. Agruparlas por nivel de material (ferricore, blazegold, celestigem, eclipse), como las escaleras de Powah y Mystical Agriculture, la dejaría en ~110 sin perder facetas. Es decisión de Elias.
- **Eternal Starlight (A): 195 quests, en el techo del nivel.** Tiene 23 facetas, como Twilight (19, 90 quests) y el Aether (18, 100); sus 115 ramas son un arma, una criatura o un logro cada una. Tampoco es relleno; agrupar herramientas y armaduras por material la dejaría cerca de 150.
- **Draconic Evolution (A): 140.** Repite por nivel de equipo (núcleos de nivel 2 a 7 en seis nodos; módulos, armas, escudos y resurrección en wyvern, dracónico y caótico). Dentro del presupuesto A; sólo se marca.
- **Mystical Agriculture (B): 112**, apenas sobre el techo de B, con las familias ya agrupadas el 27/9. Se queda.
- **Metas del plan para cadenas sin escribir** que la regla baja: Artifacts (C, un sistema de accesorios con muchas variantes) 50 → 30, Hostile Neural Networks (C, cinco facetas) 50 → 25, Equipo y armadura 80 → 40 (un nodo por nivel de material y otro por plantilla), Construcción 100 → 75 (21 mods de decoración, de dos a cinco nodos cada uno), Estructuras 70 → 50 y Viaje 70 → 50 (paraguas de mods C), Aparatos 70 → 60, Criaturas y jefes 60 → 50, Mundos profundos 50 → 45, Granja · Macetas 35 → 20.

## Sólo el tronco obliga

FTB completa un capítulo cuando están todas sus quests no opcionales. El motor (`tools/quest_engine.py`) marca opcionales las ramas, consejos, notas, secretos, encargos y jefes, y la entrada si es un checkmark; entrada, pasos, hitos y cumbre obligan. Pero una quest obligatoria que depende de una opcional la vuelve obligatoria. Se recorrieron las dependencias de cada tronco, respetando `min_deps` y `any_dep`.

- **Colecciones en el tronco.** Occultism · Rituales tiene 38 de sus 60 quests en el tronco (63 %): las 16 tizas son pasos en fila hacia la tiza arcoíris de la cumbre, que usa 12. Propuesta: dejar como pasos las tizas que abren un ritual del tronco y juntar el resto en un nodo con varias tareas, o volverlas ramas junto al pentáculo que las usa.
- **Troncos largos por diseño.** Create · Vías y energía tiene 29 de 51 en el tronco (57 %) porque la línea de energía de Crafts & Additions y New Age (laminadora, alambres, motor, calor solar, bobina y energizador) son pasos e hitos: aunque la cumbre no dependa de ella, FTB pide todo paso para completar el capítulo. Propuesta: volverla ramas, o llevarla a Complementos. Modern Industrialization · Digital (54 %) y · Final (57 %) son largos porque en MI cada máquina habilita la siguiente; se aceptan.
- **Jefes que pide la cumbre.** L_Ender's Cataclysm · Arenas pide los ocho jefes y Iron's Spells · Arcana, tres. Es una decisión de diseño, no del mod; propuesta: `min_deps` (por ejemplo, seis de ocho) o dejarlo si el capítulo es justamente «todos los jefes». Los demás jefes obligados los exige el mod (el candado de biomas de Twilight, las llaves del Aether, los drops que forjan el equipo de Cataclysm, Eternal Starlight, Undergarden y Draconic Evolution): se aceptan.
- **Ramas de las que depende un nodo del tronco** (hay que volver a apuntar la dependencia o subir la rama a paso): Create · Complementos (cintas, tanque, cobre y caja de Cinética) y · Vías y energía (el surtidor de Cinética, del que cuelga la entrada), AE2 · Automatización (terminal inalámbrica, CPU profunda y coprocesador), Industrial Foregoing (los generadores miceliales y el de biocombustible antes del reactor, y un addon de rango), JDT · Paradojas (bloque de tiempo, llave, catalizador de portal y la cadena de carbones), Draconic Evolution · Caos (armas caóticas, antes de la cumbre), Granja · Cocina (la cadena del sushi) y · Campos (suelo del Nether), Mystical Agriculture · Esencia (semillas de diamante) y · Almas (semillas de criaturas), Productive Bees · Cría (familia de redstone), Immersive Engineering · Industria (muestra de núcleo y acumulador HV), Undergarden (regalium y Frostfields), PneumaticCraft · Drones (tres), Occultism · Espíritus (taller), Ars Nouveau (tres), Aether (moas), Twilight (cuero curtido y antorchabayas), Apotheosis (biblioteca), Bumblezone (dos), Iron's Spells (escuela de hielo), Integrated Dynamics (uno), Ender IO (el cristal pulsante) y dos de Mekanism que vienen de Básico.
- **Elecciones, no violaciones.** La cumbre de Ars · Complementos pide una de las cuatro escuelas (`any_dep`): obliga una rama, pero la elige el jugador.
- **Consejos en el camino.** Create · Complementos y Ars · Complementos tienen un consejo del que depende el tronco: es un clic, pero conviene sacarlo del camino.

## Índices del mod que nombra cada cadena

Control independiente de las facetas: qué parte del índice del propio mod aparece en la cadena (un ítem de la entrada, la escena o el logro en una tarea, un ícono o el texto). No todas las entradas merecen nodo (los 85 glifos de Ars, los widgets de drones de PneumaticCraft), pero una proporción baja en un mod grande marca el hueco: Create nombra la mitad de su Ponder.

| Cadena | Índices del mod que nombra la cadena |
|---|---|
| Ars Nouveau / Ars Nouveau · Complementos | Ars Nouveau: Patchouli 147/269, logros 35/39; Ars Technica: Ponder 1/1; Ars Ocultas: Patchouli 2/3; Ars Énergistique: GuideME 0/1; Ars Additions: logros 1/2; Starbunclemania: Patchouli 7/15 |
| Create · Cinética / Vías y energía / Complementos | Create: Ponder 87/167, logros 68/102; Create Crafts & Additions: Ponder 5/11; Create: New Age: Ponder 2/8; Create: Enchantment Industry: Ponder 2/8, logros 16/32; Create: Connected: Ponder 1/17, logros 4/9; Create: Dragons Plus: Ponder 1/9; Create Hypertube: Ponder 0/5; Create Slice & Dice: Ponder 1/1; Create: Central Kitchen: Ponder 0/12 |
| Applied Energistics 2 | Applied Energistics 2: GuideME 85/125, logros 24/25; ExtendedAE: GuideME 29/46; Advanced AE: GuideME 10/13; MEGA Cells: GuideME 0/7; AE2WTLib: GuideME 7/7; ME Requester: GuideME 1/1; AE2 Import Export Card: GuideME 1/1; AE2NetworkAnalyzer: GuideME 1/2 |
| Granja · Cocina y cultivos | Farmer's Delight: logros 19/20; Croptopia: Patchouli 32/165, logros 22/28; Sushi Go Crafting: Patchouli 11/12; End's Delight: logros 4/12; Twilight Flavors & Delight: Patchouli 4/11 |
| Immersive Engineering | Immersive Engineering: manual de IE 54/111, logros 55/62 |
| Mekanism | Mekanism: logros 95/97; Mekanism: Generators: logros 4/4; Mekanism: Tools: logros 4/6; MekanismExtras: logros 9/11; Refined Storage - Mekanism Integration: logros 1/1 |
| Almacenamiento y mochilas | Trash Cans: logros 2/2 |
| The Twilight Forest | The Twilight Forest: logros 44/45 |
| Iron's Spells 'n Spellbooks | Iron's Spells 'n Spellbooks: Patchouli 9/9, logros 28/29 |
| Occultism | Occultism: Modonomicon 153/230, logros 76/201 |
| PneumaticCraft: Repressurized | PneumaticCraft: Repressurized: Patchouli 130/215, logros 45/46 |
| Refined Storage | Refined Storage: logros 27/27 |
| The Aether | The Aether: logros 29/30; Deep Aether: logros 5/5 |
| Industrial Foregoing | Industrial Foregoing: Patchouli 64/73 |
| Modern Industrialization | Modern Industrialization: GuideME 32/39, logros 68/71 |
| Mystical Agriculture | Mystical Agriculture: Patchouli 47/64 |
| Oritech | Oritech: Oracle Index 73/104 |
| Powah | Powah: GuideME 25/28 |
| The Bumblezone | The Bumblezone: logros 130/133 |
| Apotheosis | Apotheosis: Patchouli 69/107, logros 5/10; Apothic Enchanting: logros 23/23; Apothic Spawners: logros 13/15 |
| Just Dire Things | Just Dire Things: Patchouli 155/156 |
| L_Ender's Cataclysm | L_Ender's Cataclysm 1.21.1: logros 21/21 |
| Productive Bees | Productive Bees: Patchouli 67/70, logros 22/22; ModularBees: Patchouli 13/15 |
| Eternal Starlight | Eternal Starlight: logros 61/61 |
| Forbidden and Arcanus | Forbidden Arcanus: Ponder 0/2 |
| Nature's Aura | NaturesAura: Patchouli 81/89, logros 25/25 |
| The Undergarden | The Undergarden: logros 35/36 |
| Theurgy | Theurgy: Modonomicon 76/89 |
| Actually Additions | Actually Additions: Patchouli 4/5, logros 17/18 |
| Ender IO | Ender IO: logros 3/3 |
| Integrated Dynamics y Tunnels | IntegratedDynamics: infobook 7/20, logros 39/40; IntegratedTunnels: infobook 4/4, logros 22/22 |
| Redes de logística | LaserIO: Patchouli 34/35; Energy Meter: GuideME 1/2 |
| Modular Routers | Modular Routers: Patchouli 52/55 |
| RFTools y XNet | RFToolsBase: Patchouli 10/11; RFToolsBuilder: Patchouli 16/19; RFToolsPower: Patchouli 11/11; RFToolsUtility: Patchouli 24/24; XNet: Patchouli 8/8 |

## Facetas por cadena

Una faceta es un sistema que un jugador tiene que entender, no un ítem. **Con nodo:** una tarea o el ícono de una quest la enseña. **Sólo mención:** aparece en el texto de otra quest. **Faltan:** ninguna quest la nombra. En las cadenas sin escribir cuenta lo que el libro enseña hoy en cualquier capítulo (los ítems) y el texto de su guía v2.

### Ars Nouveau / Ars Nouveau · Complementos

Nivel A · 162 quests hoy (sector_ars_nouveau, sector_ars_addons) · facetas 49/51 en la cadena · meta 170.

- **Ars Nouveau** (A, 28/28): con nodo: Gemas de fuente, mesa de escriba y libros de hechizos · Glifos de efecto de nivel 1 · Glifos de nivel 2 · Glifos de nivel 3 · Aumentos (amplificar, extender, dividir) · Fuentes de energía (sourcelinks) · Jarras y relés de fuente · Aparato de encantamiento y pedestales · Cámara de imbuimiento · Anillos, amuletos y cinturones · Equipo del encantador (espada, escudo, espejo, caña, guante) · Armaduras de mago y mesa de alteración · Hilos (perks de armadura) · Rituales y brasero · Familiares · Starbuncle (transporte) · Whirlisprig, Drygmy y Wixie (granjas y alquimia) · Gólem de amatista y Alakarkinos · Filtros y varita de dominio · Torretas y prismas · Pociones: jarra, fundidor, difusor y frasco · Adivinación y portales · Almacenamiento: atril y repositorio · Mundo: arcos, fuentebayas, magebloom · Wilden y la Quimera · Bloques mágicos (magelight, ilusión, jarras de luz); **sólo mención:** Formas de hechizo (proyectil, toque, uno mismo, bajo los pies, órbita) · Encantamientos nuevos del aparato.
- **Ars Elemental** (B, 6/7): con nodo: Escuelas y focos elementales · Familiares nuevos (sirena, flashjack, firenando) · Bolsas de lanzador y curios · Marca de maestría; **sólo mención:** Armaduras elementales · Brazaletes y tomos; **faltan:** Torretas, relés y prismas elementales.
- **Ars Technica** (C, 3/4): con nodo: Motor de fuente y mecanismo calibrado · Enfoque y torreta de transmutación; **sólo mención:** Armaduras de tecnomante; **faltan:** Fábrica de bolsillo y herramientas.
- **Ars Additions** (C, 5/5): con nodo: Índices de warp y nexo · Amuletos de protección · Jarra de fuente del End y generador de fuente · Rituales de chunks y estructuras; **sólo mención:** Guía de páginas perdidas del códice.
- **Ars Controle** (C, 1/1): con nodo: Control remoto de hechizos.
- **Not Enough Glyphs** (C, 1/1): con nodo: Glifos e hilos extra.
- **Starbunclemania** (C, 2/2): con nodo: Fluidos con starbuncles (jarras, fuentes) · Mejoras del starbuncle.
- **Ars Creo** (C, 1/1): con nodo: Rueda de starbuncle (Create).
- **Ars Ocultas** (C, 1/1): con nodo: Jarras de espíritu y transmutación.
- **Ars Énergistique** (C, 1/1): con nodo: Fuente en AE2.

### Create · Cinética / Vías y energía / Complementos

Nivel S · 140 quests hoy (sector_create_kinetics, sector_create_logistics, sector_create_addons) · facetas 77/94 en la cadena · meta 250.

- **Create** (S, 42/55): con nodo: Ejes, engranajes y cajas · Manivela y ruedas de agua · Molinos de viento y velas · Motor de vapor y caldera · Estrés, velocidad y medidores · Control de rotación (embrague, cambios, cadenas, controlador de velocidad) · Cintas y depósitos · Eyector con peso · Embudos y túneles · Tolvas (chutes) · Filtros y filtros de atributo · Prensa y compactado · Mezcladora, cuenca y quemador de blaze · Molino y ruedas trituradoras (duplicar menas) · Ventiladores: lavar, ahumar, hornear, embrujar · Desplegador · Ensamblado secuenciado · Ensamblador mecánico · Brazo mecánico · Caños, bombas y válvulas · Surtidor y drenaje de ítems · Pistones y rodamientos · Actores: taladro, cosechadora, arado, rodillo · Interfaz portátil de almacenamiento · Enlaces redstone y palanca analógica · Observador inteligente e interruptor de umbral · Enlace de pantalla, tableros y nixies · Bóveda y tanques · Trenes: vías, estaciones y horarios · Señales y observador de vías · Red de paquetes: empaquetadora, ranas y buzones · Stock: enlace, ticker, solicitante y reempaquetadora · Medidor de fábrica · Cinta de cadenas · Latón, tubos de electrones y cuarzo rosa · Herramientas: llave, extendo grip, cañón de papas · Revestimientos y decoración; **sólo mención:** Sierra mecánica · Polea de manguera y fluidos en contraptions · Chasis, pegamento y sticker · Caja de herramientas y portapapeles · Zinc y piedras nuevas; **faltan:** Caja secuenciada · Rodamiento de reloj y estabilizados · Pórticos · Poleas de soga y ascensores · Contraptions en vagonetas · Controles de contraption · Pulsos y latches · Buceo y tanque de aire · Esquemas y cañón de esquemas · Control remoto · Rarezas: reloj cucú, campanas · Comida de Create · Plano de fabricación.
- **Create Crafts & Additions** (B, 7/8): con nodo: Motor eléctrico y alternador · Laminadora: varillas y alambres · Conectores y cables de energía · Acumulador modular · Bobina Tesla; **sólo mención:** Quemador de blaze líquido y biocombustibles · Interfaz portátil de energía; **faltan:** Control por redstone y ComputerCraft.
- **Create: New Age** (B, 7/7): con nodo: Bobina de generador y escobillas · Energizadores · Motores y extensiones · Cableado · Calor solar, caños de calor y Stirling · Reactor de torio; **sólo mención:** Escalera de imanes.
- **Create: Enchantment Industry** (B, 6/7): con nodo: Experiencia líquida (drenaje, escotilla, farol) · Encantador de blaze y plantillas · Forjador de blaze y superencantamientos · Impresora · Amoladora mecánica · Afijos de Apotheosis con Create; **faltan:** Infusor y bolsa tejida del End.
- **Create: Connected** (B, 6/7): con nodo: Cajas paralelas, puentes cinéticos y conectores · Embragues especiales · Batería cinética · Catalizadores de ventilador; **sólo mención:** Ruedas manivela · Puente de inventarios, puerto de acceso y silo; **faltan:** Pulsos secuenciados, transmisores y tableros.
- **Create: Dragons Plus** (C, 1/2): **sólo mención:** Procesado en lote nuevo (congelar, lijar, teñir, End); **faltan:** Tanque frágil y aliento de dragón.
- **Create: Copycats+** (C, 1/1): con nodo: Bloques copycat.
- **Create Deco** (C, 1/1): con nodo: Decoración industrial.
- **Create Hypertube** (C, 1/1): con nodo: Hypertubos.
- **Create: Bells & Whistles** (C, 1/1): con nodo: Decoración de trenes.
- **Create Slice & Dice** (C, 1/1): con nodo: Rebanadora y rociador.
- **Create: Central Kitchen** (C, 1/1): **sólo mención:** Cocina de Farmer's Delight automatizada.
- **Rechiseled: Create** (C, 1/1): con nodo: Cincel mecánico.
- **Sophisticated Storage In Motion** (C, 1/1): con nodo: Almacenamiento en contraptions.

### Applied Energistics 2

Nivel S · 140 quests hoy (sector_ae2_network, sector_ae2_automation) · facetas 59/62 en la cadena · meta 190.

- **Applied Energistics 2** (S, 34/34): con nodo: Meteoritos y piedra celeste · Cuarzo certus y crecimiento · Cargador y manivela · Fluix, polvo de ender y perla fluix · Inscriptor, prensas y procesadores · Energía de la red · Controlador y canales · Cables, anclas, fachadas y pintura · Celdas, drive y cofre ME · Mesa de celdas y particiones · Terminales y celdas de vista · Buses de importación, exportación y almacenamiento · Interfaz y emisor de nivel · Planos de formación y aniquilación · Tarjetas de mejora · Herramientas de red (llave, network tool, memoria) · Herramientas especiales (entropía, bastón, cañón, cuchillo) · Celdas portátiles · P2P · Puente cuántico · Acceso inalámbrico · IO espacial · Patrones y codificación · Proveedor de patrones y ensamblador molecular · CPU de fabricación · Terminal de acceso a patrones · Condensador, materia y singularidades · IO Port y toggle bus · Monitores de almacenamiento y conversión · Herramientas y armas de cuarzo y fluix · Decoración (vidrio de cuarzo, luminarias, piedra celeste) · Subredes; **sólo mención:** Montajes de ejemplo: autostock con emisor o interfaz · Montajes de ejemplo: granjas de certus y procesadores automáticos.
- **ExtendedAE** (B, 9/11): con nodo: Proveedores e interfaces extendidos · Matriz ensambladora · Drive, cargador, inscriptor y puerto IO extendidos · Cristal entro y ensamblador de cristales · Buses precisos, por etiqueta, por mod y de umbral · Hub y conector inalámbrico · Procesador concurrente · Herramientas de patrones (modificador, buffer de ingredientes, enlatadora); **sólo mención:** Celdas especiales (infinita, vacío); **faltan:** Planos activos y aniquilación inteligente · Mejoras de dispositivo en el lugar.
- **Advanced AE** (B, 7/7): con nodo: Computadora cuántica · Cámara de reacción y aleación cuántica · Patrones avanzados y proveedor avanzado · Buses avanzados (IO, stock, importación-exportación) · Fabricador cuántico y sus terminales · Armadura cuántica y sus tarjetas; **sólo mención:** Monitor de rendimiento.
- **MEGA Cells** (B, 5/6): con nodo: Celdas MEGA (1M a 256M) · Celda a granel y compresión · CPU MEGA y procesador de acumulación · Energía MEGA e interfaz y proveedor MEGA · Aleaciones celestes y cell dock; **faltan:** Celdas de otros tipos (químicos, fuente, alma, experiencia, radiactiva).
- **AE2WTLib** (C, 1/1): con nodo: Terminal universal y tarjetas (imán, puente cuántico).
- **ME Requester** (C, 1/1): con nodo: ME Requester.
- **AE2 Import Export Card** (C, 1/1): con nodo: Tarjetas de importación y exportación.
- **AE2NetworkAnalyzer** (C, 1/1): con nodo: Analizador de red.

### Granja · Cocina y cultivos

Nivel B, paraguas · 120 quests hoy (sector_farm_kitchen, sector_farm_fields) · facetas 20/21 en la cadena · meta 120.

- **Farmer's Delight** (B, 6/7): con nodo: Cuchillos y tabla de cortar · Olla y sartén (cocina con calor) · Suelo rico, compost y abono orgánico · Cultivos silvestres y nuevos (tomate, repollo, arroz, cebolla) · Comidas servidas y festines; **sólo mención:** Nutrición y confort (efectos); **faltan:** Canasta y estantes.
- **Croptopia** (C, 2/2): con nodo: Cultivos y árboles de Croptopia · Utensilios de Croptopia (sartén, prensa, mortero).
- **Pam's HarvestCraft - Crops** (C, 1/1): con nodo: Cultivos de Pam's.
- **Pam's HarvestCraft - Trees** (C, 1/1): con nodo: Frutales de Pam's.
- **Pam's HarvestCraft - Food Core** (C, 1/1): con nodo: Recetas de Pam's (herramientas de cocina).
- **Mama's Herbs and Harvest** (C, 1/1): con nodo: Hierbas y cosechas de Mama's.
- **Sushi Go Crafting** (C, 1/1): con nodo: Sushi: arroz, nori, pescado y enrollador.
- **Cooking for Blockheads** (C, 1/1): con nodo: Cocina multibloque (libro de recetas, heladera, horno).
- **Farming for Blockheads** (C, 1/1): con nodo: Mercado y fertilizantes.
- **My Nether's Delight** (C, 1/1): con nodo: Cocina del Nether.
- **End's Delight** (C, 1/1): con nodo: Cocina del End.
- **Aethers Delight** (C, 1/1): con nodo: Cocina del Aether.
- **Twilight Flavors & Delight** (C, 1/1): con nodo: Cocina del Crepúsculo.
- **Spice of Life: Carrot Edition** (C, 1/1): con nodo: Variedad de dieta (corazones extra).

### Immersive Engineering

Nivel A · 130 quests hoy (sector_immersive_workshop, sector_immersive_industry) · facetas 49/49 en la cadena · meta 130.

- **Immersive Engineering** (A, 49/49): con nodo: Martillo, placas y alambres · Menas y metales nuevos · Cáñamo, tela y madera tratada · Coque, grafito y horno de coque · Horno de ladrillos (aleaciones) · Alto horno y alto horno mejorado (acero) · Mesa de ingeniero y planos · Componentes mecánicos y electrónicos · Dinamo, rueda hidráulica y molinos de viento · Cableado LV/MV/HV y conectores · Relés, pasamuros y transformador de corriente · Capacitores y acumuladores · Interruptores, disyuntores y voltímetro · Generador termoeléctrico · Generador diésel y biodiésel · Pararrayos · Prensa de metal y moldes · Trituradora · Aserradero · Mezcladora · Embotelladora · Fermentador, exprimidor y refinería · Horno de arco y electrodos · Excavadora y taladro de muestras · Ensamblador y mesa de trabajo automática · Cloche y fertilizantes · Calefactor externo, estación de carga, electroimán y plataforma giratoria · Cintas transportadoras y clasificadores · Tolvas y escotillas · Caños, bomba, válvula y clasificador de fluidos · Almacenamiento: cajones, barriles, silo, tanque y estante · Construcción: concreto, andamios, pasarelas y postes · Iluminación y globo · Herramientas: taladro, sierra circular y mejoras · Gancho aéreo (skyhook) y planeador · Mochila de energía y mejoras · Caja de herramientas, bidón, orejeras y kit de mantenimiento · Revólver y munición · Railgun · Lanzaquímicos · Defensa: bobina Tesla, torretas y alambre de púas · Escudo y armaduras (acero, Faraday) · Redstone: cables, celdas de estado, temporizador, sirena y sonda · Circuitos lógicos · Interfaz de máquina y torre de radio · Ciencia rara: Resonanz y lobos autómatas; **sólo mención:** Vagonetas de almacenamiento · Ingenieros aldeanos y asalto · Shaders.

### Mekanism

Nivel S · 240 quests hoy (sector_mekanism_basics, sector_mekanism_energy, sector_mekanism_logistics) · facetas 53/55 en la cadena · meta 240.

- **Mekanism** (S, 37/37): con nodo: Osmio, acero y circuitos · Infusor metalúrgico y aleaciones · Procesado x2: cámara de enriquecimiento y trituradora · Procesado x3: purificación (oxígeno) · Procesado x4: inyección química (cloruro de hidrógeno) · Procesado x5: disolución, lavado y cristalización · Química básica: separador, evaporación, infusor, oxidante · Plástico (HDPE) y sustrato · Fábricas y niveles · Mejoras de máquina · Configurador, tarjeta de configuración y lados · Aserradero, combinador y pintura · Formulaic Assemblicator · Diccionario y oredictionificator · Cables, cubos e inducción · Calor: calentadores y conductores · Caldera termoeléctrica · Fisión y combustible · Residuos, radiación y protección · Plutonio, polonio y SPS · Antimateria y nucleosíntesis · Transportadores y clasificador · Caños y tubos presurizados · Tanques y tanque dinámico · Bomba eléctrica y plenisher · Bins y almacenamiento personal · QIO · Teletransporte · Minero digital y vibrador sísmico · Robit y caja de cartón · Seguridad y red · Láseres y tractor · Equipo: jetpack, scuba, free runners, lanzallamas, arco · Herramienta atómica y Meka-Tool · MekaSuit y módulos · Comida: licuadora nutricional · Estabilizador dimensional (chunks).
- **Mekanism: Generators** (A, 4/4): con nodo: Generadores básicos (calor, solar, viento, bio, gas) · Turbina industrial · Reactor de fisión · Reactor de fusión.
- **Mekanism: Tools** (C, 1/1): con nodo: Herramientas y armaduras por material (paxel).
- **MekanismExtras** (B, 4/4): con nodo: Niveles más allá de Ultimate · Aleaciones y enriquecidos nuevos · Reactor de naquadah · Discos QIO extra.
- **Mekanism: MoreMachine** (B, 3/5): con nodo: Máquinas nuevas: plantado, estampado, prensa, torno y laminadora · Máquinas grandes · Transmisión inalámbrica y carga; **faltan:** Reciclador y chatarra · Recolector de gases del ambiente; desactivado en el pack: Replicadores y materia UU.
- **Applied Mekanistics** (C, 1/1): con nodo: Químicos en AE2 (celdas y P2P).
- **Mekanism Covers** (C, 1/1): con nodo: Tapas para cables.
- **Mekanistic Routers** (C, 1/1): con nodo: Módulos químicos para Modular Routers.
- **Refined Storage - Mekanism Integration** (C, 1/1): con nodo: Químicos en Refined Storage.

### Almacenamiento y mochilas

Nivel B, paraguas · 90 quests hoy (sector_storage) · facetas 16/16 en la cadena · meta 90.

- **Sophisticated Backpacks** (B, 6/6): con nodo: Mochilas por nivel · Mejoras de recolección, filtro y depósito · Mejoras de herramientas (swap, alimentación, imán) · Mejoras de fabricación y hornos · Mejoras de tanque, batería y bomba · Mejoras de compactación, anulación y refrigeración.
- **Sophisticated Storage** (B, 3/3): con nodo: Cofres, barriles y cajones por nivel · Controlador y enlace de almacenamiento · Mejoras de Sophisticated Storage.
- **Functional Storage** (B, 3/3): con nodo: Cajones y controlador · Mejoras de cajones (void, cobre, hierro, recolector) · Llave de configuración y enlazador.
- **EnderStorage** (C, 1/1): con nodo: Cofres y tanques del End.
- **Storage Delight** (C, 1/1): con nodo: Muebles de almacenamiento.
- **Trash Cans** (C, 1/1): con nodo: Tachos de basura.
- **Tool Belt** (C, 1/1): con nodo: Cinturón de herramientas.

### The Twilight Forest

Nivel A · 90 quests hoy (sector_twilight) · facetas 19/19 en la cadena · meta 90.

- **The Twilight Forest** (A, 19/19): con nodo: Portal y entrada · Naga y su patio · Torre del Lich y cetros · Laberinto del Minotauro y Minoshroom · Hidra y pantano de fuego · Caballeros fantasma y caballerometal · Torre oscura y Ur-Ghast · Yeti alfa y glaciar (Reina de las Nieves) · Tierras altas y meseta final · Gigantes y tallo de habichuelas · Mapas mágicos y de menas · Mesa de desencantar (uncrafting) · Árboles mágicos (tiempo, transformación, clasificación, minería) · Equipo: hierro de madera, hojacero, ártico · Amuletos de vida y guardado, cofre de recuerdos · Trofeos y pedestal · Frascos de poción · Ropa de viajero y modificadores · Luz y cosas del bosque (luciérnagas, cigarras, antorchabayas).

### Iron's Spells 'n Spellbooks

Nivel A · 115 quests hoy (sector_irons_spellbooks, sector_irons_arcana) · facetas 14/15 en la cadena · meta 120.

- **Iron's Spells 'n Spellbooks** (A, 13/14): con nodo: Pergaminos, tinta y mesa de inscripción · Libros de hechizos y ranuras · Escuelas de magia y runas · Yunque arcano y orbes de mejora · Afinidad y anillos · Armaduras de escuela · Bastones y armas mágicas · Caldero del alquimista y elixires · Mithril y botín arcano · Estructuras y mapas (fortaleza de evocadores, catacumbas, guarida de araña) · Jefes (Rey Muerto, Tyros, araña de hielo) · Libros únicos · Aprendices y magos (NPC y comercio); **faltan:** Dimensión de bolsillo.
- **Iron's Gems 'n Jewelry** (C, 1/1): con nodo: Joyería (gemas y engarces).

### Occultism

Nivel A · 115 quests hoy (sector_occultism_rituals, sector_occultism_spirits) · facetas 17/17 en la cadena · meta 115.

- **Occultism** (A, 17/17): con nodo: Ver lo oculto: demon's dream y lentes · Tiza y pentáculos · Cuencos de sacrificio y rituales · Libros de atadura y de llamado · Espíritus trabajadores (leñador, transporte, limpieza, granjero) · Espíritus de máquinas y comercio · Mina dimensional y mineros · Almacenamiento mágico · Iesnio y herramientas · Posesión e invocación de criaturas · Familiares · Equipo ritual: satchels, cáliz, brújula, bastón de visión · Varillas de adivinación · Jefes y esencias (afrit, marid) · Otherworld: plantas y madera; **sólo mención:** Fuego espiritual y cristales · Gólem de iesnio y cosas raras.

### PneumaticCraft: Repressurized

Nivel A · 130 quests hoy (sector_pnc_pressure, sector_pnc_drones) · facetas 24/24 en la cadena · meta 130.

- **PneumaticCraft: Repressurized** (A, 24/24): con nodo: Hierro comprimido y explosiones · Compresores de aire · Tubos de presión y módulos · Cámara de presión · Calor: fuentes, disipadores, marcos y vortex · Petróleo y refinería · Planta termoneumática y plástico · Placas de circuito: UV, tanque de grabado y ensamblaje · Mezclador de fluidos y renovables (biodiésel, etanol) · Generador neumático y energía · Mejoras (velocidad, volumen, seguridad, etc.) · Drones: programador y piezas · Drones especializados (recolector, cosechador, guardia, logística) · Controlador programable · Logística: marcos y configurador · Armadura neumática y sus mejoras · Armas y herramientas (minigun, jackhammer, vortex cannon, micromisiles) · Seguridad y torretas · Ascensores, puertas y cañón de aire · Sensores y GPS · Aldeanos mecánicos y Amadron · Generador de spawners y trampas · Almacenamiento: cofre reforzado, inteligente, tolvas; **sólo mención:** Integración con computadoras.

### Refined Storage

Nivel B · 75 quests hoy (sector_rs) · facetas 13/13 en la cadena · meta 75.

- **Refined Storage** (B, 13/13): con nodo: Controlador y cables · Discos y drives · Bloques de almacenamiento · Grillas (normal, crafteo, patrones) · Importadores y exportadores · Constructor y destructor · Detector y monitor · Almacenamiento externo e interfaz · Autocrafteo: patrones, autocrafter y gestor · Inalámbrico y portátil · Relés y seguridad · Fluidos · Mejoras.

### The Aether

Nivel A · 100 quests hoy (sector_aether, sector_aether_deep) · facetas 18/18 en la cadena · meta 100.

- **The Aether** (A, 13/13): con nodo: Portal y dimensión · Madera skyroot, holystone y herramientas · Zanite y gravitita · Ambrosium, altar e incubadora · Accesorios (capas, guantes, anillos, pendientes) · Moas y montura · Fauna: aerbunny, phyg, flying cow, sheepuff, aerwhale · Mazmorra de bronce (Slider) · Mazmorra de plata (Reina Valquiria) · Mazmorra de oro (Espíritu del Sol) · Armas y armaduras (valquiria, neptuno, fénix, obsidiana) · Nubes, paracaídas y dardos; **sólo mención:** Mímicos, centinelas y trampas.
- **Deep Aether** (B, 5/5): con nodo: Biomas y maderas de Deep Aether · Skyjade y stratus · Ojo de la tormenta (jefe) · Quails, venomites y fauna · Brass dungeon y sus premios.

### Industrial Foregoing

Nivel B · 80 quests hoy (sector_if) · facetas 18/18 en la cadena · meta 80.

- **Industrial Foregoing** (B, 18/18): con nodo: Látex, extractor y plástico · Marcos de máquina · Cámara de disolución · Agricultura: sembrador, fertilizante, recolector, hidroponía · Crianza: ganadero, alimentador, separador · Mobs: matadero, trituradora, duplicador · Generadores: biorreactor, biocombustible, pitiful · Reactor micelial (generadores miceliales) · Taladro láser (menas y fluidos) · Producción: fábrica de piedra, lavado, refinador de lodo · Rompedor, colocador y colector · Cinta transportadora y transportadores · Herramientas infinitas · Encantamientos: extractor, aplicador, fábrica · Fisher marino, condensador y tamiz · Cámara de estasis y detector; **sólo mención:** Mejoras y addons de máquina · Almacenamiento de agujero negro.

### Modern Industrialization

Nivel S · 175 quests hoy (sector_mi_steam, sector_mi_electric, sector_mi_digital, sector_mi_endgame) · facetas 30/30 en la cadena · meta 175.

- **Modern Industrialization** (S, 30/30): con nodo: Bronce, calderas y máquinas a vapor · Horno de coque y ladrillos refractarios · Martillo de forja y herramientas · Caños de ítems y fluidos (configuración) · Tanques y barriles · Acero y alto horno a vapor · Bombas de agua y agua infinita · Canteras de vapor y eléctrica · Electricidad: cables, tiers y transformadores · Turbinas de vapor y generadores diésel · Calderas grandes y alta presión · Máquinas eléctricas · Circuitos (analógico, electrónico, digital, procesador, cuántico) · Ensamblador y componentes · Alto horno eléctrico y bobinas · Multibloques: hatches y cascos · Mejoras y overclock · Petróleo: taladro, destilería y torre de destilación · Química: reactor, electrolizador, centrífuga · Craqueo, diésel y combustibles mejorados · Polímeros y caucho (polietileno, PVC, caucho sintético) · Acero inoxidable y titanio · Compresor de implosión e intercambiador de calor · Congelador de vacío y aire líquido · Reactor nuclear · Fusión y plasma · Nivel cuántico, replicador y singularidades · Equipo: jetpack diésel, motosierra, gravichestplate, armadura cuántica · Almacenamiento portátil y cofre configurable · Tarjeta de configuración y redstone.

### Mystical Agriculture

Nivel B · 112 quests hoy (sector_ma_essence, sector_ma_souls) · facetas 13/13 en la cadena · meta 112.

- **Mystical Agriculture** (B, 13/13): con nodo: Inferium, prosperidad y semillas base · Niveles de esencia (prudentium a insanium) · Altar de infusión y pedestales · Cristal de infusión y cristal maestro · Tierra de esencia, fertilizantes y aceleradores · Regaderas · Semillas de recursos (familias) · Almas: frascos, daga de soulium y extractor · Semillas de criaturas · Despertar: altar, esencias elementales, supremium despertado · Máquinas: horno, cosechadora, reprocesador, spawner de soulium · Mesa de ajuste, aumentos y equipo de esencia · Hoz, guadaña y cápsula de experiencia.

### Oritech

Nivel A · 70 quests hoy (sector_oritech) · facetas 24/25 en la cadena · meta 110.

- **Oritech** (A, 24/25): con nodo: Pulverizador y moledora · Centrífuga y fluidos · Fundición y horno de aleaciones · Ensamblador y componentes (motor, bobina, unidad de proceso) · Núcleos de máquina y addons · Generación: básica, lava, bio, combustible, vapor, solar · Almacenamiento de energía y caños · Postes de energía y compuertas de flujo · Fluxita, compuesto endérico y cristal sobrecargado · Láser endérico y nodos de recursos · Refinería, petróleo y turbocombustible · Marcos y pórticos (destructor, colocador, fertilizador) · Forja atómica y fragmentos · Acelerador de partículas y colisionador · Reactor nuclear: controlador, barras, calor y puertos · Extractor de lecho de roca y minería profunda · Equipo: exotraje, jetpacks, taladro, motosierra, maza · Aumentos cibernéticos (estaciones de aumento) · Arcano: encantador estabilizado, catalizador, jaula de spawner · Drones y puerto de drones · Materiales tardíos (duratium, prometheum, adamant, energita); **sólo mención:** Níquel, platino y menas nuevas · Biomasa, silicio y plástico · Bloques de construcción industriales; **faltan:** Explosivos nucleares.

### Powah

Nivel B · 56 quests hoy (sector_powah) · facetas 9/9 en la cadena · meta 56.

- **Powah** (B, 9/9): con nodo: Orbe energizante y energizado · Generadores: furnator, magmator, termo, solar · Reactor de uraninita · Celdas y cables de energía · Ender cells y ender gates · Transmisor de jugador, tolva y descargador · Escalera de niveles (starter a nitro) · Baterías, tarjetas de enlace y lente del End · Hielo seco y bola de nieve cargada.

### The Bumblezone

Nivel A · 119 quests hoy (sector_bumblezone_hive, sector_bumblezone_court) · facetas 12/12 en la cadena · meta 119.

- **The Bumblezone** (A, 12/12): con nodo: Entrar a la Bumblezone · Abejas, panales y miel · Reina abeja y comercio · Deseos de la Reina (misiones) · Esencias y pruebas · Equipo de abeja (armadura, lanza, stinger) · Cristal cósmico (jefe) y santuario sempiterno · Estructuras (Honey cave, Sempiternal) · Velas y pociones de abeja · Flores y plantas · Pegajoso, miel de abeja y bloques · Criaturas (beehemoth, honey slime, arañas).

### Apotheosis

Nivel A · 125 quests hoy (sector_apotheosis_enchanting, sector_apotheosis_adventure, sector_apotheosis_spawners) · facetas 15/16 en la cadena · meta 130.

- **Apotheosis** (A, 8/9): con nodo: Afijos, rarezas y botín · Reforja, salvamento y aumento · Gemas, engarces y corte de gemas · Niveles del mundo (Haven a Pinnacle) · Spawners rebeldes y mazmorras · Amuletos de poción · Sigilos (malicia, renacer, retiro, mejora); **sólo mención:** Mobs élite e invasores; **faltan:** Jefes de Apotheosis.
- **Apothic Enchanting** (B, 5/5): con nodo: Eterna, quanta y arcana · Estanterías hasta la dracónica · Infusión de encantamientos · Biblioteca de encantamientos · Tomos y filtros.
- **Apothic Spawners** (C, 2/2): con nodo: Captura de spawners · Modificadores de spawners.

### Draconic Evolution

Nivel A · 140 quests hoy (sector_draconic_wyvern, sector_draconic_chaos) · facetas 16/16 en la cadena · meta 140.

- **Draconic Evolution** (A, 16/16): con nodo: Draconio: menas, polvo y lingotes · Núcleo de fusión e inyectores · Núcleos (draconio, wyvern, despierto, caótico) · Núcleo de energía y estabilizadores · Cristales de energía inalámbrica · Controladores de energía y compuertas · Herramientas modulares (wyvern, dracónico, caótico) · Armadura modular (pechera) y módulos · Capacitores · Dislocadores · Máquinas: generador, grinder, spawner estabilizado, desencantador · Manipulador celestial, sensores y detectores · Corazón de dragón y draconio despertado · Guardián del Caos e islas · Reactor dracónico · Bastones de poder.

### Just Dire Things

Nivel B · 155 quests hoy (sector_jdt_goo, sector_jdt_paradox) · facetas 18/18 en la cadena · meta 110.

- **Just Dire Things** (B, 18/18): con nodo: Goo: tipos y cómo se esparce · Suelos de goo · Ferricore · Blazegold y brasa de blaze · Celestigema y voidshimmer · Aleación de eclipse · Herramientas y armaduras por nivel · Mejoras y habilidades · Varitas (blazejet, voidshift, eclipsegate, polimórfica) · Máquinas simples (rompedor, colocador, clicker, sensor, dropper) · Máquinas avanzadas · Fluidos: colector y colocador · Generadores y combustibles refinados · Portales: fluido y pistola · Tiempo: cristales, fluido y varita · Máquina de paradojas · Utilidades: capturador, copiador, llave, canisters · Sostenedores de experiencia e inventario.

### L_Ender's Cataclysm

Nivel A · 115 quests hoy (sector_cataclysm_arenas, sector_cataclysm_armory) · facetas 16/16 en la cadena · meta 115.

- **L_Ender's Cataclysm 1.21.1** (A, 16/16): con nodo: Ojos y altares (encontrar cada arena) · Remanente Antiguo y pirámide · Maledictus y prisión helada · Leviatán y ciudad hundida · Scylla y la acrópolis · Heraldo y fábrica antigua · Ignis y arena ardiente · Monstruosidad de netherita y forja de almas · Guardián del End y ciudadela · Witherita y acero negro · Cursium, ignitium y metal antiguo · Yunque de fusión mecánica y forja infernal · Armas de jefe · Armaduras de jefe · Accesorios (amuletos, anillos, cinturones) · Minijefes y criaturas de las arenas.

### Productive Bees

Nivel A · 266 quests hoy (sector_bees_apiary, sector_bees_breeding, sector_bees_metals, sector_bees_crown) · facetas 14/14 en la cadena · una quest por abeja que se consigue jugando (189 de 197), 28/9.

- **Productive Bees** (A, 12/12): con nodo: Nidos, jaulas y atrapar abejas · Colmenas avanzadas y cajas de expansión · Centrífuga y centrífuga eléctrica · Panales y recursos · Mejoras de colmena · Cría y mutaciones · Genes: indexador, extractor y tratamientos · Incubadora, embotelladora y alimentación · Abejas solitarias y silvestres · Abejas profesionales (colectora, granjera, cantera) · Generador de miel y catcher · Colmena del huevo de dragón y abejas raras.
- **ModularBees** (C, 2/2): con nodo: Colmena modular (núcleo, alvéolo, hatch) · Centrífuga modular.

### Eternal Starlight

Nivel A · 195 quests hoy (sector_starlight_night, sector_starlight_metals, sector_starlight_crest) · facetas 23/23 en la cadena · meta 195.

- **Eternal Starlight** (A, 23/23): con nodo: Ojo buscador, orbe de profecía y portal · Biomas de Starlight (bosque, pantano oscuro, desierto de cristal, permafrost, abismo) · Guardián (Gatekeeper) y su prueba · Gólem de Starlight y la forja · Monstruosidad Lunar · Permafrost y bosque de ceniza · Tangled, Hatred y cráneos · Lluvia de meteoros y aethersent · Amaramber (torreya) · Plata del pantano (deepsilver) y pungencia · Springstone termal · Glacita · Tioquarzo y cristales de Starlight · Flowglaze y fuego estelar · Unrealium y diamante estelar · Horno de aleaciones y cristales de maná · Éter (fluido) y sus reglas · Accesorios y colgantes (combinar con equipo) · Armas únicas (guadañas, espada de energía, daga del hambre, martillo) · Gólems invocables (aethersent, grimstone) · Criaturas y mascotas (polilla, pájaro de fuego estelar, stranghoul) · Cultivos y comida (crinoa, mijo, frutas) · Catalizador cristalino, redstone y transmisores.

### Forbidden and Arcanus

Nivel B · 73 quests hoy (sector_forbidden_arcanus) · facetas 14/14 en la cadena · meta 73.

- **Forbidden Arcanus** (B, 14/14): con nodo: Cristal arcano y polvo · Forja de Hefesto y rituales · Potenciadores de la forja y reliquias · Horno clibano · Deorum, obsidiansteel y darkstone · Mazos de herrero y modificadores · Polvo Mundabitur y transformaciones · Edelwood y cubos · Almas: extractor, alma encantada y cristal de atadura · Stella arcanum y stella eterna · Capturadores cuánticos y de jefes · Armaduras y armas dracónicas (Draco Arcanus, Tyr, Mortem) · Calaveras de obsidiana y botellas aureales · Prismas y cosas mágicas (varita mágica, orbe petrificado).

### Malum

Nivel A · 71 quests hoy (sector_malum) · facetas 21/23 en la cadena · meta 115.

- **Malum** (A, 21/23): con nodo: Piedra de alma y guadaña (cosecha de espíritus) · Arcanas: primaria, elemental, eldritch · Madera rúnica y de alma · Altar de espíritus e infusión · Acero manchado de alma y oro consagrado · Crisol de espíritus y enfoque · Tótems y ritos · Runas y mesa rúnica · Anillos, collares, cinturones y broches · Armaduras (manchada de alma, cazador de almas, fortaleza maligna) · Éter y antorchas · Jarras de espíritu y bolsas · Obeliscos y pilones (reparación, arcana) · Nodos de metal e impetus alquímicos · Pozo que llora y vacío · Tizón (blight) · Artificio: dispositivo, diseño completo, conciencia fusionada · Bastones y armas (hex staff, tyrving, guadaña) · Brasero de atadura de almas; **sólo mención:** Transmutación desencadenada · Geas y pactos; **faltan:** Ritos corruptos · Magia de espejos, vudú y muñecos.

### Nature's Aura

Nivel B · 76 quests hoy (sector_naturesaura) · facetas 12/12 en la cadena · meta 76.

- **NaturesAura** (B, 12/12): con nodo: Aura: concepto, ojo ambiental y medición · Soporte de madera y ritual del bosque · Altar natural e infusión · Ofrendas a los dioses · Botellas de aura y flores de aura · Generadores de aura (absorbedor, rosa, reaper...) · Consumidores de aura (aparatos) · Desequilibrio de aura y sus efectos · Lingotes del cielo y de profundidad · Herramientas y armaduras (botanista, cielo, profundidad) · Bastones, tokens y colgantes · Dispositivos mecánicos (distribuidor, tolva adepta, carro de atracción).

### The Undergarden

Nivel B · 85 quests hoy (sector_undergarden) · facetas 11/11 en la cadena · meta 85.

- **The Undergarden** (B, 11/11): con nodo: Catalizador y portal · Cloggrum · Froststeel · Utherium y su corrupción · Regalium · Guardián Olvidado y equipo olvidado · Infusor y mezcla virulenta · Criaturas (Stoneborn, Rotbeast, Muncher, Gwib) · Plantas y comida (gloomgourd, underbeans, hongos) · Honda y lanza · Biomas y exploración.

### Theurgy

Nivel B · 61 quests hoy (sector_theurgy) · facetas 13/13 en la cadena · meta 61.

- **Theurgy** (B, 13/13): con nodo: Varas de adivinación · Brasero piromántico · Calcinación (sales) · Licuefacción (azufre) · Destilación e incubación (metales desde principios) · Fermentación · Digestión · Mercurio y catalizador · Reformación (conversión entre materiales) · Acumulador de sal amoníaco · Emisores de flujo (calórico y sulfúrico) · Logística mercurial; **sólo mención:** Exaltación y espagiria.

### Actually Additions

Nivel B · 79 quests hoy (sector_aa) · facetas 16/16 en la cadena · meta 79.

- **Actually Additions** (B, 16/16): con nodo: Reconstructor atómico y cristales · Lentes del reconstructor · Empoderador y cristales empoderados · Caras y rompedores fantasma · Relés láser y mejoras · Granjero, rompedor, colocador y excavadora vertical · Generadores (carbón, aceite, calor, hojas, bio) · Canola, aceites y prensa · Café y máquina de café · Taladros y mejoras · Baterías y cajas de baterías · AIOTs, anillos y bolsos · Interfaces de ítems y de jugador, cajas · Gusanos, arroz y cultivos · Fábrica de lava, cuarzo negro y sólidos de XP · El folleto (manual).

### Ender IO

Nivel A · 76 quests hoy (sector_enderio) · facetas 22/22 en la cadena · meta 95.

- **Ender IO** (A, 22/22): con nodo: Granos de infinito y aglutinante de conductos · Fundidora de aleaciones y aleaciones · Molino SAG y bolas de molienda · Conductos (ítems, fluidos, energía, redstone) · Filtros de conductos · Capacitores y mejoras de máquina · Chasis y máquinas · Generadores (Stirling, combustión, solar) · Bancos de capacitores · Aglutinador de almas y viales · Generador de almas y spawner eléctrico · Cuba, alcohol y combustibles · Cortadora y empalmadora (slice'n'splice) y cabezas · Pintora y fachadas · Encantador y experiencia · Obeliscos (aversión, inhibidor, atracción, reubicación) · Viaje: anclas y bastón · Estación de granja, crafter y tolva de impulso · Tanques, drenaje y bombas · Acero oscuro: herramientas, armadura y mejoras · Cargadores y yeta wrench · Planeador y bastón de levedad.

### Integrated Dynamics y Tunnels

Nivel B · 64 quests hoy (sector_id) · facetas 13/13 en la cadena · meta 64.

- **IntegratedDynamics** (B, 10/10): con nodo: Menril, exprimidor y cuenca de secado · Cables y la red lógica · Lectores y escritores (aspectos) · Tarjetas de variables · Programador lógico y operadores · Paneles de pantalla · Proxies, materializadores y transformadores · Retardadores y listas · Baterías y generador · Diagnóstico de red y etiquetas.
- **IntegratedTunnels** (C, 3/3): con nodo: Importadores, exportadores e interfaces (ítems, fluidos, energía) · Interacción con el mundo (romper, colocar, soltar, recoger) · Simulación de jugador.

### Redes de logística

Nivel B, paraguas · 70 quests hoy (sector_logistics) · facetas 10/10 en la cadena · meta 70.

- **LaserIO** (B, 5/5): con nodo: Nodos y conectores láser · Tarjetas (ítems, fluidos, energía, redstone, químicos) · Filtros (básico, contador, mod, NBT, tag) · Overclockers y chips lógicos; **sólo mención:** Mecánicas de tarjeta (modos, canal, prioridad, regular).
- **Pipez** (C, 1/1): con nodo: Caños por tipo y mejoras.
- **Flux Networks** (C, 1/1): con nodo: Puntos, enchufes y controlador Flux.
- **Ranged Pumps** (C, 1/1): con nodo: Bomba de rango.
- **Item Collectors** (C, 1/1): con nodo: Recolectores de ítems.
- **Energy Meter** (C, 1/1): con nodo: Medidor de energía.

### Modular Routers

Nivel B · 65 quests hoy (sector_routers) · facetas 8/8 en la cadena · meta 65.

- **Modular Routers** (B, 8/8): con nodo: El router y cómo piensa · Módulos de mover ítems (emisor, extractor, soltador) · Módulos de mundo (colocador, rompedor, extrusor, activador, lanzador) · Módulos de fluidos y energía · Módulos de vacío y detector · Mejoras (velocidad, stack, rango, seguridad, camuflaje) · Aumentos · Filtros inteligentes.

### RFTools y XNet

Nivel B, paraguas · 67 quests hoy (sector_rftools) · facetas 22/22 en la cadena · meta 67.

- **RFToolsBase** (C, 3/3): con nodo: Marcos, bases y fragmentos dimensionales · Llave inteligente, tableta y tarjetas · Pantalla de información.
- **RFToolsBuilder** (B, 5/5): con nodo: Constructor y tarjetas de forma · Cámaras espaciales · Proyector de escudos · Escáner, compositor y proyector · Mover y vehículos.
- **RFToolsPower** (C, 3/3): con nodo: Generadores (carbón, blazing, endergénico) · Celdas de energía y celda dimensional · Monitor de energía.
- **RFToolsUtility** (B, 6/6): con nodo: Pantallas y módulos · Lógica redstone (secuenciador, temporizador, contador, emisor, receptor) · Crafter y tanque · Controlador ambiental y módulos · Teletransporte (dial, transmisor, receptor, porter) · Spawner y jeringa.
- **XNet** (B, 5/5): con nodo: Controlador, cables y conectores · Routers y redes múltiples · Antenas y router inalámbrico · Proxy redstone y fachadas; **sólo mención:** Canales (ítems, fluidos, energía, lógica).

### Artifacts

Nivel C · 16 quests hoy (guide_accessories) · facetas 7/8 hoy en todo el libro · meta 30.

- **Artifacts** (C, 7/8): con nodo: Cabeza (sombreros, gafas, esnórquel) · Cuello (colgantes, bufandas, collares) · Cintura (nube en botella, helio, obsidiana, antídoto) · Manos (guantes, garras, pistón de bolsillo) · Comida eterna y paraguas; **sólo mención:** Dónde se encuentran (cofres, campamentos, mímicos, arqueología) · Mímico (pelea); **faltan:** Pies (zapatillas, aletas, botas).

### EvilCraft

Nivel B · 20 quests hoy (guide_evilcraft) · facetas 10/13 hoy en todo el libro · meta 50.

- **EvilCraft** (B, 10/13): con nodo: Sangre: extractor, tanques y manchas · Infusor de sangre y núcleo · Gemas oscuras y minería · Acumulador ambiental y extractos de bioma · Cofre de sangre y fabricación exaltada · Horno espiritual · Escoba y partes · Purificador y agua eterna; **sólo mención:** Espíritus de venganza y frascos · Hombres lobo, veneno y peces del Nether; **faltan:** Caja del Encierro Eterno · Herramientas y armas (maza de distorsión, cetro del trueno) · Colgantes y anillos.

### Hostile Neural Networks

Nivel C · 16 quests hoy (guide_tech_gadgets) · facetas 4/5 hoy en todo el libro · meta 25.

- **Hostile Neural Networks** (C, 4/5): con nodo: Aprendiz profundo y modelos de datos · Fabricador de botín · Matriz de predicción; **sólo mención:** Niveles del modelo (de defectuoso a autoconsciente); **faltan:** Cámara de simulación y predicciones.

### Mahou Tsukai

Nivel B · 20 quests hoy (guide_mahoutsukai) · facetas 5/11 hoy en todo el libro · meta 45.

- **Mahou Tsukai** (B, 5/11): con nodo: Maná, circuitos y el compendio · Mahoujin y proyector · Familiares y hadas · Códigos místicos y armas nobles (Caliburn, Clarent, Rule Breaker); **sólo mención:** Proyección y refuerzo; **faltan:** Barreras (alarma, gravedad, drenaje, tangible) · Desplazamientos · Intercambios · Ojos místicos · Marco de realidad (Unlimited Blade Works) · Grial (copa del cielo).

### Psi

Nivel B · 19 quests hoy (guide_psi) · facetas 8/9 hoy en todo el libro · meta 45.

- **Psi** (B, 8/9): con nodo: Polvo psi, psimetal y gemas · Ensamblador de CAD y piezas (núcleo, encastre, batería, colorizador) · Programador de hechizos · Balas de hechizo y tipos · Discos de hechizo y detonador · Herramientas de psimetal; **sólo mención:** Piezas: trucos, selectores, operadores y constantes · Límites, vectores y orden de evaluación; **faltan:** Exotraje y sensores.

### Reliquary

Nivel B · 20 quests hoy (guide_reliquary) · facetas 7/10 hoy en todo el libro · meta 45.

- **Reliquary Reincarnations** (B, 7/10): con nodo: Altar y tomo de alcahestría · Lágrimas y cálices (vacío, infernal) · Pistola, ensambles, balas y cargadores · Reliquias únicas (piedra de Midas, medallón del héroe, corazón de ángel) · Bastones (glacial, pyromancer, ender, sojourner) · Amuletos de criaturas y cinturón · Antorchas y linternas (interdicción, paranoia); **faltan:** Partes de criaturas · Boticario: caldero, mortero y pociones · Pedestales.

### Silent Gear

Nivel B · 20 quests hoy (guide_silent_gear) · facetas 7/15 hoy en todo el libro · meta 70.

- **Silent Gear** (B, 7/15): con nodo: Clasificador de materiales y catalizadores · Reciclador (salvamento) · Kits de reparación · Armaduras · Accesorios (anillos, pulseras, collares) · Herramientas grandes (martillo, excavadora, mattock); **sólo mención:** Materiales y rasgos; **faltan:** Planos y papel de planos · Partes principales, varillas y encuadernaciones · Mesa de herrería de equipo y ensamblado · Forja de aleaciones y aleaciones · Mejoras (puntas, recubrimientos, kits de mod) · Arcos y ballestas · Menas y metales propios (hierro carmesí, plata azur) · Flora (lino, planta esponjosa, banana del Nether).

### Ad Astra

Nivel B · 25 quests hoy (guide_ad_astra) · facetas 12/16 hoy en todo el libro · meta 70.

- **Ad Astra** (B, 12/16): con nodo: Mesa de la NASA y cohetes por nivel · Combustible y refinería · Oxígeno: cargador, distribuidor y sensor · Energía: generador, solar y cables · Congelador criogénico · Rover; **sólo mención:** La Luna y el queso · Marte · Venus y Mercurio · Glacio · Estaciones espaciales y órbita · Lunarians y criaturas; **faltan:** Trajes espaciales (normal, netherita, jet) · Metales: desh, ostrum y calorita · Compresor y horno etriónico · Normalizador de gravedad, puertas y radio.

### ComputerCraft

Nivel B · 23 quests hoy (guide_computercraft) · facetas 10/10 hoy en todo el libro · meta 35.

- **CC: Tweaked** (B, 10/10): con nodo: Computadoras y la terminal · Monitores · Disqueteras y discos · Tortugas y mejoras · Módems y rednet · Computadoras de bolsillo · Impresora y libros · Altavoz y relé de redstone; **sólo mención:** Periféricos de otros mods · Programar en Lua.

### Criaturas y jefes

Nivel B, paraguas · 41 quests hoy (guide_creatures, guide_bosses) · facetas 10/10 hoy en todo el libro · meta 50.

- **Mowzie's Mobs** (B, 5/5): con nodo: Ferrous Wroughtnaut · Frostmaw · El Escultor (Tongbi); **sólo mención:** Umvuthi y los Umvuthana · Naga, Foliaath, Grottol, Lantern y Bluff.
- **Friends&Foes** (C, 1/1): con nodo: Gólems de cobre y toba, iceologer, wildfire.
- **Creeper Overhaul** (C, 1/1): **sólo mención:** Creepers por bioma.
- **Enderman Overhaul** (C, 1/1): con nodo: Endermen por bioma y perlas.
- **Naturalist** (C, 1/1): **sólo mención:** Fauna de Naturalist.
- **MmmMmmMmmMmm** (C, 1/1): con nodo: Muñeco de práctica.

### Mundos profundos

Nivel B · 15 quests hoy (guide_deep_worlds) · facetas 7/7 hoy en todo el libro · meta 45.

- **Deeper and Darker** (B, 6/6): con nodo: Resonarium, sculk y reinforced echo · Equipo de sculk y del Warden · Transmisor de sculk y almacenamiento remoto; **sólo mención:** Ciudades antiguas y el Warden · El Otherside (portal y dimensión) · Criaturas del Otherside (stalker, sculk snapper, leech).
- **Just Another Mining Dimension** (C, 1/1): con nodo: Dimensión minera y portales.

### Aparatos y ayudantes

Nivel C, paraguas · 31 quests hoy (guide_tech_gadgets, guide_building_tools) · facetas 10/12 hoy en todo el libro · meta 60.

- **Mining Gadgets** (C, 1/1): con nodo: Aparato minero y mejoras.
- **Building Gadgets 2** (C, 1/1): con nodo: Aparatos de construcción, copia y destrucción.
- **Construction Sticks** (C, 1/1): con nodo: Varas de construcción y plantillas.
- **Laser Bridges** (C, 0/1): **faltan:** Puentes láser.
- **Simple Magnets** (C, 1/1): con nodo: Imanes.
- **Iron Jetpacks** (C, 1/1): con nodo: Jetpacks por material.
- **Torchmaster** (C, 1/1): con nodo: Megaantorcha y antorchas de terror.
- **Utilitarian** (C, 1/1): con nodo: Utilitarian (bloques útiles).
- **ElevatorMod** (C, 1/1): con nodo: Ascensores.
- **Iron Furnaces** (C, 0/1): **faltan:** Hornos de hierro por material y aumentos.
- **Compact Machines** (C, 1/1): con nodo: Máquinas compactas.
- **Easy Villagers** (C, 1/1): con nodo: Aldeanos en bloque (trader, breeder, farmer).

### Immersive Aircraft

Nivel C · 18 quests hoy (guide_immersive_aircraft) · facetas 4/4 hoy en todo el libro · meta 25.

- **Immersive Aircraft** (C, 4/4): con nodo: Aeronaves (dirigible, biplano, girodino, quad, warship, saltamontes) · Motores, calderas y hélices · Mejoras (motor mejorado, casco, cañón); **sólo mención:** Combustible.

### Construcción

Nivel B, paraguas · 35 quests hoy (guide_building_furniture, guide_building_palette) · facetas 17/23 hoy en todo el libro · meta 75.

- **Chipped** (C, 1/1): con nodo: Mesas de Chipped.
- **Rechiseled** (C, 1/1): con nodo: Cincel de Rechiseled.
- **Rechiseled: Chipped** (C, 1/1): **sólo mención:** Rechiseled con Chipped.
- **Macaw's Bridges** (C, 0/1): **faltan:** Puentes.
- **Macaw's Doors** (C, 1/1): con nodo: Puertas.
- **Macaw's Fences and Walls** (C, 0/1): **faltan:** Cercas y muros.
- **Macaw's Lights and Lamps** (C, 0/1): **faltan:** Luces.
- **Macaw's Paths and Pavings** (C, 1/1): con nodo: Caminos.
- **Macaw's Roofs** (C, 1/1): con nodo: Techos.
- **Macaw's Stairs and Balconies** (C, 0/1): **faltan:** Escaleras y balcones.
- **Macaw's Trapdoors** (C, 0/1): **faltan:** Trampillas.
- **Macaw's Windows** (C, 1/1): con nodo: Ventanas y cortinas.
- **Handcrafted** (C, 1/1): con nodo: Muebles de Handcrafted.
- **Another Furniture** (C, 1/1): con nodo: Muebles de Another Furniture.
- **MrCrayfish's Furniture Mod: Refurbished** (C, 1/1): con nodo: Muebles de MrCrayfish (cocina, electricidad, correo).
- **FramedBlocks** (C, 1/1): con nodo: FramedBlocks y el martillo de marcos.
- **LittleTiles** (C, 1/1): con nodo: LittleTiles.
- **Supplementaries** (B, 2/3): con nodo: Supplementaries: bloques funcionales (jarras, sacos, fuelles, relojes) · Supplementaries: decoración (banderas, carteles, macetas); **faltan:** Supplementaries: herramientas y armas (honda, cuerda, cañón, burbujas).
- **Amendments** (C, 1/1): **sólo mención:** Amendments (cambios a vanilla).
- **Glassential-renewed** (C, 1/1): con nodo: Vidrios de Glassential.
- **Simply Light** (C, 1/1): con nodo: Luces de Simply Light.

### Energía, menas y automatización básica

Nivel transversal · 45 quests hoy (guide_energy, guide_ore_processing, guide_automation_basics) · facetas 3/3 hoy en todo el libro · meta 50.

- **minecraft** (C, 3/3): **sólo mención:** Qué generador conviene cuándo · Rutas de duplicado de menas · Primeros circuitos de automatización.

### Granja · Macetas y automatización

Nivel C · 8 quests hoy (guide_farming_resources) · facetas 2/3 hoy en todo el libro · meta 20.

- **BotanyPots** (C, 1/1): con nodo: Macetas y suelos.
- **BotanyPotsTiers** (C, 1/1): con nodo: Niveles de macetas.
- **BotanyTrees** (C, 0/1): **faltan:** Árboles en maceta.

### Equipo y armadura

Nivel transversal · 15 quests hoy (guide_gear) · facetas 3/3 hoy en todo el libro · meta 40.

- **minecraft** (C, 3/3): con nodo: Niveles vanilla hasta netherita · Plantillas de herrería; **sólo mención:** Mapa de armaduras de mods.

### Estructuras y exploración

Nivel C, paraguas · 35 quests hoy (guide_structures, guide_nether_end) · facetas 7/7 hoy en todo el libro · meta 50.

- **YUNG's Better Dungeons** (C, 1/1): **sólo mención:** YUNG's: mazmorras, minas, fortalezas, templos.
- **Repurposed Structures** (C, 1/1): **sólo mención:** Repurposed Structures.
- **Towns and Towers** (C, 1/1): **sólo mención:** Towns and Towers.
- **Dungeons and Taverns** (C, 1/1): **sólo mención:** Dungeons and Taverns.
- **Explorify** (C, 1/1): **sólo mención:** Explorify.
- **Lootr** (C, 1/1): con nodo: Lootr (botín por jugador).
- **Hellish Trials** (C, 1/1): **sólo mención:** Desafíos (Hellish Trials).

### Tombstone

Nivel C · 15 quests hoy (guide_tombstone) · facetas 4/4 hoy en todo el libro · meta 20.

- **Corail Tombstone** (C, 4/4): con nodo: Tumba y recuperación · Llaves, pergaminos y portal del alma · Tumbas decorativas; **sólo mención:** Familiares y ventajas.

### Viaje y navegación

Nivel B, paraguas · 32 quests hoy (guide_travel, guide_navigation) · facetas 10/14 hoy en todo el libro · meta 50.

- **Waystones** (C, 1/1): con nodo: Waystones y piedras de retorno.
- **Explorer's Compass** (C, 1/1): con nodo: Brújula de exploradores.
- **Nature's Compass** (C, 1/1): con nodo: Brújula de la naturaleza.
- **Fireproof Boats** (C, 1/1): con nodo: Botes a prueba de fuego.
- **Jumpy Boats** (C, 1/1): **sólo mención:** Botes saltarines.
- **Exposure** (C, 0/3): **faltan:** Cámara y rollos · Revelado (sala oscura, químicos) · Fotos, álbumes y marcos.
- **Aquaculture 2** (B, 3/4): con nodo: Cañas, anzuelos y cebos · Neptunio y equipo · Tesoros de pesca; **faltan:** Peces y filetes.
- **Carry On** (C, 1/1): **sólo mención:** Llevar bloques y criaturas.
- **Comforts** (C, 1/1): con nodo: Bolsas de dormir y hamacas.

## Método y límites

- **Fuentes:** los 315 JAR fijados (`catalog/local-paths.json`, más los JAR que traen adentro). Por mod se leyó el índice de su manual (Patchouli, Modonomicon, GuideME con la guía de AE2 y la de MI, Oracle Index de Oritech, el manual de IE, los infobooks de Cyclops, las escenas y etiquetas de Ponder, y los libros que viven en el código a través de sus claves de idioma: la Encyclopedia Arcana de Malum con 178 entradas y el compendio de Mahou Tsukai con 92), el histograma de tipos de receta de sus namespaces, la cantidad de ítems, bloques, criaturas, estructuras y dimensiones, las etiquetas de jefes y el árbol de logros.
- **Facetas:** agrupadas a mano desde esos índices, con los ítems y las palabras que las identifican. Una faceta está cubierta si una quest de la cadena nombra uno de sus ítems (tarea, ícono, `[item:]`, logro, criatura) o sus palabras en el texto en inglés.
- **Tronco:** roles de `tools/quest_engine.py`; se siguen las dependencias entre capítulos.
- **No verificable desde acá:**
  - Nada se vio en el cliente. Que una quest nombre una faceta no dice si la enseña bien.
  - Mods sin manual en el juego (Mekanism, Draconic Evolution, Cataclysm, Twilight, Bumblezone, Aether, Refined Storage, Artifacts, Reliquary, Silent Gear, ComputerCraft, Ender IO): sus facetas salen de logros, ítems y tipos de receta, y del criterio.
  - Cambios del pack: sólo se revisó que los replicadores de MoreMachine están desactivados (`entrelumen_more_machine_balance.js`) y se sacaron de la cuenta; otra receta quitada por KubeJS podría volver irrelevante una faceta.
  - Los niveles son criterio; la cifra de ATM10 no se usó.
  - Los mods que proponen el [censo](../research/mod-census.md) y la [búsqueda fuera de los tres packs](../research/mod-outward.md) no están en el pack y no se auditaron.
  - La columna «Hoy» del plan quedó vieja para las olas 4 a 6 (Just Dire Things figura con 20 y tiene 155); no se tocó porque la actualiza quien fusiona.
- **Para repetirla:** los scripts y las listas de facetas están fuera del repo, en `E:/Elias/Codex/Entrelumen-ssd/questbook-v3/depth-audit/` (`scan.py` lee los JAR, `facets/*.txt` son las facetas, `facet_engine.py` las mide, `trunk.py` revisa el tronco, `render.py` arma las tablas de este documento).
