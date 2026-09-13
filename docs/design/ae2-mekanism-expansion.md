# ExpansiÃ³n AE2 / Mekanism

RevisiÃ³n: 2026-09-12. Propuesta de incorporaciÃ³n, sin descarga ni cambio del catÃ¡logo o instancia. Recomiendo **cinco addons principales**: ExtendedAE, AdvancedAE, Applied Mekanistics, MEGA Cells y Mekanism Extras. More Machine queda como sexta incorporaciÃ³n condicionada a revisar la replicaciÃ³n. La compatibilidad publicada de Minecraft/loader estÃ¡ comprobada; todavÃ­a no lo estÃ¡n los rangos TOML de estos archivos ni su arranque combinado.

## Base local exacta

`catalog/curated.json` contiene AE2 `appliedenergistics2-19.2.17.jar` (223794/7027323), Mekanism `10.7.19.85` (268560/7904058), Mekanism Generators de igual versiÃ³n (268566/7904061), AE2 JEI Integration `1.2.1` (1074338/7727898), Just Enough Mekanism Multiblocks `7.18` (898746/8714994), GuideME `21.1.17` y GeckoLib `4.9.2`. Ninguno de los seis addons propuestos estÃ¡ seleccionado. Glodium tampoco aparece en el catÃ¡logo.

## Archivos candidatos verificables

Todos los archivos de esta tabla estÃ¡n publicados para **Minecraft 1.21.1 / NeoForge**, release. Son candidatos fijados por archivo, no una orden de actualizar automÃ¡ticamente dependencias.

| Mod | VersiÃ³n / archivo | projectID | fileID / fuente oficial |
|---|---|---|---|
| ExtendedAE | `ExtendedAE-1.21-2.2.36-neoforge.jar` | 892005 | [8811572](https://www.curseforge.com/minecraft/mc-mods/ex-pattern-provider/files/8811572) |
| AdvancedAE | 1.6.12-1.21.1 | 1084104 | [8564177](https://www.curseforge.com/minecraft/mc-mods/advancedae/files/8564177) |
| Applied Mekanistics | 1.6.3 | 574300 | [7096962](https://www.curseforge.com/minecraft/mc-mods/applied-mekanistics/files/7096962) |
| MEGA Cells | 4.11.0 | 622112 | [7952621](https://www.curseforge.com/minecraft/mc-mods/mega-cells/files/7952621) |
| Mekanism Extras | `mekanism_extras-1.21.1-1.4.1.jar` | 1026040 | [8677677](https://www.curseforge.com/minecraft/mc-mods/mekanism-extras/files/8677677) |
| Mekanism:More Machine | `mekmm-1.21.1-1.4.1.jar` | 1275257 | [8724947](https://www.curseforge.com/minecraft/mc-mods/mekanism-more-machine/files/8724947) |

## FunciÃ³n, dependencias y decisiÃ³n

**ExtendedAE â€” incorporar.** MÃ¡quinas para fabricar chips/cargar en paralelo, proveedores ampliados y Assembler Matrix. Su papel es expandir talleres existentes y automatizar lotes del Atlas. La actualizaciÃ³n candidata corrige un fallo de carga de configuraciÃ³n. Reservar la Matrix para V; las herramientas de patrones llegan antes. Dependencias declaradas por proyecto: AE2, Glodium y GuideME; Wireless Terminals es opcional. Confirmar versiones mÃ­nimas en el archivo antes de fijar Glodium. [DescripciÃ³n](https://www.curseforge.com/minecraft/mc-mods/ex-pattern-provider), [relaciones](https://www.curseforge.com/minecraft/mc-mods/ex-pattern-provider/relations/dependencies).

**AdvancedAE â€” incorporar con potencia tardÃ­a.** Proveedores que encaminan ingredientes por cara simplifican lÃ­neas quÃ­micas; Quantum Computer permite concurrencia de fabricaciÃ³n y Quantum Crafter trabaja directamente con inventario ME. La armadura Quantum puede trivializar expediciones: su obtenciÃ³n pertenece al VI/posgame. AE2 y GeckoLib son requeridos; AE2AddonLib figura como biblioteca incluida, no duplicarla sin inspeccionar JarJar. Medir solicitudes concurrentes y crafteos recursivos, especialmente esencias MA. [DescripciÃ³n](https://www.curseforge.com/minecraft/mc-mods/advancedae), [relaciones](https://www.curseforge.com/minecraft/mc-mods/advancedae/relations/dependencies).

**Applied Mekanistics â€” primera incorporaciÃ³n.** Puente real entre dos sistemas existentes: quÃ­micos en buses, interfaces, tÃºneles y autocrafteo AE2. TambiÃ©n conecta almacenamiento QIO. Requiere AE2 y Mekanism. Las celdas quÃ­micas no almacenan gases radiactivos: enseÃ±ar esa excepciÃ³n y probar manejo de residuos por separado; no prometer almacenamiento universal. [Proyecto oficial](https://www.curseforge.com/minecraft/mc-mods/applied-mekanistics).

**MEGA Cells â€” incorporar.** Celdas y CPUs de capacidad superior, ademÃ¡s de celdas quÃ­micas cuando estÃ¡ Applied Mekanistics. Su objetivo es consolidar almacenamiento de producciÃ³n; la capacidad no exige llenar todo para avanzar. El proveedor de patrones se solapa con ExtendedAE: ofrecerlo como alternativa, sin obligar a fabricar ambos. La celda bulk puede desplazar cajones: conservar Functional Storage como buffer de granjas y dejar bulk para V. AE2 requerido; integraciones listadas como opcionales no justifican instalar Botania o ProjectE. [DescripciÃ³n](https://www.curseforge.com/minecraft/mc-mods/mega-cells), [dependencias](https://www.curseforge.com/minecraft/mc-mods/mega-cells/relations/dependencies).

**Mekanism Extras â€” incorporar despuÃ©s del puente quÃ­mico.** AÃ±ade escalones de fÃ¡bricas, contenedores, transporte y un reactor con combustible propio. Permite sustituir muchas mÃ¡quinas repetidas por instalaciones compactas; la mejora de TPS es una hipÃ³tesis que debe medirse a igual producciÃ³n. Requiere Mekanism; Generators y More Machine figuran opcionales. Afecta worldgen por naquadah: probar chunks nuevos sin retrogen supuesto. El autor advierte texturas parpadeantes Cosmic/Infinite; comprobar si esta versiÃ³n NeoForge trae recurso para evitarlas antes de elegir esos bloques en escenas principales. [DescripciÃ³n](https://www.curseforge.com/minecraft/mc-mods/mekanism-extras), [relaciones](https://www.curseforge.com/minecraft/mc-mods/mekanism-extras/relations/dependencies).

**Mekanism:More Machine â€” incorporar sÃ³lo tras auditar replicador.** Aporta estaciÃ³n de cultivo y fÃ¡bricas de procesos adicionales, como oxidaciÃ³n y disoluciÃ³n. No asumir un wrapper KubeJS especÃ­fico: el autor indica usar JSON nativo/custom para sus recetas. Integrar sus fÃ¡bricas en V como alternativa compacta; estaciÃ³n en IV. El replicador requiere inspecciÃ³n concreta de entradas, salidas y costos antes de aceptarlo: no permitir una conversiÃ³n universal que borre la progresiÃ³n. Si no puede limitarse limpiamente por recetas de obtenciÃ³n/producciÃ³n, aplazar este addon entero; no parchear clases. [Proyecto y advertencia de API](https://www.curseforge.com/minecraft/mc-mods/mekanism-more-machine), [dependencias por revisar junto al TOML](https://www.curseforge.com/minecraft/mc-mods/mekanism-more-machine/relations/dependencies).

## Crazy / Insane: nombres identificados

**Crazy AE2 Addons**, projectID **1223804**, tiene releases para **1.20.1 Forge**, no una release compatible comprobada para nuestro loader. Caso engaÃ±oso: [archivo 7250458](https://www.curseforge.com/minecraft/mc-mods/crazy-ae2-addons/files/7250458) se llama `crazyae2addons-1.21.1-all.jar`, pero su ficha dice Minecraft **1.20.1 / Forge**: ese 1.21.1 es la versiÃ³n del addon. El [repositorio del autor](https://github.com/Omicron-Industries/CrazyAE2Addons) anuncia la rama NeoForge como futura. Descartar para la instalaciÃ³n actual; no confundir anuncio con release.

**Insane AE2 Addons**, projectID **1628037**, existe realmente y es la continuaciÃ³n enlazada por Crazy. Su release publicada `3.2.3` es tambiÃ©n **1.20.1 Forge**. No instalar en este pack. [Proyecto oficial](https://www.curseforge.com/minecraft/mc-mods/insane-ae2-addons).

No aÃ±adir un tercer conjunto de proveedores/celdas sÃ³lo por llamarse Plus/Infinite. Los cuatro addons AE2 seleccionados ya cubren procesamiento, lÃ³gica de caras, concurrencia y almacenamiento. Nuevas variantes requieren una funciÃ³n distinta demostrable.

## Escalones propuestos para ENTRELUMEN

| Acto | ObtenciÃ³n normal y proyecto Ãºtil |
|---|---|
| III | Applied Mekanistics bÃ¡sico y herramientas de patrones ExtendedAE. Encargar matrices de encaminamiento y documentar un lote quÃ­mico recuperable. Mantener AE2 bÃ¡sico y mÃ¡quinas originales suficientes. |
| IV | Proveedores avanzados, primeras celdas MEGA y mejoras intermedias de fÃ¡brica. Entregar un lote del archivo espectral mediante entradas por caras; alternativa logÃ­stica convencional vÃ¡lida. |
| V | Assembler Matrix, Quantum Computer, celdas mayores y fÃ¡bricas Extras escalonadas. Producir buses del Arca con solicitudes limitadas y buffers; medir equivalencia frente a una granja de mÃ¡quinas pequeÃ±as. |
| VI / posgame | Armadura Quantum, mejoras mÃ¡ximas y reactor avanzado como maestrÃ­as. Mantener final narrativo basado en proyectos, no en conseguir armadura invulnerable ni llenar celdas enormes. |

Estas etapas se implementan mediante recetas estÃ¡ticas que reutilizan `routing_matrix`, `inventory_sensor`, `ark_bus` y materiales nativos apropiados. Fijar IDs concretos sÃ³lo despuÃ©s de inspeccionar los JAR/registro cargado. NingÃºn control de uso por equipo/acto: mÃ¡quinas, mejoras y armaduras regaladas funcionan; recibirlas no completa la historia. Nada de recompensas aleatorias que adelanten estos escalones por defecto.

## IntegraciÃ³n segura y evidencia que falta

1. Descargar los cinco archivos principales y la dependencia Glodium desde sus proyectos oficiales. Leer `neoforge.mods.toml`, JarJar, licencias y rangos mÃ­nimos contra AE2 19.2.17/Mekanism 10.7.19.85/GuideME 21.1.17/GeckoLib 4.9.2. La pÃ¡gina del proyecto agrega relaciones de varias versiones y no sustituye ese control.
2. AÃ±adir cliente y servidor, fijar SHA-256/projectID/fileID y resolver cierre de dependencias; no actualizar toda la base para satisfacer un addon sin evaluar el cambio. Si una versiÃ³n exige otra base, elegir una release compatible documentada o informar la dependencia exacta.
3. Probar carga, recetas y visor EMI/JEI; el adaptador JEI existente no prueba compatibilidad de cada mÃ¡quina. Comprobar backups de celdas quÃ­micas y recuperaciÃ³n de trabajos al reiniciar.
4. Comparar tres cargas de producciÃ³n equivalentes con spark: chips, quÃ­mica y autocrafteo masivo. Medir MSPT, memoria y crecimiento de cola. Limitar paralelismo/configuraciÃ³n sÃ³lo con claves reales inspeccionadas.
5. Auditar recetas aceleradas/recursivas, sobras de contenedores y replicaciÃ³n antes de liberar obtenciÃ³n tardÃ­a. No hay todavÃ­a prueba runtime ni rendimiento de estos addons en ENTRELUMEN.

Documento de decisiÃ³n, no lockfile. SÃ³lo se modificÃ³ este archivo.


## Selección implementada en catálogo

Se seleccionaron cinco addons y Glodium: **125 JAR cliente / 100 servidor**, frente a 119/94. No se retiró ni cambió el hash de ningún JAR anterior. More Machine continúa excluido. Malum, Lodestone, EMI y Jade Addons externos se conservaron.

| Incorporación fijada | Procedencia | projectID/fileID |
|---|---|---|
| ExtendedAE 2.2.35 | JAR local de referencia, SHA-1 coincide con metadata CF | 892005/8511901 |
| AdvancedAE 1.6.12-1.21.1 | Local verificado | 1084104/8564177 |
| Applied Mekanistics 1.6.3 | Local verificado | 574300/7096962 |
| MEGA Cells 4.11.0 | Local verificado | 622112/7952621 |
| Glodium 1.21-2.2-neoforge | Local verificado | 957920/5821676 |
| Mekanism Extras 1.21.1-1.4.1 | Descarga oficial forgecdn | 1026040/8677677 |

ExtendedAE conserva **2.2.35**, compatible y ya disponible localmente, en lugar del candidato 2.2.36 de la investigación. No se actualizó la base central. Su versión siguiente anuncia corregir un fallo de configuración: si aparece al arrancar, evaluar ese archivo concreto como primera sustitución.

`curate_pack.py` ahora reconoce `type=REQUIRED` en mayúsculas como dependencia obligatoria. Era importante para ExtendedAE, Glodium y la API incluida. La comprobación de hashes, referencias y cierre de dependencias pasa en ambos lados.

Además se contrastaron **26 restricciones TOML/JarJar** de estas incorporaciones mediante `org.apache.maven.artifact.versioning.VersionRange` y `DefaultArtifactVersion`, usando las versiones actuales. AdvancedAE incluye AE2AddonLib 1.0.3 (NeoForge mínimo 21.1.203, AE2 mínimo 19.2.16) y AE2WTLib API 19.2.5. ExtendedAE incluye API 19.2.0 con rango [19.2.0,); ambas restricciones admiten seleccionar 19.2.5. Eso satisface las dependencias declaradas, pero no las cuatro recetas de integración sin condición: ver corrección runtime debajo. No duplicar APIs manualmente. La selección efectiva de JarJar queda por observar al arrancar.

Mekanism Extras declara Minecraft [1.21,1.21.1], NeoForge [21.1,) y Mekanism [10.7.19,): nuestra base los satisface. Su archivo SHA-256 es `7138aa6530cc184ef6838b02ec7138491907c045e870507bf11ae58cc4c70541`. No se obtuvo un hash upstream en la ficha pública; se registra esa limitación sin inventarlo. La descarga fue desde [forgecdn oficial](https://edge.forgecdn.net/files/8677/677/mekanism_extras-1.21.1-1.4.1.jar).

Pendiente exclusivamente de integración: instalación con el cliente cerrado, arranque y selección JarJar, recetas/visor, texturas y medición de carga. Compatibilidad de metadatos aprobada no significa ausencia de conflictos runtime. Los 44 mods de contenido del catálogo requieren extender la matriz editorial anterior de 39; esta tarea no modificó quests ni recetas.


## Corrección de diagnóstico: Wireless Terminals ausente (2026-09-12)

El arranque dedicado de 109 dependencias reportó cuatro serializers desconocidos en AdvancedAE 1.6.12. El catálogo de 139 no contiene `ae2wtlib`: contiene únicamente `ae2wtlib_api` embebida. No se trata de un mod marcado cliente por error.

Los JSON originales `wt_combine_encoding`, `wt_combine_access`, `wt_combine_crafting` declaran `ae2wtlib:combine`; `wt_upgrade_quantum_crafter_terminal` declara `ae2wtlib:upgrade`. No incluyen condición de presencia. El TOML de AdvancedAE exige API pero declara la implementación OPTIONAL >=19.2.5. Por eso el cierre de dependencias declaradas pasa mientras esas recetas fallan.

El JAR local `ae2wtlib-19.5.1.jar` es candidato viable: [CF459929/8450398](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2-wireless-terminals/files/8450398), MIT, ambos lados, SHA1 verificado `b1bee01393b15f1eb0f46d58862ef99234e1d337`. Exige AE2 [19.2.17,20.0.0), satisfecho, y API [19.5.1] que incluye por JarJar. Los rangos de AdvancedAE/ExtendedAE admiten esa API; no agregar otro archivo de API externo. Su constructor `de.mari_023.ae2wtlib.AE2wtlib` registra ambos serializers en BuiltInRegistries.RECIPE_SERIALIZER.

Recomendación: incorporar la implementación completa en cliente y servidor (140/110), preservando los hashes existentes. Incorporación autorizada y aplicada al catálogo: 140 cliente / 110 servidor; los 139 SHA256 anteriores permanecen iguales. Refresh/check aprobado en ambos lados. Después requiere reinicio de ambas instalaciones y verificar desaparición de los cuatro errores, recetas visibles y terminales combinadas sin perder datos. No alcanza `/reload` para registrar un mod nuevo. Se modificaron catálogo, selección del curador y rutas locales. No se modificó perfil ni servidor. La prueba runtime de esta corrección sigue pendiente; no se afirma compatibilidad completa por pasar metadatos.
