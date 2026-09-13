# QoL: tanda conservadora 1

Fecha: 2026-09-12. Selección lista para prueba aislada; no instalada por este cambio.

131 JAR anteriores conservados, sin sustituciones ni cambios de SHA256. Se agregan siete: **138 cliente / 109 servidor**, cero dependencias obligatorias adicionales y cero candidatos excluidos por incompatibilidad de metadatos. Cuatro son exclusivamente visuales del cliente; Clean Swing, Akashic Tome y Crafting on a Stick se incluyen en ambos lados.

| Mod | Versión JAR | Lado | Licencia declarada en JAR | Fuente |
|---|---|---|---|---|
| Akashic Tome | 1.8-30 | both | Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License | [CF 250577/7773841](https://www.curseforge.com/minecraft/mc-mods/akashic-tome) |
| Better Ping Display | 1.1 | client | MIT | [Modrinth ZvaHbwoZ/AzKubcBR](https://modrinth.com/mod/ZvaHbwoZ) |
| Chat Heads | 0.15.7 | client | MPL-2.0 | [Modrinth Wb5oqrBJ/ZPylso9i](https://modrinth.com/mod/Wb5oqrBJ) |
| Cherished Worlds | 10.1.1+1.21.1 | client | LGPL-3.0-or-later | [Modrinth 3azQ6p0W/o5lwJaRU](https://modrinth.com/mod/3azQ6p0W) |
| Clean Swing | 1.9 | both | All Rights Reserved | [CF 915308/6153780](https://www.curseforge.com/minecraft/mc-mods/clean-swing-through-grass) |
| Crafting On A Stick | 1.21.0.6 | both | GNU GENERAL PUBLIC LICENSE | [CF 577850/8382581](https://www.curseforge.com/minecraft/mc-mods/crafting-on-a-stick) |
| SmithingTemplateViewer | 1.0.4 | client | MIT | [CF 1133580/7452053](https://www.curseforge.com/minecraft/mc-mods/smithing-template-viewer) |

## Evidencia y alcance

- Los cuatro archivos presentes en la instancia de referencia se leyeron sin modificarla. Sus SHA1 coinciden con la metadata oficial guardada por CurseForge. Sólo se reutilizan JAR; ninguna configuración, quest, texto o script de otro pack.
- Chat Heads `ZPylso9i`, Better Ping Display `AzKubcBR` y Cherished Worlds `o5lwJaRU` se descargaron desde el CDN oficial Modrinth. Las API de versión confirman NeoForge y Minecraft 1.21.1; se verificó SHA1 oficial antes de incorporarlos. Rutas locales en `catalog/local-paths.json`, fuera de publicación. SHA256 de los siete en el lock.
- Estos tres conservan IDs Modrinth explícitos, con projectID/fileID de CF nulos. El check acepta referencias oficiales completas por proveedor; no representa un manifiesto CurseForge válido. El exportador oficial debe resolver sus archivos CF antes de publicación. El acceso HTML directo devolvió 403; no se intentó eludirlo.
- Los siete TOML aceptan Minecraft 1.21.1 y NeoForge 21.1.249. No hay JarJar en estos siete ni dependencias obligatorias adicionales. Chat Heads declara Cloth Config opcional en la API; no se añade otra biblioteca para un ajuste opcional.
- Clean Swing 1.9 tiene licencia JAR `All Rights Reserved`, aunque la página del proyecto actual anuncia LGPLv3 y 1.10. Se conserva la declaración del binario seleccionado; la metadata CF permite distribución. No se presume que una licencia nueva relicencie este archivo.
- Clean Swing maneja `PlayerInteractEvent.LeftClickBlock` y cancela el evento en ambos lados; la llamada de ataque está guardada por `Level.isClientSide`. Se conserva instalación cliente y servidor de acuerdo con la página oficial. La clase no modifica el valor de daño. No se afirma inmunidad a interacciones con otros mods de combate.
- Smithing Template Viewer declara NeoForge/MC en TOML y contiene plugins JEI y EMI; ambos ya están presentes. Se selecciona como cliente por su función de presentación. No añade estaciones ni recetas de obtención.
- El refresh conserva las entradas oficiales Modrinth ya fijadas leyendo su cache y rechazando cambios de hash. No migra ni inventa referencias entre proveedores.

## QA que debe ejecutar el integrador

1. Arranque cliente y dedicado con ambos idiomas; comprobar ausencia de errores y dependencias faltantes. Esta tanda no cambia el preset de controles ni FancyMenu.
2. Atacar un objetivo entre hierba con y sin ella, en solo y cooperativo; respetar alcance, cadencia y permisos. No aceptar ataques dobles ni roturas bloqueadas fuera del caso previsto.
3. Crafting on a Stick: fabricar estación portátil con sus recetas nativas, mover ingredientes y cerrar interfaz sin pérdida/duplicación. Probar mesa y yunque con sus costos.
4. Smithing Viewer: ver moldes en EMI/JEI sin duplicación de panel ni errores al pasar el cursor.
5. Akashic Tome: insertar y recuperar guías ya adquiridas; verificar conservación de datos de guías del pack. No otorgar todos los libros automáticamente.
6. Chat Heads: dos jugadores escriben; alineación legible y mensajes multilínea. Ping Display: Tab muestra latencia sin cambiar su orden; comprobar ambos overlays juntos.
7. Cherished Worlds: marcar mundo de prueba, salir/volver a la lista, confirmar prioridad y protección de borrado; desmarcar permite control normal. Verificar lista con FancyMenu, sin borrar un mundo real.

## Verificación realizada

`python tools/curate_pack.py --refresh <instancia fuente> --check` pasó con 138 JAR y cero ausentes. El check valida hashes locales, SHA1 oficial cuando está disponible, referencias y cierre obligatorio cliente/servidor; no sustituye arranque ni benchmark. Preservación de los 131 SHA256 anteriores comprobada antes de escribir. No se instalaron archivos en el perfil activo.
