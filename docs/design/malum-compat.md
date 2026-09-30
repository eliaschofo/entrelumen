# Malum 1.8.2: reparación de tizas Occultism

El log dedicado del 2026-09-12 18:13 muestra cuatro errores `No key validItems`: `malum:malum/spirit_repair/occultism/{gold,purple,red,white}_chalk`.

Causa comprobada en el JAR fijado: esos cuatro JSON originales usan `inputs`. `SpiritRepairRecipe.lambda$static$5` define `BuiltInRegistries.ITEM.holderByNameCodec().listOf().fieldOf("validItems")`; el schema KubeJS embebido `data/malum/kubejs/recipe_schema/spirit_repair.json` también usa `validItems`. Es dato upstream obsoleto, no una transformación incorrecta de KubeJS.

**Cambio del 30/9: ya no se copia ningún archivo de Malum.** Malum es «todos los derechos reservados» y el repositorio es público (`tools/check_loot_tables.py --copies`). `tools/generate_malum_compat.py --write` escribe los cinco archivos de Malum (las cuatro tizas y `create/milling/grim_talc`) como la condición `neoforge:false` sola, y `pack/kubejs/server_scripts/entrelumen_malum_compat.js` agrega nuestras propias recetas con `ServerEvents.recipes` y `event.custom(...).id(...)` bajo los mismos cinco IDs, sólo si el mod correspondiente (`occultism`, `create`) está cargado. Las recetas salen de las especificaciones del generador (`SPECS`), con `validItems` donde el JAR trae `inputs` y `id` donde trae `item`. Mismo comportamiento que antes: lista de tizas, condición, reparación 100%, ocho espíritus arcanos y materiales polvo de oro×1, obsidiana×2, esencia afrit×1 u otherstone quemada×1. Conserva tipo, ID y comportamiento del Repair Pylon; no quita recetas ni suprime mensajes.

`--check` compara hash del JAR con lock, verifica schema nativo, cuatro entradas esperadas, que cada archivo de Malum sea igual a nuestra especificación con las claves viejas puestas de vuelta (mismas condiciones, mismos números) y paridad determinista de los stubs y del script. Falla si upstream cambia para exigir revisión del parche, en vez de perpetuar una corrección innecesaria.

Integrador: copiar los stubs y el script y recargar datapacks/reiniciar en su siguiente prueba. Comprobar que el stub deja sin receta el ID de Malum y que la receta de `event.custom` ocupa su lugar sin aviso de ID duplicado. Verificar cuatro recetas sin errores y reparación real de cada tiza consumiendo sus materiales/espíritus con durabilidad esperada. La comprobación estática no ejecuta el Codec ni demuestra reparación runtime.

Se incluye también `malum:create/milling/grim_talc`: sus tres resultados del JAR usan `item`. Create 6.0.10 `ProcessingOutput.lambda$static$20` construye CODEC_NEW con `ITEM_CODEC.fieldOf("id")`, count opcional 1 y chance opcional 1. Su receta nativa `data/create/recipe/milling/bone.json` confirma el formato. Nuestra receta usa la clave `id` en cada resultado, preservando grim_talc de entrada, condición Create presente, 100 ticks, seis bone_meal garantizados, yellow_dye al 25% y cuatro bone_meal al 25%. No cambiar la clave item del ingrediente.

`--log <latest.log fresco>` exige recibo RecipeManager `Loaded N recipes` y falla si cualquiera de los cinco IDs tiene parsing error. No confundir ausencia de error con existencia/crafting verificado: el integrador debe comprobar registro y maquinaria. El log previo falla como corresponde.

## Inventario SHA256: bytes originales JAR → nuestra receta (JSON ordenado, sin `type` ni condiciones)

```text

malum:malum/spirit_repair/occultism/gold_chalk | d5fe471996743c765b28a01cd0d6bf47c658f6b0263617b2e2b26a47c168fdf0 | ebcb8c9b6e43544494d3579d00ced5ee168d6e37540315e7434d2ff4dd4b87fb
malum:malum/spirit_repair/occultism/purple_chalk | fe05639744fbe467f96276fda7923511ce380c5b83c06ba1424542d418b0795a | 67adfb75a44ba672021765859d2a9a738ccb2f228eeb13fa68cedc032df323d0
malum:malum/spirit_repair/occultism/red_chalk | 0b6458248eaae418ed8410aff8b9a4448eda7ca5ca062a576487f045b1127dce | 4c9d893b1afab6d919daee9f9136b5cea3d8d6cbacecb4b51212f83ba6ff6c8e
malum:malum/spirit_repair/occultism/white_chalk | 7ba9217c8a4088794cc2cd03723eafedc86839c661775b975ad810a8425ce7a6 | 0b69d329cd5e55053a94b8f0efb3f11818198d9d5a798deee9ab207cce33628a
malum:create/milling/grim_talc | a1a75ba95154c23d99ac1e5122eb11bfe953b36eb22c9254539679418117657e | acbb1c2283c72577b7fd24ce3f1801031313366850f58592db2bc257ecbd4a28
PASS: five exact recipe IDs; source hash, native schema and all non-key semantics preserved.
```


## Auditoría del RecipeManager después del reload

La comprobación basada únicamente en errores queda reemplazada por un recibo positivo del objeto RecipeHolder cargado. `entrelumen_malum_audit.js` usa `ServerEvents.afterRecipes` y `event.forEachRecipe({id}, callback)` del KubeJS 2101.7.2-build.374 real. El bytecode de AfterRecipesLoadedKubeEvent obtiene los holders del mapa del RecipeManager. RecipeHolderKJS expone getRecipe/getSerializer (remapeo kjs$); se serializa ese objeto mediante su Codec nativo con JsonOps. No se lee el JSON de override para simular el estado vivo. El script no modifica recetas.

Antes de cada ensayo elegí un token nuevo (por ejemplo UUID sin guiones) y ejecutá:

```powershell
python tools/generate_malum_compat.py --write --audit-token TOKEN_NUEVO_123
```

Instalá el script generado junto con overrides y ejecutá `/reload`. Después:

```powershell
python tools/generate_malum_compat.py --check --audit-token TOKEN_NUEVO_123 --log RUTA_AL_LOG_FRESCO
```

El checker exige último begin, token nuevo esperado, firma de campos, mismo run en todos los registros, cinco IDs únicos y resumen completo sin errores. Compara todos los campos semánticos esperados del Codec: tizas, espíritus/materiales/durabilidad y grim_talc/tiempo/resultados/cantidades/chances. Sólo normaliza defaults nativos count=1/chance=1; un resultado cambiado falla. Rechaza recarga posterior visible sin nuevo recibo. Nunca retrocede a un PASS anterior. Los tokens deben ser nuevos antes de instalar y recargar; conservar el token viejo no prueba una nueva ejecución.

Esto verifica presencia y valores de recetas reales, no consumo práctico del Pylon o Millstone. El primer ensayo debe confirmar también que Rhino expone las llamadas Codec y que JsonOps dispone del contexto requerido; un error genera FAIL explícito, no una aprobación parcial. No se ha ejecutado en el servidor durante esta tarea.
