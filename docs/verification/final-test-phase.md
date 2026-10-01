# Fase final de pruebas

Desde el 29/9, todo lo que pide abrir el juego, un servidor o GameTests queda para el final del árbol de progreso (Elias: «ponemos los tests ingame a lo ultimo de todo el progress tree, una vez este el 99%»). Mientras tanto se integra con validación estática y compilación. Esta es la lista de lo que quedó pendiente, por frente; cada rama nueva que difiera algo lo suma acá.

La fase arranca con la PC libre: un solo controlador, sin otras sesiones ni agentes, con toda la RAM para el servidor y el cliente.

## Orden sugerido

1. Servidor: arranque limpio con el pack entero, sin errores de carga ni de recetas.
2. GameTests pendientes, en una sola corrida guardada (`scratchpad/guarded_gametests.sh` como base).
3. Auditoría de runtime de recetas y balance (`entrelumen_runtime_audit.js` y los `*_balance.js` con su chequeo `loaded-ingredient-check`). La auditoría viene con las líneas por ítem apagadas (eran 6.030 por carga): para esta fase prendé `{"full": true}` en `kubejs/config/entrelumen_audit.json` del servidor o generá el script con `python tools/check_runtime_content.py --sync --full` (README de `pack/kubejs`) y verificá con `--log`. En el log mirá también `[ENTRELUMEN_RECIPE_INDEX]` (`indexed` y `scans: 0`; con `fallback-to-scans` o `indexed-outputs-by-scan`, anotar el motivo) y cualquier `failed-row`: una fila que KubeJS rechazó, con su receta nativa todavía en pie.
4. Cliente: el libro de quests, capítulo por capítulo, y lo visual de cada frente.
5. Rendimiento con el pack entero.
6. Playtest de Elias.
7. Instalación en su cliente, con backup y su sí.

## Mods y catálogo

- Rondas 5 a 7 (Neo Vitae y complementos, Iris, Hardcore Revival, Mystical Agradditions, ME Beam Former, Squat Grow, Industrialization Overdrive, Extended Industrialization y el resto): QA de servidor con `runtime_all.sh` y sus 15 GameTests, y rendimiento.
- Aeronautics: decidir con sus números en mano.
- Dynamic FPS: con `idle.condition: none` el modo inactivo corre siempre (10 FPS a los 5 minutos sin tocar nada); confirmar que no molesta.
- Mystical Customization: el cultivo del lingote luminoso (nivel 5, supremium) y su receta de esencia.

## Balance y loot

- Datos de mods con derechos reservados reemplazados por stubs y archivos propios: los augments de Eterna de Apotheosis en `entrelumen/tier_augments`, el ritual de la estela eterna en `entrelumen/forbidden_arcanus`, las recetas de tiza de Malum y los stubs apagados. Confirmar que el juego los lee como se leyó en los JAR (la lista está en `docs/design/mod-pingpong.md`).
- Varita del Tiempo de Just Dire Things al 32.º: tope ×8.
- Squat Grow: 5 veces más lento y sin tocar Mystical, AE2 ni los cultivos excluidos.
- Tablas de loot nuevas y alias (`check_loot_tables.py` pasa estático; falta verlas caer).

## El Envés

- Jugarlo entero: pisos, ecos, campeones, santuarios, sellos, bóvedas, la Luz Agria y la bolsa.
- El Grillete Agrio: las marcas flotando en la muñeca con y sin ítem en la mano, el estallido y el 300 % contra jefes.
- Canje de esquirlas con Cenit en Solsticio.

## Sistemas propios

- Jardín de Terra: el motor de 3×2×2 que se arma en un modelo, la lámpara 3D, los cristales de terraluz (4 h, lluvia ×8), las 900 cosechas por segundo y que la lámpara vuelva al romperlo.
- Ruinas de Heliodor (las 11) y Solsticio: verlas generadas.

## Libro de quests (V2)

Nada del libro nuevo se vio todavía en el cliente. Revisar en cada capítulo:
- las fuentes `quest_big` y `quest_icons`, los glifos `[li:…]` y los sprites animados (llamas, Luminosidades, terraluz);
- los `glow` teñidos y el orden en que se dibuja la escena al avanzar;
- las notas con `hover`, que dependen de que FTB las muestre al hacer clic;
- las teclas `[key:…]`, que en las vistas previas salen como `[?]`;
- los ítems con modelo 3D que el renderizador de vistas previas no dibuja (mochila, cabeza de dragón, cajas, estandartes, cabezas de jugador);
- la interferencia `[glitch|…]` de los actos I a IV.

Datos leídos del código que conviene confirmar jugando:
- Neo Vitae: el nivel de orbe de la Tabula Vitae (el código pide el de la receta), los fragmentos de hierro que el mod genera al arrancar, el costo del meteoro y las tareas de mirar la demonita y matar Daemonium.
- Complementos de Modern Industrialization: los pulsos de fertilizante, las unidades del tope de transferencia de los bobinados, la duración de la celda fotovoltaica, los números del Vajra y el enlace del constructor con un punto de acceso inalámbrico.
- Tombstone: que no haya tumba mientras estás caído con Hardcore Revival.

## Instalación en tu cliente

Con todo lo anterior en verde, el sí de Elias y un backup de su instancia.
