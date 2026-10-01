# El jardín hidropónico de Terra

29 de septiembre de 2026, rama `feature/terra-garden`. Pedido de Elias: una máquina diseñada por Terra, «garden cloche, botany pot, hydroponic pero del entrelumen», ultra rápida, de final del acto V y antes de las Luminosidades; con un plano que muestra la maqueta como fantasma y un activador tipo *growth light* con el lenguaje de las Luminosidades.

Lo que vale hoy, después de sus revisiones del mismo día:
- **Look:** primero «que se vea como un motorcito … steampunk, solarpunk, como un car engine pero en el medio hay una macetita y alguna luz verde que le dé vida»; después «más chico todavía, y hacé una cosa, hacelo 3x2, hacé una onda tipo Oritech donde el multiblock se transforma en un modelo propio pero más freestyle estilo Oritech multiblocks».
- **Velocidad:** «con ganarle x20 a cualquier método endgame basta».
- **Economía:** la lámpara se gasta y vuelve a crecer de un cristal de terraluz.
- **Semillas:** «sí, entra todo».

Nada de esto se vio todavía dentro del juego. El arte es borrador para revisar pieza por pieza, y los GameTests quedan para la fase final de pruebas.

## Nombres

| Qué | ID | EN | ES |
|---|---|---|---|
| Multibloque | `entrelumen:terra_garden` | Terra's Hydroponic Garden | Jardín hidropónico de Terra |
| Plano | `terra_garden_plan` | Terra's Plan: Hydroponic Garden | Plano de Terra: jardín hidropónico |
| Activador (ítem y bloque) | `terra_grow_lamp` | Terra's Grow Lamp | Lámpara de cultivo de Terra |
| Núcleo | `terra_garden_core` | Garden Engine Core | Núcleo del motor del jardín |
| Carcasa | `terra_engine_casing` | Garden Engine Casing | Carcasa del motor del jardín |
| Salida | `terra_garden_outlet` | Garden Engine Outlet | Salida del motor del jardín |
| Varilla | `terralight_grounding_rod` | Terralight Grounding Rod | Varilla de tierra de terraluz |
| Cristal | `terralight_crystal` | Terralight Crystal | Cristal de terraluz |
| Fragmento | `terralight_shard` | Terralight Shard | Fragmento de terraluz |

«Plano de Terra» a secas ya es la pieza clave del acto II (`terra_blueprint`). Por eso este plano lleva subtítulo, y su ícono es un rollo vertical de pergamino con verdín, no el rollo azul en diagonal.

## Cómo se consiguen

- **El plano y la primera lámpara** son la recompensa de *Horizontes renovables* (`renewal_engine`, acto V) en `campaign/projects.json`: `reward` es el plano y `extraRewards` es la lámpara. Entregar el Motor de renovación prueba que sabés «tomar sólo lo que vuelve a crecer», la regla de Juan. El Atlas responde con el plano que Terra dibujó para su invernadero, y la quest `world_renewal` del acto V lo dice en EN y ES.
- **Copia del plano:** `·P· / PXP / ·P·` (cuatro papeles y el plano) da dos planos. Es para el resto del equipo.
- **Lámpara:** `BGB / GSG / BGB`: un fragmento de terraluz en el centro, vidrio a los lados (`c:glass_blocks`) y latón en las esquinas (`c:ingots/brass`). Se sacó la receta del acto V: los cristales son la única fuente renovable.
- **Núcleo:** `CLC / GFG / CWC` (bloques de cobre, farol, vidrio, maceta, balde de agua).
- **Carcasa:** `CCC / C·C / CCC` (ocho lingotes de cobre) da cuatro.
- **Salida:** `·C· / CHC / ·C·` (cobre y una tolva).
- **Varilla:** `B / C / C` (latón y dos lingotes de cobre).
- Todas son recetas del companion y pasan `tools/check_recipe_design.py`.

## El plano (fantasma)

Clic derecho en un bloque: el motor aparece como fantasma, con la capa de abajo sobre ese lugar y el frente mirando al jugador. Agachado y clic derecho sobre el fantasma, desaparece. No hace nada más.

Usa el visualizador de multibloques de Patchouli por su API pública (`makeSparseMultiblock`, `predicateMatcher`, `showMultiblock`, `getCurrentMultiblock`, `clearMultiblock`). La llama por reflexión, como la guía del Arca, así que el companion no compila contra Patchouli ni lo empaqueta. La dependencia está declarada como opcional en `neoforge.mods.toml` (`patchouli`, `[1.21.1-93,)`, lado cliente).

Cada bloque del fantasma acepta lo mismo que la validación del servidor, así que el contador de Patchouli coincide con ella. El fantasma muestra los doce bloques sin formar.

## El motor

Referencias inspeccionadas antes de dibujar:
- vanilla: la familia del cobre, el pistón, la piedra de afilar (una rueda a escala de bloque) y la maceta;
- el orden de lectura de un motor de auto: bancadas con aletas a los costados, parrilla y faros adelante, pistones arriba, volante y correa atrás;
- **Oritech** (`oritech-neoforge-1.21.1-1.2.11.jar`), sólo la técnica: sus `machine_core` son bloques miembro con una propiedad (`USED`) que, puesta, les apaga el render (`getRenderShape` y el blockstate `machine_core_hidden` apuntan a `minecraft:block/air`), y el controlador (`ASSEMBLED`) dibuja la máquina entera. Lo leímos del bytecode y de los blockstates; no se copió ningún modelo ni textura.

La forma sale de `art/structures/terra_garden.py`, que genera `data/entrelumen/terra_garden.json`. El modelo formado sale de `art/authoring/draw_terra_garden.py` (`engine_model`).

- **Forma:** 3 de ancho, 2 de fondo y 2 de alto: doce bloques, simétrica en espejo respecto de la columna del medio. Vale en las cuatro rotaciones.
  - El núcleo va al frente de la columna del medio, en la capa de abajo, y la salida justo detrás.
  - Los otros diez son carcasas.
- **Sin formar:** son doce bloques comunes. Las carcasas son chapas de cobre remachadas, y el núcleo es una carcasa con una ventana redonda al frente: oscura, o teal cuando el motor está armado.
- **Formado:** cuando la lámpara lo despierta, el núcleo marca a sus miembros `formed=true` y ellos dejan de dibujarse.
  - Los miembros usan `RenderShape.INVISIBLE`, un blockstate a `minecraft:block/air` y no tapan las caras de sus vecinos.
  - El núcleo pasa a `garden=growing`, y su modelo es un solo motor libre sobre todo el volumen de 3 × 2 × 2.
- **No se empuja:** las carcasas y la salida tienen `PushReaction.BLOCK`, así que ni un pistón ni una máquina de Create mueven el motor; moverlo es romperlo y volver a armarlo, y la lámpara vuelve. Si algo se lleva el núcleo sin su entidad (una máquina de Create que lo transporta), el núcleo desforma a los miembros que deja según su `facing`, así que no quedan bloques invisibles y sólidos. Elias lo eligió el 1/10.
- **El modelo formado** (60 elementos, 1 texel por unidad; las cajas grandes se cortan en la grilla de 16 para que la textura no se estire):
  - un cárter, el bloque del motor y dos bancadas de cilindros con aletas de verdín;
  - tapas de válvulas y cuatro pistones de latón con capuchón;
  - al frente, una parrilla de radiador con dos faros cálidos (emisivos) y una tira de luz verde encima;
  - atrás, un volante y una correa que sube a una polea, y dos caños de escape;
  - arriba, en el medio, el corazón: una maceta chica de cobre con franja de verdín, tierra y un borde que respira en verde (8 cuadros, emisivo);
  - un renderizador dibuja en esa tierra el cultivo de la semilla, pasando por sus edades unas cuatro veces por segundo;
  - el núcleo da luz 13 mientras crece.
- **Validación:**
  - las doce posiciones son requeridas;
  - no se comparan las propiedades que el juego cambia solo, ni `formed`;
  - la validación es del servidor y propia (`TerraGardenLayout`, pura, probada en JUnit): los GameTests corren sin Patchouli y Patchouli es opcional. No carga chunks.

## La lámpara entra en el motor

- **Despertar:** clic derecho en el núcleo de un motor armado. La lámpara entra y se gasta (salvo en creativo). El motor se forma y despierta: una lámpara por motor.
- **Recuperarla:** si se rompe cualquier bloque del motor, cae ese bloque y cae la lámpara, y el motor se desarma (sus miembros vuelven a verse). Es la única forma de sacarla.
  - Si lo rompe un jugador, la lámpara cae en el acto, en ese lugar (`BlockEvent.BreakEvent`).
  - Si el bloque se pierde por cualquier otra cosa (explosión), la suelta el chequeo siguiente, en menos de 5 s.
  - Si se rompe el núcleo, la lámpara cae ahí. La semilla y el depósito quedan guardados en el ítem del núcleo.
- **Volver a arrancar:** un motor arreglado necesita otra lámpara.

## Producción

- **Semilla:** cualquier ítem que coloque un `CropBlock` (trigo, zanahoria, papa, remolacha, Mystical Agriculture, Croptopia, Pam's, Farmer's Delight…).
  - Se pone con clic derecho en el núcleo, y vuelve la anterior. También entra por caño.
  - Agachado y con la mano vacía se saca; con la mano vacía se lee el estado.
- **Tanda:** una por segundo de tiempo de juego, sin trabajo por tick.
  - 16 tiradas de la tabla de botín del cultivo maduro, escaladas a **900 cosechas por segundo**.
  - El redondeo es estocástico, así que la esperanza es exacta.
  - El reloj es el `gameTime`: un acelerador no suma, y el núcleo está en `justdirethings:tick_speed_deny`.
  - Al cargar, la primera tanda y el primer chequeo de cada núcleo caen en un tick propio del segundo (y de los cinco segundos), sacado de un hash mezclado de su posición: muchos motores que cargan juntos no trabajan todos en el mismo tick.
  - El motor no toma multiplicadores de afuera: un Altar del Crecimiento activo al lado no duplica la tanda (sí duplica una cosecha a mano dentro de su campo).
- **Depósito y salida:**
  - El depósito guarda 16.384 ítems, unas seis tandas de trigo.
  - La **salida** empuja a los inventarios que la tocan desde afuera, por la capacidad de ítems de NeoForge: hasta 1.024 inserciones por tanda.
  - Un caño en la salida o en el núcleo saca del depósito. La semilla nunca sale por caño.
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
- **Contra el mismo volumen:** con 12 bloques, el motor también gana: doce fábricas (sin cables ni energía) harían 540 trigo/s.
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
  - lo crecido está atado al cristal que la varilla escribió: guarda dónde y qué etapa puso, y lo mira en cada vistazo, aunque el montaje esté roto. Si ese cristal falta, cambió de etapa por otra mano o la columna pasó a otro lado, vuelve a empezar de cero;
  - **una varilla por cristal:** después de una cosecha, toda varilla que lo había escrito arranca de cero, así que una segunda varilla en la misma columna nunca da un segundo fragmento en un mismo crecimiento; desfasadas, se pisan y el cristal no madura;
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

«sí, entra todo». Elias lo decidió el 29/9. `entrelumen:terra_garden_excluded` queda vacía (`"values": []`): cualquier semilla que coloque un `CropBlock` entra, incluidas las del acto VI y las de jefes, si algún día aparecen.

La etiqueta se conserva para que un pack o un datapack pueda excluir algo sin tocar código.

Segunda red: `entrelumen:terra_garden_forbidden_drops`. El motor nunca fabrica estrellas del Nether, huevos de dragón ni las esencias y semillas de sus cultivos de Mystical Agradditions (`minecraft:nether_star`, `minecraft:dragon_egg`; opcionales: `mysticalagriculture:nether_star_essence`, `dragon_egg_essence`, `nether_star_seeds`, `dragon_egg_seeds`, y `mysticalagradditions:nether_star_shard`, `dragon_egg_chunk`), aunque la tabla de un cultivo los tenga. El cultivo de Agradditions no mira su crux cuando el motor tira su tabla, así que sin esta red daría estrellas sin jefe. El campo con su crux y el Wither siguen siendo las únicas fuentes, aunque entre todo. Elias eligió esta red, y no excluir las semillas, el 1/10.

## Guía

`content/sectors/sector_entrelumen_terra_garden.json`, presentación v2, grupo ENTRELUMEN, acto V. Arranca como boceto.

- **Nodos:**
  - el plano;
  - el fantasma;
  - armar el motor: núcleo, salida y diez carcasas;
  - la lámpara entra;
  - la primera cosecha, donde todas las semillas valen;
  - poner la lámpara a tierra;
  - la terraluz (el primer fragmento);
  - una segunda lámpara;
  - un millón de cosechas;
  - y un adorno.
- **Estadísticas que usa:** `entrelumen:terra_garden_activations`, `terra_garden_harvests` (se le acredita a quien despertó el motor, si está conectado) y `terra_grow_lamps_crafted`.
- **Escena:** el motor de 3 × 2 en alzado y, al lado, el montaje de terraluz, que llegan con sus quests.
- **Estándar:** `tools/test_sector_book.py` lo exime del estándar de cadena, porque es un capítulo corto de un sistema propio.
- No toca otros capítulos. En el acto V, `world_renewal` suma una oración en EN y ES que nombra el plano y la lámpara.

## Arte

- **Fuentes:** `art/authoring/draw_terra_garden.py` escribe grillas de 16 × 16, dibujadas desde la mitad izquierda y reflejadas, y modelos esculpidos a 1 texel por unidad en `art/models/block`. Las procesa `build_art.py` y las referencias están en `art/grids/provenance.json`.
- **Piezas:**
  - lámpara: 8 cuadros;
  - plano: el alzado del motor;
  - carcasa, frente del núcleo (apagado y armado) y salida;
  - las texturas del motor formado: aletas, parrilla, latón, rueda, faros;
  - la maceta: cuerpo, tierra y un borde animado y emisivo;
  - varilla;
  - cristal: cuatro etapas cruzadas, agujas finas color menta;
  - fragmento: 8 cuadros, en el lenguaje de las Luminosidades;
  - la lámpara en 3D (pedido de Elias: «que la terra lamp tenga un modelo 3D también»): cuelga de una placa de cobre por un vástago de latón, con campana de verdín, aro de cobre y una bombita de vidrio en una jaula de cuatro varillas de latón. Adentro está la luz verde, emisiva y animada en 4 cuadros. Son 13 elementos, simétricos en los dos ejes y a 1 texel por unidad, y cuelga sobre el cristal de terraluz.
  - el ítem de la lámpara se ve en 3D en la mano, en el suelo y en los marcos. En el inventario usa el ícono plano animado, con el lenguaje de las Luminosidades (`neoforge:separate_transforms`): a 16 px, la lámpara en 3D es una mancha.
- **Modelos:** `terra_garden_core` (sin armar), `terra_garden_core_built` (armado, a la espera de la lámpara) y `terra_garden_core_formed` (el motor entero, 60 elementos, simétrico).
- **Blockstates:** los miembros tienen `formed=false` (su cubo) y `formed=true` (`minecraft:block/air`). El núcleo gira con su frente. Su modelo formado está dibujado mirando al sur, así que lleva 180° más.
- **Vistas de revisión:** son renders de software (`art/structures/terra_garden_render.py`), no capturas:
  - formado de frente, de costado y de atrás;
  - sin formar de frente y de atrás;
  - una maqueta del fantasma;
  - la maceta y su luz en la oscuridad.
  - la lámpara puesta sobre el montaje (de día y de noche), de cerca, en la mano y en una ranura (el plano contra el 3D).

## Pruebas

- **Unitarias:**
  - `TerraGardenTest`:
    - la forma de 3 × 2 × 2: doce posiciones, diez carcasas y una salida, simetría y límites;
    - las rotaciones, y que un miembro formado siga contando;
    - la aritmética, y 900 = 20 × 45;
    - la recompensa;
    - la etiqueta de exclusión vacía, y EN/ES.
  - `TerralightRulesTest`: los números, la lluvia, el tiempo y las etapas.
- **GameTests, pendientes para la fase final de pruebas:**
  - `RuntimeGameTestsTerraGarden`:
    - el motor en las cuatro rotaciones;
    - la lámpara que entra y forma el motor (sus miembros invisibles);
    - que no entra dos veces;
    - romper una carcasa: cae la carcasa y la lámpara, y el motor se desarma, con jugador y sin él;
    - una tanda exacta por la salida;
    - semillas excluidas por etiqueta de prueba, y drops prohibidos;
    - la parada con el depósito lleno;
    - el núcleo que guarda lo suyo.
  - `RuntimeGameTestsTerralight`:
    - la validación del montaje;
    - que nada lo acelere;
    - la lluvia ×8 y su ventana;
    - la cosecha de un fragmento, sólo con el cristal completo.

## Para decidir (Elias)

- Si la Varita del Tiempo se bloquea en otras máquinas de cultivo (pendiente).
- Nada visto en el juego:
  - el fantasma de Patchouli;
  - el motor formado y el paso de doce bloques a un modelo;
  - el cultivo en la maceta;
  - las luces emisivas;
  - las etapas del cristal;
  - las animaciones.
