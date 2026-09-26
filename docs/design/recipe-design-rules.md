# Reglas de diseño de recetas

Auditoría del 25 de septiembre de 2026, rama `feature/recipe-audit`. Aplica la devolución del primer playtest de Elias ([playtest-2026-09-24](playtest-2026-09-24.md)) a todas las recetas que el pack cambia o agrega: los scripts de KubeJS, los datos de `pack/kubejs/data`, las familias generadas y el datapack del companion. Las reglas valen también para lo que se agregue después, y `tools/check_recipe_design.py` las hace cumplir en CI.

## Las cinco reglas

1. **Menos anidado.** Un componente de ENTRELUMEN va en un hito: el controlador, el reactor, la máquina que abre una función o el módulo del Arca. Lo básico, lo que se fabrica por docenas y los escalones internos de un mod no piden componentes. Si tienen que seguir escalonados, llevan el **material del acto** (ver abajo), que no obliga a fabricar otro componente.
2. **Menos abanico.** Cada componente cierra a lo sumo ocho recetas (algunos, menos), y cada una está declarada con el hito que la justifica. Una receta nueva con un componente falla el chequeo hasta que se la declare, y declararla obliga a sacar otra si el componente ya está en su meta.
3. **El crafteo es un arte.** Toda receta de mesa que lleva o fabrica un ítem de ENTRELUMEN tiene forma y se lee igual reflejada de izquierda a derecha. Sus ítems de ENTRELUMEN van en el eje vertical, en las esquinas o en la fila del medio, nunca en el casillero de un ingrediente cualquiera. Ningún escalón rompe un dibujo que el mod ya tenía simétrico. Las seis Luminosidades cuentan como una sola clase: el Lingote Luminoso las enfrenta por pares.
4. **Aumentadores de spawner.** Siguen siendo la medalla de cobre de cinco ítems (`·K· / SOS / ·S·`) del lote de progresión, con el componente arriba. El chequeo lo exige para los 16.
5. **Emperor's Cloth.** Su receta de mesa y su herrería sin plantilla siguen funcionando, pero EMI y JEI no las muestran (ver abajo).

## Materiales de acto

Lo que antes pedía un componente sin ser un hito ahora pide un material del mismo acto. Los actos II, III y V siguen la escalera de aleaciones de Mekanism, cuya única fuente es el infusor metalúrgico que abre el Marco de calibración. El acto IV es un lugar.

| Acto | Material | De dónde sale |
|---|---|---|
| II | aleación infundida | infusor metalúrgico (pide el Marco) |
| III | aleación reforzada | infusor metalúrgico, con diamante |
| IV | lingote de ironwood, gema de zanita | Twilight Forest y el Aether |
| V | aleación atómica, lingote celeste | infusor (obsidiana refinada), Nature's Aura |

Oritech 0.19 copiaba las aleaciones de Mekanism en su fundición y los circuitos en su forja atómica. Esas siete recetas salteaban el infusor, la entrada a Mekanism que Elias aprobó como «buen gate mechanic», y con ellas todos los materiales de esta tabla; la familia `functions` las quita.

Cada función (canteras, vuelo, teletransporte, reactores, granjas de mobs) conserva su acto en todos los mods: el componente cierra sus hitos (`FUNCTION_KEYSTONES`) y el resto de sus miembros lleva el material (`FUNCTION_MEMBERS`). Un mod nuevo que sume una cantera hereda la ironwood, no una Lente espectral más.

## Abanico antes y después

Recetas que consumen cada componente (`python tools/check_recipe_design.py --report --baseline origin/main`). El «antes» cuenta las 1.101 macetas de Botany Pots Tiers y las 305 macetas con tolva.

| Componente | Antes | Ahora | Meta | Hitos |
|---|---:|---:|---:|---|
| Motor de renovación | 383 | 6 | 6 | módulo de naturaleza, SPS, macetas Mega, 3 aumentadores |
| Cápsula de ecosistema | 380 | 8 | 8 | Motor, módulo de naturaleza, macetas Ultra, 3 granjas de mobs, 2 aumentadores |
| Núcleo de propagación | 371 | 5 | 6 | Motor, macetas Elite, 2 copias de altar, 1 aumentador |
| Marco de calibración | 314 | 8 | 8 | Matriz viva, Matriz de distribución, Lente, módulo de ingeniería, infusor, portal minero, estante del II, Resonador II |
| Regulador de energía | 28 | 8 | 8 | Núcleo de manipulación, jetpack de Mekanism, ensamblador de Psi, motor de avión, estante del III, Resonador III, 2 aumentadores |
| Matriz de distribución | 28 | 8 | 8 | Sensor, módulo de logística, controladores de AE2 y RS, teletransportador, portal a Undergarden, copia de altar, 1 aumentador |
| Bus del Arca | 22 | 8 | 8 | módulos de ingeniería y logística, reactor de fusión, reactor de MI, Resonador V, 3 aumentadores |
| Lente espectral | 17 | 8 | 8 | Sello, módulos arcano y de exploración, Digital Miner, Llave de Luz, estante del IV, Resonador IV, 1 aumentador |
| Sello de contención | 14 | 8 | 8 | módulo arcano, reactor de fisión, portal minero del Nether, Estela eterna, emisor de Theurgy, biblioteca del Atlas, copia de altar, 1 aumentador |
| Carta de horizontes | 10 | 8 | 8 | módulo de exploración, Llave de Luz, 2 vuelos creativos, portales a Eternal Starlight y minería del End, estante del V, copia de altar |
| Núcleo de manipulación | 8 | 7 | 8 | Bus, módulo de logística, autocrafter de RS, constructor de RFTools, comercio automático, copia de altar, 1 aumentador |
| Matriz viva | 5 | 5 | 6 | Núcleo de propagación, Cápsula, módulos de naturaleza y habitabilidad, granja de hierro |
| Sensor de inventario | 3 | 3 | 6 | Bus, QIO, 1 aumentador |
| Acoplador de energía | 3 | 3 | 4 | Regulador, módulo de ingeniería, escobillas de New Age |
| Provisiones de viaje | 3 | 3 | 4 | Carta de horizontes, Carta de habitabilidad, módulo de habitabilidad |
| Lente en bruto, notas, núcleo de señal | 3, 2, 2 | 3, 2, 2 | 3, 2, 2 | el Marco, el núcleo de señal, la estación, el controlador del Arca y el Resonador I |
| Carta de habitabilidad | 1 | 1 | 2 | módulo de habitabilidad |

La Matriz de distribución y el Regulador, que el lote de progresión dejó con cerca de veinte recetas cada uno, quedan en ocho. Antes había 273 recetas sin forma y 508 asimétricas con ítems de ENTRELUMEN; ahora ninguna.

## Inventario

`python tools/check_recipe_design.py --report` lista cada receta con ítems de ENTRELUMEN con su forma, su grilla, sus componentes y su anidado (niveles de fabricación de ENTRELUMEN por debajo); `--all` suma las demás. Resumen por fuente, antes y ahora:

| Fuente | Recetas | Con ítems de ENTRELUMEN | Sin forma |
|---|---:|---:|---:|
| Familias escalonadas (industrial, QoL, arcana, Apotheosis, ping-pong, funciones, RFTools, aeronaves) | 106 → 98 | 101 → 38 | 0 → 0 |
| Recursos (Botany Pots, JAMD, Modular Bees) | 1.421 → 1.105 | 1.421 → 6 | 245 → 0 |
| Integración y provisiones | 24 → 24 | 24 → 24 | 23 → 0 |
| Datos del pack (estantes, aumentadores, creativos, resonadores, lingote, datos corregidos) | 90 → 90 | 82 → 82 | 1 → 1 |
| Companion | 13 → 13 | 13 → 13 | 5 → 0 |

La receta sin forma que queda en los datos del pack es la copia sin cambios del manual de PneumaticCraft (`patchouli:guide_book`), sin ítems de ENTRELUMEN. Además, los scripts quitan 84 recetas nativas por ID (77 antes; las siete nuevas son las copias de Oritech).

## Qué dejó de pedir componentes

**Nativas otra vez** (cubiertas por otra puerta o fabricadas por docenas):

- Macetas con tolva de Botany Pots y Botany Pots Tiers: 305 recetas. Automatizar una maceta es lo básico del mod.
- Bobina generadora de New Age: el Acoplador pasó a las escobillas de carbón, una por generador. La placa solar avanzada necesita hierro sobrecargado, que ya depende de esa electricidad.
- Núcleos de CAD de Psi (el ensamblador ya es del acto III), routers de XNet (necesitan el controlador), punto y controlador de Flux (sin un enchufe la red no lleva nada), porter cargado de RFTools (sólo llega a receptores).
- Modular Bees: partes, exportadores e importadores ME, overclockers, apilador, colmena de dragón y electrodo de netherita. Todo necesita un núcleo; el de colmena lleva dos lingotes de ironwood y el de centrífuga ya pide netherita y un huevo de dragón.

**Con material de acto:** jetpacks de Iron Jetpacks, Oritech y MI; Mining Gadgets; taladro y bomba de Industrial Foregoing, láseres de minerales y de fluidos; enchufe de Flux; pistolas de portal, varita del tiempo y máquina paradoja de Just Dire Things; ancla y bastón de Ender IO; cofre, tanque y bolsa de Ender Storage (la aleación en lugar de la perla); dislocador y núcleos de Draconic; receptor, spawner, controlador ambiental, celdas dimensionales y tarjeta de cantera de RFTools; controlador de XNet; cantera de MI y taladro profundo de Oritech; pozo dimensional de Occultism; girodino; PSD de Compact Machines; exojetpack, spawner, bombas, controlador de partículas y capacitor octádico; banco de la NASA; circuito cuántico de MI; escalones de New Age; sculkshelves, endshelf y endshelf dracónico; capacitores de Powah.

**Macetas por color:** las 1.098 recetas de Elite, Ultra y Mega consumen la mejora del tier en lugar de su viejo catalizador. El componente queda en tres recetas, una por mejora, y la mejora se sigue aplicando a una maceta colocada sin perder su contenido.

**Creativos y estantes:** los doce creativos llevan un segundo drop de jefe en lugar del Bus o el Motor (`PXP / MCM / SXS`: las Luminosidades ya los hacen del VI). El estante del horizonte lleva un lingote celeste en el centro y la biblioteca del Atlas, dos Sellos sobre y bajo el Ender Library con cuatro lingotes celestes; pierden el Motor y el Bus.

## Dibujos

`·` es un casillero vacío. Los conteos son los de `content/integration-design.json`, así que la entrega de cada módulo del Arca sigue igual a su receta.

```
Acoplador (II)     Matriz viva (II)    Provisiones (II)   Matriz de distribución (III)
R · R              · S ·               · V ·              · P ·
· C ·              F M F               F · F              L M L
A · A              · S ·               · V ·              A · A
restonia,          gema fuente,        sopa de verduras,  placa de circuito, procesador
carcasa, aleación  magebloom, Marco    filete             lógico, Marco, cristal palis
```

```
N. de propagación (III)  Regulador (III)     Sensor (III)        N. de manipulación (III)
E · E                    · C ·               · N ·               · P ·
· M ·                    P K P               L M L               B R B
W · W                    · C ·               · N ·
prudentium, Matriz       circuitos, acero,   menril, chips,      mecanismo de precisión,
viva, cera               Acoplador           Matriz              módulos, Regulador
```

```
Lente espectral (IV)   Carta de horizontes (IV)  Cápsula (IV)       Sello (IV)
· C ·                  A · A                     · M ·              D · D
S M S                  I N I                     W · W              · L ·
· P ·                  · R ·                     P M P              S · S
cristal, espíritus,    ambrosio, ironwood,       Matriz viva, cera, gema oscura, Lente,
Marco, mecanismo       notas, provisiones        puf de polen       acero de alma
```

```
Bus del Arca (V)     Motor de renovación (V)  Carta de habitabilidad (V)
A H A                K C K                    F R F
P S P                I · I                    S C S
· H ·                P C P                    · R ·
aleación atómica,    lingote celeste,         guiso, provisiones,
Núcleos, placas,     Cápsulas, imperium,      ensalada, procesador
Sensor               Núcleos de propagación   de cálculo
```

```
Ingeniería (V)   Arcano (V)        Naturaleza (V)    Exploración (V)   Logística (V)   Habitabilidad (V)
· F ·            · N ·             · W ·             S H S             · R ·           · R ·
E B E            L I L             C R C             Z L Z             H B H           M C M
A F A            S · S             · M ·             · D ·             · R ·           · R ·
Marcos,          estrella,         esponja,          steeleaf, Carta,  Matrices,       provisiones,
Acopladores,     Lentes, iesnium,  Cápsulas, Motor,  zanita, Lente,    Núcleos, Bus    Matrices, Carta
Bus, atómica     Sellos            Matriz viva       aliento           
```

```
Lente en bruto   Notas   Núcleo de señal   Atlas   Controlador del Arca
G                P       · L ·             C       D
C                I       C R C             B       S
G                P       · N ·                     L
```

Algunos escalones, con el componente o el material en su lugar:

```
Infusor (Marco)   Controlador ME   Cofre de Ender       Portal minero   Mejora Elite    Simulación HNN
I # I             a b a            B W B                O F O           i P i           · G ·
R F R             b M b            O C O                O P O                           E K E
I # I             a b a            B A B                O O O                           M C M
Marco en el       Matriz en el     aleación reforzada   Marco sobre     Núcleo entre    la Cápsula es
núcleo            núcleo           en lugar de la perla el pico         dos lingotes    la cámara
```

## Cambios de costo

- **Módulo de exploración:** vuelve la segunda gema de zanita (siete ítems). Cuatro ítems sueltos no entran en un solo eje; la receta, el proyecto de `projects.json`, sus pruebas y los textos del acto V se actualizaron juntos.
- **Núcleo de señal:** un lingote de cobre más, por la misma razón.
- **Un escalón con par de casilleros** (sculkshelves, endshelf dracónico, energizador reforzado, cantera de MI, tarjeta de cantera, celda dimensional) pide dos unidades del material. Donde el mod no tenía un casillero libre en el eje, el material o el componente reemplaza un ingrediente único del eje: la perla del cofre y el tanque de Ender, la obsidiana de la cámara de HNN, la verruga del duplicador, el controlador lógico del spawner de Ender IO, la carcasa del autocrafter de RS, la redstone de los láseres de IF y el oro del controlador de XNet. En los capacitores de Powah el material toma el cristal de arriba, que era uno de cuatro.
- **Macetas por color:** fabricar una directamente cuesta la mejora más los materiales; aplicar la mejora a una maceta colocada sigue costando sólo la mejora.

## Emperor's Cloth

Twilight Forest 4.8.3345 registra en EMI su receta especial de mesa bajo el ID del serializador con barra (`twilightforest:/emperors_cloth_recipe`, `EmiEmperorsClothRecipe.getId`) y la herrería bajo su ID de datos (`twilightforest:emperors_cloth_smithing`). Las dos toman como entrada todas las armaduras, así que encabezaban sus usos.

- **EMI 1.1.24** filtra recetas por datos: `EmiDataLoader` lee `assets/emi/recipe/filters/*.json`, sólo del namespace `emi`. KubeJS carga siempre `kubejs/assets` como paquete de recursos del cliente (`ClientAssetPacks`), así que el filtro vive en `pack/kubejs/assets/emi/recipe/filters/entrelumen_hidden_filler.json`, sin depender de un resource pack opcional.
- **JEI 19.50** no tiene filtro por datos; sólo la API `IRecipeManager.hideRecipes`. KubeJS 2101.7.2 la llama desde `RecipeViewerEvents.removeRecipes` en los scripts de cliente (`KubeJSJEIPlugin.onRuntimeAvailable`): `pack/kubejs/client_scripts/entrelumen_recipe_viewer.js`. El plugin de EMI de KubeJS no recibe ese evento, por eso EMI usa su filtro propio.
- Las recetas siguen cargadas: la GameTest `emperorsclothstillworkswhiletheviewershideit` tiñe una pechera real en la mesa y la acepta en la herrería.

## Balance de jetpacks

Elias pidió que todos los jetpacks del pack gasten 2,5 veces más energía por tick: los de FE y también el de hidrógeno de Mekanism, para que quede parejo. `tools/generate_jetpack_balance.py` lo escribe desde `content/jetpack-balance.json`, que guarda los valores de fábrica de los JAR fijados; `--check` corre en CI y también mantiene la tabla de abajo. Los ajustes enteros redondean para arriba (el de cobre, 212,5, queda en 213; el combustible del exo de Oritech, 37,5, en 38).

- **Iron Jetpacks** lee cada tipo de `config/ironjetpacks/jetpacks` y escribe los de fábrica sólo si falta la carpeta, así que el pack trae los catorce tipos, iguales a los de fábrica salvo `usage`. El servidor manda los tipos a los clientes.
- **Oritech y las botas de PneumaticCraft** van en TOML parciales; NeoForge completa el resto con sus valores de fábrica. Los jetpacks de Oritech gastan `fuelUsage` de combustible si tienen y, si no, `energyUsage`.
- **Mekanism** (jetpack, blindado y MekaSuit), **el jetpack diésel de MI y el traje propulsor de Ad Astra** fijan el gasto en el código y no tienen ajuste. `entrelumen_jetpack_balance.js` mira después de cada tick cuánto perdió la pieza del pecho desde el tick anterior y le saca 1,5 veces eso, con arrastre de fracciones: el jetpack de Mekanism paga 1 y 2 mB alternados, 2,5 de promedio. Una pérdida mayor que el máximo nativo por tick es un cambio de pieza y no se cobra; un tick en que algo también cargó la pieza puesta, tampoco.
- Capacidad, velocidad y recarga no cambian: cada carga dura 2,5 veces menos. La modulación gravitatoria y la unidad de élitros de la MekaSuit son vuelo, no jetpack, y quedan como están.
- **Instalación:** en una instancia ya jugada estos archivos existen con los valores de fábrica y `sync_pack.py` los frena como cambio local; hay que adoptarlos con backup, como se hizo con `ftbultimine-server.snbt`.

<!-- jetpack-table:start -->

| Jetpack | Mod | Ajuste | Antes | Después |
|---|---|---|---|---|
| Madera (`ironjetpacks:jetpack`, tipo `wood`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 32 | 80 |
| Piedra (`ironjetpacks:jetpack`, tipo `stone`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 70 | 175 |
| Cobre (`ironjetpacks:jetpack`, tipo `copper`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 85 | 213 |
| Hierro (`ironjetpacks:jetpack`, tipo `iron`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 120 | 300 |
| Bronce (`ironjetpacks:jetpack`, tipo `bronze`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 120 | 300 |
| Plata (`ironjetpacks:jetpack`, tipo `silver`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 150 | 375 |
| Oro (`ironjetpacks:jetpack`, tipo `gold`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 300 | 750 |
| Electrum (`ironjetpacks:jetpack`, tipo `electrum`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 310 | 775 |
| Invar (`ironjetpacks:jetpack`, tipo `invar`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 350 | 875 |
| Acero (`ironjetpacks:jetpack`, tipo `steel`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 350 | 875 |
| Diamante (`ironjetpacks:jetpack`, tipo `diamond`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 650 | 1625 |
| Platino (`ironjetpacks:jetpack`, tipo `platinum`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 720 | 1800 |
| Esmeralda (`ironjetpacks:jetpack`, tipo `emerald`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 880 | 2200 |
| Creativo (`ironjetpacks:jetpack`, tipo `creative`) | Iron Jetpacks 8.0.11 | `usage`, FE/t | 0 | 0 |
| Jetpack (`oritech:jetpack`) | Oritech 1.2.11 | `energyUsage`, RF/t; `fuelUsage`, mB/t en `oritech-startup.toml` | 128; 10 | 320; 25 |
| Jetpack con élitros (`oritech:jetpack_elytra`) | Oritech 1.2.11 | `energyUsage`, RF/t; `fuelUsage`, mB/t en `oritech-startup.toml` | 128; 10 | 320; 25 |
| Exo jetpack (`oritech:exo_jetpack`) | Oritech 1.2.11 | `energyUsage`, RF/t; `fuelUsage`, mB/t en `oritech-startup.toml` | 256; 15 | 640; 38 |
| Exo jetpack con élitros (`oritech:jetpack_exo_elytra`) | Oritech 1.2.11 | `energyUsage`, RF/t; `fuelUsage`, mB/t en `oritech-startup.toml` | 256; 15 | 640; 38 |
| Botas neumáticas con mejora Jet Boots (`pneumaticcraft:pneumatic_boots`) | PneumaticCraft 8.2.23 | `jet_boots_air_usage`, mL de aire/t por mejora en `pneumaticcraft-common.toml` | 12 | 30 |
| Jetpack de hidrógeno (`mekanism:jetpack`) | Mekanism 10.7.19 | fijo en el código; recargo del script | 1 mB de hidrógeno/t | 2,5 mB/t |
| Jetpack blindado (`mekanism:jetpack_armored`) | Mekanism 10.7.19 | fijo en el código; recargo del script | 1 mB de hidrógeno/t | 2,5 mB/t |
| MekaSuit con unidad jetpack (`mekanism:mekasuit_bodyarmor`) | Mekanism 10.7.19 | fijo en el código; recargo del script | 1 a 4 mB/t (según el empuje) | 2,5 a 10 mB/t |
| Jetpack diésel (`modern_industrialization:diesel_jetpack`) | Modern Industrialization 2.5.6 | fijo en el código; recargo del script | 1 mB/t en el aire, 2 subiendo | 2,5 a 5 mB/t |
| Traje propulsor (`ad_astra:jet_suit`) | Ad Astra 1.16.19 | fijo en el código; recargo del script | 50 FE/t subiendo, 100 a pleno | 125 a 250 FE/t |

<!-- jetpack-table:end -->

## Verificación

- `tools/check_recipe_design.py` (CI): hitos, abanico, forma, simetría, posición, medalla de aumentadores y los dos filtros. `--report` imprime el inventario completo de recetas con su forma, grilla, componentes y anidado.
- Los generadores comprueban contra los JAR fijados lo que el chequeo no ve: que un escalón no rompa un dibujo nativo simétrico, que un componente sólo entre en uno simétrico y que la reversión recupere la receta nativa. `generate_integration_recipes.py` exige que cada dibujo use exactamente los insumos del diseño y que la entrega de cada módulo del Arca sea igual a su receta.
- GameTests de pack completo: `RecipeDesignFullpackGameTests` (dibujos cargados, abanico cargado, entrada a Mekanism sin Oritech, Emperor's Cloth) y las pruebas de RFTools, Botany Pots, New Age, AE2, provisiones y módulos del Arca, ajustadas a las recetas nuevas.
- Jetpacks: `tools/test_jetpack_balance.py` (CI) compara los archivos con los valores de fábrica y corre el script en Node con capacidades simuladas. `JetpackBalanceFullpackGameTests` lee en el servidor los valores cargados de Iron Jetpacks, Oritech y PneumaticCraft, y hace volar con el gasto nativo real el jetpack y el blindado de Mekanism, el diésel de MI y el traje de Ad Astra: tras cuatro ticks falta 2,5 veces lo nativo, y un cambio de pieza no cobra nada. La MekaSuit comparte el camino del hidrógeno y no se prueba aparte, porque pide instalar el módulo.

Recibo: [`docs/verification/recipe-audit-runtime.json`](../verification/recipe-audit-runtime.json). Los 33 chequeos de Python dan 0; 225 JUnit y 108 GameTests aisladas pasan. En dos servidores propios y desechables con el pack completo y mundo nuevo pasaron las 145 GameTests de la corrida final (26f4986), con todas las familias de KubeJS en `loaded` sin fallas y la auditoría de contenido en PASS. La prueba de las cuatro recargas necesita la tolerancia de 180 s sólo para QA, como en los lotes anteriores.

Pendiente en un cliente: ver los dibujos en EMI y JEI y que Emperor's Cloth ya no aparezca en los usos de las armaduras.
