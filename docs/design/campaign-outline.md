# ENTRELUMEN — mapa editorial de campaña

Estado: **diseño de producción, no contenido jugable añadido**. Este documento acompaña `content/campaign-outline.json`. No modifica las primeras 25 quests, el companion ni los archivos FTB. La producción posterior espera el cierre del QA de entrada y GUI del Atlas.

## Distribución y ritmo

36 capítulos en seis actos, con un presupuesto editorial de 25 quests por capítulo: 900 en total, de las cuales 150 serían principales. Es una herramienta de planificación, no una cuota que justifique relleno. Si una tarea no enseña, plantea una decisión, verifica una instalación útil o desarrolla un relato ganado, se fusiona o se elimina.

La primera slice ya contiene los cinco hitos principales del acto I. Se conserva su cierre en `first_signal`; sus otros cinco capítulos serán aprendizaje opcional y no bloquearán el acto II. Los actos II–VI presupuestan 29 principales cada uno: cinco en los primeros cinco capítulos y cuatro en el cierre. Así se preserva lo existente sin estirar artificialmente el early game.

Las **150–200 horas son un objetivo pendiente de playtests**. Hipótesis inicial para el recorrido principal: I 1–3h, II 20–27h, III 30–38h, IV 30–42h, V 35–45h, VI 34–45h (150–200h sumadas). Estos rangos no son temporizadores, bloqueos ni duración publicada. Repetir un crafteo para esperar que pase el tiempo no cuenta como diseño. Registrar tiempo activo, espera, viaje repetido y ayuda recibida por separado; ajustar proyectos si la espera domina.

## Estructura de cada capítulo

El JSON contiene título y beat narrativo en EN/ES; propósito, aprendizaje, alternativa, cierre, utilidad retenida de mods pequeños, proyecto asociado, prerrequisitos y criterio de verificación en español editorial. Los textos finales para jugadores se escribirán completos en ambos idiomas con el glosario vigente.

Los capítulos II–V abren una entrada, ramas de oficios paralelas y una convergencia. En VI, los cinco primeros módulos pueden prepararse en paralelo tras el cierre de V; exploración reúne módulos, expediciones y puesta en marcha. Los prerrequisitos editoriales no se convierten automáticamente en requisitos de servidor: esa integración debe implementarse y probarse explícitamente.

Un proyecto asociado puede aparecer en varios capítulos porque sostiene aprendizajes distintos. No se vuelve a cobrar ni se vuelve a premiar por aparecer otra vez. Las demostraciones tienen hitos narrativos distintos a implementar; nunca se acreditan por tener un objeto. Los 22 proyectos de `integration-design.json` quedan cubiertos; sus recetas siguen siendo propuestas y no se presentan como funcionalidad terminada.

## Mapa de capítulos

### Acto 1

| ID | Capítulo | Principal / opcional | Resultado y función |
|---|---|---|---|
| a1_c1 | Una luz entre ruinas / A Light Among Ruins | 5 / 20 | Preservar íntegra la primera hora ya escrita. |
| a1_c2 | Estantes con nombre / Shelves with Names | 0 / 25 | Hacer que ordenar sea una decisión útil y breve. |
| a1_c3 | La segunda cena / The Second Supper | 0 / 25 | Ofrecer variedad alimentaria sin exigir una cocina enorme. |
| a1_c4 | Una puerta bajo la lluvia / A Door in the Rain | 0 / 25 | Enseñar construcción cómoda sin puntuar decoración arbitraria. |
| a1_c5 | El regreso seguro / The Safe Way Home | 0 / 25 | Practicar orientación antes de exigir grandes expediciones. |
| a1_c6 | Dejá lugar para crecer / Leave Room to Grow | 0 / 25 | Preparar una base ampliable sin convertir planificación en grind. |

### Acto 2

| ID | Capítulo | Principal / opcional | Resultado y función |
|---|---|---|---|
| a2_c1 | Una medida de latón / A Measure of Brass | 5 / 20 | Convertir fabricación precisa en capacidad durable. |
| a2_c2 | Luz entre cobre / Light through Copper | 5 / 20 | Diferenciar energía, transformación y materiales conductores. |
| a2_c3 | Un taller que respira / A Workshop that Breathes | 5 / 20 | Presentar magia como un proceso que se comprende y cuida. |
| a2_c4 | Provisiones para el camino / Provisions for the Road | 5 / 20 | Dar propósito a cocina y pesca durante expediciones más largas. |
| a2_c5 | Dos maneras de mover una rueda / Two Ways to Turn a Wheel | 5 / 20 | Comparar soluciones sin declarar obsoleto el oficio más pequeño. |
| a2_c6 | El aprendiz ausente / The Missing Apprentice | 4 / 21 | Cerrar el acto uniendo oficios y preparar el intercambio. |

### Acto 3

| ID | Capítulo | Principal / opcional | Resultado y función |
|---|---|---|---|
| a3_c1 | Un lenguaje compartido / A Shared Language | 5 / 20 | Introducir almacenamiento digital al servicio de sistemas existentes. |
| a3_c2 | Semillas de continuidad / Seeds of Continuity | 5 / 20 | Abrir recursos renovables por capacidades y tamaño razonable. |
| a3_c3 | Energía en lugares pequeños / Power in Small Places | 5 / 20 | Enseñar energía suficiente y reserva, sin acelerar a reactores. |
| a3_c4 | Cuando el estante está lleno / When the Shelf Is Full | 5 / 20 | Enseñar que una automatización buena también se detiene. |
| a3_c5 | Manos para el taller / Hands for the Workshop | 5 / 20 | Reservar transporte flexible para problemas donde aporta algo. |
| a3_c6 | El primer enlace roto / The First Broken Link | 4 / 21 | Unir logística con la razón humana de abastecer. |

### Acto 4

| ID | Capítulo | Principal / opcional | Resultado y función |
|---|---|---|---|
| a4_c1 | El archivo espectral / The Spectral Archive | 5 / 20 | Abrir investigación arcana con pruebas contradictorias. |
| a4_c2 | Dos cielos ajenos / Two Foreign Skies | 5 / 20 | Dar expediciones concretas con regreso preparado. |
| a4_c3 | El acuerdo de los polinizadores / The Pollinator Treaty | 5 / 20 | Presentar ecosistema como relación, no sólo cosecha de drops. |
| a4_c4 | Un recuerdo sellado / A Memory with a Seal | 5 / 20 | Hacer contención comprensible y útil para el Arca. |
| a4_c5 | El margen del guardián / The Guardian’s Margin | 5 / 20 | Concentrar combate en una expedición memorable y recuperable. |
| a4_c6 | El Atlas fue corregido / The Atlas Was Edited | 4 / 21 | Resolver la contradicción central sin convertirla en examen de lore. |

### Acto 5

| ID | Capítulo | Principal / opcional | Resultado y función |
|---|---|---|---|
| a5_c1 | Una red que se adapta / A Backbone that Bends | 5 / 20 | Construir capacidad de recuperación antes de escalar. |
| a5_c2 | Una reserva viva / A Living Reserve | 5 / 20 | Integrar tres vías renovables sin crecimiento descontrolado. |
| a5_c3 | Un lugar al que volver / A Place Worth Returning To | 5 / 20 | Cerrar el arco de cocina y habitabilidad sin consumir decoración. |
| a5_c4 | Una entrega fallida / One Failed Delivery | 5 / 20 | Practicar errores recuperables antes de comprometer el Arca. |
| a5_c5 | Lugar para otra respuesta / Room for a Second Answer | 5 / 20 | Premiar alternativas reales sin forzar duplicar fábricas. |
| a5_c6 | La red que no repetimos / The Network We Refuse | 4 / 21 | Elegir reconstrucción distribuida como resolución de la investigación. |

### Acto 6

| ID | Capítulo | Principal / opcional | Resultado y función |
|---|---|---|---|
| a6_c1 | El motor del Arca / The Beating Engine | 5 / 20 | Instalar ingeniería y conservar fabricación precisa. |
| a6_c2 | La cámara que escucha / The Listening Chamber | 5 / 20 | Estabilizar memoria y preservar contexto. |
| a6_c3 | El jardín en reserva / The Garden in Reserve | 5 / 20 | Completar naturaleza con abastecimiento finito y reposición. |
| a6_c4 | Nada perdido en el camino / Nothing Lost in Transit | 5 / 20 | Hacer legibles las cantidades de puesta en marcha. |
| a6_c5 | Un lugar para todos / Room for Everyone | 5 / 20 | Vincular cierre con hospitalidad y base útil. |
| a6_c6 | El último horizonte / The Last Horizon | 4 / 21 | Completar exploración, puesta en marcha y cierre recuperable. |

## Integración, límites y comprobación

- **Oficios que permanecen:** cristales de Actually Additions en acopladores/redes, precisión de Create y alambre/acero de Immersive Engineering, regeneración de Nature’s Aura, pesca de Aquaculture, interpretación/contención de Malum y EvilCraft, sensores y transporte de Integrated Dynamics/LaserIO/routers. Su función debe observarse en un sistema útil, no sólo en la receta final.
- **Decoración:** Supplementaries y Amendments aportan señalización, circulación y experiencia del hogar. Una autoevaluación de refugio se etiqueta como tal; no se afirma que un servidor mide belleza ni se consumen sillas como materiales raros.
- **Alternativas:** se exige demostrar una solución válida al problema, no construir todas. Cuando una receta propuesta mezcla varias disciplinas, la alternativa se refiere al abastecimiento o al transporte descrito; no inventa una sustitución de ingredientes que aún no existe.
- **Intercambio:** los regalos funcionan libremente. Tener una pieza no acredita lectura, expedición ni reconocimiento narrativo del equipo. No se comprueba quién fabricó el objeto.
- **Combate:** un encuentro destacado en el acto IV, seleccionado contra la progresión real de Twilight Forest antes de producir quests; encuentros posteriores sólo si añaden una experiencia, sin repetir bosses para llenar capítulos. La retirada y el reintento conservan los hitos ya obtenidos.
- **Arca:** seis módulos, cuatro fases propuestas (`calibrate`, `stabilize`, `provision`, `chart`) y descubrimientos del equipo. Preservar bloques y lotes aceptados al reiniciar. El controlador actual debe ampliarse: sus seis interacciones no equivalen a esas fases. Ningún drenaje offline ni extracción remota implícita.
- **Pruebas por capítulo:** ejecutar el criterio concreto del JSON; distinguir autoevaluación manual de evento verificado en servidor. Comprobar recetas cargadas, adquisición survival sin ciclos, replay/reinicio sin premios duplicados y retorno de recipientes. No usar checks de estructura como sustituto del playtest.

Antes de autorizar cada paquete de capítulos: cerrar sus integraciones, escribir un recorrido principal completo, probarlo desde el estado del acto anterior y recién entonces desarrollar sus ramas opcionales. No producir 900 entradas automáticas a partir de este mapa.

## Riesgos que este esquema no cierra

La duración, diversión, rendimiento y curva de poder requieren juego real. El encuentro exacto del acto IV y las demostraciones detectables en servidor se resolverán contra los mods y eventos disponibles antes de implementar esos capítulos. Los IDs del esquema son editoriales; no son nuevos IDs FTB ni migran campañas guardadas. Sólo se consideran estables los IDs ya publicados por la slice existente.
