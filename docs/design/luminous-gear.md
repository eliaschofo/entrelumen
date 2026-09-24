# Contenido luminoso: Luminosidades, Lingote Luminoso, equipo luminoso e ítems creativos

Dirección: [biblia de la historia](story-bible.md) del 24 de septiembre de 2026. Los aldeanos nativos de Solsticio venden seis Luminosidades; con ellas se fabrican el Lingote Luminoso, el mejor equipo del pack y los ítems creativos, que nunca se venden terminados.

Código: `companion/.../LuminousRules.java` (números y reglas puras), `Luminous.java` (registro, material, tier, reparación por luz y bono de set), `LuminousGear.java` (clases de ítem). Recetas: `tools/generate_family_balance.py --family luminous` escribe `pack/kubejs/data/entrelumen/recipe/{luminous_*,creative/**}.json` y `pack/kubejs/server_scripts/entrelumen_luminous_balance.js`. Texturas, modelos y la capa de armadura (`entrelumen:textures/models/armor/luminous_layer_1.png` y `luminous_layer_2.png`) son del controlador; este lote no escribe arte.

## Luminosidades

| ID | Disciplina | Color del nombre |
|---|---|---|
| `entrelumen:luminosity_engineering` | Ingeniería | cobre-naranja `#E07B39` |
| `entrelumen:luminosity_arcane` | Arcana | violeta `#A66BE8` |
| `entrelumen:luminosity_nature` | Naturaleza | verde `#6CC66C` |
| `entrelumen:luminosity_exploration` | Exploración | celeste `#7CC8F0` |
| `entrelumen:luminosity_logistics` | Logística | turquesa `#3CC7B4` |
| `entrelumen:luminosity_habitation` | Habitabilidad | dorado cálido `#F2B84B` |

- **Sin foil.** El brillo queda en el sprite animado del controlador. El glint violeta de vanilla taparía los seis colores y se lee como "encantado". El nombre sí lleva el color de su disciplina; el lingote y cada pieza luminosa van en blanco crema dorado `#F3E3B0`.
- **Fuente: pendiente.** Los aldeanos nativos de Solsticio todavía no existen. No hay receta que produzca una Luminosidad: el generador lo comprueba contra los 272 JAR fijados, los datos del pack y del companion, y el script de KubeJS quita al cargar cualquier receta que las produzca y avisa si queda alguna. Para QA u operadores: `/give @p entrelumen:luminosity_arcane 16`.
- Tag `entrelumen:luminosities`. Tooltip con una línea de lore y "Sólo la intercambian los aldeanos nativos de Solsticio".

## Lingote Luminoso (`entrelumen:luminous_ingot`)

```
Ingeniería   Lingote de los cielos    Exploración
Arcana       Perla forjada por dioses Logística
Naturaleza   Aleación atómica         Habitabilidad
```

La receta es simétrica: las seis Luminosidades ocupan las columnas laterales y la central funde magia, lo divino y la máquina. Así se forjaba Heliodor, pero esta vez con respeto.

- `naturesaura:sky_ingot`: magia ambiental, material de etapa V de la familia industrial.
- `apotheosis:godforged_pearl`: material de rareza mítica. Sólo sale de desguazar botín mítico, que aparece desde el World Tier Summit, abierto al terminar el acto V. Es tan de acto VI como las Luminosidades.
- `mekanism:alloy_atomic`: tecnología de etapa V.

Da un lingote por receta: seis Luminosidades por lingote. Es fireproof y repara todo el equipo luminoso en el yunque.

## Equipo luminoso

### Recetas: mejora de netherita por herrería

Las nueve piezas (`luminous_helmet`, `_chestplate`, `_leggings`, `_boots`, `_sword`, `_pickaxe`, `_axe`, `_shovel`, `_hoe`) salen de `minecraft:smithing_transform`:

- **plantilla:** estrella del Nether (drop del Wither, que la historia exige como material);
- **base:** la pieza de netherita equivalente;
- **adición:** un Lingote Luminoso.

Es el mismo camino que diamante → netherita. La herrería copia todos los componentes de la base, así que se conservan encantamientos, afijos y gemas de Apotheosis, nombre y desgaste. El full-pack lo comprueba con Sharpness/Protection IV, un nombre propio y 300 de daño. El equipo completo cuesta 9 lingotes, 9 estrellas y 9 piezas de netherita: 54 Luminosidades y 9 lingotes de los cielos, perlas forjadas por dioses y aleaciones atómicas.

### Stats

| | Luminoso | Techo pasivo actual |
|---|---|---|
| Armadura del set | **34** (6/12/10/6) | Ignitium de Cataclysm 32 (6/11/9/6); Obsidiana Refinada de Mekanism Tools 31 (6/12/8/5); netherita y MekaSuit 20 |
| Dureza por pieza | **6** | Obsidiana Refinada 5; Ignitium 4; netherita/MekaSuit 3 |
| Resistencia al empuje por pieza | **0,25** (set 1,0: inmune) | Obsidiana Refinada 0,2; Ignitium 0,15; netherita/MekaSuit 0,1 |
| Encantabilidad | **40** | Draconic caótico 35; Obsidiana Refinada 18; netherita 15 |
| Durabilidad | 1100/1600/1500/1300; herramientas 8192; **nunca se rompe** | Obsidiana Refinada 825–1200, herramientas 4096 |
| Herramientas | velocidad **16**, cosecha todo (tag `entrelumen:incorrect_for_luminous_tool` vacío) | Obsidiana Refinada 12; netherita 9 |
| Espada / hacha | **14** a 1,6/s; **16** a 1,0/s | Obsidiana Refinada 12; netherita 8 |

Fuentes, leídas el 24 de septiembre de 2026:

- `config/Mekanism/startup.toml` para MekaSuit y `tools-materials-startup.toml` para Obsidiana Refinada;
- bytecode de `Armortier` en Cataclysm 3.33 para el Ignitium;
- `ModularChestpiece` de Draconic Evolution 3.1.4, que usa material de diamante, y `DraconicEvolution.cfg` para sus valores internos (caótico: daño 2,5 × 7, 2 golpes/s × 1,6, velocidad 50, encantabilidad 35, módulos de daño de +16);
- `ALCombatRules` de Apothic Attributes. Con su fórmula (daño × a/(a+armadura), a = 10 para golpes menores de 20), 34 de armadura deja pasar el 22,7 % del golpe; 32, el 24,4 %; 20, el 33 %. La dureza resiste la perforación de armadura a 2 % por punto: 24 puntos en el set dan 48 %, bajo el tope de 60 %.

### Habilidades

- **Nunca se rompe.** El daño se corta un punto antes del máximo (`damageItem` de NeoForge, antes de Irrompibilidad). Una pieza en su último punto se **apaga**: `ItemAttributeModifierEvent` le vacía los atributos (sin armadura, dureza, ataque ni velocidad), mina a velocidad de puño y no da drops que pidan herramienta. Funciona como la élitra, que deja de planear en su último punto. Las herramientas guardan sus atributos en el componente por defecto, igual que la netherita. Así Apotheosis, que lee ese componente directamente, las archiva como armas; la primera corrida full-pack encontró que el hacha quedaba sin categoría cuando los atributos venían de un override.
- **Reparación por luz.** Cada segundo, la luz en los ojos del jugador repara todas las piezas luminosas que lleva, sea en el inventario, la armadura o la mano secundaria. Repara `brillo − 11` puntos por pieza: nada por debajo de 12 y 4 a pleno sol. La noche a cielo abierto no repara, porque cuenta el brillo del cielo oscurecido por la hora. Una pechera agotada queda entera en menos de 7 minutos de sol. El Remiendo y el lingote en el yunque siguen funcionando.
- **Bono de set** con las cuatro piezas encendidas:
  - visión nocturna ambiental, renovada cada segundo a 13 s, siempre fuera de la ventana de parpadeo de 10 s. Se quita sólo si la dio el set, nunca la de una poción;
  - sin daño de caída, para quien lo lleve.
- **Espada:** cada golpe revela al objetivo (Brillo 5 s) y hace +25 % de daño a no-muertos (`#minecraft:undead`).
- Todo es fireproof y está en los tags vanilla de armadura y herramientas. Por eso los encantamientos y los tags `c:` funcionan, y Apotheosis archiva cada pieza en la misma categoría de afijos que su netherita.

### Criterio frente a Draconic y MekaSuit

El set luminoso supera **cada número pasivo** del pack: armadura, dureza, empuje, encantabilidad, durabilidad, velocidad y daño sin energía. Además no necesita energía ni infraestructura. **No** supera a la MekaSuit ni a la pechera caótica de Draconic cuando tienen energía:

- la MekaSuit absorbe hasta el 100 % del daño con energía (`unspecifiedDamageReductionRatio = 1.0`);
- el escudo caótico absorbe cientos de puntos y se recupera;
- las armas caóticas empiezan en unos 17,5 de daño a 3,2 golpes/s y suman módulos de +16.

Superarlos exigiría invulnerabilidad o matar de un golpe, es decir, romper todo. Por eso el equipo luminoso es el mejor sin energía y el único con set completo, bono, reparación por luz y compatibilidad total con afijos. Si Elias quiere que también supere a Draconic caótico, habría que agregarle un escudo de luz recargable; es una decisión de diseño abierta.

## Ítems creativos

Cada receta es simétrica:

```
P X P    P: Luminosidad primaria        X: drop de jefe
M C M    M: material caro del mod       C: su versión tope no creativa
S R S    S: Luminosidad secundaria      R: componente del Arca de acto V
```

| Ítem creativo | Primaria / secundaria | X | M | C | R |
|---|---|---|---|---|---|
| `mekanism:creative_energy_cube` (sale **lleno**, como el de la pestaña creativa) | Ingeniería / Logística | corazón de dragón | pellet de antimateria | cubo de energía definitivo | bus del Arca |
| `powah:energy_cell_creative` | Ingeniería / Habitabilidad | corazón de dragón | bloque de cristal nitro | celda nitro | bus del Arca |
| `create:creative_motor` | Ingeniería / Exploración | estrella del Nether | mecanismo de precisión | motor de vapor | bus del Arca |
| `ae2:creative_energy_cell` | Logística / Ingeniería | corazón de dragón | singularidad | celda superdensa (MEGA) | bus del Arca |
| `mekanism:creative_fluid_tank` | Logística / Naturaleza | esponja mojada | circuito definitivo | tanque definitivo | bus del Arca |
| `mekanism:creative_chemical_tank` | Logística / Exploración | estrella del Nether | pellet de antimateria | tanque químico definitivo | bus del Arca |
| `ars_nouveau:creative_source_jar` | Naturaleza / Arcana | estrella del Nether | bloque de gema de fuente | jarra de fuente | motor de renovación |
| `create:creative_fluid_tank` | Naturaleza / Habitabilidad | esponja mojada | polea de manguera | tanque de fluidos | motor de renovación |
| `evilcraft:creative_blood_drop` | Arcana / Naturaleza | estrella del Nether | bloque de gema de poder oscuro | tanque oscuro | motor de renovación |
| `create_enchantment_industry:creative_bookshelf` | Arcana / Habitabilidad | estrella del Nether | estante del horizonte | endshelf dracónico | motor de renovación |
| `draconicevolution:creative_capacitor` | Exploración / Arcana | corazón de dragón | lingote de dragonio despertado | capacitor caótico | bus del Arca |
| `create:creative_blaze_cake` | Habitabilidad / Exploración | estrella del Nether | quemador de blaze | pastel de blaze | motor de renovación |

- **Simetría:** cada disciplina aparece en cuatro recetas, con 8 Luminosidades cada una (48 en total). El generador rechaza cualquier desbalance.
- **Drops de jefe:** Wither, Dragón (el corazón de Draconic cae al matarlo) y Guardián Anciano (esponja). Los tres están presentes como materiales, sin enmarcarlos en la historia.
- **Cubo de energía:** el cubo creativo de Mekanism fabricado vacío no sirve, porque su contenedor nunca cambia. La receta lo entrega con `mekanism:energy` al máximo. El full-pack lo compara con `StorageUtils.getFilledEnergyVariant`.
- **Sin duplicadores.** Quedan sin receta, a propósito, los ítems que copian cualquier ítem: `create:creative_crate`, `ae2:creative_storage_cell` (no existe celda creativa de fluidos aparte), `mekanism:creative_bin`, `functionalstorage:creative_vending_upgrade`, `modularrouters:creative_module` y las mejoras de infinito de Sophisticated. Clonarían Luminosidades, estrellas y perlas, y romperían la economía de Solsticio. El script avisa si alguno gana una receta.
- **Recetas previas:** ningún JAR fijado ni dato del pack produce hoy estos ítems. Las únicas recetas de creativos que existen son las fábricas creativas de `mekmm`, condicionadas a Evolved Mekanism (ausente), y el teñido del libro creativo de Ars; ninguna está en el catálogo. Aun así, el script quita al cargar cualquier otra receta que produzca una salida luminosa o creativa del catálogo y lo registra en `exclusive-outputs`.
- Quedan sin receta por ahora otros creativos redundantes: generadores de IE, Integrated Dynamics, RFTools, Create Crafts & Additions y Titanium, el compresor creativo de PneumaticCraft, las fábricas creativas de mekmm y la mejora creativa de Mekanism Extras. Se pueden sumar después con el mismo patrón.

## Sin ciclos

La alcanzabilidad simple no sirve: vía adoquín, procesamiento de minerales y alquimia, casi cualquier ítem llega a casi cualquier otro (el generador encontró cadenas como hacha → ritual de Occultism → … → estrella del Nether). Por eso el generador comprueba lo que hace que un ciclo se sostenga solo:

- ninguna receta produce una Luminosidad, y toda creación consume una directa o indirectamente, a través del lingote;
- las creaciones no forman ciclo entre sí (sólo lingote → equipo);
- ninguna receta devuelve la salida de una creación a una de sus entradas: desguace del equipo en lingotes, fundición del lingote o reciclaje de un creativo.

Tiene tests sintéticos para las tres propiedades.

## Verificación

- `python tools/generate_family_balance.py --check` (las cinco familias) y `tools/test_family_balance.py` (11 tests, 4 nuevos; el que compara con los JAR corre localmente).
- JUnit `LuminousRulesTest` (6): stats frente al techo, nunca se rompe, reparación por luz, set y colores.
- GameTests aislados `RuntimeGameTestsLuminous` (4), que corren con el resto de la suite:
  - material, tier, atributos, tags y colores;
  - la pieza se apaga en vez de romperse y la luz la repara;
  - visión nocturna estable y caída anulada, también para otra entidad;
  - la espada revela y castiga no-muertos.
- Full-pack `LuminousGameTests` (4), registrado en `FullpackQABootstrap`:
  - las 22 recetas cargan con los ítems reales y salidas exclusivas;
  - las 13 grillas crean lo que dicen, con el cubo lleno;
  - las 9 mejoras conservan encantamientos, desgaste y nombre;
  - las categorías de afijo de Apotheosis coinciden con las de la netherita.
- El resultado del runtime y la instalación quedan en [luminous-runtime.json](../verification/luminous-runtime.json).

## Pendiente

- Fuente de las Luminosidades: los aldeanos nativos de Solsticio y sus intercambios.
- Arte del controlador: sprites animados de Luminosidades y lingote, íconos y capa de armadura, revisados dentro del juego.
- Revisión en cliente: texto EN/ES, colores de nombre, visión nocturna y tooltips.
- Ritmo de supervivencia y costo real de las Luminosidades.
- Decidir si el equipo luminoso debe superar también a Draconic caótico y MekaSuit con energía.
