# Recetas estáticas de integración

`tools/generate_integration_recipes.py --write` genera exclusivamente `pack/kubejs/server_scripts/entrelumen_integration_recipes.js` a partir de `content/integration-design.json`. `--check` exige paridad exacta, 22 recetas, cantidades enteras positivas, hasta nueve casillas, resultados propios únicos, títulos de proyecto EN/ES y grafo acíclico de componentes. Verifica además colisiones en los recursos de recetas del companion y KubeJS. No equivale a comprobar las traducciones de los 16 objetos por registrar.

## Carga y fallos

El script usa `ServerEvents.recipes`, `event.custom(json).id(id)` y JSON nativo `minecraft:crafting_shapeless` con `result.id/count`; expande cada cantidad en entradas individuales de ingredientes. No cambia recetas ajenas ni consulta acto, equipo o procedencia. Las recetas normales quedan disponibles para mesa de crafteo y automatización.

Antes de añadir cualquiera de las 22, comprueba todos los ingredientes/resultados con `Item.exists` y cada ID con `event.containsRecipe({id})`. Una ausencia o colisión registra el contexto y lanza un error sin registrar ninguna receta de este conjunto. No impide que Minecraft continúe cargando otros datos: un servidor que arranca con ese error NO pasa aceptación. Primero deben estar registrados los 16 componentes propios. Otro script posterior aún podría alterar una receta; el recibo de `afterRecipes` comprueba los 22 pares ID/salida efectivamente cargados.

Los recibos `[ENTRELUMEN_INTEGRATION]` incluyen firma del conjunto: se exige `registered` con 22 y `loaded` con 22 sin faltantes. `failed-preflight` o `failed-loaded-check` requiere corregir la causa. La verificación de salida no comprueba su cantidad ni ingredientes finales: eso se valida en crafteo real.

## IDs reservados

- `entrelumen:integration/precision_bench` → `entrelumen:calibration_frame` × 1
- `entrelumen:integration/crystal_grid` → `entrelumen:energy_coupler` × 1
- `entrelumen:integration/living_workshop` → `entrelumen:living_matrix` × 1
- `entrelumen:integration/travelling_pantry` → `entrelumen:ration_bundle` × 1
- `entrelumen:integration/signal_exchange` → `entrelumen:routing_matrix` × 1
- `entrelumen:integration/nursery_protocol` → `entrelumen:propagation_core` × 1
- `entrelumen:integration/distributed_power` → `entrelumen:power_regulator` × 1
- `entrelumen:integration/measured_logistics` → `entrelumen:inventory_sensor` × 1
- `entrelumen:integration/workshop_hands` → `entrelumen:handling_core` × 1
- `entrelumen:integration/spectral_archive` → `entrelumen:spectral_lens` × 1
- `entrelumen:integration/horizon_survey` → `entrelumen:horizon_chart` × 1
- `entrelumen:integration/pollinator_treaty` → `entrelumen:ecosystem_capsule` × 1
- `entrelumen:integration/sealed_memory` → `entrelumen:containment_seal` × 1
- `entrelumen:integration/resilient_backbone` → `entrelumen:ark_bus` × 1
- `entrelumen:integration/renewal_engine` → `entrelumen:renewal_engine` × 1
- `entrelumen:integration/settlement_supply` → `entrelumen:habitation_contract` × 1
- `entrelumen:integration/ark_engineering` → `entrelumen:engineering_module` × 1
- `entrelumen:integration/ark_arcana` → `entrelumen:arcane_module` × 1
- `entrelumen:integration/ark_nature` → `entrelumen:nature_module` × 1
- `entrelumen:integration/ark_exploration` → `entrelumen:exploration_module` × 1
- `entrelumen:integration/ark_logistics` → `entrelumen:logistics_module` × 1
- `entrelumen:integration/ark_habitation` → `entrelumen:habitation_module` × 1

## Cuencos: comportamiento nativo, sin duplicación

Se inspeccionó `FarmersDelight-1.21.1-1.3.3.jar`, `vectorwing.farmersdelight.common.registry.ModItems`. Las fábricas de `VEGETABLE_SOUP`, `FISH_STEW` y `MIXED_SALAD` usan `bowlFoodItem`, que declara `Item.Properties.craftRemainder(Items.BOWL)`. Por eso se conserva el serializer shapeless vanilla, sin `replaceIngredient`, recompensas de cuencos, callbacks de inventario ni recetas inversas.

Prueba concreta pendiente:

1. `travelling_pantry`: dos vegetable_soup + dos fish_fillet_cooked producen exactamente un ration_bundle y devuelven dos cuencos.
2. `settlement_supply`: dos fish_stew + dos mixed_salad + los ingredientes restantes exactos producen un habitation_contract y devuelven cuatro cuencos.
3. Repetir con shift-click y espacio insuficiente: contabilizar ingredientes, resultado y cuencos en rejilla/inventario/suelo; nunca desaparecen ni se multiplican.
4. Solicitar diez unidades por autocrafteo AE2 y comprobar consumo exacto y 20/40 cuencos respectivamente. Cancelar/reanudar una solicitud para verificar que no duplica resultados. Si el adaptador altera remainders, corregir su integración; no compensar con una receta duplicadora.

## API y evidencia

API consultada localmente en el JAR KubeJS `2101.7.2-build.374` mediante javap: `RecipesKubeEvent.custom(Context, JsonObject)`, `containsRecipe(Context, RecipeFilter)`, `KubeRecipe.id(KubeResourceLocation)`. Los parámetros Context son inyectados por Rhino. `Item.exists`, `ServerEvents.afterRecipes` y `AfterRecipesLoadedKubeEvent.countRecipes` ya están comprobados en el diagnóstico instalado, con documentación de origen en `pack/kubejs/README.md`.

Verificación realizada: `--check` aprobado; mutaciones de prueba rechazan ciclo, exceso de casillas y título faltante. Un harness JavaScript simulado registra 22 recetas y aborta antes de mutar cuando falta un resultado, ingrediente o existe una colisión. El harness comprueba callbacks y estructura, no ejecución Rhino ni crafteo de Minecraft. Quedan pendientes reinicio real, lectura de recibos, recetas en visor, supervivencia y pruebas de cuencos/autocrafteo. No se escribió en la instancia activa.
