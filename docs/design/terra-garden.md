# El jardín hidropónico de Terra

29 de septiembre de 2026, rama `feature/terra-garden`. Pedido de Elias: una máquina diseñada por Terra, «garden cloche, botany pot, hydroponic pero del entrelumen», ultra rápida, de final del acto V y antes de las Luminosidades; con un plano que muestra la maqueta como fantasma y un activador tipo *growth light* con el lenguaje de las Luminosidades.

Después de ver la primera versión pidió tres cambios, que son los que valen:
- **Otro look:** «que se vea como un motorcito, 4x4x4, steampunk, solarpunk, como un car engine pero en el medio hay una macetita y alguna luz verde que le dé vida».
- **Otra velocidad:** «con ganarle x20 a cualquier método endgame basta».
- **Otra economía:** la lámpara se gasta y vuelve a crecer de un cristal de terraluz.

Nada de esto se vio todavía dentro del juego. El arte es borrador para revisar pieza por pieza, y los GameTests quedan para la fase final de pruebas.

## Nombres

| Qué | ID | EN | ES |
|---|---|---|---|
| Multibloque | `entrelumen:terra_garden` | Terra's Hydroponic Garden | Jardín hidropónico de Terra |
| Plano | `terra_garden_plan` | Terra's Plan: Hydroponic Garden | Plano de Terra: jardín hidropónico |
| Activador (ítem y bloque) | `terra_grow_lamp` | Terra's Grow Lamp | Lámpara de cultivo de Terra |
| Núcleo | `terra_garden_core` | Garden Engine Pot | Maceta del motor del jardín |
| Salida | `terra_garden_outlet` | Garden Engine Outlet | Salida del motor del jardín |
| Varilla | `terralight_grounding_rod` | Terralight Grounding Rod | Varilla de tierra de terraluz |
| Cristal | `terralight_crystal` | Terralight Crystal | Cristal de terraluz |
| Fragmento | `terralight_shard` | Terralight Shard | Fragmento de terraluz |

«Plano de Terra» a secas ya es la pieza clave del acto II (`terra_blueprint`). Por eso este plano lleva subtítulo, y su ícono es un rollo vertical de pergamino con verdín, no el rollo azul en diagonal.

## Cómo se consiguen

- **El plano y la primera lámpara** son la recompensa de *Horizontes renovables* (`renewal_engine`, acto V) en `campaign/projects.json`: `reward` es el plano y `extraRewards` es la lámpara. Entregar el Motor de renovación prueba que sabés «tomar sólo lo que vuelve a crecer», la regla de Juan. El Atlas responde con el plano que Terra dibujó para su invernadero.
- **Copia del plano:** `·P· / PXP / ·P·` (cuatro papeles y el plano) da dos planos. Es para el resto del equipo.
- **Lámpara:** `BGB / GSG / BGB`: un fragmento de terraluz en el centro, vidrio a los lados (`c:glass_blocks`) y latón en las esquinas (`c:ingots/brass`). Se sacó la receta del acto V: los cristales son la única fuente renovable.
- **Maceta:** `CLC / GFG / CWC` (bloques de cobre, farol, vidrio, maceta, balde de agua).
- **Salida:** `·C· / CHC / ·C·` (cobre y una tolva).
- **Varilla:** `B / C / C` (latón y dos lingotes de cobre).
- Todas son recetas del companion y pasan `tools/check_recipe_design.py`.

## El plano (fantasma)

Clic derecho en un bloque: el motor aparece como fantasma, con el cárter sobre ese lugar y el frente mirando al jugador. Agachado y clic derecho sobre el fantasma, desaparece. No hace nada más.

Usa el visualizador de multibloques de Patchouli por su API pública (`makeSparseMultiblock`, `predicateMatcher`, `showMultiblock`, `getCurrentMultiblock`, `clearMultiblock`). La llama por reflexión, como la guía del Arca, así que el companion no compila contra Patchouli ni lo empaqueta. La dependencia está declarada como opcional en `neoforge.mods.toml` (`patchouli`, `[1.21.1-93,)`, lado cliente).

Cada bloque del fantasma acepta lo mismo que la validación del servidor, así que el contador de Patchouli coincide con ella. La maceta se ve en el fantasma porque es un modelo de bloque.

## El motor

Referencias inspeccionadas antes de dibujar:
- vanilla: la familia del cobre, la cabeza del pistón, la ranaluz verde y la maceta;
- del pack: el controlador del Arca y la ciudad de Solsticio, por el cobre y el vidrio;
- el orden de lectura de un motor de auto: bancadas con aletas a los costados, faros y parrilla adelante, tapas de válvulas arriba y la salida atrás.

La fuente es `art/structures/terra_garden.py`, que genera `data/entrelumen/terra_garden.json`.

- **Forma:** 4 × 4 × 4, simétrico en espejo respecto del plano entre las dos columnas del medio. Vale en las cuatro rotaciones.
- **y 0, el cárter:** cobre cincelado en las esquinas y cobre cortado en el resto.
- **y 1-2, el bloque:**
  - una bancada de cilindros a cada lado: cobre cortado abajo y aletas de rejilla oxidada arriba;
  - adelante, en las esquinas, faros (bombillas de cobre encendidas) bajo una parrilla;
  - atrás, las dos salidas.
- **El corazón:** en el medio, abierto al frente, una cama de cuatro ranaluces verdes, la luz verde que le da vida. Encima está el núcleo:
  - una maceta chica de cobre con franja de verdín y remaches de latón;
  - su modelo la dibuja sobre el plano de simetría, medio bloque al costado de su propia celda, girando con el motor;
  - el borde brilla: verdín apagado mientras falta el motor, teal cuando está armado y un verde que respira (8 cuadros, emisivo) mientras crece;
  - un renderizador dibuja en la tierra el cultivo de la semilla, pasando por sus edades unas cuatro veces por segundo.
- **y 3, la tapa:** cuatro pistones mirando arriba en las esquinas, tapas bajas (losas) sobre las bancadas, cobre atrás y una claraboya de vidrio sobre el corazón que llega hasta el frente.
- **Validación:**
  - 57 posiciones, todas requeridas. Las otras siete celdas del corazón y la abertura del frente no cuentan.
  - El cobre vale en cualquier etapa y encerado.
  - No se comparan las propiedades que el juego cambia solo: `shape`, `waterlogged`, `powered`, `lit`, `extended`, `age`, `berries`, `axis`, `distance`, `persistent`. Los pistones sí tienen que mirar hacia arriba.
  - La validación es del servidor y propia (`TerraGardenLayout`, pura, probada en JUnit): los GameTests corren sin Patchouli y Patchouli es opcional. No carga chunks.

## La lámpara entra en el motor

- **Despertar:** clic derecho en la maceta de un motor armado. La lámpara entra y se gasta (salvo en creativo). El motor despierta: una lámpara por motor.
- **Recuperarla:** si se rompe cualquier bloque del motor, cae ese bloque y cae la lámpara; es la única forma de sacarla.
  - Si lo rompe un jugador, la lámpara cae en el acto, en ese lugar (`BlockEvent.BreakEvent`).
  - Si el bloque se pierde por cualquier otra cosa (explosión, pistón), la suelta el chequeo siguiente, en menos de 5 s.
  - Si se rompe la maceta, la lámpara cae ahí. La semilla y el depósito quedan guardados en el ítem de la maceta.
- **Volver a arrancar:** un motor arreglado necesita otra lámpara.

## Producción

- **Semilla:** cualquier ítem que coloque un `CropBlock` (trigo, zanahoria, papa, remolacha, Mystical Agriculture, Croptopia, Pam's, Farmer's Delight…).
  - Se pone con clic derecho, y vuelve la anterior. También entra por caño.
  - Agachado y con la mano vacía se saca; con la mano vacía se lee el estado.
- **Tanda:** una por segundo de tiempo de juego, sin trabajo por tick.
  - 16 tiradas de la tabla de botín del cultivo maduro, escaladas a **900 cosechas por segundo**.
  - El redondeo es estocástico, así que la esperanza es exacta.
  - El reloj es el `gameTime`: un acelerador no suma, y la maceta está en `justdirethings:tick_speed_deny`.
- **Depósito y salida:**
  - El depósito guarda 16.384 ítems, unas seis tandas de trigo.
  - Las **salidas** empujan a los inventarios que las tocan desde afuera, por la capacidad de ítems de NeoForge: hasta 1.024 inserciones por tanda.
  - Un caño en una salida o en la maceta saca del depósito. La semilla nunca sale por caño.
  - Una salida suelta, sin motor, no ofrece nada.
- **Lleno, para:** si la tanda no entra, se recorta pareja; con el depósito lleno no produce. Nunca fabrica algo para tirarlo. El comparador da de 0 a 15.
- Sin energía.

## Números: veinte veces lo mejor

Medidos de los JAR fijados y de la configuración del pack. ENTRELUMEN no trae configuración propia de estos mods, así que valen los valores por defecto.

| Productor | Fuente | Trigo por segundo |
|---|---|---|
| **Motor de Terra** | `TerraGardenRules` | **900** |
| Fábrica de plantación definitiva de More Machine, 8 mejoras de velocidad (el mejor productor único) | `mekmm` `BASE_TICKS_REQUIRED` 200; Mekanism `getTicks` = 200 × 10^(−8/8) = 20 ticks; 9 procesos; `planting/wheat_from_wheat_seeds.json` da 5 | 45 |
| Maceta Mega con tolva de Botany Pots Tiers | velocidad ×10 sumada al divisor, rendimiento +4; `grow_time` 1200, mejor suelo 0,15, Eficiencia 0,05 por nivel, 5 ticks de espera | ≈ 0,93 cosechas |
| Garden Cloche de IE | `cloche/wheat.json`: 640 ticks, 2 trigo, fertilizante ×1,6 | 0,1 |
| Altar del Crecimiento (propio) | ×20 sobre vanilla, ×2 de cosecha | ≈ 0,08 por bloque de cultivo |
| Aceleradores de Mystical Agriculture | un tick aleatorio cada 10 s | marginal |

- **Contra una máquina:** el motor gana ×20 a la fábrica definitiva, ×970 a una maceta Mega y ×9.000 a la Garden Cloche. More Machine queda como está.
- **Contra el mismo volumen:** a igual volumen ya no gana siempre. Una pared de 64 fábricas (el volumen del motor, sin cables ni energía) haría 2.880 trigo/s. El pedido es ganarle ×20 a cualquier método de final, no al volumen.
- **Mystical Agriculture:** en este pack no hay cultivos de MA para Botany Pots, y el `planting` de MA está quitado de More Machine.
- **Varita del Tiempo de Just Dire Things:** multiplica macetas y fábricas; el motor la rechaza.

## La economía de las lámparas

«Usar una lámpara en tu PRIMER multiblock es lo más RÁPIDO que podés hacer, pero también te castiga hacer crecer el setup, sí o sí tenés que bancarte que tu lámpara te crezca UN terralight para poder empezar a multiplicarla, ese es el chiste». Escalar es exponencial, con muchos montajes de lámpara a tierra.

**El cristal de terraluz** es la única fuente renovable de lámparas, y por lo tanto de motores.

- **El montaje** son dos columnas de tres:
  - columna A, de arriba a abajo: la lámpara puesta como bloque, el aire donde crece el cristal y pasto (vale cualquier `#minecraft:dirt`);
  - columna B, al lado: un «cable» junto a la lámpara, otro junto al aire y la varilla de tierra junto al pasto, con tierra o pasto debajo;
  - un **cable** es cualquier bloque de cualquier mod con capacidad de energía (FE), de cualquier lado. No tiene que pasar nada, y la lámpara no pide energía.
  - la **varilla** es nuestra: un palito de cobre con collares de bronce, el controlador del montaje.
- **Crecimiento** (`TerralightRules`, `GroundingRodEntity`):
  - cuenta el tiempo de juego que la varilla ve pasar mientras el montaje está entero, mirado una vez por segundo;
  - si el montaje se rompe, espera sin perder lo crecido;
  - cuatro etapas: brote chico al empezar, mediano al cuarto, grande a la mitad y el racimo sólo al completarse;
  - la duración base es **4 h** de juego (288.000 ticks).
- **Nada lo apura:**
  - el cristal no toma ticks aleatorios ni polvo de hueso;
  - una segunda llamada en el mismo tick no cuenta tiempo;
  - la varilla y el cristal están en `tick_speed_deny`;
  - un hueco largo (un chunk descargado) cuenta en seco.
- **La lluvia sí:**
  - mientras llueve en el lugar y **5 min** después, el tiempo cuenta **×8**: unos 30 min de lluvia continua para un cristal;
  - es lluvia del mundo y un bioma con lluvia; no hace falta cielo abierto, porque la lámpara lo tapa.
- **Configurable:** todo está en `entrelumen-terralight-server.toml` (`fullGrowthTicks`, `rainMultiplier`, `afterRainTicks`).
- **Cosecha:**
  - el cristal completo da **1 fragmento**; fortuna y toque de seda no lo cambian;
  - picado antes, no da nada;
  - después de la cosecha, vuelve a empezar de cero.

## Exclusiones

`entrelumen:terra_garden_excluded` (ítems). Todas las entradas son opcionales, porque Mystical Agradditions no está en el pack:

- **Drops de jefes:** `mysticalagradditions:nether_star_seeds` y `dragon_egg_seeds`. La regla del pack es que el Wither es la única fuente de estrellas del Nether.
- **Acto VI**, el tier insanium de Agradditions: `gaia_spirit_seeds`, `awakened_draconium_seeds`, `neutronium_seeds` y `nitro_crystal_seeds`.
- **Por las dudas:** las mismas rutas con el namespace `mysticalagriculture:`.

Segunda red: `entrelumen:terra_garden_forbidden_drops` (`minecraft:nether_star`, `minecraft:dragon_egg`). El motor nunca fabrica esos ítems, aunque la tabla de un cultivo los tenga.

## Guía

`content/sectors/sector_entrelumen_terra_garden.json`, presentación v2, grupo ENTRELUMEN, acto V. Arranca como boceto.

- **Nodos:**
  - el plano;
  - el fantasma;
  - armar el motor;
  - la lámpara entra;
  - la primera cosecha;
  - poner la lámpara a tierra;
  - la terraluz (el primer fragmento);
  - una segunda lámpara;
  - un millón de cosechas;
  - y un adorno.
- **Estadísticas que usa:** `entrelumen:terra_garden_activations`, `terra_garden_harvests` (se le acredita a quien despertó el motor, si está conectado) y `terra_grow_lamps_crafted`.
- **Escena:** el motor en alzado y, al lado, el montaje de terraluz, que llegan con sus quests.
- **Estándar:** `tools/test_sector_book.py` lo exime del estándar de cadena, porque es un capítulo corto de un sistema propio.
- No toca otros capítulos.

## Arte

- **Fuentes:** `art/authoring/draw_terra_garden.py` escribe grillas de 16 × 16, dibujadas desde la mitad izquierda y reflejadas, y modelos esculpidos a 1 texel por unidad en `art/models/block`. Las procesa `build_art.py` y las referencias están en `art/grids/provenance.json`.
- **Piezas:**
  - lámpara: 8 cuadros;
  - plano: el alzado del motor;
  - salida;
  - maceta: cuerpo, tierra y tres bordes, uno de ellos animado y emisivo;
  - varilla;
  - cristal: cuatro etapas cruzadas, agujas finas color menta;
  - fragmento: 8 cuadros, en el lenguaje de las Luminosidades;
  - la lámpara como bloque: campana y llama animada.
- **Vistas de revisión:** son renders de software (`art/structures/terra_garden_render.py`), no capturas.

## Pruebas

- **Unitarias:**
  - `TerraGardenTest`: la forma, la simetría, las rotaciones, el cobre, los pistones, la aritmética, 900 = 20 × 45, la recompensa, las etiquetas y EN/ES;
  - `TerralightRulesTest`: los números, la lluvia, el tiempo y las etapas.
- **GameTests, pendientes para la fase final de pruebas:**
  - `RuntimeGameTestsTerraGarden`:
    - el motor en las cuatro rotaciones;
    - la lámpara que entra, no entra dos veces y vuelve al romper un bloque (con jugador y sin él);
    - una tanda exacta por las salidas;
    - semillas excluidas y drops prohibidos;
    - la parada con el depósito lleno;
    - la maceta que guarda lo suyo.
  - `RuntimeGameTestsTerralight`:
    - la validación del montaje;
    - que nada lo acelere;
    - la lluvia ×8 y su ventana;
    - la cosecha de un fragmento, sólo con el cristal completo.

## Para decidir (Elias)

- Si se excluye el tier supremium de Mystical Agriculture (pendiente de la consulta del controlador).
- Si la Varita del Tiempo se bloquea en otras máquinas de cultivo (pendiente).
- Nada visto en el juego:
  - el fantasma de Patchouli;
  - la maceta sobre el plano de simetría y el cultivo que crece en ella;
  - el borde emisivo;
  - las etapas del cristal;
  - las animaciones.
