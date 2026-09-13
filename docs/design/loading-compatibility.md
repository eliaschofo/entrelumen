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
