# Renumeración de actos y Corazón de Heliodor

Decisión de Elias del 24 de septiembre de 2026 ([biblia](story-bible.md), «Estructura de actos» y «El Corazón de Heliodor»). Rama `feature/acts-renumber`.

- La preparación industrial (el acto V viejo) y el Arca (el acto 6 del código: seis módulos, lotes y la activación que forja la Llave de Luz) se fusionan en un solo **acto V, «El Arca»**.
- **Solsticio pasa a ser el acto VI.** Empieza cuando el equipo activa el Arca: la activación registra `last_horizon`, forja la Llave de Luz y pone la campaña en el acto 6 en la misma mutación.
- **El Sun Spirit cierra el acto IV.** Suelta el Corazón de Heliodor, y el acto IV sólo cierra con el Corazón entregado en el Atlas.

## Actos y capítulos

| Acto | Capítulos de quests | Proyectos del Atlas | Cierra con |
|---|---|---|---|
| I–III | sin cambios | sin cambios | Avanzar en el Atlas |
| IV · Las voces del Atlas | `voices_of_the_atlas` (31 quests: +2) | los cuatro de antes + `heliodor_heart` | `atlas_voices`, que ahora también pide `heliodor_heart`; después Avanzar |
| V · El Arca | `world_we_build` «V · El Arca» (24) y `last_horizon` «V · La activación» (27) | `resilient_backbone`, `renewal_engine`, `settlement_supply`, `world_network` y los seis módulos | la activación del Arca, sin Avanzar |
| VI · Solsticio | `solsticio` «VI · Solsticio» (1 quest, para crecer) | ninguno todavía | — |

Son 174 quests (antes 171) y 45 tareas de campaña (antes 42). El capítulo lateral *El inventario que recuerda* pasa a ser el octavo; sólo cambió su `order_index`.

## Gates antes y después

«Antes» es `main` en 605c9b6.

| Gate | Antes | Después | Por qué |
|---|---|---|---|
| Seis proyectos de módulo (`projects.json`, `integration-design.json`) | acto 6 | **acto 5** | El Arca es el acto V. Recetas, costos y recompensas no cambian. |
| Lotes del controlador (`ArkCommissioning.eligible`) | acto == 6 | **acto == 5** | Ídem. En el acto IV o en el VI no se acepta nada. |
| Fases `ark_calibrated` … `ark_charted` (`CampaignMilestones.isComplete`) | acto == 6 | **acto ≥ 5** | Siguen completas después de la activación, ya en el acto VI. |
| Activación (`CampaignMilestones.canFinish`/`finish`) | acto == 6 | **acto == 5**; al activar, acto = 6 | El acto VI empieza al activar el Arca, con la Llave forjada. |
| Avanzar desde el acto V | proyectos del acto 5 (`world_network`) → acto 6 | proyectos del acto 5 **y** `last_horizon` | Ningún Avanzar saltea la activación. En la práctica el Atlas no ofrece Avanzar en el acto V: la activación ya pasa al VI. |
| Avanzar desde el acto VI | no existe | no existe | `Campaigns.FINAL_ACT = 6`. |
| `Solsticio.GATE` | `ActGate(6, last_horizon)` | **igual**: `ActGate(Solsticio.ACT = 6, last_horizon)` | Mismo valor, sentido nuevo: acto VI = Solsticio. Conserva el hito para que un acto puesto a mano no abra la ciudad. |
| Llave de Luz, portal, reliquias, lote, región protegida | `Solsticio.GATE` | `Solsticio.GATE` | Sin cambios. |
| `solsticio_arrival` (nuevo) | — | campaña que pasa `Solsticio.GATE` | Es el cruce del propio equipo. Un visitante que llega por el portal o la waystone de otro equipo no lo registra. |
| Oferta de élitros (`solsticio_shops/rarities.json`) | acto 6 | **acto 6 (VI)** | Premio del cruce propio. En el acto V el equipo ya va al End y puede sacarlos de las ciudades del End; un visitante que todavía no activó su Arca no los compra. |
| Resto de ofertas del comercio (actos 3, 4 y 5) | 3–5 | sin cambios | Mismo sentido: el acto 5 viejo es la primera mitad del V nuevo. |
| Texto de logística y diarios («hace falta el acto …») | acto VI | **acto V** | Ídem módulos. |
| World Tier Summit | acto 6 | **acto 5** | Ver abajo. |
| World Tier Pinnacle | `last_horizon` | **acto 6 o `last_horizon`** | Equivalentes desde la renumeración. |
| Brújula: mazmorras de plata y de oro | acto 5; el oro avanzaba con el logro `aether:gold_dungeon` | **acto 4**; el oro avanza con `heart_recovered` | El Sun Spirit cierra el IV. El hito no depende de que el jugador conectado tenga el logro, y el Corazón entregado también cuenta. |
| Brújula: monumento oceánico, fortaleza, End | acto 6 | **acto 5** | El viaje al End es parte del Arca (`end_arrival`). |
| Brújula: Solsticio (nuevo) | — | **acto 6**: dimensión `entrelumen:solsticio`, avanza con `solsticio_arrival` | La brújula apunta al cruce; la aguja queda en «otra dimensión». |
| Cierre del acto IV (`atlas_voices`) | cuatro proyectos | cuatro + **`heliodor_heart`** | Ver el Corazón. |
| Escalonado de recetas (KubeJS, datos) | por componente de acto | **sin cambios** | Ninguna receta usaba componentes del acto 6. El bus del Arca y el motor de renovación ya eran del acto V. Lo luminoso sigue en el VI por las Luminosidades. |
| Perla divina (material de la familia luminosa) | acto VI (Summit tras el V) | **acto V** | Summit ahora abre con el V. No cambia ninguna receta: todo lo luminoso ya pide Luminosidades (VI). |
| Acceso a dimensiones: Ad Astra (banco NASA, bus del Arca), Eternal Starlight (Orbe de la Profecía, carta de horizontes), The Undergarden (catalizador, matriz de distribución) | V, IV, III | **sin cambios** | Van por componente, no por número de acto. |
| Psi (pedido del 24/9) | II: el ensamblador de CAD pedía un marco de calibración | **III**: el ensamblador pide un regulador de energía | Psi entero en el III; los núcleos de psigema ya pedían una matriz de distribución (III). |
| `/entrelumen admin set <acto>` | 1–6 | 1–6 | — |
| Campañas guardadas (`CampaignData` v2 → v3) | acto 6 = construir el Arca | acto 6 sin `last_horizon` → **5**; con `last_horizon` → queda en 6 | Migración única al leer; se reescribe en el próximo guardado. Módulos, lotes y depósitos intactos. |

## World Tiers

La historia sigue fijando el tier y nunca lo baja (`ApotheosisTiers`).

| Acto | Tier |
|---|---|
| I–II | Haven |
| III | Frontier |
| IV (Sun Spirit) | Ascent |
| V · El Arca | Summit |
| VI · Solsticio | Pinnacle |

Antes, Summit llegaba al terminar la preparación industrial y Pinnacle con la activación. Ahora Summit abre con todo el acto V y Pinnacle con el VI. Una campaña migrada del acto 6 al 5 queda en Summit, igual que antes. El tier guardado del jugador (`entrelumen:story_tier`) no se toca.

## Corazón de Heliodor (`entrelumen:heart_of_heliodor`)

- **Ítem.** Stack de 1, épico, a prueba de fuego, sin receta. Nombre EN/ES «Heart of Heliodor» / «Corazón de Heliodor». Una línea de lore gris: «El cristal que el Espíritu del Sol le quitó a Bodhi. Está tibio y recuerda cada voz.» El modelo usa por ahora la textura `entrelumen:item/heliodor_relic_3`. Es un modelo escrito a mano en el acompañante, fuera de `art/`; cuando el controlador dibuje la variante sin bendecir, `art/build_art.py` lo reemplaza.
- **Fuente.** Un modificador global de loot sobre `aether:entities/sun_spirit` (`HeliodorHeart.DropModifier`, condición `neoforge:loot_table_id`).
  - Lo verifiqué en el JAR fijado `aether-1.21.1-1.5.10-neoforge.jar` (SHA-1 `5b5592989c6d2aabf80a9f100e18c258813db497`, igual al del catálogo). Esa tabla siempre suelta la llave de la mazmorra de oro y el altar solar. `SunSpirit.die` llama a la muerte vanilla, y sólo lo hieren los cristales de hielo que devuelve el jugador (`isInvulnerableTo`), que quedan a su nombre. Por eso el loot trae `LAST_DAMAGE_PLAYER`.
  - Si el jugador acreditado es real (no un FakePlayer) y su campaña no tiene Corazón, el Corazón se suma al loot y la campaña registra `heart_recovered`. Uno por campaña y por mundo: un segundo Sun Spirit, una campaña archivada o una máquina no dan nada. Si un jugador consiguió el Corazón solo y después funda un grupo, el grupo hereda el registro.
  - Descarté el cofre de recompensa: es uno por mazmorra, se llena una vez y lo vacía el primero que llega.
- **En el piso.** No recibe daño (cactus, explosiones, lava), no desaparece por tiempo y, si cae debajo del mundo, vuelve al último piso donde descansó. Si nunca tocó piso, queda flotando donde apareció. Brilla para que se vea. Importa porque las islas del Aether flotan sobre el vacío.
- **Entrega.** El proyecto `heliodor_heart` («A Clear Voice» / «Una voz clara») es del acto 4. Pide `exchange_route` y `heart_recovered`, y consume el Corazón. Sólo vale el Corazón del propio equipo: uno regalado por otro equipo no alcanza. `atlas_voices` lo pide, así que el acto IV no cierra sin él. Con eso el Atlas «habla claro», como dice la biblia.
- **Conservación: el Atlas lo guarda.** La entrega lo consume y la campaña queda con `heliodor_heart`; no hay un ítem que se pueda perder entre los actos IV y VI. Para la misión de Bodhi del acto VI:
  - `HeliodorHeartRules.inAtlas(campaign)` dice si el Corazón sigue en el Atlas.
  - `HeliodorHeart.release(player)` lo devuelve una sola vez, y sólo en el acto VI: da el ítem y registra `heart_released`.
  - La misión puede usar `release` y pedir el ítem, o consumir directamente el estado del Atlas. Convertirlo en `entrelumen:heliodor_relic_3` le toca al próximo worker.
- **Comandos de operador.** `/entrelumen admin heart give` recupera un Corazón perdido antes de entregarlo; registra `heart_recovered` en la campaña del operador. `/entrelumen admin heart status` muestra el estado. `/entrelumen admin heart release` prueba la devolución.
- **Brújula y quests.** En el acto IV la brújula apunta a la mazmorra de oro hasta `heart_recovered`, que también cuenta como cumplido si el Corazón ya está en el Atlas. Hay dos quests nuevas en el acto IV: `voices_sun_spirit` (hito observado `heart_recovered`, sin dependencias, como las llegadas) y `voices_heart` (entrega `heliodor_heart`). `voices_chorus` ahora también depende de `voices_heart`.

## Quests

- **Acto IV:** el subtítulo nombra al Espíritu del Sol. Se sumaron un grupo «El Aether · El Espíritu del Sol» y las dos quests. Cambiaron los textos de `voices_welcome` y `voices_chorus`; el coro ya no manda a buscar al Espíritu, que es parte del acto.
- **Acto V, «El Arca»:** `world_welcome` ya no dice «si el Espíritu del Sol todavía tiene el Corazón». `world_network` ya no pide Avanzar: abre los módulos de «V · La activación».
- **«V · La activación»** (el capítulo `last_horizon`, antes «VI · Solsticio»): se reescribieron las 27 quests con la voz de [quest-lore.md](quest-lore.md). Qué hacer y por qué, en dos párrafos de 500 caracteres como máximo, con Terra, Juan, Bodhi y Aurelia en lugar de Mara, Ivo y Sera. Tareas, IDs, dependencias, íconos y layout no cambiaron: el digest del `.snbt` es el mismo. Los módulos se nombran como en el Atlas, y los insumos, con el nombre del juego.
- **«VI · Solsticio»** (`content/act_six.json`, capítulo `solsticio`): una quest de entrada, `solsticio_arrival`, que depende de `horizon_last`: cruzar con la Llave de Luz y llegar a la ciudad. Tiene su grupo de layout («La Llave de Luz · El cruce»), autofocus propio y lugar para crecer. Las misiones del acto VI las escribe otro worker.
- **Archivos:** `content/act_six.json` pasó a ser `content/act_five_activation.json`, y `content/act_six.json` es el capítulo nuevo.

**Hashes y tests congelados** (`tools/test_generate_quests.py`), reemplazados a propósito:

- `voices_of_the_atlas.snbt`: `1c120b93…` → `7e540719…`. Dos quests nuevas y una dependencia más en `voices_chorus`. Un test nuevo comprueba que las otras 29 quests conservan ID, tarea, ícono y layout.
- Digest del mapa de tareas de los primeros 4 y 5 capítulos: ahora incluye `heart_recovered` y `heliodor_heart`.
- `inventory_that_remembers.snbt` cambió sólo el `order_index` (6 → 7).
- Siguen iguales `a_light_among_ruins`, `the_lost_crafts`, `routes_of_exchange`, `world_we_build` y `last_horizon`, y ahora el digest de `last_horizon` también queda congelado.
- Las frases de adquisición exigidas del capítulo de activación usan los nombres del juego: «otras dos matrices de distribución», «otras ocho provisiones de viaje» / «eight more packs of Travel Rations» y «otra carta de horizontes».
- Contratos nuevos: títulos de los siete capítulos, historia por acto (V junta sus dos capítulos), qué y por qué en todos los actos, sin interferencia desde el V, recompensas de los seis módulos nombradas, cierre del acto IV con el Corazón y entrada del VI.

## Pedidos sumados el mismo día

- **Brazo de Terra:** +3 de alcance de bloques (antes +5), con el mismo modificador `entrelumen:terra_arm_reach`. Suma una línea azul en el tooltip, como las de atributos de vanilla: «+3 block reach» / «+3 de alcance de bloques». Ver [heliodor-ruins.md](heliodor-ruins.md).
- **Psi** pasa entero al acto III (tabla de arriba y [mod-pingpong.md](mod-pingpong.md)).

## Tests

- **JUnit:**
  - tiers por acto y migración (`ApotheosisTiersTest`);
  - lotes sólo en el acto V (`ArkCommissioningTest`);
  - la activación abre el VI, Avanzar no saltea la activación, observaciones (`CampaignMilestonesTest`);
  - migración v2 → v3 (`CampaignPersistenceTest`);
  - `solsticio_arrival` sólo para el cruce propio (`ExpeditionsTest`);
  - brújula renumerada (`CompassTargetsTest`);
  - proyecto del Corazón y módulos en el acto 5 (`ProjectValidationTest`);
  - Brazo +3 (`TerraArmDataTest`).
- **GameTests aisladas** (`RuntimeGameTestsActs`): el loot del Sun Spirit pasa por el pase real de modificadores de NeoForge (un Corazón por campaña, nada para otra tabla, un FakePlayer o una campaña archivada); propiedades y lore del ítem; el Corazón en el piso resiste daño y vuelve del vacío; el Atlas lo devuelve sólo en el VI; el acto del Arca es el 5 y sólo la activación abre el 6 y la puerta de Solsticio.
- **GameTests actualizadas:**
  - acto IV con el Corazón;
  - el acto V ya no avanza con `world_network`;
  - activación → acto VI y puerta abierta;
  - el cruce registra `solsticio_arrival` y un visitante no.
- **Pack completo** (`ActsFullpackGameTests`): un Sun Spirit real del Aether 1.5.10 muere a manos de un jugador y suelta un Corazón junto a su llave de oro; un segundo no suelta otro; el Corazón cierra el acto IV en el Atlas y el acto V juega en Summit; la brújula apunta a la mazmorra de oro en el IV y a Solsticio en el VI. `RuntimeGameTestsActs` también corre ahí.

## Verificación

Recibo: [`docs/verification/acts-renumber-runtime.json`](../verification/acts-renumber-runtime.json). Los scripts y logs están en `E:/Elias/Codex/Entrelumen-ssd/acts-20260924`.

- **Build y tests.** `gradlew test build runGameTestServer qaJar`: 199 JUnit, las 96 GameTests aisladas pasan.
- **Chequeos de Python.** Los 31 comandos de siempre dan 0: la lista de `verify.yml`, `generate_quests --check`, `curate_pack --check` de cliente y de servidor, las familias con `--check`, la auditoría de teclas y `unittest discover`.
- **QA de pack completo.** Corrió en un servidor propio y nuevo, `server-acts-qa`: de `server-slice` sólo se copiaron las librerías. Tenía los 272 JAR de servidor del lock, `pack/` y el JAR de QA, mundo nuevo y `-Dentrelumen.qa=true`. Hay 127 tests registrados y corren de a uno.
  - **A** (watchdog de 60 s): pasaron 69 de 70, incluidos el Sun Spirit real, el cierre del acto IV en Summit, la brújula, las pruebas aisladas de actos y el Brazo de Terra con +3.
    - Falló el test de tooltip del Corazón: `getTooltipLines` dispara los listeners del pack y uno carga clases de cliente en el servidor dedicado. Ahora el test lee sólo las líneas propias del ítem.
    - La prueba de altar falló por tiempo y pasó al repetirla.
    - La corrida terminó por el watchdog en la prueba de las cuatro recargas, como en `fix/fullpack`. Dejó su datapack temporal en el mundo, así que la siguiente corrida arrancó con un mundo nuevo.
  - **C** (con la tolerancia de 180 s sólo para QA, como en `fix/fullpack`): 55 de 55. Corrió lo que faltaba y repitió los dos tests del Corazón que cambiaron.
  - **B** (JVM nueva, watchdog otra vez en 60 s): pasan los verificadores de reinicio de equipos y de logística.
  - En total pasan 125 de 127. No se corrió el par de backup en vivo, que necesita un ZIP de SimpleBackups y un servidor de restauración; su código no cambió.
- **Auditoría de KubeJS en el servidor de QA:** 131 ítems, entre ellos `heart_of_heliodor`, y 37 recetas, sin fallas.
- **Instalación** en `server-slice` y en los perfiles «ENTRELUMEN» y «ENTRELUMEN Defaults QA», con `main` en 0406861. Se hizo con el instalador administrado de `fix/fullpack`.
  - Antes de escribir se guardaron en ZIP verificados el mundo del servidor (1566 archivos) y `saves/` de los dos clientes (186 y 163). Además quedaron backups de cada archivo reemplazado.
  - En cada perfil se escribieron 16 archivos y se agregaron 7 JAR. La instalación también trae `feature/mods-r4` (605c9b6), que todavía no estaba instalado: sus cinco mods, dos librerías, configs y scripts. Las dos tablas de loot de Create: Dragons Plus que esa ronda dejó de pisar se retiraron al backup.
  - FTB Quests había vuelto a guardar nuestros capítulos en su propio formato SNBT. El instalador ahora acepta esa copia como intacta sólo cuando es semánticamente idéntica al archivo instalado la vez anterior. Así pasaron `voices_of_the_atlas` y, en dos perfiles, `inventory_that_remembers`: se guardó la copia y se reemplazó. Una edición real sigue abortando la instalación.
  - Ningún archivo protegido (opciones, servidores, `local/`) cambió. No se abrió ningún cliente.
  - Arranque normal de `server-slice` después de instalar: listo en 61 s, sin watchdog. El diagnóstico del acompañante da `schema=3` con 161 campañas personales y 31 de grupo ya migradas. En el log quedaron sólo los 3 errores upstream de siempre, ninguno del acompañante. Se detuvo con código 0.

## Pendiente

- Arte: la variante sin bendecir del Corazón (controlador).
- Misiones del acto VI y la misión de Bodhi que convierte el Corazón en la tercera reliquia (próximo worker, con la API de arriba).
- Ver en un cliente las quests nuevas, la aguja «otra dimensión» apuntando a Solsticio y el Corazón brillando en el piso.
- La pelea real contra el Sun Spirit no se jugó: la prueba de pack completo le da el golpe final con daño a nombre del jugador, no con cristales de hielo.
