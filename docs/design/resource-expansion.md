# Recursos, macetas y apiarios

Estado: dependencias fijadas en catálogo; balance, instalación y pruebas de juego pendientes. No se modificó ninguna instancia activa. Los 125 JAR anteriores conservan sus hashes y versiones; el catálogo suma cinco JAR, hasta 130.

## Selección verificada

| Mod / ID | Versión NeoForge 1.21.1 | Proyecto / archivo CurseForge | Dependencias relevantes |
|---|---|---|---|
| JAMD / `jamd` | 21.1.1 | [422981 / 6280200](https://www.curseforge.com/minecraft/mc-mods/jamd/files/6280200) | Trenzalore >=6.1.0 |
| Trenzalore / `trenzalore` | 6.1.1 | [870210 / 5623160](https://www.curseforge.com/minecraft/mc-mods/trenzalore/files/5623160) | Minecraft 1.21.1, NeoForge |
| Botany Pots / `botanypots` | 21.1.44 | [353928 / 8243851](https://www.curseforge.com/minecraft/mc-mods/botany-pots/files/8243851) | Bookshelf >=21.1.75; Prickle 21.1.x |
| Botany Pots Tiers / `botanypotstiers` | 7.0.11 | [526754 / 7878798](https://www.curseforge.com/minecraft/mc-mods/botany-pots-tiers/files/7878798) | Botany Pots >=21.1.41; Bookshelf y Prickle |
| Modular Bees / `modularbees` | 3.4 | [1350380 / 8705124](https://www.curseforge.com/minecraft/mc-mods/modular-bees/files/8705124) | Productive Bees >=13.12.0; Glodium >=2.0 |

«Abejas modulares» tiene correspondencia exacta: Modular Bees, complemento de Productive Bees. No reemplaza la cría inicial. Bookshelf 21.1.81, Prickle 21.1.11, Glodium 2.2 y Productive Bees 13.13.5 ya estaban presentes y satisfacen los requisitos declarados. NeoForge 21.1.249 supera los mínimos 21.1.209/214 de estos agregados.

Se reutilizaron únicamente JAR y metadatos oficiales locales para macetas y abejas; JAMD y Trenzalore se descargaron de forgecdn. SHA256 local queda en `catalog/curated.json`; para los dos descargados no se inventa un hash publicado por CurseForge. Botany Pots/Tiers declaran LGPL2.1 y Modular Bees LGPL3.0. Las licencias propias de JAMD/Trenzalore se conservan literalmente en el catálogo; la distribución oficial será mediante referencias CurseForge, sin relicenciar sus JAR.

## Integración de progresión propuesta

Estos cambios de recetas todavía NO están implementados. El acto describe la obtención normal; regalos y uso de objetos siguen libres.

- Acto I: maceta básica para alimentos y flores, conectada a cocina y preparación de expediciones. Cosecha manual inicial; no agregar Botany Pots Mystical ni Botany Trees implícitamente.
- Acto II: macetas con tolva y portal minero mediante fabricación mecánica. JAMD separa la cantera del paisaje habitado; sus capas planas son una elección de diseño, no evidencia de rendimiento.
- Acto III: primer tier de macetas para abastecer cocinas y componentes botánicos. Integrar su mejora con `entrelumen:propagation_core`; medir producción antes de elegir multiplicadores.
- Acto IV: apiario modular tras aprender cría y procesamiento de Productive Bees; relacionar su construcción con `entrelumen:ecosystem_capsule`. Mantener apiarios pequeños útiles para especies y producción especializada.
- Actos V–VI: tiers superiores y ampliaciones del apiario consumen componentes industriales/naturales. No entregar versiones creativas como recompensas ni habilitar conversión universal.

## Riesgos concretos para el integrador

1. JAMD incluye tres dimensiones y tres portales, no sólo una cantera: `jamd:mining`, `jamd:nether`, `jamd:end`. Las recetas nativas son `jamd:portal_block`, `jamd:nether_portal_block`, `jamd:end_portal_block`. La primera usa obsidiana y pico de diamante; las otras pueden adelantar recursos dimensionales. Reemplazar las tres recetas al definir etapas y conservar acceso de retorno. No borrar mundos/dimensiones existentes.
2. Worldgen propio se configura mediante datapack. El JAR aporta `data/jamd/dimension/mining.json` con lecho de roca, deepslate, piedra, tierra y césped; revisar sus biomas y modificadores antes de añadir minerales. Crear una lista explícita de minerales y alturas, sin introducir todos los minerales de todos los mods ni copiar datapacks de otros packs.
3. Botany Pots Tiers trae rutas directas, mejoras, variantes de material y recetas `upgrade_quick`. Cambiar sólo `elite_upgrade` deja rutas alternativas. Auditar todas las recetas por resultado/tier, conservar colores y remainders, comprobar en EMI la ausencia de rutas de adquisición más baratas involuntarias.
4. No asumir que las semillas de Mystical Agriculture son cultivables en estas macetas: no se seleccionó su complemento de compatibilidad. Si se añaden recetas propias, decidir cada recurso por etapa y multiplicación total semilla/suelo/tier.
5. Modular Bees aporta colmena y centrifugado modulares, electrodos e interfaces ME. No basta encarecer una carcasa: inventariar controladores, mejoras y métodos alternativos. La disponibilidad de un componente regalado no valida un hito narrativo.

## Verificación pendiente de juego

El cierre de dependencias cliente/servidor y los hashes pasan con `python tools/curate_pack.py --check`. Esto no ejecuta el loader ni valida rendimiento.

Después de integrar recetas/datapacks: arranque limpio cliente/dedicado; entrada y retorno de dos jugadores desde portales en bases distintas; reconexión dentro de cada dimensión; cultivos con inventario de salida lleno y tras reinicio; conservación de abejas y panales al descargar chunks; comparación de producción y MSPT con macetas/apiarios pequeños y avanzados. Medir generación nueva separadamente y verificar que estas granjas no sustituyan la exploración narrativa.
