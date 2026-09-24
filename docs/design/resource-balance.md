# Balance de adquisición de recursos — primera integración estática

Estado: implementado en fuentes del pack, pendiente de crafting/EMI/servidor real. No hubo escrituras de instancia, catálogo o companion. Generador `tools/generate_resource_balance.py`; salida `pack/kubejs/server_scripts/entrelumen_resource_balance.js`. El generador lee cuatro JAR fijados (JAMD, Botany Pots, Botany Pots Tiers, Modular Bees), valida SHA256 y contrasta todas las recetas del inventario previo mediante hash de cada JSON.

## Límite importante de etapas

Los 16 IntegrationItems tienen recetas shapeless públicas; las 22 recetas de integración no consultan campaña ni acto. Por ejemplo, propagation_core deriva de prudentium+wax+living_matrix; ecosystem_capsule usa pollen_puff+wax+living_matrix; renewal_engine usa sky_ingot+imperium+esas piezas. Ninguna exige completar un acto. Por eso los números II–VI siguientes son **objetivos de progresión tecnológica y editorial, no gates narrativos demostrados**. No se disfrazó esa limitación agregando supuestos permisos al crafting. El progreso de campaña continúa siendo independiente de posesión y regalos.

La revisión de las cadenas fijadas del 23 de septiembre no encontró un salto de tier que justifique agregar permisos de campaña. Se conserva progresión tecnológica por materiales, máquinas y el tier anterior; los proyectos narrativos mantienen su validación autoritativa separada. Esto no garantiza que cada tecnología se obtenga en un acto exacto ni acredita duración o dificultad final. Una ficha nueva sólo tendría sentido ante un bloqueo rígido requerido o un atajo demostrado; no es una condición necesaria para estas recetas.

Tres comprobaciones concretas de fuentes upstream e integración:

- JAMD minería conserva siete obsidianas y pico de diamante además del `calibration_frame`. El frame requiere dos iron sheets de Create, dos copper wires de IE y el lente propio. Ningún ingrediente exige entrar primero en JAMD.
- Elite usa `propagation_core`, que combina esencias iniciales, cera y la matriz de Ars/Create/IE. Las 366 recetas shaped Ultra conservan una maceta Elite y las 366 Mega, una Ultra. Los ítems de mejora también conservan la condición nativa de tier previo en `useOn`. La cápsula Ultra incorpora materiales de Bumblezone; el motor Mega incorpora Nature's Aura, incluidas botellas de aura Overworld y Nether. No hace falta que la cápsula contenga otro núcleo de propagación para conservar la secuencia.
- El core modular conserva advanced beehive, simulator y adult upgrade. El simulator requiere anti-teleport upgrade, blaze rods, perla y bloques de panal; fabricar partes modulares sueltas no evita esa cadena. La cera inicial se obtiene en la centrifugadora básica, sin exigir previamente el apiario modular.

Una ficha emitida por `nursery_protocol` o `pollinator_treaty` y exigida para fabricar el componente que consume ese mismo proyecto produciría un ciclo. Tampoco se introduce una recompensa nueva al incorporarse a un equipo. Los regalos siguen funcionando y recibir materiales no completa hitos. La comprobación actual es de recetas y prerrequisitos; crafting cargado, conservación de contenido al mejorar, autocrafteo y supervivencia necesitan evidencia propia.

## Costos concretos aplicados

| Sistema | Obtención preparada | Alcance |
|---|---|---|
| JAMD minería, objetivo II | Receta original con diamond_pickaxe y obsidiana; una de las ocho obsidianas pasa a calibration_frame | Resultado sigue jamd:portal_block |
| JAMD Nether, objetivo IV | Conserva netherite_pickaxe y siete nether_bricks; agrega containment_seal en lugar de un ladrillo | Resultado jamd:nether_portal; no habilita Nether sin materiales de esa dimensión |
| JAMD End, objetivo IV | Conserva diamond_pickaxe y siete end_stone; un bloque pasa a horizon_chart | Resultado jamd:end_portal; end_stone sigue siendo requisito normal, aunque regalos lo adelanten |
| Botany Pots base, objetivo I | Sin cambios | Colores, bowls, cultivo y recetas de comida intactos |
| Hopper inicial, objetivo II | Mantiene hopper y maceta/material; agrega una calibration_frame en slot libre o reemplaza una unidad de material estructural repetido | 122 rutas, incluidas variantes; sin destruir el ingrediente único maceta |
| Elite, objetivo III | Catalizador ender_pearl reemplazado por propagation_core; dos iron_blocks pasan a dos iron_ingots | Todas las rutas directas, same-material, quick y elite_upgrade |
| Ultra, objetivo IV | Catalizador nether_star reemplazado por ecosystem_capsule; dos diamond_blocks pasan a dos diamonds | Conserva requisito de maceta previa en recetas que ya lo incluyen |
| Mega, objetivo V | Catalizador enchanted_golden_apple reemplazado por renewal_engine; dos netherite_blocks pasan a dos netherite_ingots | Evita el costo arbitrario de 18 lingotes y una manzana encantada por upgrade |
| Hopper del mismo tier | Una calibration_frame además de hopper y maceta existente | 183 rutas, no rebaja el tier de entrada |
| Modular partes/cores, objetivo IV | Una ecosystem_capsule por receta de parte/core, en hueco libre o reemplazando un material estructural repetido | Se conserva máquina/ingrediente único; otros módulos heredan acceso mediante las partes |
| ME exports/imports, objetivo V | Una routing_matrix | Mantiene serializer, resultado y módulos previos |
| Overclockers y stacker, objetivo V | Una power_regulator | No inventa multiplicadores/costos de energía |
| Dragon hive, objetivo VI | Una renewal_engine junto al hive y parte originales | No altera la receta ni el comportamiento del hive base de Productive Bees |
| Electrodo netherite, objetivo VI | Template pasa a containment_seal; base electrode_gold y addition netherite_block quedan intactos | Serializer minecraft:smithing_transform conserva el mecanismo nativo de copia de componentes de base |

## Cobertura y conservación

1421 recetas modificadas: 3 JAMD, 122 hopper iniciales, 1101 rutas tier/catalizadores, 183 hopper del mismo tier, 11 sistemas Modular y 1 smithing. No son 1421 experiencias nuevas ni prueba de balance final. Las variantes son necesarias para evitar rutas equivalentes que omitan el costo.

Cada reemplazo conserva ID, tipo nativo, resultado completo/count, conditions Bookshelf/NeoForge, group y demás campos. No convierte smithing en shapeless. Los cambios de shaped preservan dimensiones y las piezas únicas: si no hay hueco, sustituyen una sola ocurrencia del material repetido, manteniendo el resto. No toca fuel definitions electrode/* ni treater/*, comida, bottles/dragon-breath conversions, guide/components ni remainders de bowls. El generador deja sin cambios conversiones de wax del mismo tier.

Los tres upgrade items también reciben el nuevo catalizador. Su useOn original queda intacto: según inventario de bytecode, acepta sólo tier siguiente, preserva metadata del block entity y consume el upgrade. No se intercepta interacción, campaña o uso prestado. Por eso una maceta colocada no saltea el costo de adquisición del upgrade, pero un upgrade recibido funciona libremente.

La conservación de serializer no es garantía nueva de NBT: crafting vanilla no preserva automáticamente todos los datos de macetas de entrada. No se añadieron regresiones intencionales cambiando su serializer; probar macetas con contenido por crafting y por useOn es obligatorio. Para preservación completa de contenido, la ruta useOn mantiene su implementación original.

## Verificación y pendientes

`python tools/generate_resource_balance.py --check --self-test`: hashes fijados, cobertura de inventario, IDs únicos, tres upgrades cubiertos, resultado/tipo/conditions invariantes; pruebas sintéticas smithing/base/addition, maceta normal y wax sin modificar, herramienta única JAMD conservada. `node --check` pasa. No se ejecutó Gradle ni servidor.

Actualización del 23 de septiembre: tres GameTests posteriores pasaron con los mods reales. Cubren 17 recetas blancas representativas, Shift+clic para los tres upgrades regalados de un equipo en acto V a otro en I, preservación exacta de suelo/semilla/herramienta/stock/NBT y producción efectiva con conservación del stock lleno. No convierten la auditoría de 1421 transformaciones en 1421 pruebas runtime ni verifican smithing o todas las variantes. Ver [casos y límites](resource-farm-runtime-cases.md) y [recibo](../verification/rftools-resource-runtime.json).

El evento modifica sólo recipes ya presentes: conditions opcionales inactivas no se fuerzan. afterRecipes emite un recibo limitado a presencia/resultado (`loaded-output-check-only`), deliberadamente no PASS de balance, ingredientes o NBT. Debe verificarse en servidor el JSON/ingredientes efectivos de rutas directas/quick/upgrade, resultado/count/color, crafting remainder, smithing con componentes, useOn con maceta llena y regalo a otro equipo. Revisar también recipes aportadas por otros addons/datapacks: el inventario cubre sólo los cuatro JAR fijados. Si cambia alguno, regenerar después de auditar la diferencia; no actualizar silenciosamente.

## Corrección del recibo posterior a la carga

`AfterRecipesLoadedKubeEvent` de KubeJS 2101.7.2-build.374 no expone `containsRecipe`. Se reemplazó únicamente esa consulta del auditor por `countRecipes({id}) > 0`; la fase `ServerEvents.recipes` y sus 1421 transformaciones permanecen intactas. Bytecode del JAR confirma que countRecipes filtra una lista construida desde `RecipeManagerKJS.kjs$getRecipeIdMap().values()`, por lo que el recibo inspecciona recetas del servidor cargadas, no los JSON previstos. La segunda consulta verifica el output por ID. Sigue siendo sólo comprobación de presencia/output: no acredita ingredientes, NBT ni gates narrativos. Las rutas opcionales inactivas no se consideran errores. Se requiere nuevo arranque/reload del integrador para obtener evidencia runtime corregida.

24 de septiembre (QA de pack completo con el watchdog de 60 s): esas dos consultas por fila recorrían las ~43 000 recetas 2842 veces, unos 30 s. Como `afterRecipes` también corre en el hilo del servidor durante `/reload`, el test `datapackreloadisatomicandrejectscycles` superó el watchdog. El auditor ahora recorre una sola vez las recetas cargadas (`forEachRecipe('*')`), cuenta los IDs de las filas y lee el resultado con el codec de la propia receta, como el chequeo de aeronaves. El veredicto es el mismo: cada fila activa, cargada una vez y con su output.
