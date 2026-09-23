# El inventario que recuerda — capítulo opcional

`content/inventory_that_remembers.json` contiene 16 quests originales EN/ES: siete reconocen objetos y nueve son autoevaluaciones manuales. Todas tienen `optional: true`; el capítulo declara `milestones: []` y cada quest genera `rewards: []`. La entrada depende de `signal`, clave real de la quest del hito `first_signal` en `content/first_hour.json`. No se agregó ninguna dependencia desde los capítulos de campaña hacia esta rama. La primera señal abre la nota de Mara cuando ya existe un hogar al cual volver; conseguir una mochila antes no completa el relato principal.

## Recorrido y límites editoriales

La primera hora ya pide un cofre, enseña el depósito de objetos coincidentes y propone reponer comida y antorchas al regresar (`storage`, `unpack`). Esta rama no repite esa consigna: prueba qué queda en la mochila, qué recuerda un cofre de Sophisticated Storage, cómo se filtra la recogida, qué repone la mochila y qué pasa en una ruta de depósito concreta. `content/act_three.json` trata logística medida y AE2; aquí la interfaz del taller es local y temprana. La última autoevaluación se deja para cuando exista una red AE2 y comprueba que una reserva física siga siendo útil. Esa comparación futura no es requisito de ningún acto.

Los cuadrados detectan posesión, sin consumir objetos. La mochila, el cofre y las cinco mejoras/controladores no prueban instalación, filtros, enlaces ni transferencias. Los círculos dicen **Self-check / Autoevaluación** en el título y piden observación del jugador con materiales reemplazables. No se finge un detector de reservas, automatización o fallas de red. Las variantes de tier y los filtros avanzados se agruparon en la función básica; no son quests extra para inflar la cuenta.

## Referencias de la instalación exacta

JARs leídos de `G:/curseforge/Instances/ENTRELUMEN/mods`, sin ejecutar el juego ni copiar contenido de otro pack:

| Archivo instalado | Evidencia dentro del JAR | Decisión de contenido |
| --- | --- | --- |
| `sophisticatedbackpacks-1.21.1-3.25.78.2107.jar` | `data/sophisticatedbackpacks/recipe/{backpack,pickup_upgrade,refill_upgrade,deposit_upgrade}.json`; `assets/sophisticatedbackpacks/models/item/` para los cuatro IDs; `assets/sophisticatedbackpacks/lang/{en_us,es_es}.json` | La mochila básica usa cofre de madera, cuero e hilo. Pickup recoge; Refill repone una pila seleccionada desde la mochila; Deposit transfiere al inventario usado con clic derecho agachado. El texto español **Coincidir con el contenido del inventario** aparece en el locale instalado. |
| `sophisticatedstorage-1.21.1-1.5.91.2127.jar` | `data/sophisticatedstorage/recipe/{oak_chest_from_vanilla_chest,filter_upgrade,controller}.json`; modelos `chest`, `filter_upgrade`, `controller`; `assets/sophisticatedstorage/lang/{en_us,es_es}.json` | El cofre se obtiene de cofre común + palanca. Filter gobierna entrada/salida transportada; Controller admite depósito y transporte hacia/desde un grupo conectado de almacenamientos. Se prueban en escala pequeña. |
| `sophisticatedcore-1.21.1-1.4.90.2299.jar` | `assets/sophisticatedcore/lang/{en_us,es_es}.json`, claves `gui.sophisticatedcore.settings.memory` y `gui.sophisticatedcore.settings.memory.tooltip_detail` | Memory permite reservar una ranura para pilas coincidentes aun cuando se vacía. La autoevaluación retira y devuelve materiales comunes para observarlo. |

Los siete IDs de las tareas tienen modelo y receta en esos archivos. `G:/curseforge/Instances/ENTRELUMEN/config/sophisticatedcore-common.toml` los enumera con `|true`; las recetas usan la condición `sophisticatedcore:item_enabled`. `content/integration-design.json` ya registra como anclas `sophisticatedbackpacks:backpack` y `sophisticatedstorage:barrel`, coherentes con la familia instalada. `docs/design/keybinding-review.md` registra la acción de abrir mochila y colisiones de teclas; por eso la quest remite a **Controles** y no prescribe una tecla.

## Validación y pendientes

Se cargaron los seis capítulos existentes más esta rama y se llamó `generate(data, all_quests, 6)` en memoria: 16 nodos, IDs y DAG válidos, grupos y espaciado válidos, textos EN/ES completos, cero hitos y recompensas. El generador no escribió archivos de salida; la integración de FTB Quests queda a cargo del dueño del generador. Esta única rama acotada no habilita la producción masiva: la primera hora natural sigue pendiente.

Queda la prueba real en cliente de las interfaces y acciones: configurar Pickup y Memory, alimentar el filtro con tolva, conectar dos almacenamientos al Controller, usar Deposit con coincidencia y comprobar la reserva después del depósito. También hay que ver la legibilidad de ambos idiomas y confirmar que FTB Quests abra esta rama después de `signal` sin bloquear campaña. La inspección de recetas, modelos, locales y configuración no demuestra esas interacciones en juego.

## Revisión didáctica de la versión fijada

La mochila básica tiene una sola ranura (`config/sophisticatedbackpacks-server.toml`, `upgradeSlotCount=1`). Los ensayos de Rellenado y Depósito enseñan a intercambiar la mejora anterior; no exigen subir de tier. Se usan los nombres es_es instalados: Recolección y Rellenado. La revisión de bytecode confirmó la coincidencia contra contenido del destino para Depósito y las rutas de Memoria/Filtro; esto sigue sin reemplazar el ensayo manual de esas configuraciones.

## Integración

El generador carga el nuevo capítulo y conserva byte por byte los seis archivos de capítulos anteriores, sus 155 quests y el mapa de 42 hitos; todos los valores EN/ES anteriores permanecen iguales. El capítulo y sus locales se instalaron en cliente principal, cliente QA y servidor propio. La introducción se leyó dentro del juego en ambos idiomas; el servidor resolvió los 122 IDs de objetos de la auditoría, incluidos los siete de esta rama. Los 27 tests de capítulos pasaron. Evidencia y límites: [atlas-inventory-runtime.json](../verification/atlas-inventory-runtime.json). Las autoevaluaciones de instalaciones siguen requiriendo juego real.
