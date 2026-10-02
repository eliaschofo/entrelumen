# FTB Teams: ciclo de vida nativo en QA

`TeamLifecycleGameTests.nativePartySnapshotsArchiveRecoveryAndReconnect` usa dos `ServerPlayer` conectados por la ruta de `placeNewPlayer` con conexión mock NeoForge, como `ResourceFarmGameTests`. Las transiciones de equipo pasan por `TeamManager.createPartyTeam`, `PartyTeam.invite`/`join` y el comando `ftbteams party leave`. En FTB Teams 2101.1.11 estas rutas emiten `TeamEvent.CREATED`, los cambios de jugador y `TeamEvent.DELETED` al salir el último dueño; no se invocan los hooks de Entrelumen directamente.

Antes de crear el partido, ambos jugadores ganan `atlas_awakened` por el comando de entrega con materiales reales; sólo el invitado gana además `lens_assembled`. Se cuentan Atlas y lente en inventarios. La creación copia una vez la campaña del fundador; el ingreso del invitado conserva su historial personal distinto sin fusionarlo. Una entrega real de `travellers_table` hecha por el fundador dentro del partido consume exactamente pan y bowls y queda sólo en la campaña compartida. Se comparan todos los totales de inventario en creación, ingreso, salidas, reconexión y recuperación para detectar pérdidas o duplicaciones de materiales/recompensas.

Para cubrir la conservación de depósitos sin montar el Ark, el test prepara tres historiales act 6 elegibles con depósitos parciales y distintos, dentro de los topes del paso de calibración: fundador 1 frame, invitado 1 regulador, partido 2 frames. Son **fixtures de SavedData**, no evidencia de que la interacción física del Ark acepte depósitos. La salida del invitado restaura su snapshot; la reconexión con el mismo UUID comprueba que sigue en su equipo personal y que recupera inventario y campaña. La salida del último dueño elimina el equipo nativo y archiva su campaña, sin alterar los snapshots personales.

La rama administrativa se prueba desde un jugador normal (comando denegado por el nodo Brigadier `requires(permission 2)`) y luego desde una fuente de comando de jugador elevada a permiso 2. `entrelumen admin recover <uuid>` copia el archivo a la campaña personal del ejecutor: se comprueba el estado recuperado, la permanencia del archivo y la invariancia de inventarios. La elevación en el test no instala un operador permanente. La limpieza sale sólo del equipo QA creado, desconecta ambos jugadores y libera los canales; se ejecuta en un mundo QA descartable.

La prueba es headless con conexiones mock: no acredita un cliente humano, paquetes de invitación aceptados por UI, reinicio de proceso ni restauración de backup. La reconexión sí pasa por otra llamada real a `placeNewPlayer` con el mismo UUID.

Semántica actual de recuperación: `admin recover` **reemplaza** la campaña activa del equipo o la personal del OP por `archived.copy()`; no fusiona historiales ni preserva lo que tenía el destino. El archivo original sigue marcado como archivado, por lo que la operación puede repetirse. Es una restauración destructiva para un destino con progreso; la UX o una recuperación protectiva quedan como decisión posterior, fuera de esta prueba.

El caso pasó el 23 de septiembre en el dedicado con 135 dependencias reales y watchdog de 60 segundos. El recibo confirma todas las transiciones, dos Atlas y una lente en total, depósitos conservados y cero jugadores conectados después de limpiar las sesiones. [Evidencia y límites](../verification/magic-cooking-teams-runtime.json).

## Administración desde la consola (1 de octubre)

`/entrelumen admin` (permiso 2) suma tres formas que no necesitan un jugador que ejecute, así funcionan desde la consola del servidor:

- `admin archived` lista cada campaña de partido archivada: su UUID, el acto y cuántos proyectos completó. Devuelve la cantidad.
- `admin recover <uuid> <jugador>` hace lo mismo que `admin recover <uuid>`, pero sobre la campaña actual del jugador nombrado (la de su partido o la personal).
- `admin set <jugador> <acto>` lleva la campaña actual del jugador nombrado a ese acto, como `admin set <acto>` con el propio OP.

Las formas viejas siguen iguales y las nuevas comparten la misma rutina (`Entrelumen.recover` y `Entrelumen.setAct`). En `admin set` el acto se prueba antes que el jugador, así `set 3` sigue siendo un acto aunque exista un jugador llamado «3». `nativePartySnapshotsArchiveRecoveryAndReconnect` cierra con la consola: lista los archivos, recupera el del partido para el invitado por nombre y le pone el acto 2 sin tocar al fundador. Esta parte todavía no corrió en el servidor de pruebas.

## Qué pasa con el Arca, la parcela y el jardín (1 de octubre)

`TeamHandoff` acompaña los mismos eventos de FTB Teams: al crear un partido, el Arca del fundador pasa al partido, se copian sus tiendas conocidas y su sitio de jardín, su parcela de Solsticio pasa al partido y su Llave forjada cuenta también para el partido. Al borrarse el partido, el Arca y la parcela vuelven al dueño si no tiene propias; si las tiene, el registro del Arca se borra y la parcela queda libre (los bloques quedan). Las tiendas conocidas y las Llaves forjadas quedan con el archivo. Lo cubren `TeamHandoffTest` (unidad) y `RuntimeGameTestsArkCampaign` (servidor de pruebas, todavía sin correr).
