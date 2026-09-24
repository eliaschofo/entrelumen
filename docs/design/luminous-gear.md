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
- **Fuente:** los seis aldeanos nativos de la sala de comercio de Solsticio, uno por disciplina. Cada uno pide un bioma del Overworld y, una vez que estuvo ahí, vende su Luminosidad por 8 bloques de esmeralda y un catalizador, hasta 4 por reposición ([solsticio-commerce.md](solsticio-commerce.md)). No hay receta que produzca una Luminosidad: el generador lo comprueba contra los 272 JAR fijados, los datos del pack y del companion, y el script de KubeJS quita al cargar cualquier receta que las produzca y avisa si queda alguna. Para QA u operadores: `/give @p entrelumen:luminosity_arcane 16`.
- Tag `entrelumen:luminosities`. Tooltip con una línea de lore y "Sólo la intercambian los aldeanos nativos de Solsticio".

## Lingote Luminoso (`entrelumen:luminous_ingot`)

```
Ingeniería   Lingote de los cielos    Exploración
Arcana       Perla forjada por dioses Logística
Naturaleza   Aleación atómica         Habitabilidad
```

La receta es simétrica: las seis Luminosidades ocupan las columnas laterales y la central funde magia, lo divino y la máquina. Así se forjaba Heliodor, pero esta vez con respeto.

- `naturesaura:sky_ingot`: magia ambiental, material de etapa V de la familia industrial.
- `apotheosis:godforged_pearl`: material de rareza mítica. Sólo sale de desguazar botín mítico, que aparece desde el World Tier Summit. Desde la renumeración del 24/9 Summit abre con el acto V, el Arca ([act-renumbering.md](act-renumbering.md)), así que la perla es del acto V. Las recetas que la piden igual quedan en el VI por las Luminosidades.
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

El 24 de septiembre, Elias pidió stats absurdas: el equipo luminoso tiene que ser, por lejos, el mejor del pack.

| | Luminoso | Techo actual del pack |
|---|---|---|
| Armadura del set | **60** (12/22/16/10) | Ignitium de Cataclysm 32; Obsidiana Refinada 31 en Mekanism Tools y 30 en Silent Gear; netherita y MekaSuit 20 |
| Dureza por pieza | **15** | Obsidiana Refinada 5 (4 en Silent Gear); Ignitium 4; netherita/MekaSuit 3 |
| Resistencia al empuje por pieza | **0,25** (set 1,0: inmune) | Obsidiana Refinada 0,2; Ignitium 0,15; netherita/MekaSuit 0,1 |
| Encantabilidad | **60** | Obsidiana Refinada de Silent Gear 40; Draconic caótico 35; netherita 15 |
| Durabilidad | 2750/4000/3750/3250; herramientas 32.768; **nunca se rompe** | Acero tirio de Silent Gear 3652; Obsidiana Refinada 4096 en herramientas |
| Herramientas | velocidad **60**, cosecha todo (tag `entrelumen:incorrect_for_luminous_tool` vacío) | Draconic caótico 50 con energía; electro azur de Silent Gear 29; netherita 9 |
| Espada / hacha | **30** a 1,6/s; **32** a 1,0/s | Draconic caótico 17,5 base con energía (más módulos); Obsidiana Refinada 12; netherita 8 |

Fuentes, leídas el 24 de septiembre de 2026:

- `config/Mekanism/startup.toml` para MekaSuit y `tools-materials-startup.toml` para Obsidiana Refinada;
- bytecode de `Armortier` en Cataclysm 3.33 para el Ignitium;
- `ModularChestpiece` de Draconic Evolution 3.1.4, que usa material de diamante, y `DraconicEvolution.cfg` para sus valores internos (caótico: daño 2,5 × 7, 2 golpes/s × 1,6, velocidad 50, encantabilidad 35, módulos de daño de +16);
- los 138 materiales de Silent Gear 4.2.1.1 (tabla más abajo);
- `ALCombatRules` de Apothic Attributes. Con su fórmula (daño × a/(a+armadura), a = 10 para golpes menores de 20), 60 de armadura deja pasar el 14,3 % del golpe; 32, el 24,4 %; 20, el 33 %. La dureza resiste la perforación de armadura a 2 % por punto: los 60 del set llegan al tope de 60 %. AttributeFix, fijado en el pack, levanta el tope vanilla de 30 de armadura.

### Habilidades

- **Nunca se rompe.** El daño se corta un punto antes del máximo (`damageItem` de NeoForge, antes de Irrompibilidad). Una pieza en su último punto se **apaga**: `ItemAttributeModifierEvent` le vacía los atributos (sin armadura, dureza, ataque ni velocidad), mina a velocidad de puño y no da drops que pidan herramienta. Funciona como la élitra, que deja de planear en su último punto. Las herramientas guardan sus atributos en el componente por defecto, igual que la netherita. Así Apotheosis, que lee ese componente directamente, las archiva como armas; la primera corrida full-pack encontró que el hacha quedaba sin categoría cuando los atributos venían de un override.
- **Reparación por luz.** Cada segundo, la luz en los ojos del jugador repara todas las piezas luminosas y de Silent Gear radiantes que lleva, sea en el inventario, la armadura o la mano secundaria. Repara `brillo − 11` puntos por pieza: nada por debajo de 12 y 4 a pleno sol. La noche a cielo abierto no repara, porque cuenta el brillo del cielo oscurecido por la hora. Una pechera agotada queda entera en menos de 17 minutos de sol. El Remiendo y el lingote en el yunque siguen funcionando.
- **Bono de set** con cuatro piezas encendidas, luminosas, de Silent Gear radiantes o mezcladas:
  - **vuelo tipo creativo**, por el atributo `neoforge:creative_flight` con un modificador propio (`entrelumen:luminous_flight`), nunca con `abilities.mayfly`. Se recalcula al cambiar el equipo (`LivingEquipmentChangeEvent`) y cada segundo. Sacarse una pieza quita sólo ese modificador: el vuelo creativo, los jetpacks, los anillos y otros modificadores siguen. Si el jugador estaba volando y ya no puede, recibe caída lenta 8 s: perder el set nunca mata y no deja trampa de caída;
  - visión nocturna ambiental, renovada cada segundo a 13 s, siempre fuera de la ventana de parpadeo de 10 s. Se quita sólo si la dio el set, nunca la de una poción;
  - sin daño de caída, para quien lo lleve.
- **Luz dinámica** con LambDynamicLights: la armadura puesta y la herramienta en mano iluminan con nivel 15 mientras están encendidas; el lingote y las Luminosidades, con 12. Ver la sección de Silent Gear.
- **Espada:** cada golpe revela al objetivo (Brillo 5 s) y hace +25 % de daño a no-muertos (`#minecraft:undead`).
- Todo es fireproof y está en los tags vanilla de armadura y herramientas. Por eso los encantamientos y los tags `c:` funcionan, y Apotheosis archiva cada pieza en la misma categoría de afijos que su netherita.

### Frente a Draconic y MekaSuit

Con los números nuevos, el set luminoso supera todos los valores base del pack, incluido el arma caótica de Draconic sin módulos, y vuela sin energía. Todavía no iguala dos mecanismos alimentados por energía:

- la absorción de hasta el 100 % del daño de la MekaSuit (`unspecifiedDamageReductionRatio = 1.0`);
- el escudo caótico de Draconic, con módulos de daño de +16 cada uno.

Superar eso exigiría invulnerabilidad. Decisión de Elias (24/9): se deja así. El luminoso es más barato y no usa energía; MekaSuit y Draconic caótico siguen siendo el techo para quien arme la infraestructura.

## Silent Gear y luz dinámica

Pedido de Elias del 24 de septiembre: el Lingote Luminoso también es material de Silent Gear, con stats absurdas y traits de luz; el set completo vuela y el equipo luminoso ilumina alrededor.

### Mods agregados

| Mod | Versión y archivo | Origen | Lado | Por qué |
|---|---|---|---|---|
| Silent Gear | 4.2.1.1 (`silent-gear-1.21.1-neoforge-4.2.1.1.jar`, CF 297039/8095210, MIT) | instancia de referencia ATM10, SHA-1 verificado | ambos | herramientas, armas y armaduras por materiales |
| Silent Lib | 10.6.0 (`silent-lib-1.21.1-neoforge-10.6.0.jar`, CF 242998/7935618, MIT) | ídem | ambos | dependencia requerida de Silent Gear |
| LambDynamicLights | 4.8.11+1.21.1 (`lambdynamiclights-4.8.11+1.21.1.jar`, Modrinth yBW8D80W/ksaGCvSu, The Lambda License) | CDN oficial de Modrinth, descarga aprobada por Elias; SHA-1 y SHA-512 verificados | cliente | luz dinámica |

- Familia `catalog/families/silent-gear.json`, agregada con `curate_pack.py --add-families`. El lock pasa de 272/232 a **275 cliente / 234 servidor** y las entradas previas no cambian.
- Silent Gear Metalworks no hizo falta.
- Silent Gear trae sus propios minerales (hierro carmesí, plata azur, bort) y su generación de mundo. No se tocaron sus configs.
- **Luz dinámica.** Sodium Dynamic Lights, la opción obvia, quedó descartado:
  - su última versión para NeoForge 1.21.1 es 1.0.10, de enero de 2025;
  - depende de Sodium Options API, incompatible con Sodium 0.8 (issues #80 y #82 del repo);
  - con Sodium 0.8 congela el cliente (issue #79).

  RyoamicLights y el port no oficial de LambDynamicLights están abandonados desde 2024. **LambDynamicLights 4.8.11** oficial carga en NeoForge desde 4.5.0. Su runtime usa la API de configuración propia de Sodium 0.8: `ConfigBuilder`, `ModOptionsBuilder` y `ExternalPageBuilder`, que están presentes con esas firmas en el Sodium 0.8.13 fijado. Declara incompatibles a Sodium Dynamic Lights y RyoamicLights, y deja su tecla sin asignar. Su modo por defecto es `fancy`, con luces de entidades y del propio jugador activas.

### Material `entrelumen:luminous`

Archivo: `companion/src/main/resources/data/entrelumen/silentgear_materials/luminous.json`, formato `silentgear:simple` de 4.2.1.1. Usa el Lingote Luminoso como ingrediente, con color crema dorado `#F3E3B0` y textura `HIGH_CONTRAST`. El salvage está desactivado para que desguazar no devuelva Luminosidades. Parte principal frente al mejor valor de cada stat entre los 138 materiales de Silent Gear:

| Stat (parte principal) | Luminoso | Mejor material existente |
|---|---|---|
| Armadura del set (casco/pechera/grebas/botas) | **60** (12/22/16/10) | Obsidiana Refinada 30 (5/12/8/5) |
| Dureza (total del set; ÷4 por pieza) | **60** | Obsidiana Refinada 16 |
| Resistencia al empuje (÷10 por pieza) | **2,5** → 0,25 por pieza | sólo la capa de netherita, +1,0 |
| Armadura mágica | **60** | electro azur 19 |
| Durabilidad de armadura | **250** | barrera 84; acero tirio 81 |
| Daño de ataque | **30** | Obsidiana Refinada 10 |
| Velocidad de ataque | **+1,0** | piedra luminosa +0,4 |
| Daño mágico | **30** | electro azur 11 |
| Daño a distancia | **12** | acero tirio y Obsidiana Refinada 4 |
| Durabilidad | **32.768** | acero tirio 3652 |
| Encantabilidad | **60** | Obsidiana Refinada 40 |
| Velocidad de cosecha | **60** | electro azur 29 |
| Carga / tensado / precisión / velocidad de proyectil | **2,5 / 1,0 / 2,0 / 3,0** | 1,5 / 0,4 / 1,5 / 2,0 |
| Rareza | **250** | barrera 111 |
| Nivel de cosecha | `luminous`, cosecha todo | netherita / acero tirio |

Además define una punta (+10 ataque, +4096 durabilidad, +20 velocidad) y una capa: duplica ataque, durabilidad y velocidad de la pieza que recubre, y agrega Radiante e ignífugo.

**Traits de la parte principal:**

- **Radiante** (`entrelumen:radiant`, nuevo, datos en `silentgear_traits/radiant.json`, lógica en el companion). Cuenta para el bono de set, mezclable con la armadura luminosa propia: vuelo, visión nocturna y sin caída. Da luz dinámica, y la luz lo repara. Silent Gear no tiene un bono de set completo nativo que dé vuelo, así que el companion lee el trait por reflexión (`TraitHelper.getTraitLevel`, `GearHelper.isBroken`) sin enlazar contra Silent Gear.
- Sagrado V (`silentgear:holy`): +10 de daño a no-muertos.
- Lustroso V (`silentgear:lustrous`): gran bonus de velocidad de cosecha con luz.
- Refractivo (`silentgear:refractive`): las herramientas colocan luces fantasma.
- Visión felina (`silentgear:kitty_vision`): visión nocturna en el casco solo.
- Ignífugo.

**Luz dinámica.** `companion/src/main/resources/assets/entrelumen/dynamiclights/item/`:

- `luminous_gear.json` (nivel 15);
- `luminous_materials.json` (nivel 12);
- `silent_gear_radiant.json` (nivel 15, las 33 piezas de equipo de Silent Gear, `silence_error` si falta Silent Gear).

Los archivos de equipo usan el sub-predicado `entrelumen:radiant`, que registra el companion: coincide con una pieza luminosa encendida o con una de Silent Gear radiante que no esté rota, así que el equipo apagado no ilumina. LambDynamicLights recorre `getAllSlots()` y cuenta lo que se sostiene y lo que se lleva puesto.

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

- `python tools/generate_family_balance.py --check` (las cinco familias) y `tools/test_family_balance.py` (12 tests, 5 nuevos; el que compara con los JAR corre localmente). La familia luminosa también genera un override reversible de la receta de corte de netherwood de Farmer's Delight 1.3.3 para Silent Gear, que venía en formato anterior a 1.21 y fallaba al cargar.
- JUnit `LuminousRulesTest` (7): stats frente al techo, nunca se rompe, reparación por luz, set, colores y el material de Silent Gear frente al mejor valor de cada stat.
- GameTests aislados `RuntimeGameTestsLuminous` (6), que corren con el resto de la suite (63/63):
  - material, tier, atributos, tags y colores;
  - la pieza se apaga en vez de romperse y la luz la repara;
  - visión nocturna estable y caída anulada, también para otra entidad;
  - la espada revela y castiga no-muertos;
  - el vuelo del set sin tocar otras fuentes de vuelo, con aterrizaje en caída lenta;
  - los datos de luz dinámica.
- Full-pack `LuminousGameTests` (5), registrado en `FullpackQABootstrap`:
  - las 22 recetas cargan con los ítems reales y salidas exclusivas;
  - las 13 grillas crean lo que dicen, con el cubo lleno;
  - las 9 mejoras conservan encantamientos, desgaste y nombre;
  - las categorías de afijo de Apotheosis coinciden con las de la netherita;
  - la armadura de Silent Gear, fabricada con las recetas reales, supera a la de Obsidiana Refinada (12/22/16/10 contra 5/12/8/5, dureza 15 contra 4 por pieza), vuela con el set completo o mezclado y deja de volar con una pieza común.
- Runtime, instalación y hallazgos en [luminous-runtime.json](../verification/luminous-runtime.json).

## Pendiente

- Arte del controlador (sprites animados de Luminosidades y lingote, íconos y capa de armadura) revisado dentro del juego.
- Revisión en cliente: luz dinámica en pantalla, sensación del vuelo, texto EN/ES y el material de Silent Gear.
- Herramientas y armas de Silent Gear con el material luminoso probadas en juego; hoy las cubre la tabla de JUnit.
- Ritmo de supervivencia y precio real de las Luminosidades.
- Decidir si también debe superar la absorción de la MekaSuit y el escudo caótico de Draconic con energía.
