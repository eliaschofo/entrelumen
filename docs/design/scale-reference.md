# Escala comparada y nueva meta

Actualización solicitada: igualar o superar en mods y quests a ATM10, FTB Evolution y Craftoria, con sensación extra large y sin contenido de relleno. **900 quests deja de ser objetivo final o techo.** Fecha de inspección: 2026-09-12.

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
| FTB Evolution **1.43.1**, pack **125**, versión **100487**, MC 1.21.1 | Versión publicada y fecha **27-08-2026** comprobadas. Su página recomienda **8 GB**, mínimo 6 GB | No se obtuvo inventario ni questbook completo de esa versión en esta revisión; sus cuentas actuales quedan **sin medir**. No convertir testimonios o cifras de otra versión en exactitud. |

Fuentes: [ATM10 y release 8.1](https://www.curseforge.com/minecraft/modpacks/all-the-mods-10), [Craftoria 1.36.0](https://www.curseforge.com/minecraft/modpacks/craftoria/files/8850018), [declaración de Craftoria](https://www.curseforge.com/minecraft/modpacks/craftoria), [FTB Evolution oficial](https://www.feed-the-beast.com/modpacks/125-ftb-evolution).

### Procedencia y método

ATM10: `G:/curseforge/Instances/All the Mods 10 - ATM10/manifest.json`, nombres de `mods/*.jar` y lectura estructural de `config/ftbquests/quests/chapters/*.snbt`. No se modificaron esos archivos ni se reutilizó texto, arte o recetas.

Craftoria: [ZIP oficial](https://edge.forgecdn.net/files/8850/018/Craftoria-1.36.0.zip), SHA-256 `c87a6dd7471c34f459d93910a4285c7166ce5995ba996ce92f57ba02cd43084c`. Se leyeron sólo el manifiesto, inventarios y estructura de capítulos para contar. No se extrajeron archivos a ENTRELUMEN ni se copió narrativa. Un tokenizador respetó cadenas entre comillas y delimitadores para contar únicamente `id` de compuestos directamente dentro de `quests`; número de IDs y número de IDs únicos coinciden en ambas mediciones. Esto no evalúa calidad ni si la pantalla expone todas esas quests.

FTB: la API antigua `api.modpacks.ch/public/modpack/125` devuelve como última versión 1.3.0 (2024), y solicitar 100487 devuelve versión inválida. El endpoint actual consultado respondió HTTP 403. Se descartó usar el inventario antiguo como si fuera 1.43.1. Hace falta una exportación oficial actual o acceso al manifiesto vigente; no se requiere copiar su contenido editorial.

No hay cifra comparable exacta de bibliotecas para las tres referencias. Un manifiesto público no aporta esa clasificación por sí mismo. Por eso no se presenta una resta inventada de “mods reales”. En ENTRELUMEN sí se mantiene clasificación editorial propia.

## Meta de producción propuesta

**Objetivo provisional: 600 proyectos de mods/JAR principales útiles y 5.000–5.500 quests originales únicas**, contando dependencias principales dentro de los 600 pero mostrándolas separadas. Excluir resource packs, shaders, idiomas, tareas y recompensas del contador correspondiente. Apuntar a la parte baja del rango editorial: pasar el máximo por un margen pequeño, no perseguir miles adicionales por prestigio.

La condición final de escala es **igualar o superar el máximo de los tres packs en una unidad común y versiones congeladas**, no cumplir un número publicitario. Los 600 y 5.000 son planificación prudente a partir de los datos disponibles, **no prueba de haber superado FTB Evolution actual ni toda Craftoria**. Antes de cerrar producción hay que completar esas dos mediciones; ajustar el objetivo sólo si el máximo comparable lo exige. No reducir silenciosamente el pedido a 125 mods/900 quests.

Para que 5.000 quests no conviertan la campaña en una lista de compras: conservar unos 150–200 hitos narrativos principales y distribuir el resto en tutoriales de sistemas, diseños de instalaciones, expediciones, alternativas y maestrías. La campaña no exige completismo. No desdoblar una acción en cinco quests, contar la traducción como otra ni pedir cada color decorativo para inflar el número. Cada nodo tiene que enseñar, abrir una experiencia, comprobar un sistema útil o plantear una decisión.

## Diferencia con la selección actual

ENTRELUMEN tiene **125 JAR seleccionados**, con **44 roles de contenido, 36 QoL, 34 dependencia, 8 rendimiento y 6 infraestructura**. Esos 128 roles corresponden a mod IDs, no a 128 JAR: algunos archivos declaran más de uno. El companion se contabiliza por separado al empaquetar. La distancia frente a las referencias es real; cinco addons AE2/Mekanism enriquecen una familia, pero no cubren toda la variedad pendiente.

Falta ampliar con curaduría concreta: ingeniería industrial alternativa y redes especializadas; automatización de magia y sus addons; fabricación agrícola y cocina variada; exploración, estructuras y encuentros de dificultad moderada; utilidades de construcción y decoración funcional; movilidad, transporte y bases remotas; herramientas para colecciones y especializaciones. No equivale a instalar todos los candidatos: cada incorporación debe justificar introducción, utilidad posterior y relación con otro sistema. No apilar varios reemplazos completos de biomas ni tres soluciones idénticas de almacenamiento para sumar entradas.

Las bibliotecas y optimizadores no cuentan como diversidad jugable. Si una función ya está cubierta, exigir una diferencia concreta. La cuota de 600 no autoriza mods sin función ni sustituye el criterio de estabilidad.

## Memoria y rendimiento

Mantener **heap hasta 8 GB / PC total 16 GB** como objetivo de aceptación pendiente. La recomendación de memoria de FTB muestra que esa clase de pack existe; no prueba FPS/MSPT en nuestro i7-8750H/GTX1070 ni seis jugadores. No prometer simultáneamente 600 JAR y rendimiento cumplido antes de medir.

Ampliar por familias y perfilar mundo precargado, exploración, base avanzada y sesión de dos horas. Reducir generación redundante, entidades, redes y paralelismo antes de eliminar profundidad. Si la escala útil y los objetivos técnicos resultan incompatibles tras optimización, mostrar la medición y la decisión material al usuario; no esconder el incumplimiento ni bajar el tamaño por cuenta propia.


## Segunda comprobación acotada de cifras pendientes

Se volvió a inspeccionar el mismo ZIP Craftoria 1.36.0, sin extracción. El tokenizador se amplió para respetar tanto cadenas simples como dobles de SNBT: **el resultado sigue siendo 1.136 IDs únicos**. Los 28 capítulos no tienen líneas de comentarios `#`/`//` ni claves `id` entre comillas que expliquen la diferencia. Los tres archivos `.txt` (`flattened_mi1b`, `core`, `frame_and_nightsky`) no contienen listas `quests` ni campos `id`; no son capítulos adicionales contables. No apareció otra lista raíz `quests` en los demás archivos SNBT del ZIP. Las traducciones y snapshots de recuperación bajo `lang/` no se sumaron como quests nuevas.

El único JAR directo del export es CC:Tweaked. El ZIP también incluye numerosos resource packs; no se asumió que fueran questbooks ni se descargaron 560 dependencias para perseguir el dato. Esto descarta errores obvios del contador y ubicaciones SNBT adicionales del export, pero **no verifica el contenido aportado por los mods descargados ni el questbook cargado**. La discrepancia de la publicidad de Craftoria permanece abierta; 1.136 sigue siendo sólo el mínimo de definiciones exportadas medido.

Para FTB, una lectura directa del HTML oficial actual también respondió **HTTP 403**. No se intentó evadirlo ni reutilizar datos de versiones antiguas. La ruta corta oficial no aportó manifiesto nuevo: quedan pendientes el inventario y las quests efectivamente cargadas de 1.43.1. No cambia la meta provisional ni se afirma paridad cumplida. Esta revisión se mantuvo en el ZIP oficial conocido y la página FTB, sin expansión de catálogo.
