# Escala comparada y nueva meta

Actualización solicitada: igualar o superar en mods y quests a ATM10, FTB Evolution y Craftoria, con sensación extra large y sin contenido de relleno. **900 quests deja de ser objetivo final o techo.** Inspección inicial: 2026-09-12; medición del export FTB completada el 2026-09-23.

## Unidades que no deben mezclarse

- **Proyecto de manifiesto**: referencia de descarga; puede ser mod, biblioteca, resource pack u otro recurso. No equivale a sistema jugable.
- **JAR físico en mods/**: archivo principal; puede declarar varios mod IDs e incluir bibliotecas JarJar. No contar cada biblioteca incluida como otro JAR principal.
- **Sistema de contenido**: clasificación editorial, separada de bibliotecas, QoL y optimizadores. Se debe documentar; no se deduce del nombre ni de una categoría publicitaria.
- **Quest**: objeto directo de la lista `quests` de un capítulo, identificado por su ID. Tareas, recompensas, dependencias, idiomas y capítulos no se suman como quests. Contar IDs únicos y distinguir las definiciones exportadas de las realmente cargadas.

## Referencias concretas

| Pack / edición | Medición o declaración | Alcance y limitación |
|---|---|---|
| ATM10 **8.1**, MC 1.21.1 / NeoForge 21.1.249 | **491 proyectos únicos** en manifest; **498 JAR físicos** en la instancia local; **4.790 IDs únicos de quest en 66 capítulos SNBT** | Medido local read-only. La instancia puede contener diferencias respecto del export público; 491 no son 491 sistemas. No se clasificaron todas sus bibliotecas. |
| Craftoria **1.36.0**, archivo CF **8850018** | **560 referencias CF únicas**, **1 JAR adicional** en overrides/mods. **1.136 IDs únicos** en **28 capítulos .snbt** del export | Medido en ZIP oficial, en memoria. Hay otros tres archivos .txt en el directorio de capítulos que no se contabilizaron como capítulos cargables. El snapshot de Crash Assistant incluye **521 nombres .jar**, pero no es inventario de un arranque verificado. |
| Craftoria, presentación oficial actual | **500+ mods / 2.000+ quests** | Declaración del editor, no medición exacta. La diferencia frente a 1.136 definiciones exportadas queda abierta: puede haber contenido aportado por mods u otros mecanismos. No afirmar que el pack completo sólo tiene 1.136. |
| FTB Evolution **1.43.1**, pack **125**, versión **100487**, MC 1.21.1 / NeoForge 21.1.248 | **523 JAR directos**, **520 proyectos CF únicos** y tres JAR sin project ID; **2.072 IDs únicos de quest en 40 capítulos SNBT** | Medido en el manifiesto y capítulos oficiales: 29 JAR client-only, 494 seleccionables por el installer de servidor. No se sumó JarJar. Son definiciones exportadas; no se lanzó el pack para comprobar el total cargado. |

Fuentes: [ATM10 y release 8.1](https://www.curseforge.com/minecraft/modpacks/all-the-mods-10), [Craftoria 1.36.0](https://www.curseforge.com/minecraft/modpacks/craftoria/files/8850018), [declaración de Craftoria](https://www.curseforge.com/minecraft/modpacks/craftoria), [FTB Evolution oficial](https://www.feed-the-beast.com/modpacks/125-ftb-evolution).

### Procedencia y método

ATM10: `G:/curseforge/Instances/All the Mods 10 - ATM10/manifest.json`, nombres de `mods/*.jar` y lectura estructural de `config/ftbquests/quests/chapters/*.snbt`. No se modificaron esos archivos ni se reutilizó texto, arte o recetas.

Craftoria: [ZIP oficial](https://edge.forgecdn.net/files/8850/018/Craftoria-1.36.0.zip), SHA-256 `c87a6dd7471c34f459d93910a4285c7166ce5995ba996ce92f57ba02cd43084c`. Se leyeron sólo el manifiesto, inventarios y estructura de capítulos para contar. No se extrajeron archivos a ENTRELUMEN ni se copió narrativa. Un tokenizador respetó cadenas entre comillas y delimitadores para contar únicamente `id` de compuestos directamente dentro de `quests`; número de IDs y número de IDs únicos coinciden en ambas mediciones. Esto no evalúa calidad ni si la pantalla expone todas esas quests.

La revisión del 23 de septiembre conserva [evidencia agregada de Craftoria](../verification/scale-craftoria-1.36.0.json): confirma 1.136 quests y cuenta aparte 1.592 tareas. Revisó 131 scripts KubeJS y no encontró registros dinámicos mediante los patrones acotados consultados ni otras listas SNBT de quests fuera de los capítulos. Esto no excluye contenido aportado por dependencias no inspeccionadas. El lema «2.000+» aparece también en la ficha de [0.9.1](https://www.curseforge.com/minecraft/modpacks/craftoria/files/5662985), dentro de la presentación compartida del proyecto; no es una medición publicada por versión. Su origen sigue desconocido y no se usa para afirmar 864 quests ocultas ni un error del autor. El [servidor 1.36.0](https://www.curseforge.com/minecraft/modpacks/craftoria/files/8850019) contiene cinco archivos de instalación, sin inventario de mods ni capítulos. Se conservan el objetivo provisional y la verificación pendiente del contenido realmente cargado.

FTB, intento inicial del 12 de septiembre: la API antigua `api.modpacks.ch/public/modpack/125` devolvía como última versión 1.3.0 (2024), y solicitar 100487 daba versión inválida. El endpoint entonces consultado respondió HTTP 403; se descartó sustituir la versión por un inventario antiguo. La medición del 23 de septiembre resolvió la ruta vigente mediante el código del instalador oficial; se detalla al final.

No hay cifra comparable exacta de bibliotecas para las tres referencias. Un manifiesto público no aporta esa clasificación por sí mismo. Por eso no se presenta una resta inventada de “mods reales”. En ENTRELUMEN sí se mantiene clasificación editorial propia.

## Meta de producción propuesta

**Objetivo provisional: 600 proyectos de mods/JAR principales útiles y 5.000–5.500 quests originales únicas**, contando dependencias principales dentro de los 600 pero mostrándolas separadas. Excluir resource packs, shaders, idiomas, tareas y recompensas del contador correspondiente. Apuntar a la parte baja del rango editorial: pasar el máximo por un margen pequeño, no perseguir miles adicionales por prestigio.

La condición final de escala es **igualar o superar el máximo de los tres packs en una unidad común y versiones congeladas**, no cumplir un número publicitario. Los 600 y 5.000 son planificación prudente a partir de los datos disponibles, **no prueba de paridad cumplida**. El export FTB ya está medido; el contenido completo cargado de Craftoria sigue pendiente, y ningún conteo de export equivale por sí solo a un arranque. Ajustar el objetivo sólo si el máximo comparable lo exige. No reducir silenciosamente el pedido a 125 mods/900 quests.

Para que 5.000 quests no conviertan la campaña en una lista de compras: conservar unos 150–200 hitos narrativos principales y distribuir el resto en tutoriales de sistemas, diseños de instalaciones, expediciones, alternativas y maestrías. La campaña no exige completismo. No desdoblar una acción en cinco quests, contar la traducción como otra ni pedir cada color decorativo para inflar el número. Cada nodo tiene que enseñar, abrir una experiencia, comprobar un sistema útil o plantear una decisión.

## Diferencia con la selección actual

La selección inicial descrita aquí tenía **125 JAR**, con **44 roles de contenido, 36 QoL, 34 dependencia, 8 rendimiento y 6 infraestructura**. El 23 de septiembre el catálogo pasó de 140 cliente / 110 servidor a 178 / 143 y, con la [familia industrial](industrial-expansion-family.md), 197 / 162; con la [familia QoL y decoración funcional](qol-functional-decor-family.md), 217 / 178; con la [familia arcana](arcane-expansion-family.md), 230 / 191; con la [familia de exploración](exploration-structures-family.md), 248 / 209; con las familias de [integraciones de taller](workshop-integrations-family.md) y [QoL menor](small-qol-family.md), 268 / 228; con la [familia Apotheosis](apotheosis-family.md) llega a **272 JAR de cliente / 232 de servidor**, más el companion propio. De los 94 agregados por las siete familias, 80 son selección de contenido/QoL/rendimiento y 14 bibliotecas requeridas; no se cuentan como sistemas jugables por separado. La distancia frente a los ~500 JAR de las referencias sigue abierta. La distancia frente a las referencias sigue siendo real; los addons AE2/Mekanism enriquecen una familia, pero no cubren toda la variedad pendiente. Los roles se clasifican por mod ID en `catalog/curated.json`; no equivalen a JAR ni se suman como diversidad jugable.

Falta ampliar con curaduría concreta: ingeniería industrial alternativa y redes especializadas; automatización de magia y sus addons; fabricación agrícola y cocina variada; exploración, estructuras y encuentros de dificultad moderada; utilidades de construcción y decoración funcional; movilidad, transporte y bases remotas; herramientas para colecciones y especializaciones. No equivale a instalar todos los candidatos: cada incorporación debe justificar introducción, utilidad posterior y relación con otro sistema. No apilar varios reemplazos completos de biomas ni tres soluciones idénticas de almacenamiento para sumar entradas.

Las bibliotecas y optimizadores no cuentan como diversidad jugable. Si una función ya está cubierta, exigir una diferencia concreta. La cuota de 600 no autoriza mods sin función ni sustituye el criterio de estabilidad.

## Memoria y rendimiento

Mantener **heap hasta 8 GB / PC total 16 GB** como objetivo de aceptación pendiente. La recomendación de memoria de FTB muestra que esa clase de pack existe; no prueba FPS/MSPT en nuestro i7-8750H/GTX1070 ni seis jugadores. No prometer simultáneamente 600 JAR y rendimiento cumplido antes de medir.

Ampliar por familias y perfilar mundo precargado, exploración, base avanzada y sesión de dos horas. Reducir generación redundante, entidades, redes y paralelismo antes de eliminar profundidad. Si la escala útil y los objetivos técnicos resultan incompatibles tras optimización, mostrar la medición y la decisión material al usuario; no esconder el incumplimiento ni bajar el tamaño por cuenta propia.


## Segunda comprobación acotada de cifras pendientes

Se volvió a inspeccionar el mismo ZIP Craftoria 1.36.0, sin extracción. El tokenizador se amplió para respetar tanto cadenas simples como dobles de SNBT: **el resultado sigue siendo 1.136 IDs únicos**. Los 28 capítulos no tienen líneas de comentarios `#`/`//` ni claves `id` entre comillas que expliquen la diferencia. Los tres archivos `.txt` (`flattened_mi1b`, `core`, `frame_and_nightsky`) no contienen listas `quests` ni campos `id`; no son capítulos adicionales contables. No apareció otra lista raíz `quests` en los demás archivos SNBT del ZIP. Las traducciones y snapshots de recuperación bajo `lang/` no se sumaron como quests nuevas.

El único JAR directo del export es CC:Tweaked. El ZIP también incluye numerosos resource packs; no se asumió que fueran questbooks ni se descargaron 560 dependencias para perseguir el dato. Esto descarta errores obvios del contador y ubicaciones SNBT adicionales del export, pero **no verifica el contenido aportado por los mods descargados ni el questbook cargado**. La discrepancia de la publicidad de Craftoria permanece abierta; 1.136 sigue siendo sólo el mínimo de definiciones exportadas medido.

En esa segunda revisión, una lectura directa del HTML FTB también respondió **HTTP 403**. No se intentó evadirlo ni reutilizar datos antiguos. Ese intento no aportó un manifiesto nuevo. Se conserva como antecedente; la consulta del 23 de septiembre siguiente sí obtuvo el export oficial.

## FTB Evolution: export oficial medido el 23 de septiembre

El [instalador oficial FTB](https://github.com/FTBTeam/FTB-Server-Installer/blob/main/repos/ftb.go) identifica la [API vigente para pack 125 / versión 100487](https://api.feed-the-beast.com/v1/modpacks/modpack/125/100487). Una consulta sin autenticación devolvió 5.315.824 bytes, SHA-256 `25329424e52c186702e401efcc104c34c651ab86078f93a7d561328d883345ca`. No se instaló ni ejecutó FTB Evolution.

Se contaron registros `type=mod` en `./mods` con nombre `.jar`: 523 nombres distintos. Hay 520 project IDs CF distintos y tres JAR sin ese dato; las bibliotecas embebidas no se cuentan aparte. El filtro del instalador excluye los 29 client-only y deja 494 entradas, sin afirmar arranque de servidor.

Un lector de delimitadores que respeta cadenas contó únicamente compuestos directos de la lista raíz `quests` y sus IDs directos: 2.072 objetos e IDs únicos en 40 capítulos. Los 40 archivos coincidieron con el SHA-1 publicado en el manifiesto. El [índice agregado de evidencia](../verification/scale-ftb-evolution-1.43.1.json) conserva URLs, tamaños, hashes por capítulo y método; no incluye textos ni IDs de quests. El hash del corpus completo es `213d38b840af22c72d5b1cf69e5f4416f2e2ce1b3b1e08b28a7cce291de66a68`.

Se inspeccionaron los 78 scripts publicados y sus hashes: no se encontraron patrones reconocidos de registro dinámico de quests. Eso no descarta aportes de código de mods ni acredita el total cargado. La meta provisional queda igual; las 4.790 quests de ATM10 siguen siendo el mayor total estructural medido entre estas referencias.
