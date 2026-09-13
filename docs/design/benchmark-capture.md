# Captura de benchmarks optativa — diseño pendiente de implementación

Estado: propuesta técnica; no existe colector nuevo ni medición realizada por este documento. No modifica companion ni el analizador. Referencia: `tools/benchmark.py`, Minecraft 1.21.1, NeoForge 21.1.249, Java 21.

## Activación y costo

Instrumentación local deshabilitada por defecto. Una futura opción explícita habilita comandos de inicio/parada; la captura de servidor requiere operador, la captura cliente sólo afecta al propio cliente. Nada se inicia al entrar a un mundo, abre conexiones externas, publica perfiles ni envía CSV a otros jugadores. Guardar en un directorio local por run_id, nunca sobreescribir una corrida. Registrar hora UTC y revisión/hash de pack, companion, configuración y colector.

Fuera de captura: listeners con retorno inmediato, sin timers, escritura ni muestreo. Durante captura: timestamps monotónicos y escritura de primitivas a buffers acotados; un escritor local por proceso drena lotes, sin IO, formato CSV, consulta de registros ni asignaciones grandes por frame/tick. Si se llena el buffer, marcar corrida incompleta y detener: nunca omitir muestras silenciosamente ni bloquear el render. Cerrar handles y retirar muestreadores al parar, desconectar o apagar. Medir el overhead posteriormente con recorridos A/B equivalentes; todavía no se le atribuye un costo probado.

## Frames: intervalo real del bucle, no tiempo de dibujo

API verificada: `net.neoforged.neoforge.client.event.RenderFrameEvent.Pre/Post`. Los sources locales muestran `ClientHooks.fireRenderFramePre(timer)` antes de `gameRenderer.render(...)` y Post después; `window.updateDisplay()` y `RenderSystem.limitDisplayFPS(...)` ocurren posteriormente. Los eventos se omiten con `Minecraft.noRender`.

En cada Pre guardar `System.nanoTime()`. Para dos Pre consecutivos válidos, emitir el segundo timestamp y `(ahora-anterior)/1e6` como frame_ms. Esto incorpora dibujo, trabajo entre frames, swap/vsync y espera de limitador. No usar Pre→Post como frametime: sólo mide parte del trabajo CPU. No usar DeltaTracker/partial ticks ni FPS agregado. Esta medida es cadencia del bucle de render; no es duración GPU ni garantiza cuándo el monitor mostró cada frame.

CSV de ruta: `elapsed_ms,frame_ms` (se permiten columnas extra). elapsed_ms se deriva del mismo instante final que frame_ms, con origen fijo. Conservar decimales suficientes y no redondear a enteros. El primer Pre sólo arma el par; no inventar un primer delta. Capturar hasta que el span entre primera y última fila de ambos streams alcance al menos 300000 ms; apuntar a 305 s evita fallar por los extremos. No detener sólo al alcanzar 300 s desde el comando.

Flags en archivo raw/sidecar: foco (`Minecraft.isWindowActive()`), pausa (`isPaused()`), mundo/jugador presentes, dimensión, pantalla/overlay, noRender y segmento. El gate inicial requiere mundo cargado, jugador presente, foco, no pausa/overlay de carga y recorrido preparado. Un menú de inventario no equivale automáticamente a carga: registrar su clase y declarar la política del recorrido.

Si ocurre alt-tab, pausa, pantalla de carga, desconexión o cambio de dimensión durante la ruta, cerrar el segmento y marcarlo no apto para esa aceptación; reiniciar un tramo continuo de cinco minutos. No borrar filas intermedias y unir extremos: viola el chequeo de frames consecutivos y falsea los percentiles. Preservar raw y motivos. Worldgen de exploración, autosaves y GC durante juego válido NO se descartan aunque sean lentos. Una transición intencional puede tener un segmento diagnóstico separado.

## Ticks: costo y cadencia son magnitudes distintas

API verificada: `ServerTickEvent.Pre/Post`, `getServer()`, `MinecraftServer.getTickCount()`, `getTickTimesNanos()` y `isPaused()`. En Post, el source de tickServer ya escribió el costo de ese tick en `getTickTimesNanos()[getTickCount() % 100]`. Copiar ese valor inmediatamente (no guardar el array mutable). Emitir `elapsed_ms,tick_ms`, timestamp monotónico de Post y costo nanosegundos/1e6. Así se conserva la definición vanilla de duración de tick, sin confundir el descanso entre ticks con trabajo.

Opcionalmente conservar Pre→Post como `event_work_ms` diagnóstico; su alcance excluye partes del bucle y depende de orden de listeners. La métrica vanilla también excluye trabajo de listeners Post y tareas ejecutadas durante la espera del bucle: documentar `tick_scope=vanilla_tickServer` y no presentarla como costo total del proceso. No usar el promedio suavizado o la media de 100 ticks como muestras individuales. TPS se deriva de timestamps consecutivos, no de 1000/tick_ms.

Registrar tick counter; detectar saltos, duplicados, reinicio y modo /tick freeze/sprint/rate diferente de 20. La corrida de aceptación exige cadencia normal y ausencia de pérdida de filas. El analizador actual exige TPS promedio 19.8–20.2 y p95 tick_ms <50, pero NO prueba TPS sostenidos; agregar revisión de ventanas móviles de 10 s/60 s en evidencia auxiliar, sin declarar que ese chequeo ya existe en benchmark.py.

## Origen temporal y servidor dedicado

Integrado/LAN: cliente y servidor comparten proceso y un único origen nanoTime de la sesión; no crean relojes independientes. Dos productores separados evitan carreras. En dedicado, nanoTime de máquinas diferentes no se puede restar directamente. Capturar streams locales y sincronizar una sesión explícita mediante varios intercambios de timestamps cliente/servidor, estimando offset por menor RTT y registrando incertidumbre, RTT, deriva inicial/final y transformaciones. Conservar raw sin conversión. La conversión sólo ajusta elapsed_ms a origen común, nunca las duraciones medidas.

No afirmar aceptación si incertidumbre o deriva impiden asegurar alineación dentro de un segundo y diferencia de spans <=1%; eso requiere primero implementar y validar el protocolo. Alternativa inicial: dedicado como diagnóstico separado hasta tener alineación reproducible. Registrar hardware del servidor aparte; seis jugadores requieren clientes externos reales. No simular seis clientes en el portátil objetivo.

## Memoria y GC: sesión de dos horas

Sampler optativo cada 30 s en hilo propio: `ManagementFactory.getMemoryMXBean().getHeapMemoryUsage()` (used/committed/max), `getGarbageCollectorMXBeans()` (count/time por collector; -1 se guarda como desconocido en sidecar), uptime y process CPU opcional. `memory.csv` obligatorio: `elapsed_ms,heap_used_mb`, MiB=bytes/1048576 documentado pese al nombre histórico mb. Guardar desde t=0 y hasta span >=7200000 ms, >=121 filas, ningún intervalo >65000 ms. Dormir el sampler no depende de frames/ticks: pausa no impide observar memoria.

Java21 permite notifications de GC vía `NotificationEmitter`/`com.sun.management.GarbageCollectionNotificationInfo` y `GcInfo`: conservar inicio/fin/duración y uso por pool después de GC. Comprobar soporte antes; si no existe, usar log local JVM `-Xlog:gc*,safepoint:file=...:time,uptime,level,tags` elegido al arrancar. No activar GC forzado, heap dumps ni System.gc() para mejorar la gráfica. Eventos de GC son evidencia auxiliar; `heap_used_mb` sigue siendo muestreo real, no valores inventados post-GC.

Revisión escrita requerida por benchmark.py: comparar piso post-GC en ventanas de carga comparable, cambios de committed, ocupación old-gen, frecuencia/tiempo de pausas y cambios de escenario. Distinguir calentamiento/cache de crecimiento persistente; máximo de heap o porcentaje de RAM por sí solo no prueba fuga. No declarar stable si no hubo ciclos comparables suficientes: dejar incomplete. Dedicado guarda memoria cliente y servidor separadas, nunca suma series o comparte una conclusión sin revisar ambas.

## Metadatos y entrega compatible

Config JSON con claves actuales: run_id, captured_at, pack_revision, collector, evidence_kind=real sólo tras captura real, scenario (`early_base`, `mid_base`, `late_base`, `exploration`), mode (`singleplayer`, `integrated_lan`, `dedicated`), players y external_clients. hardware: cpu=i7-8750H, gpu=GTX 1070, ram_gb=16, os y java exactos. preset: width=1920,height=1080,render_chunks=10,simulation_chunks=6,shaders=false,heap_gb<=8 (presupuesto único cliente+servidor integrado). Registrar también vsync, límite FPS, escalaGUI, alimentación/perfil térmico, driver y mods efectivos; no cambiarlos entre rutas sin declararlo.

world: seed, save_id, route_id, dimension, preloaded=true para bases/false para exploración; guardar ruta, preparación y snapshot del mundo con su hash. No exportar save ni datos personales automáticamente. dedicated añade server_hardware. memory_review: conclusion stable/sustained_growth y evidence_file relativo al config, sólo después de revisión humana; hasta entonces unreviewed.

Artefactos: config.json, frames.csv, ticks.csv, memory.csv, raw flags/GC, capture-integrity.json (drops, segmentos, clocks, abortos), memory-review.md y reporte. Invocación: `python tools/benchmark.py --config <run>/config.json --frames <run>/frames.csv --ticks <run>/ticks.csv --memory <run>/memory.csv --output <run>/report.json`. La sesión de memoria puede analizarse sola omitiendo ambos streams de ruta. No alimentar raw con segmentos inválidos directamente: el script ignora columnas extra, no filtra flags. Una futura exportación debe negarse ante integridad inválida.

Definiciones vigentes: FPS medio=1000*N/suma(frame_ms); 1%low=1000/p99 nearest-rank, NO promedio del peor 1%; p95 ticks nearest-rank. El status pass cubre un escenario y revisión suministrada, no campaña, rendimiento integral ni TPS sostenidos.

## Alternativa y verificación previa a implementar

PresentMon puede aportar observación externa de presentación en Windows, útil para contrastar bucle CPU con frames presentados. No es primera opción: OpenGL/driver, semántica de la versión instalada, proceso objetivo y event loss deben verificarse realmente antes de recomendar un comando o convertir columnas; no se verificó aquí una instalación ni se promete compatibilidad. Ninguna descarga/instalación autorizada por este diseño.

Antes de aceptar un colector: tests de buffer overflow/abort, pares de frames/clock origin, cambios de flags, ticks sin pérdidas, cierre; corrida corta real para confirmar cadencia con cap/vsync y etiquetas; A/B de overhead; luego cinco minutos continuos por escenario y dos horas GC-aware. Los tests sintéticos sólo prueban matemática/protocolo. No reemplazan estos recorridos.

## Evidencia de API local consultada

- `neoforge-21.1.249-sources.jar`, eventos RenderFrameEvent y ServerTickEvent, caché Maven local NeoForge.
- `sourcesAndCompiledWithNeoForge_447a22689bb98030d481b11ff9a729ce8e4aa204_output.jar`, fuentes Minecraft.java y MinecraftServer.java de la caché neoformruntime: posiciones de hooks, presentación, pausa/foco y ring de ticks.
- `tools/benchmark.py`: contrato CSV, duración, reloj, metadata y revisión de memoria inspeccionados directamente. Documento no altera ninguno de esos contratos.
