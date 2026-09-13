# Mekanism: More Machine — auditoría del candidato 8724947

Recomendación: **incorporación condicionada para fábricas, con replicación desactivada mediante datapack y cultivos MA excluidos inicialmente**. No instalar el JAR stock. La incorporación sólo queda validada después de probar esos overrides en el servidor del pack. Esta auditoría no cambió catálogo, instancia ni configuración.

## Artefacto y compatibilidad

Archivo oficial [CurseForge 1275257 / 8724947](https://www.curseforge.com/minecraft/mc-mods/mekanism-more-machine/files/8724947): `mekmm-1.21.1-1.4.1.jar`, mod ID `mekmm`, MIT, cliente y servidor. Descargado desde CDN oficial a `work/more-machine-audit/mekmm.jar`; SHA256 `58d4eb5630c179f54cea6c91180eb4c1ba27f70ef2417d73d78be76061791219`.

`META-INF/neoforge.mods.toml` del JAR requiere NeoForge `[21.1,)`, Minecraft `[1.21, 1.21.1]`, Mekanism `[10.7.19,)`, después de Mekanism; Evolved Mekanism es opcional. NeoForge 21.1.249 y Mekanism 10.7.19.85 satisfacen esos rangos. Esto es compatibilidad declarada, no arranque ni prueba binaria completa. El JAR contiene mixins/access transformer: no prometer ausencia de conflictos sin iniciar el pack. También se encontró la misma versión nominal en ATM10 local, sin modificarla ni usar sus configuraciones.

Fuente upstream fijada al [tag v1.4.1-1.21.1](https://github.com/lostmyself8/Mekanism-MoreMachine/tree/v1.4.1-1.21.1), commit `2d7ff00085c07ab8ca20c6955e6b6cb4daec9538`. Se inspeccionó ZIP de ese tag y bytecode del JAR con javap, sin compilar.

## Replicación real: README desactualizado

El [README upstream](https://github.com/lostmyself8/Mekanism-MoreMachine#replicator) afirma que no hay recetas predeterminadas. El JAR exacto contiene estos data maps activos:

| Archivo dentro de data/mekmm | Defaults |
|---|---|
| data_maps/item/item_replicator.json | `#c:stones`: 1 UU; `#c:ores`: 1 UU; `#c:ingots`: 5 UU; `#minecraft:logs`: 4 UU; `#minecraft:planks`: 1 UU |
| data_maps/fluid/fluid_replicator.json | tags `#c:water` y `#c:lava`: muestra de 1000, costo 1 UU, salida 500 |
| data_maps/mekanism/chemical/chemical_replicator.json | `mekanism:fissile_fuel`: muestra 1, costo 1 UU, salida 100; `mekanism:antimatter`: muestra 1, costo 249 UU, salida 1 |

`TileEntityReplicator.getRecipe` y `TileEntityReplicatingFactory.getRecipe` consultan el data map del holder. La muestra del ítem es una unidad y la salida una unidad. `ReplicatorCachedRecipe.finishProcessing` **no consume la muestra primaria**: consume el UU secundario y produce la salida. Confirmado en fuente y bytecode del JAR; no es una interpretación de JEI. Ese cached recipe sirve también a los replicadores de fluidos/químicos.

Costo base del replicador de ítems: 200 ticks, `machine-usage` → `replicator.itemReplicator=102400` de energía Mekanism por tick. Fluido/químico tienen el mismo default de energía por tick. No se traduce a FE ni se asume que los upgrades mantienen ese costo; hay que medir la configuración efectiva. Un costo energético alto no evita que los tags abran materiales raros de otros mods.

Cadena UU comprobada en JSON del JAR: `mekmm:nucleosynthesizing/uu_matter` consume 64 `mekmm:empty_crystal` y 2 unidades de antimateria, duration 5000, per_tick_usage=false, produce 1 ítem `mekmm:uu_matter`. Conversión química y oxidación producen 500 UU químico por ítem. Recycler acepta tierra/grass y `#c:stones` con chance .17, substrate con .43 para scrap. No se afirma una cadena directa scrap→UU sin examinar los demás pasos. La réplica de antimateria permite recuperar 2 unidades por 498 de esos 500 UU; es una ruta que merece análisis de bucles, no prueba de ganancia infinita por sí sola.

## Control exacto: data maps, no blacklist inventada

`MoreMachineGeneralConfig` conserva `[replicator_recipes]` con listas `itemReplicatorRecipe`, `fluidReplicatorRecipe`, `chemicalReplicatorRecipe` vacías por defecto. Las rutas activas getRecipe consultan data maps; la variante antigua basada en config está comentada y customRecipeMap no gobierna la ruta activa. **Vaciar esas listas no desactiva los defaults del JAR.** No se encontró una blacklist global de replicación en los configs inspeccionados.

Override recomendado, idéntico contenido para estos tres archivos del datapack de mayor prioridad:

- `data/mekmm/data_maps/item/item_replicator.json`
- `data/mekmm/data_maps/fluid/fluid_replicator.json`
- `data/mekmm/data_maps/mekanism/chemical/chemical_replicator.json`

```json
{"replace": true, "values": {}}
```

El codec `net.neoforged.neoforge.registries.datamaps.DataMapFile` de NeoForge 21.1.249 verifica campos reales `replace` (boolean), `values` (map) y `remove` (lista tags/IDs). `replace:true` limpia valores heredados de prioridad menor. Asegurar que ningún pack de prioridad posterior repueble el mapa. Para KubeJS la ruta equivalente empieza en `kubejs/data/mekmm/...`. Esto cubre máquina y fábrica de replicación porque comparten lookup; no bloquea uso de regalos de las demás máquinas.

Como protección de obtención/UI adicional, retirar recetas de salida `mekmm:replicator`, `mekmm:fluid_replicator`, `mekmm:chemical_replicator` y las fábricas `mekmm:{basic,advanced,elite,ultimate}_replicating_factory`. IDs de receta comprobados: `mekmm:replicator`, `mekmm:fluid_replicator`, `mekmm:chemical_replicator`, `mekmm:factory/{basic,advanced,elite,ultimate}/replicating`. Si se instala Evolved Mekanism, existen además `mekmm:compat/evolvedmekanism/factory/{dense,multiversal,overclocked,quantum,creative}/replicating`. Retirar crafting solo no sería suficiente ante upgrades/objetos existentes: el mapa vacío es la protección funcional principal. No hacer un blacklist parcial de materiales que quede obsoleto al añadir mods.

## Fábricas y progresión útil

El JAR incluye fábricas basic/advanced/elite/ultimate para oxidizing, dissolving, washing, crystallizing, centrifuging, liquifying, pressurised_reacting, pigment_extracting, painting y procesos propios. Basic oxidizing consume chemical_oxidizer, aleaciones/basic, circuitos/basic e hierro. Ultimate consume elite_oxidizing_factory, aleaciones/atomic, circuitos/ultimate y diamantes. Son ampliaciones paralelas de procesos existentes, apropiadas para ahorrar instalaciones cuando el costo y carga se comprueben.

Propuesta editorial de obtención normal: acto III primeras basic/advanced al conectar logística; acto IV elite vinculadas a instalaciones químicas funcionando; acto V ultimate/maestrías con una mejora propia del pack y la fábrica anterior. Mantener el uso libre de máquinas regaladas, no comandos que bloqueen uso por acto. No introducir tiers creative ni Evolved Mekanism sólo para obtener más tiers. No se afirma ganancia de rendimiento: comparar una fábrica con las máquinas equivalentes, incluyendo energía, outputs y tick cost.

Otro riesgo independiente: 139 recipes del JAR bajo `compat/mysticalagriculture/planting/` y `compat/mysticalagradditions/planting/`, tipo `mekmm:planting`. Incluyen semillas muy avanzadas; usan nutrient_solution y están condicionadas a mods presentes. Ejemplo exacto: `mekmm:compat/mysticalagradditions/planting/awakened_draconium`, consume muestra de awakened_draconium_seeds y genera su essence con nutrient_solution por tick. Retirar inicialmente ambos prefijos de recipes y reintroducir una lista explícita por etapas después del balance de granjas. No deshabilitar todo planting: comida y botánica conservan un papel útil. No copiar recipes/configs de ATM10.

## Gate de integración y límites

Antes de pasar de candidato a jugable: arranque con Mekanism 10.7.19.85; verificar maps vacíos tras carga y /reload, replicador y fábrica sin receta para hierro, mineral de otro mod, combustible fisible y antimateria; comprobar ausencia de las rutas MA excluidas; crafting y upgrades de fábricas permitidas; guardar/reconectar y comparar producción/energía. Buscar entradas activas con JEI y consulta servidor real, no sólo JSON. Revisar otras máquinas/generadores añadidos antes de autorizar sus rutas avanzadas.

Si el integrador no puede aplicar y comprobar estos overrides en esta iteración, **aplazar este addon**, conservando Mekanism base. Motivo concreto: default de replicación por tags y químicos más aceleración MA, no una incompatibilidad declarada ni rechazo genérico de máquinas adicionales. No hubo prueba de rendimiento, cliente o servidor ni cambios de catálogo durante esta auditoría.

## Preparación aplicada (sin instalar el addon)

Se añadieron los tres overrides vacíos en `pack/kubejs/data/mekmm/data_maps/` y `entrelumen_more_machine_balance.js`: siete recetas de replicación exactas y los dos prefijos MA retirados, con patrón acotado para fábricas replicating opcionales de Evolved. No se eliminan fábricas oxidizing normales ni cultivos vanilla.

El comando de operador nivel 2 `/entrelumen_more_machine_audit` consulta los tres data maps vivos con `Registry.getDataMap`, verifica holders reales iron_ingot/water/fissile_fuel/antimatter con `Holder.getData`, y lee RecipeManager para comprobar recetas prohibidas ausentes y dos fábricas oxidizing conservadas. Emite `[ENTRELUMEN_MORE_MACHINE]` en el log. Ejecutarlo después del arranque completo y nuevamente después de completar `/reload`; no hay sondeo permanente ni se supone un orden de reload a partir de afterRecipes. El comando no cambia mapas, recetas, inventarios ni progreso.

APIs verificadas en JAR local: KubeJS build.374 `ServerEvents.COMMAND_REGISTRY`, `CommandRegistryKubeEvent.register/getCommands`; NeoForge `IRegistryExtension.getDataMap`; Mekmm `IMoreMachineDataMapTypes.INSTANCE` y sus tres accessors. Clases opcionales se resuelven dentro del comando, luego de comprobar el ítem registrado, para no romper la carga cuando el addon todavía no está instalado. La conversión Rhino/Java requiere aún ejecución real; cualquier excepción produce FAIL, nunca un recibo favorable por inspeccionar JSON fuente.

`python tools/check_more_machine_balance.py --self-test` verifica estáticos y rechazo de recibos sintéticos defectuosos. `node --check` y un harness sintético ejecutaron los filtros/permiso y comprobaron que conservan fábricas normales. No son evidencia runtime. Tras usar el comando, `python tools/check_more_machine_balance.py --log <server.log>` valida únicamente el último recibo real presente: tres mapas vacíos, cuatro probes negativos, cero recetas prohibidas y fábricas conservadas. Un último FAIL/malformado nunca cae hacia un PASS anterior. Falta instalar y ejecutar esta verificación real de arranque/reload; no se modificó catálogo ni instancia.

## Selección en catálogo (131 JAR cliente)

More Machine 1.4.1 quedó seleccionado para ambos lados con projectID 1275257/fileID 8724947 y el SHA256 auditado, reutilizando la descarga oficial en catalog/downloads. Se agregó la intención a tools/curate_pack.py y la referencia externa verificable. Las 130 entradas previas se compararon como objetos completos antes/después y permanecieron iguales; no se refrescó todo el catálogo desde ATM10 ni se escribió en la instancia viva.

Rol III–V: fábricas químicas adicionales con obtención escalonada. Los tres overrides y retiradas de recipes ya están en el contenido del pack; su aplicación efectiva aún necesita el comando de auditoría tras arranque y reload. Seleccionado no significa aceptado en runtime.

Verificación de catálogo: hashes, referencias y cierre de dependencias requeridas para cliente y servidor. TOML del addon pide sólo Minecraft, NeoForge y Mekanism como requeridos; las versiones fijadas satisfacen sus rangos declarados. Evolved Mekanism permanece opcional y no fue incorporado por este cambio. No se ejecutó Minecraft ni Gradle en esta operación.

## Corrección del auditor Rhino tras primer arranque real

El integrador obtuvo un recibo FAIL con `InternalError: TypeError: redeclaration of var Types` en la carga de `IMoreMachineDataMapTypes`. No demuestra mapas activos ni vacíos: el auditor se interrumpió antes de leerlos. Bytecode de Rhino 2101.2.8-build.91 confirma que `ScriptableObject.putConstImpl` emite `msg.var.redecl` al encontrar un slot existente sin atributo readonly. No se estableció que `Types` sea una palabra reservada ni un global específico.

El callback usa ahora variables locales `var` y nombres de clases prefijados `entrelumenAudit*`, evitando la inicialización const problemática dentro del try. Se conserva la resolución opcional de clases sólo al invocar el comando, los cuatro holders, tres mapas, recetas, permisos y fallo cerrado. El checker incluye el error observado entre los recibos que debe rechazar. Esta corrección necesita un nuevo recibo real tras integrar el script y repetir arranque/reload; las pruebas estáticas no acreditan conversión Java/Rhino ni aceptación de MoreMachine.
