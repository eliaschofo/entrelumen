# Consistencia de respaldos en Windows

## Evidencia fijada

Inspección de SimpleBackups 1.21-4.0.30, NeoForge 21.1.249 y Ars Nouveau 5.13.1 mediante bytecode de los JAR instalados. No se modificó el servidor.

En `work/server-slice/logs/latest.log` se observó un único `AccessDeniedException`: a las 18:24:31.138 del 12-09-2026, `IO-Worker-10` no pudo mover `an_redstone_signals.dat*.neoforge-tmp` a `an_redstone_signals.dat`. El backup comenzó18:24:30.167 y terminó18:24:33.627. En la inspección posterior el destino existía, pesaba63bytes y tenía modificación18:24:55; no quedaba temporal. Esto demuestra un guardado posterior, no equivalencia semántica entre el estado anterior y el reintentado.

Ars `RedstoneSavedData.isDirty()` retorna siempre true, por lo que los próximos guardados vuelven a persistirlo. NeoForge `IOUtilities.atomicWrite` escribe y fuerza el temporal, intenta ATOMIC_MOVE y sólo usa REPLACE_EXISTING ante AtomicMoveNotSupportedException. AccessDenied no tiene reintento: elimina el temporal y propaga la excepción. El destino previo no fue reemplazado por ese intento.

## Hook disponible: no en esta versión

`BackupThread.makeWorldBackup(Path)` es privado. Cuando `saveAll=true` llama `server.executeBlocking(Runnable)`; el runnable privado `lambda$makeWorldBackup$7` ejecuta `server.saveEverything(true,false,true)`. Inmediatamente después de executeBlocking comienza la apertura de ZipOutputStream y posteriormente walkFileTree. No hay llamada a `IOUtilities.waitUntilIOWorkerComplete`, ni publicación de evento pre/post entre flush y ZIP, ni callback inyectable en la firma.

Los únicos tipos Event/Hook/API encontrados en este JAR son `EventListener` y `client.ClientEventHandler`. EventListener consume eventos NeoForge de registro de comandos, LevelTick.Post y conexión/desconexión. No es un evento de backup extensible. Las entradas públicas tryCreateBackup/createBackup tampoco ofrecen callback después del flush.

Configuración inspeccionada: enabled=true, saveAll=true, FULL_BACKUPS, experimental.enabled=false. No existe opción para esperar la cola SavedData. No se recomienda desactivar saveAll ni excluir an_redstone_signals: ocultaría el problema y debilitaría la copia.

## Recomendación concreta

La compatibilidad mínima, si se decide implementarla, es una inyección versionada al retorno de `MinecraftServer.saveEverything(ZZZ)Z` dentro del runnable de makeWorldBackup, ejecutada en el **Server thread**, que invoque `IOUtilities.waitUntilIOWorkerComplete()` antes de devolver el control a SimpleBackups. Así la espera termina antes de abrir el ZIP y no corre en el IO worker que debe completar la cola. Mantener saveAll=true. El método lambda es detalle privado: fijar versión/descriptor y exigir que el punto de inyección exista; no tratarlo como API estable. No implementar sobre IO-Worker ni añadir sleeps arbitrarios.

Preferencia a largo plazo: corrección upstream de SimpleBackups en ese mismo punto. No se verificó una versión publicada que ya la incluya, por lo que no se recomienda actualizar a ciegas ni sustituir el sistema de respaldos sin evidencia. Repositorio oficial declarado por el JAR: https://github.com/ChaoticTrials/SimpleBackups ; licencia Apache2.0.

La barrera elimina la superposición con las escrituras ya encoladas por ese flush, pero no convierte el ZIP completo en un snapshot atómico ni impide futuros autosaves/mods/antivirus durante su creación. El error coincide temporalmente con el backup: todavía no identifica quién retuvo el handle de Windows. No se demostró una colisión repetible ni corrupción permanente. El backup de ese instante pudo contener datos anteriores.

## Validación antes de aceptar una corrección

En una copia aislada Windows: repetir guardado+backup con cambios reales de señales Ars, comprobar ausencia de errores, abrir el archivo dentro del ZIP y restaurar la copia para contrastar los datos. Verificar que la espera ocurre en Server thread y que el backup sólo inicia después de completar la cola; no aceptar únicamente el mensaje Backup completed. Si persiste AccessDenied, identificar el bloqueo externo y revisar la consistencia del snapshot antes de ampliar el parche.

Estado: diagnóstico y recomendación; sin Mixin, cambios de configuración ni afirmación de reproducción.

## Restauración offline posterior — 23 de septiembre

Un ZIP del mundo QA con el juego cerrado se restauró íntegro en un servidor separado con el mismo build: 100 archivos y 56 directorios verificados, excluyendo sólo `session.lock`. El arranque, guardado y cierre normales conservaron la campaña, los datos de jugador y FTB; el mundo original quedó intacto. El log no tuvo líneas ERROR ni AccessDenied, pero conserva 88 advertencias. La [evidencia de restauración](../verification/offline-restore-runtime.json) fija hashes, solicitudes y límites. Esta prueba no ejecutó SimpleBackups en vivo ni reproduce o resuelve la carrera descrita arriba; tampoco verifica actualización entre betas o interacción de un cliente con la copia restaurada.
