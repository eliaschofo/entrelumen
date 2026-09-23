# Adquisición de Immersive Aircraft

Estado: dos overrides estáticos en `pack/kubejs/server_scripts/entrelumen_aircraft_balance.js`, comprobados con el servidor completo y visibles en EMI. [Evidencia de integración](../verification/building-travel-runtime.json). Referencia primaria: JAR oficial `immersive_aircraft-1.5.2+1.21.1-neoforge.jar`, SHA-256 `ef5b68c04171d1eadb3bb70e600eb766534ef7588a5b4737f9d55a4f38550ae9`.

## Corte mínimo del grafo nativo

`data/immersive_aircraft/recipe/engine.json` produce el motor de airship, biplane y quadrocopter. Sustituimos **uno** de sus tres cobblestones por `entrelumen:power_regulator` (proyecto `distributed_power`, Acto III); permanecen blast furnace, pistones, boiler y los otros dos cobblestones. `data/immersive_aircraft/recipe/gyrodyne.json` es la excepción: usa sail×2, hull×2 y propeller, **sin motor**. Añadimos un `entrelumen:handling_core` (proyecto `workshop_hands`, Acto III) en un espacio libre y conservamos todos los insumos nativos. Es el control de maniobra de esta aeronave manual.

Las demás recetas nativas encadenan estos cuatro modelos: `cargo_airship.json` requiere airship, `warship.json` requiere cargo_airship y `bamboo_hopper.json` requiere biplane. Por eso bastan dos cambios para los siete vehículos, sin cobrar cuatro componentes nuevos por las cuatro hélices de quadrocopter. Las mejoras `eco_engine.json`, `nether_engine.json` y `enhanced_propeller.json` parten de engine o propeller y no son recetas alternativas de vehículos.

Ambas recetas mantienen sus IDs, tipo `minecraft:crafting_shaped` y resultados. `ServerEvents.recipes` comprueba presencia de cada item y ambos IDs antes de mutar algo. Sólo remueve esos dos IDs. No elimina recetas por output: una alternativa de addon permanece disponible, pero `ServerEvents.afterRecipes` inspecciona las dos recetas cargadas (patrón, ingredientes y resultado) y todas las rutas cargadas hacia el motor y los siete vehículos; emite un único recibo `ENTRELUMEN_AIRCRAFT` con `FAIL` si aparece otra ruta. Esta auditoría no cambia recetas.

No hay workstation ni loot/worldgen dentro del JAR fijado: sus 27 recetas son crafting shaped. `data/immersive_aircraft/advancement/misc/aircraftery.json` desbloquea recetas al obtener hierro, no entrega aeronaves. El config del mod controla combustible, uso, dimensiones y drops, sin puerta de adquisición por acto. Addons o datapacks futuros pueden introducir vehículos por otras recetas o estructuras: hay que evaluar esas rutas antes de afirmar cobertura del pack.

El objetivo Acto III se basa en la cadena tecnológica de dos componentes craftables, **no** en un permiso de campaña. Un componente o aeronave regalado sigue siendo utilizable; no se bloquean uso, reparación, combustible, drops ni intercambio. No hay comprobaciones de equipo, entregas, recompensas o avance.

El servidor completo comprobó ocho recetas mediante `RecipeManager` y `CraftingInput`, incluido el rechazo de las dos cuadrículas antiguas. Las comprobaciones pasaron antes y después de recargar. El cliente mostró en EMI la receta efectiva del motor, y se inspeccionaron el modelo y el inventario nativo de un dirigible de QA. El dirigible se generó con `NoGravity` y se montó mediante comando: esto no prueba consumo de combustible, adquisición survival ni rendimiento de vuelo. El toque breve de R no produjo una bajada visible; se volvió a la plataforma mediante teletransporte y queda pendiente probar el control normal.

Las siete comprobaciones de fuente/JAR pasan localmente. En CI corren las tres comprobaciones de fuente y se omiten explícitamente las cuatro comparaciones que requieren el JAR ignorado; no se redistribuye ni descarga una dependencia sólo para esas pruebas.
