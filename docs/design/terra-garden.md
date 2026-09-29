# El jardín hidropónico de Terra

29 de septiembre de 2026. Pedido de Elias: «una especie de garden cloche, botany pot, hydroponic pero del entrelumen. ultra hiper mega rápido pero también super late game pero pre luminosidad», una máquina diseñada por Terra, con plano (fantasma para ver la maqueta), un activador infinito tipo *growth light* con el lenguaje de las Luminosidades, y un multibloque solarpunk y hermoso. Rama `feature/terra-garden`. Nada de esto se vio todavía dentro del juego: el arte es borrador para la revisión pieza por pieza.

## Nombres

| Qué | ID | EN | ES |
|---|---|---|---|
| Multibloque | `entrelumen:terra_garden` | Terra's Hydroponic Garden | Jardín hidropónico de Terra |
| Plano | `entrelumen:terra_garden_plan` | Terra's Plan: Hydroponic Garden | Plano de Terra: jardín hidropónico |
| Activador | `entrelumen:terra_grow_lamp` | Terra's Grow Lamp | Lámpara de cultivo de Terra |
| Núcleo | `entrelumen:terra_garden_core` | Hydroponic Garden Core | Núcleo del jardín hidropónico |
| Bloque propio | `entrelumen:hydroponic_trough` | Hydroponic Trough | Bandeja hidropónica |

«Plano de Terra» a secas ya es la pieza clave del acto II (`terra_blueprint`); por eso este lleva el subtítulo, y su ícono es un rollo colgante vertical de pergamino y verdín, no el rollo azul en diagonal.

## Cómo se consigue

- **El plano:** es la recompensa de *Horizontes renovables* (`renewal_engine`, acto V) en `campaign/projects.json`. Entregar el Motor de renovación es probar que sabés «tomar sólo lo que vuelve a crecer», la regla de Juan; el Atlas responde con el plano que Terra dibujó para su invernadero. El Atlas da un solo plano, a quien entrega. Para el resto del equipo, la copia: `·P· / PXP / ·P·` (cuatro papeles y el plano, en el eje) → dos planos. Receta del companion.
- **La lámpara:** `·S· / ABA / ·S·`: dos lingotes celestes (Nature's Aura), dos aleaciones atómicas (Mekanism) y una bombilla de cobre. Son los dos materiales del acto V de [recipe-design-rules](recipe-design-rules.md#materiales-de-acto); no lleva componentes ni Luminosidades. Vive en `pack/kubejs/data/entrelumen/recipe/terra_grow_lamp.json` porque sus ingredientes son de otros mods (el servidor aislado de GameTests no los tiene). Pasa `tools/check_recipe_design.py`.
- **Núcleo:** `CLC / GMG / CWC` (bloques de cobre, farol, vidrio, bloque de musgo, balde de agua) → 1. **Bandejas:** `CWC / GCG` (lingotes de cobre, balde de agua, vidrio) → 4. Vanilla, en el companion: la puerta es la lámpara.

## El plano (fantasma)

Clic derecho en un bloque: el jardín aparece como fantasma con el núcleo donde iría un bloque puesto en esa cara y el frente mirando al jugador. Agachado y clic derecho sobre el fantasma (o sobre donde iría el núcleo): se va. No hace nada más.

Usa el visualizador de multibloques de Patchouli (el «ojito» de Botania, Occultism y otros) por su API pública (`makeSparseMultiblock`, `predicateMatcher`, `showMultiblock`, `getCurrentMultiblock`, `clearMultiblock`), por reflexión como la guía del Arca, así el companion no compila contra Patchouli ni lo empaqueta. La dependencia está declarada como opcional en `neoforge.mods.toml` (`patchouli`, `[1.21.1-93,)`, lado cliente); sin Patchouli el plano avisa que lo necesita. Cada bloque del fantasma acepta lo mismo que acepta el núcleo, así el contador de Patchouli coincide con la validación. Límite de Patchouli: dibuja con `renderSingleBlock`, que no dibuja fluidos; por eso el agua del jardín está dentro de las bandejas (un bloque con modelo) y no hay agua suelta.

## El jardín

Referencias inspeccionadas antes de dibujar (gancho de AGENTS.md): el invernadero-domo del acto III (`art/structures/preview/ruin_act3_greenhouse.png`), la Garden Cloche de Immersive Engineering (`block/metal_device/cloche.png`), el macetero de Supplementaries (`block/planter_side.png`) y los bloques vanilla de cobre oxidado, calcita, vidrio, musgo, faroles y flores de esporas. Fuente: `art/structures/terra_garden.py` → `data/entrelumen/terra_garden.json`.

- 7 × 9 × 7, simétrico en espejo (el script lo verifica, con los estados reflejados) y válido en las cuatro rotaciones.
- Una cascada de 25 bandejas en tres escalones (16, 8 y 1) sobre un zócalo de calcita con cama de musgo; alrededor, setos de azalea florida y alfombra de musgo.
- Cuatro pilares de cobre oxidado con una bombilla en el medio, y cuatro arcos (escaleras invertidas) con bayas luminosas colgando.
- Una cúpula escalonada de vidrio con nervios de rejilla de cobre en los ejes y las diagonales: vista desde arriba es un sol. Arriba, una ranalumbre ocre de sol y un pararrayos. Faroles colgantes y flores de esporas debajo.
- El núcleo va en el centro del borde del frente, mirando afuera; delante va un cofre o un caño. Un farol sobre el núcleo.
- 219 posiciones, todas requeridas: el fantasma muestra exactamente lo que se revisa. El cobre vale en cualquier etapa y encerado; una vid de bayas crecida cuenta como vid. No se comparan las propiedades que el juego cambia solo (`shape`, `waterlogged`, `powered`, `lit`, `age`, `berries`, `axis`, `distance`, `persistent`) ni hacia dónde se inclina un hombro de arco; sí la mitad de las escaleras, el tipo de losa y si un farol cuelga.
- La validación es del servidor, propia (`TerraGardenLayout`, pura y probada en JUnit), no la de Patchouli: los GameTests corren sin Patchouli y Patchouli es opcional. No carga chunks. El núcleo se revisa al usar la lámpara y cada 5 s; si el jardín se rompe, pausa, y al arreglarlo sigue sin volver a despertarlo.

## Producción

- Semilla: cualquier ítem que coloque un `CropBlock` (trigo, zanahoria, papa, remolacha, Mystical Agriculture, Croptopia, Pam's, Farmer's Delight…). Se pone con clic derecho (vuelve la anterior) o por caño en el hueco 0; agachado con la mano vacía se saca; con la mano vacía se lee el estado.
- **Tanda por segundo de tiempo de juego:** 16 tiradas de la tabla de botín del cultivo maduro, escaladas a **32.768 cosechas por segundo** con redondeo estocástico (la esperanza es exacta). Nada por tick. El reloj es el `gameTime` del nivel, así que un acelerador de bloques no suma, y el núcleo está en `justdirethings:tick_speed_deny`.
- **Depósito de 262.144 ítems** (4.096 stacks, unas tres tandas de trigo) y exportación a los inventarios de los seis lados por la capacidad de ítems de NeoForge, hasta 8.192 inserciones por tanda. Además, el núcleo expone la capacidad: hueco 0 para la semilla (sólo entra, nunca sale por caño) y 64 huecos de salida que sólo dan.
- **Lleno, para:** si la tanda no entra, se recorta parejo lo que entra; con el depósito lleno no produce. Nunca fabrica algo para tirarlo. El comparador da 0–15 según el depósito.
- Sin energía. Roto, el núcleo guarda semilla y depósito en el ítem (componente `entrelumen:terra_garden_contents`, como una caja de shulker) y hay que despertarlo de nuevo.

## Números: contra qué gana

Medidos de los JAR fijados y de la configuración del pack (ENTRELUMEN no trae configuración propia de estos mods, así que valen los valores por defecto). «Caja» = el volumen del jardín, 7 × 9 × 7 = 441 bloques. Trigo como cultivo de referencia (una cosecha madura = 1 trigo + ~1,7 semillas).

| Alternativa | Fuente | Por unidad | La caja llena |
|---|---|---|---|
| **Jardín de Terra** | `TerraGardenRules` | 32.768 cosechas/s | **32.768 trigo/s** |
| Fábrica de plantación definitiva de More Machine, 8 mejoras de velocidad | `mekmm` `TileEntityPlantingStation.BASE_TICKS_REQUIRED` 200; Mekanism `getTicks` = 200 × 10^(−8/8) = 20 ticks; `FactoryTier.ULTIMATE` 9 procesos; `planting/wheat_from_wheat_seeds.json` 5 trigo | 45 trigo/s por bloque | 19.845 trigo/s con 441 fábricas y cero cables, caños ni energía (imposible); realista, la mitad |
| Maceta Mega con tolva de Botany Pots Tiers | `PotTier`/`Gameplay`: velocidad ×10 sumada al divisor, rendimiento +4; Botany Pots `grow_time` 1200, `global_growth_modifier` 1, mejor suelo 0,15, Eficiencia 0,05/nivel, 5 ticks de espera | 5 cosechas / 108 ticks ≈ 0,93/s | ≈ 205 cosechas/s (maceta + cofre por columna); 408 sin cofres |
| Garden Cloche de IE | `cloche/wheat.json` 640 ticks, 2 trigo; mejor fertilizante ×1,6 | 2 trigo / 20 s | ≈ 22 trigo/s (2 bloques de alto) |
| Altar del Crecimiento (propio) | `AltarEffectRules`: ×20 sobre vanilla, ×2 de cosecha | ~0,08 trigo/s por bloque de cultivo | < 40 trigo/s, más la cosechadora |
| Aceleradores de Mystical Agriculture | `GrowthAcceleratorBlock`: un tick aleatorio cada 10 s | marginal | marginal |

- Gana por 1,65× contra una caja llena de fábricas imposible y por más de 3× contra una armable; por más de 80× contra las macetas Mega.
- **Mystical Agriculture:** en este pack no hay cultivos de MA para Botany Pots (ningún JAR fijado ni `pack/kubejs/data` los define) y `entrelumen_more_machine_balance.js` quita el `planting` de MA de More Machine. Lo mejor para esencias era un campo con suelo de supremium (×3), aceleradores y el Altar del Crecimiento. El jardín las multiplica por miles.
- **Fuera de la comparación:** aceleradores genéricos de entidades de bloque, como la Varita del Tiempo de Just Dire Things (hasta ×256 sobre una maceta o una fábrica, pagando fluido del tiempo). Multiplican cualquier máquina; el jardín los rechaza.

## Exclusiones

`entrelumen:terra_garden_excluded` (ítems; todas opcionales, porque Mystical Agradditions no está en el pack):

- **Drops de jefes** (la regla del pack: el Wither es la única fuente de estrellas del Nether): `mysticalagradditions:nether_star_seeds`, `dragon_egg_seeds`.
- **Contenido del acto VI** (el tier insanium de Agradditions): `gaia_spirit_seeds`, `awakened_draconium_seeds`, `neutronium_seeds`, `nitro_crystal_seeds`.
- Las mismas rutas con el namespace `mysticalagriculture:`, por si una versión las registra ahí.

Segunda red: `entrelumen:terra_garden_forbidden_drops` (`minecraft:nether_star`, `minecraft:dragon_egg`): el jardín nunca fabrica esos ítems aunque la tabla de un cultivo los tenga. Las semillas de esqueleto wither de MA quedan permitidas: dan calaveras, y el Wither sigue siendo la fuente de la estrella.

## Guía

`content/sectors/sector_entrelumen_terra_garden.json`, presentación v2, grupo ENTRELUMEN, acto V. Seis quests: conseguir el plano, ver el fantasma, armarlo, despertarlo, la primera cosecha y diez millones de cosechas. Las dos últimas cuentan la estadística `entrelumen:terra_garden_harvests`, que se le acredita a quien despertó el jardín mientras está conectado; despertar cuenta `entrelumen:terra_garden_activations`. No toca otros capítulos; el enlace desde el de macetas queda para la integración.

## Arte

`art/authoring/draw_terra_garden.py` (grillas nativas de 16 × 16, dibujadas desde la mitad izquierda y reflejadas) y `build_art.py`; referencias en `art/grids/provenance.json`. La lámpara tiene 8 cuadros a `frametime` 2 como las Luminosidades: una llama luminosa al revés, colgando de una campana de verdín, oro de sol con borde verde. El núcleo tiene tres frentes (`garden=unbuilt|built|growing`) y brilla 0/7/13. Las vistas de revisión son renders de software (`art/structures/terra_garden_render.py`), no capturas.

## Pruebas

- JUnit `TerraGardenTest` (16): el diseño, la simetría, las cuatro rotaciones y sólo la propia, el cobre y las vides, las escaleras, los faroles, chunks sin cargar, la aritmética de la tanda, los números contra las alternativas, la recompensa y las etiquetas, EN/ES.
- GameTests `RuntimeGameTestsTerraGarden` (6): el jardín en las cuatro rotaciones (dos con cobre encerado y envejecido), la lámpara que despierta y no se gasta ni se daña y sirve para dos jardines, una tanda exacta de trigo con exportación a un cofre y extracción por capacidad, la semilla excluida y el drop prohibido (el fixture excluye semillas de antorchaflor y prohíbe semillas de remolacha), la parada con el depósito lleno y la tanda recortada, y el núcleo que guarda lo suyo al romperse.

## Para decidir (Elias)

- **El número.** 32.768/s existe por la fábrica de plantación de More Machine, que ya da 45 trigo/s por bloque en el acto IV–V. Si preferís que el jardín sea el único salto grande, se puede recortar la fábrica (velocidad o recetas) y bajar el jardín a ~1.024/s, que igual le gana 5× a las macetas Mega.
- **Esencias de MA a miles por segundo** equivalen a recursos casi infinitos a fines del acto V. Si molesta, el tag de exclusión puede sumar los cultivos de nivel 5 (supremium).
- **La Varita del Tiempo** de Just Dire Things acelera macetas y fábricas ×256; el jardín la rechaza, pero las otras máquinas no.
- **Nada visto en el juego:** fantasma de Patchouli, luz del núcleo, animación de la lámpara, cúpula y texturas.
