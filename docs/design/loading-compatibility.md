# Compatibilidad de pantallas de carga

2026-09-12. Drippy Loading Screen 3.1.5 incorporado como candidato de cliente: **139 JAR cliente / 109 servidor**, conservando exactamente los 138 SHA256 anteriores. Ninguna instalación ni configuración de perfil modificada.

## Binario y dependencias

Archivo `drippyloadingscreen_neoforge_3.1.5_MC_1.21.1.jar`, proyecto CurseForge **511770**, archivo **8552907**. Descarga efectiva desde [CDN oficial](https://edge.forgecdn.net/files/8552/907/drippyloadingscreen_neoforge_3.1.5_MC_1.21.1.jar), idéntica por SHA1 al archivo de la [API oficial Modrinth MVgsDsSB](https://api.modrinth.com/v2/version/MVgsDsSB): `9d27991a7f5a551265692abf8ed40ece10ea1958`. El ID de archivo se corroboró mediante la descarga oficial y su contenido; la página de archivo no estuvo disponible en web. [Proyecto oficial CF](https://www.curseforge.com/minecraft/mc-mods/drippy-loading-screen).

TOML: `drippyloadingscreen` 3.1.5; Minecraft >=1.21.1, NeoForge >=21.1.47, FancyMenu >=3.9.9, Konkrete >=1.9.0. Los cuatro mínimos están satisfechos. No JarJar ni nueva biblioteca; Melody está presente a través de FancyMenu. Sólo cliente. Licencia exacta JAR: **DSMSLv3.1**; la [licencia del proyecto](https://github.com/Keksuccino/Drippy-Loading-Screen/blob/main/LICENSE.md) exige distribución mediante referencia oficial, sin reempaquetar el JAR público. El cache local se conserva únicamente para desarrollo; no colocarlo en overrides publicados.

## Origen real del Pro-Consejo

Es **Aether 1.5.10**, no Cumulus: su `assets/aether/lang/es_es.json` define `gui.aether.pro_tip = Pro-Consejo:`. `GuiHooks.drawTrivia(Screen, GuiGraphics)` dibuja el texto sobre `GenericMessageScreen`, `LevelLoadingScreen` y `ReceivingLevelScreen` cuando `AetherConfig.STARTUP.enable_trivia` vale true. La configuración generada del perfil confirma:

```toml
# config/aether-startup.toml
[Gui]
"Enables random trivia" = true
```

Parche recomendado al integrador: cambiar **sólo esa clave a false**, reiniciando el cliente por tratarse de startup config. Preserva dimensión, recetas, accesorios, música y todas las funciones jugables. No hace falta retirar Aether ni Cumulus. Este cambio elimina el texto; no promete eliminar el blur.

El fondo desenfocado no queda atribuido por esa evidencia: drawTrivia sólo dibuja texto. No hay prueba de otro mod de carga responsable. Puede ser el fondo nativo de una pantalla transitoria; identificar pantalla/render durante QA antes de tocar blur global o más opciones.

## Cumulus y Drippy

La configuración actual de Cumulus mantiene Menu API activa, menú `cumulus_menus:minecraft`, world preview desactivado y botones de edición ocultos. Cumulus controla selección del título/preview; Drippy personaliza splash y recarga de recursos mediante FancyMenu. No hay conflicto real demostrado entre ellos por estas lecturas. No desactivar Menu API para resolver trivia de Aether.

La [lista oficial de incompatibilidades](https://github.com/Keksuccino/Drippy-Loading-Screen/wiki/Incompatibility-List) menciona Memory Usage Screen, Dash Loader, Dark Loading Screen y Remove Reloading Screen; ninguno integra el catálogo. Cumulus/Aether no aparecen allí. Esto es evidencia negativa limitada, no garantía de compatibilidad. El solapamiento visual comprobado es trivia de Aether sobre pantallas que también personaliza FancyMenu.

## Verificación y QA pendiente

`curate_pack.py --check`: hash SHA256 local, SHA1 oficial, referencias y cierre de dependencias aprobados; 139 cliente, 109 servidor. El refresh conserva el archivo oficial de cache fijado. No se alteraron las versiones de los 138 anteriores.

El integrador debe revisar arranque, recarga F3+T, entrada/salida de mundo y conexión/cancelación en EN/ES. Confirmar progreso real, ausencia de texto superpuesto y retorno al menú sin destellos/errores. Agregar Drippy no crea por sí mismo el layout original ni cubre la ventana temprana NeoForge: Drippy Early Loading Module es una integración separada y no fue agregado.

## ModernFix: recursos dinámicos (1 de octubre de 2026)

`pack/config/modernfix-mixins.properties` fija `mixin.perf.dynamic_resources=true`. ModernFix 5.27.20 la trae apagada por defecto (`ModernFixEarlyConfig` la declara en `false`). Motivo: según la revisión adversaria, 379 JAR casi llenan un heap de 6 GB (ya desde unos 314); esta opción carga los modelos horneados bajo demanda en vez de todos al arrancar. All the Mods 10 corre con este mismo JAR (`modernfix-neoforge-5.27.20+mc1.21.1`) y la misma opción en `true` (su `config/modernfix-mixins.properties` local; además pone `stability_level=BETA`, que acá no se copia porque esta opción no lo necesita).

### Incompatibilidades conocidas, leídas en el JAR fijado y contrastadas con los 378 JAR (`catalog/curated.json`)

- Connectedness: `ModernFixClientForge` trae el aviso de carga `modernfix.connectedness_dynresoruces` ("Connectedness y los recursos dinámicos no son compatibles") para ese caso. No está en el catálogo.
- Parches propios de la opción: `ctm` (CTM, `perf.dynamic_resources.ctm.*`) y `ldlib` (LDLib, `ldlib.ClientProxyImplMixin`). Ni CTM ni LDLib están en el catálogo, así que no se aplican. El idioma del JAR nombra además parches para AE2, Refined Storage, SuperMartijn642 Core Lib y Diagonal Fences; este JAR no trae ninguna clase para ellos (la lista de mixins sólo tiene `ctm` y `ldlib`). AE2, Refined Storage y SuperMartijn642 Core Lib sí están en el pack; Diagonal Fences no.
- `ModernFixEarlyConfig` no apaga la opción por ningún mod presente (a diferencia de `dynamic_dfu`, `cache_strongholds` y otras, que sí se apagan solas con ciertos mods): ninguno de esos casos toca `dynamic_resources`.
- La [FAQ de recursos dinámicos](https://github.com/embeddedt/ModernFix/wiki/Dynamic-Resources-FAQ) no nombra mods incompatibles; advierte de cierre al iniciar o texturas faltantes en modelos de mods que alteran el sistema de modelos con APIs no estándar.

### Riesgo sin resolver

Ninguna prueba de arranque corrió: no hay evidencia propia de que alguno de los 378 mods use una API de modelos no estándar. Los candidatos son los que cambian modelos o texturas por su cuenta (Fusion, texturas conectadas de Create y sus complementos, modelos de LittleTiles y Framed Blocks, Sodium/Iris). Hay que mirar en el primer arranque real: texturas faltantes o morado y negro, el log de ModernFix y el uso de heap en el menú. Si algo falla, borrar el archivo o poner la línea en `false` basta.

## Distant Horizons 3.3.2 (3 de octubre de 2026)

Decisión de Elias del 3/10: Distant Horizons (DH) sólo en el cliente, prendido por defecto y sólo en el Overworld ([mod-pingpong](mod-pingpong.md#distant-horizons-310)). Esta sección junta lo que se leyó en los JAR fijados; no corrió ningún arranque.

### Binario y dependencias

- `DistantHorizons-3.3.2-1.21.1-fabric-neoforge.jar`: CurseForge 508933/8943824, Modrinth `uCdwusMi`/`Ez3cx7Yd`. Bajado del CDN de Modrinth; SHA-1 `d18b0829416ec6948102524b6e913e5bc73ae402` y SHA-512 iguales a los de su API; el registro de CurseForge da el mismo nombre y 28.224.156 bytes. Licencia LGPL-3.0. No va en el repo ni en overrides: el lock referencia el archivo.
- `neoforge.mods.toml`: `clientSideOnly = "true"`, Minecraft `[1.21],[1.21.1]`, NeoForge `[*,)` y una sola incompatibilidad, SSRD `[*,1.8.6]`, que no está en el lock. Ningún JAR del pack declara a DH. La familia `catalog/families/distant-horizons.json` lo marca `clientOnly`: el lock pasa a **378 cliente**; el servidor y el server pack no cambian.
- El JAR trae Fabric API en `META-INF/jars` para el lado Fabric; no tiene `META-INF/jarjar`, así que NeoForge no lo carga.
- DH no registra teclas fuera de las de depuración (F6 a F8), que dependen de `enableDebugKeybindings` y van apagadas.

### Chunky apaga el generador de DH: Chunky pasa a ser sólo del servidor

`AbstractModInitializer.logIncompatibilityWarnings` de 3.3.2 busca la clase `org.popcraft.chunky.api.ChunkyAPI`. Chunky 1.4.23 la trae. Si la encuentra, DH fija por su API `generatorPlan = DISABLED` y `disableUnchangedChunkCheck = true` (origen «Distant Horizons / Chunky»), por encima del archivo; su pantalla lo muestra bloqueado. Además avisa en el log que Chunky puede dejar huecos.

Con Chunky en el cliente, DH no generaría terreno lejano: sólo armaría LODs de chunks ya existentes. Elias decidió el 3/10 que Chunky quede sólo en el servidor: la familia lo marca `serverOnly`, el lock le pone el lado `server` y `tools/curate_pack.py` lo deja fuera de toda instalación de cliente (el servidor y el server pack lo siguen trayendo; `--check` falla si un mod de cliente lo pide). Así DH genera el terreno lejano con el `SURFACE_THEN_CHUNKS` y `FEATURES` del archivo sembrado: primero la superficie, después los chunks con árboles y aldeas. En un mundo de un jugador ya no hay `/chunky`; en un servidor sin DH, el cliente sólo arma LODs de lo que recibe.

### Mixins frente al pack

DH 3.3.2 en NeoForge inyecta, en 1.21.1 (los demás mixins de su lista están vacíos para esta versión, entre ellos el de Twilight Forest):

- Servidor (también el integrado): `ChunkMap.save` al volver, `ServerPlayer.changeDimension` al empezar y `setServerLevel` al volver, `Util.backgroundExecutor` y `wrapThreadWithTaskName` al empezar.
- Cliente: `ClientPacketListener.handleLogin` y `close`, `DebugScreenOverlay.getSystemInformation`, `FogRenderer.setupFog` al volver, `LevelRenderer.renderSectionLayer` al empezar, `LightTexture.updateLightTexture` al volver, `OptionsScreen.init` al volver (el botón), `Minecraft.close`, un `@Redirect` de `Runnable.run` dentro de `Minecraft.onGameLoadFinished`, `Main.main` (sólo carga RenderDoc en desarrollo) y un `@Redirect` en `GlFramebuffer.addDepthAttachment` de Iris. El de Immersive Portals no aplica: no está.

Contra el índice de 5.603 inyecciones de los 377 JAR anteriores (con los anidados), ningún otro mod redirige las mismas llamadas, así que no aparece un choque de carga. Coinciden en el mismo método:

- `FogRenderer.setupFog`: Iris al empezar, Sodium Extra al final y DH al volver. Tres mods escriben la niebla: hay que mirarla.
- `LevelRenderer.renderSectionLayer`: Iris (al empezar y al volver), Sable, Create Aeronautics y Veil (inyecciones en el medio y un `@Redirect` del perfilador). DH entra al empezar; el orden de dibujo con las estructuras de Sable no se puede leer sin correrlo.
- `LightTexture.updateLightTexture`: Iris, Just Dire Things y Tombstone envuelven llamadas internas; DH lee al volver.
- `DebugScreenOverlay.getSystemInformation` (seis mods suman líneas al F3), `ClientPacketListener.handleLogin` y `close`, `Minecraft.close`, `ServerPlayer.changeDimension` (Moonlight, sin cancelar) y `Main.main` (Veil): sólo agregan, sin pisarse.
- `OptionsScreen`: Sodium inyecta en la lambda del botón de video, no en `init`; el botón de DH queda a la izquierda del campo de visión.
- El access transformer de DH abre campos (por ejemplo `ThreadingDetector.lock` sin `final`); los transformers se suman.

### Con los mods de render del pack

- **Sodium 0.8.13:** sin relación declarada de ningún lado. DH dibuja en su propio pase. Sin evidencia de choque; se ve en juego.
- **Iris 1.8.14-beta:** las 108 referencias de `net/irisshaders/iris/compat/dh` a clases y miembros de DH resuelven contra 3.3.2. Con Iris presente, DH fuerza por API el motor `OPEN_GL` (el archivo deja `AUTO`) y la transparencia `COMPLETE`. Si alguien elige a mano el motor `BLAZE_3D` con Iris instalado, DH muestra un diálogo y cierra el juego; el archivo no toca `renderingEngine`. Con un shader pack, los LODs salen sin textura (DH #1316, abierto: Iris 1.10 y anteriores).
- **ImmediatelyFast 1.6.13:** sólo comparte el método del F3. Que 1.6.14 arregle un estado de profundidad con DH no se verificó.
- **ModernFix con `dynamic_resources=true`:** ningún JAR nombra al otro. DH arma colores y modelos desde hilos propios; si un modelo se carga tarde, el LOD puede salir morado o negro. Se ve en juego.
- **Flywheel** (en Create 6.0.10 y Aeronautics): no comparte ningún método con DH. Los bloques con entidad no van a los LODs.
- **Sable y Create Aeronautics:** `SableConfig` deja `sub_level_tracking_range` en 320 bloques y el pack no lo cambia. Pasada esa distancia el barco deja de existir en el cliente mientras el terreno lejano sigue. Sable #376 (abierto) además muestra estructuras tapadas por los LODs.
- **LittleTiles:** sus construcciones no aparecen en los LODs (DH #1301, abierto).
- **Chunky 1.4.23:** sólo en el servidor, ver arriba. 3.3.2 no trae el `ChunkyAccessor` de 3.3.3 que tira «Chunky is not loaded» (#1329).

### Dimensiones

`ignoredDimensionCsv` se compara nombre por nombre (`equalsIgnoreCase`, sin recortar espacios) y no admite comodines. El archivo sembrado lleva las 29 dimensiones que registran los JAR fijados y el companion, menos el Overworld: Nether, End, Aether (el Deep Aether vive ahí), Twilight Forest, Undergarden, Bumblezone, Eternal Starlight, los cinco planetas y las cinco órbitas de Ad Astra, Compact Machines, Solsticio, Envés, la Otherside de Deeper and Darker, el bolsillo de Iron's Spells, la Reality Marble de Mahou Tsukai, el calabozo de Neo Vitae, las tres de JAMD y el almacenamiento espacial de AE2 (registrado en código). Las dimensiones del planarium de Ars Nouveau tienen nombres al azar (UUID) y no se pueden listar; el jugador no entra en ellas. `tools/test_generate_client_defaults.py` falla si un JAR fijado o el companion suma una dimensión que la lista no tiene.

### Otros riesgos

- **Hilos:** el preset LOW_IMPACT no se guarda en el archivo (es sólo de la pantalla) y vale el 25 % de los hilos de la CPU, redondeado para arriba. El archivo fija 3 hilos al 100 %, lo que LOW_IMPACT da con 9 a 12 hilos. En una CPU de 4 hilos, 3 es casi todo; la pantalla muestra «Custom».
- **Calidad:** igual, MEDIUM no se guarda. El archivo lleva los valores de MEDIUM con SSAO apagado; la pantalla muestra «Custom».
- **Avisos:** los avisos de chat de DH van apagados (memoria, recolector, compatibilidad). El log los sigue escribiendo.
- **Memoria y GPU:** los búferes de DH van fuera del heap; la GTX 1070 de Elias ya tiene reinicios de driver sin DH. Se mide en la fase final.
- **Respaldo:** la base de DH queda dentro de cada mundo y SimpleBackups la comprime con el resto. No se verificó si se puede excluir.
- **Cambio de dimensión:** DH #1279 (niveles viejos que no se liberan) figura cerrado; se mira igual.
