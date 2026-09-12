# Integración de mods — actos II–VI

**Estado: propuesta implementable, no campaña implementada ni balance validado.** El JSON adjunto es la fuente de IDs, cantidades, proyectos y evidencia. Son 22 recetas nuevas de mesa de crafteo y 16 componentes por registrar; los seis resultados restantes son módulos propios existentes.

## Regla de progresión

Los actos indican cuándo se enseña y obtiene normalmente una técnica. Las recetas son estáticas y no consultan equipo, acto ni procedencia. Los regalos funcionan. Presentar un objeto no acredita una expedición ni completa automáticamente la historia: cada proyecto requiere antecedentes narrativos, la demostración indicada y aceptación explícita. Los ejercicios de instalación que se resuelvan con una casilla manual deben identificarse como autoevaluación, sin prometer detección automática.

Las cantidades son prototipos pequeños. La continuidad proviene de reutilizar componentes en proyectos posteriores y lotes del Arca, no de fabricar miles de objetos. No se añade una máquina ensambladora, un escaneo global ni un coste de mantenimiento offline. Cada receta expande sus cantidades en nueve casillas o menos; deben conservarse los recipientes mediante el comportamiento nativo.

## Proyectos

| Acto | Proyecto / ID local | Resultado | Demostración útil |
|---|---|---|---|
| 2 | Una medida de latón — `precision_bench` | `entrelumen:calibration_frame` | Producir 4 marcos con prensa y trefilado; conservar la línea para futuros instrumentos. |
| 2 | Luz entre cobre — `crystal_grid` | `entrelumen:energy_coupler` | Convertir redstone con láser y abastecer componentes sin destruir instalaciones. |
| 2 | Un taller que respira — `living_workshop` | `entrelumen:living_matrix` | Obtener fibra renovable y hierro infundido; explicar consumo/regeneración de aura. |
| 2 | Provisiones para el camino — `travelling_pantry` | `entrelumen:ration_bundle` | Preparar dos cadenas alimentarias; el componente es provisión de proyectos, no comida con buffs acumulables. |
| 3 | Un lenguaje compartido — `signal_exchange` | `entrelumen:routing_matrix` | Fabricar procesadores y PCB usando sus procesos propios; guardar y solicitar un lote de matrices. |
| 3 | Semillas de continuidad — `nursery_protocol` | `entrelumen:propagation_core` | Completar un ciclo de cosecha y otro de colmena; el núcleo habilita obtención de granjas seleccionadas, no su uso. |
| 3 | Energía a pequeña escala — `distributed_power` | `entrelumen:power_regulator` | Mantener una línea pequeña durante un lote; generación eólica o generador IE son alternativas, sin reactor obligatorio. |
| 3 | Cuando el estante está lleno — `measured_logistics` | `entrelumen:inventory_sensor` | Mover un lote a un buffer y detener entrada al umbral; demostrar una de dos rutas, sin imponer ambas redes. |
| 3 | Manos pequeñas, grandes talleres — `workshop_hands` | `entrelumen:handling_core` | Transportar 32 objetos entre dos contenedores sin arrojar entidades; routers avanzados conviven con tuberías simples. |
| 4 | Voces en el cristal — `spectral_archive` | `entrelumen:spectral_lens` | Recuperar testimonios y fabricar lentes; no consumir familiares ni obligar ciclos de sacrificio masivos. |
| 4 | Más allá del dosel — `horizon_survey` | `entrelumen:horizon_chart` | Visitar dos destinos distintos y volver con muestras; mapa personal y waystones ayudan, no equivalen a completar la expedición. |
| 4 | El acuerdo de los polinizadores — `pollinator_treaty` | `entrelumen:ecosystem_capsule` | Observar una interacción ecológica en Bumblezone y mantener una colmena productiva en casa. |
| 4 | Un recipiente para la memoria — `sealed_memory` | `entrelumen:containment_seal` | Procesar materiales arcanos y sellar un lote; otra ruta opcional de transformación acompaña su utilidad tardía. |
| 5 | Una red que se recupera — `resilient_backbone` | `entrelumen:ark_bus` | Vaciar y reponer un buffer; reanudar fabricación sin duplicación. Panel ComputerCraft opcional, mismo resultado sin código. |
| 5 | Horizontes renovables — `renewal_engine` | `entrelumen:renewal_engine` | Entregar un lote de cada vía renovable sin forzar chunks adicionales ni multiplicadores descontrolados. |
| 5 | Un lugar al que volver — `settlement_supply` | `entrelumen:habitation_contract` | Abastecer cocina y depósito; habitación jugable con luz, descanso y señalización. Decoración libre no consumida ni contada como rareza. |
| 6 | El motor del Arca — `ark_engineering` | `entrelumen:engineering_module` | Instalar módulo y completar lote de calibración; conserva su estado al reiniciar. |
| 6 | La cámara que escucha — `ark_arcana` | `entrelumen:arcane_module` | Instalar módulo y entregar un lote sellado; fallo de activación no elimina bloques ni exige familiares nuevos. |
| 6 | La reserva viva — `ark_nature` | `entrelumen:nature_module` | Completar reserva por lotes y cerrar; sin drenaje mientras nadie juega. |
| 6 | Una brújula para otras orillas — `ark_exploration` | `entrelumen:exploration_module` | Registrar rutas descubiertas del equipo y preparar destino final; muestras regaladas no simulan descubrimientos. |
| 6 | Nada perdido en el camino — `ark_logistics` | `entrelumen:logistics_module` | Entregar lote mixto desde buffer y acreditar una sola vez; cualquiera de las redes puede transportarlo. |
| 6 | Un lugar para todos — `ark_habitation` | `entrelumen:habitation_module` | Completar suministros de viaje y zona de descanso; sin recetas que consuman sillas o bloques decorativos arbitrarios. |

Los ingredientes exactos están en `projects[].recipe.inputs`. Cada disciplina conserva su fabricación nativa antes de ensamblar el componente común. Introducir los componentes no reemplaza las recetas de máquinas existentes.

## Papel de los 39 mods de contenido

Las relaciones enlazan proyectos concretos del JSON. Los mods arquitectónicos ofrecen ejercicios opcionales y no se consumen como peaje en el Arca. Los transportes simples siguen abasteciendo tramos locales aunque exista AE2.

| Mod | Introducción | Utilidad posterior | Proyectos relacionados |
|---|---|---|---|
| `actuallyadditions` | II: Laser conversion and compact production | Retain conversion recipes in advanced instruments | `crystal_grid`, `signal_exchange`, `ark_engineering` |
| `ae2` | III: Digital logistics | Coordinate inventories used by all six Ark disciplines | `signal_exchange`, `settlement_supply`, `ark_logistics` |
| `aether` | IV: Sky expeditions | Recover observatory knowledge through a distinct dimension | `horizon_survey`, `ark_exploration` |
| `amendments` | I: Habitabilidad interactiva: ajustes a bloques existentes y detalles del puesto de suministros. | Añadir interacción al asentamiento con Supplementaries sin pedir su ítem de tinte como impuesto. | `settlement_supply` |
| `aquaculture` | I: Fishing variety | Food and collection routes for coastal expeditions | `travelling_pantry`, `ark_habitation` |
| `ars_nouveau` | II: Spellcraft and source automation | Magic instruments and automated botanical supplies | `living_workshop` |
| `buildinggadgets2` | III: Construir grandes envolventes consumiendo materiales del jugador. | Usar buffers Functional Storage para abastecer herramientas; no ingrediente de módulos. | `settlement_supply` |
| `chipped` | I: Taller de variantes y paletas arquitectónicas. | Personalizar la envolvente FramedBlocks/Arca sin cuotas de bloques raros. | `settlement_supply` |
| `computercraft` | III: Optional programming | Monitor workshop systems without making coding mandatory | `resilient_backbone` |
| `constructionstick` | I: Construcción repetitiva cómoda desde temprano. | Extender almacenes y jardines con materiales de Sophisticated Storage. | `settlement_supply` |
| `cookingforblockheads` | I: Practical kitchen | Teach shared provisioning infrastructure | `travelling_pantry`, `settlement_supply`, `ark_habitation` |
| `create` | II: Precision manufacturing | Produce mechanical instruments for Atlas projects | `precision_bench`, `spectral_archive`, `ark_engineering` |
| `evilcraft` | IV: Blood machinery | Alternative specialized transformation research | `sealed_memory`, `ark_arcana` |
| `farmersdelight` | I: Cooking and provisions | Expedition meals and the habitability Ark module | `travelling_pantry`, `settlement_supply`, `ark_habitation` |
| `farmingforblockheads` | I: Accessible farming | Establish food production and garden planning | `nursery_protocol` |
| `framedblocks` | I: Formas y recubrimientos para arquitectura propia. | Construir junto a Chipped y Rechiseled; sólo estructura opcional, sin receta de tributo. | `settlement_supply` |
| `functionalstorage` | I: Bulk storage | Keep foundational material buffers useful at endgame | `measured_logistics` |
| `handcrafted` | I: Habitaciones y mobiliario libre. | Puesto de cocina de Cooking for Blockheads rodeado por una zona de descanso elegida por el jugador. | `settlement_supply` |
| `immersiveengineering` | II: Visible electrical engineering | Bulk materials and wiring for observatory infrastructure | `precision_bench`, `crystal_grid`, `distributed_power` |
| `integrateddynamics` | III: Logic and measurement | Instrument reconstruction systems | `measured_logistics`, `ark_logistics` |
| `integratedtunnels` | III: Programmable transport | Alternative precise material logistics | `measured_logistics`, `ark_logistics` |
| `laserio` | III: Compact channel logistics | Connect production disciplines with bounded networks | `measured_logistics`, `ark_logistics` |
| `malum` | IV: Soul research | Alternative late magic research and stabilization | `spectral_archive`, `sealed_memory`, `ark_arcana` |
| `mekanism` | III: Chemical and industrial processing | Supply advanced alloys to the engineering Ark module | `distributed_power`, `resilient_backbone`, `ark_engineering` |
| `mekanismgenerators` | III: Scalable energy | Power industrial reconstruction without mandatory reactors | `distributed_power` |
| `modularrouters` | III: Compact item automation | Reduce entity-heavy automation in reconstruction projects | `workshop_hands`, `resilient_backbone`, `ark_logistics` |
| `mysticalagriculture` | III: Staged resource crops | Crop specialization as an alternative resource route | `nursery_protocol`, `renewal_engine`, `ark_nature` |
| `naturesaura` | II: Environmental magic | Nature restoration and sustainable ritual materials | `living_workshop`, `pollinator_treaty`, `renewal_engine`, `ark_nature` |
| `occultism` | II: Ritual craft and spirits | Spirit processing for the magic Ark module | `spectral_archive`, `ark_arcana` |
| `pipez` | II: Introductory transport | Teach inventories before advanced logistics | `workshop_hands` |
| `pneumaticcraft` | III: Pressure automation | Precision logistics and pressure-made components | `signal_exchange`, `resilient_backbone` |
| `productivebees` | III: Bee breeding and resources | Staged biological inputs and nature research | `nursery_protocol`, `pollinator_treaty`, `renewal_engine`, `ark_nature` |
| `rechiseled` | I: Variantes coherentes de piedra y cobre. | Paleta alternativa a Chipped para infraestructura IE; ninguna variante obligatoria. | `settlement_supply` |
| `sophisticatedbackpacks` | I: Portable inventory | Expedition organization with staged advanced upgrades | `horizon_survey` |
| `sophisticatedstorage` | I: Configurable workshop storage | Upgrade existing workshop organization | `workshop_hands` |
| `supplementaries` | I: Utility decoration | Useful infrastructure for inhabited reconstruction sites | `settlement_supply`, `ark_habitation` |
| `the_bumblezone` | III: Pollinator expeditions | Link bees and nature module research | `pollinator_treaty`, `ark_nature` |
| `twilightforest` | IV: Structured adventure | Spaced encounters for exploration research | `horizon_survey`, `ark_exploration` |
| `waystones` | II: Established travel routes | Connect discovered expedition outposts | `horizon_survey`, `ark_exploration` |

## Arca funcional y recuperable

Las funciones siguientes son comportamiento propuesto del controlador, no APIs ya comprobadas. Los módulos se colocan una vez y no se consumen. La interacción valida un volumen acotado; no debe cargar chunks ni recorrer máquinas ajenas.

| Módulo | Función propuesta |
|---|---|
| `entrelumen:engineering_module` | Acepta lotes de calibración y muestra reservas pendientes; habilita el diagnóstico de ingeniería del controlador. |
| `entrelumen:arcane_module` | Registra sellos de contención y testimonios recuperados; permite reanudar la estabilización sin repetir entregas. |
| `entrelumen:nature_module` | Registra cápsulas ecológicas y el proyecto de restauración; muestra qué abastecimiento natural falta. |
| `entrelumen:exploration_module` | Compila la carta final con expediciones del equipo verificadas; abre la escena final sólo junto a las demás fases. |
| `entrelumen:logistics_module` | Presenta cantidades restantes por fase y acepta lotes parciales por interacción; no extrae inventarios remotos. |
| `entrelumen:habitation_module` | Registra provisiones y el refugio documentado; conserva el abastecimiento sin hambre artificial ni deterioro offline. |

Las cuatro fases son calibrar, estabilizar, abastecer y cartografiar. Sus lotes están en `arkBehavior.phases`; cada aceptación debe persistirse por equipo y ser idempotente. Reiniciar conserva lotes y módulos. La finalización exige las expediciones del equipo y las cuatro fases, sin pedir que las fábricas permanezcan encendidas. Estas funciones son de proyectos e información; no generan FE, objetos o buffs pasivos gratuitos.

## Recursos por etapas

Acto III: obtener núcleos de propagación para las primeras rutas de hierro, cobre y redstone. Acto IV: cápsulas ecológicas para especializaciones de oro, cuarzo y recursos dimensionales. Acto V: motor de renovación y bus del Arca para abundancia de diamante, esmeralda y netherita. Esto es una selección de obtención pendiente de cerrar, no cambios de recetas aplicados.

Antes de modificar una semilla o cría hay que enumerar todas sus recetas cargadas, intercambios y vías alternativas. No tocar la esencia base ni los productos requeridos para fabricar su propio componente: eso produciría ciclos. No bloquear crecimiento, producción ni uso de semillas o abejas regaladas. No elevar simultáneamente coste y velocidad para convertir la misma espera en un trámite.

## Evidencia y verificaciones pendientes

El JSON registra 54 IDs de mods mediante modelos exactos de objetos en los JAR seleccionados, con nombre de JAR y SHA-256 del catálogo. Incluye referencias exactas a recetas cuando existen. Un modelo o una referencia dentro de una receta prueba el recurso, no que el registro esté activo o que la receta sobreviva condiciones de carga. No se copian recetas ni contenido editorial de otros packs.

Antes de implementar/aceptar:

1. Confirmar en el servidor los IDs, recetas efectivamente cargadas y sus salidas. Las referencias pueden ser ingredientes, no necesariamente recetas productoras.
2. Registrar y traducir los 16 componentes; exportar después las 22 recetas. Verificar crafteo manual, automatización AE2 y devolución de cuencos.
3. Demostrar cada proceso nativo sin dependencias circulares; comprobar conexiones de energía e inventario en estas versiones, sin asumir compatibilidad por nombre.
4. Resolver IDs concretos de semillas, crías, componentes de datos y alternativas antes de editar obtención de recursos.
5. Probar regalos, equipos separados, reinicios durante cada lote y solicitudes repetidas. La autoevaluación de edificios no sustituye validación de entregas.
6. Traducir tutoriales y nombres propios EN/ES, revisar tiempos reales y medir las líneas de producción. No hay evidencia todavía de 150–200 horas ni del rendimiento de estas instalaciones.
