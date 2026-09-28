# Brazos hacia afuera: mods fuera de los tres packs (27/9)

Pedido de Elias (27/9): después de absorber ATM10, FTB Evolution y Craftoria ([censo](mod-census.md)), buscar afuera QoL y addons (tiers de Botany Pots, extras de Mekanism, addons de AE2, Create y Ars) que sumen contenido divertido y explosivo sin romper la estabilidad, y no sumar por sumar.

## Método

Seis búsquedas de sólo lectura en paralelo, una por familia: Create; AE2 y almacenamiento; Mekanism y demás tecnología; Ars y magia; granja y automatización; QoL y rendimiento.

- **Fuentes:** la API de Modrinth (NeoForge 1.21.1), CurseForge vía cfwidget y el proxy público curse.tools, más código e issues en GitHub.
- **Qué se cruzó:** el lock (`catalog/`), el censo y las decisiones de las rondas 1 a 4 ([mod-pingpong](../design/mod-pingpong.md)).
- **Qué no se hizo:** no se bajaron JAR ni se probó nada en el juego. La compatibilidad sale de lo que declaran los mods o de su código.

## Entran (ronda 6)

| Mod | Qué aporta | Acto | Notas |
|---|---|---|---|
| Create Collision Fix 1.0.0 | Evita un crash de Create 6.0.10 que deja el servidor reiniciándose en bucle (choques de contraptions) | todos | Adelantado a la ronda 5. Se saca con Create 6.0.11 (PR #10301) |
| Mekanism Pipez Fix 1.0.1 | Los caños de Pipez dejan de mover cosas a los multibloques de Mekanism tras reiniciar; un reactor de fisión se queda sin agua y explota | III–V | 12 mixins; confirmar el arranque con Mekanism 10.7.19 y Pipez 1.2.31 |
| Neruina 3.3.3 + Configurable 3.5.2 | Congela la entidad o máquina que falla en cada tick en vez de tumbar el servidor | todos | En QA sus avisos cuentan como error |
| Async Locator Refined 1.6.0 | Mapas del tesoro, delfines y `/locate` buscan sin frenar el servidor | todos | Complementa la brújula, que ya busca asíncrona |
| Chunky 1.4.23 | Pregenera el mundo antes de abrir el servidor | servidor | Probar que las ruinas salen igual en chunks pregenerados |
| Dynamic FPS 3.11.4 | Baja FPS y volumen con la ventana en segundo plano | cliente | Desenfocado a unos 15 FPS; la función de batería apagada |
| Ping Wheel 1.12.2 | Marcar un lugar o una criatura para el equipo de FTB Teams | todos | Trae español argentino; botón Mouse5 |
| Petrol's Parts 1.3.7 | Piezas de ingeniería para Create: diferencial, engranajes planetarios, rueda de 5×5, tubo neumático, calentador por fricción, programador MIDI | II (hidráulica en IV) | Ata Create a 6.0.10; apagar su batería cinética (la tiene Create Connected) |
| Rail Grinding 1.2.2 | Deslizarse por las vías de Create conservando el impulso | II | |
| Create: Integrated Farming 1.4.1c | Cosechadora de área, redes de pesca en trenes y barcos, gallineros con spouts | II | Fijar 1.4.1c: desde 1.4.2 pide Supplementaries 3.9.9 (tenemos 3.9.5) |
| Croptopia & Botany Pots compat 1.0.0 | Cultivos y árboles de Croptopia en macetas y Tiers | I–II | Autor único; si no, sus JSON (MIT) van al generador |
| Alshanex's Familiars 4.0.4 + FamiliarsLib 1.8 | Familiares magos de Iron's que se doman y pelean con hechizos | II–V | Medir su worldgen; su mago carga creepers |
| Ars Affinity 1.1.1 | Afinidad por escuela de Ars: pasivas y una habilidad activa | II–IV | Compilado contra Ars 5.10: probar en 5.13.1 |
| Sanguine Neural Networks 2.0 | Sangre para el altar de Neo Vitae desde un modelo de HNN, sin granja de mobs | IV | Entra con Neo Vitae |
| Psionic Utilities 1.4 | Colores y atajos para programar Psi | III | Cliente |
| Irons Spell N FTB Teams 1.0.0 | Las invocaciones de Iron's no atacan a tu equipo | II–VI | Beta de un mixin: pedirle un GameTest; se apaga por config |

Resultado: entraron en las rondas 5 y 6 ([mod-pingpong](../design/mod-pingpong.md#ronda-6-279-contenido-de-la-búsqueda-hacia-afuera)), salvo Petrol's Parts: su librería fija JEI en rangos que no incluyen el del pack.

## Preguntas para Elias

Elias las decidió el 28/9; el detalle está en [mod-pingpong, Ronda 7](../design/mod-pingpong.md#ronda-7-289-las-preguntas-de-la-búsqueda-hacia-afuera).

| Mod | Qué es | Choque o riesgo | Decisión (28/9) |
|---|---|---|---|
| ME Beam Former 1.3.0 | La red ME unida con rayos de luz visibles | ExtendedAE ya conecta sin cable; su torre de energía sin tope pisaría a Flux Networks (se le saca la receta) | Entra (ronda 7), sin la torre de energía |
| Dark Doppelganger 3.4.0 | Jefe opcional del End que copia tu equipo y castea hechizos de Iron's | Ronda 3: «jefes: ninguno» | Afuera |
| Animus 5.2.13 | Addon de Neo Vitae: sigilos, rituales y LP compartido | Crash de cliente abierto en multijugador; un sigilo acelera bloques ×32 | Aprobado si se arregla el crash; sigue en 5.2.13 ([#156](https://github.com/TeamDman/Animus/issues/156)): pospuesto |
| Cataclysm: Spellbooks 1.1.14 | 65 hechizos de los jefes de Cataclysm y dos escuelas | Beta y dos librerías nuevas | Afuera |
| Adam's Ars Plus 6.0.5 | Aumentos de nivel II y III, Dominio, Limitless | Sube el techo de Ars; la Eficiencia apilada rompe costos | Afuera |
| Create: Wizardry 0.5.0 | Puente Create–Iron's: tintas y libros con Create | Ata Create 6.0.10; la 0.5.1 en pre-release; sólo vía CurseForge | Afuera |
| Create Big Cannons 5.11.7 | Cañones multibloque, obuses y espoletas | Diseñado para PvP; los proyectiles ignoran reclamos; opción: sólo daño a entidades | Afuera |
| Steam 'n' Rails (port) 0.3.0-beta.2 | Todo Steam 'n' Rails 1.7 portado | Beta con un crash abierto de carga de mundo en NeoForge 21.1.249 | Afuera |
| Create: Gunsmithing 1.4.9 | Armas steampunk fabricadas con máquinas de Create | Ronda 3: «combate: ninguno»; un crash de servidor dedicado abierto | Afuera |
| Brewin' and Chewin' 4.5.0 | Barril de fermentación, quesos y bebidas | Ronda 2: afuera «para no inflar»; licencia ambigua | Afuera |
| Mystical Agradditions 8.0.14 | Sexto tier de MA con semillas de estrella del Nether y huevo de dragón | Rompe «el Wither es la única fuente» de estrellas, que ahora también abren el Envés | Entra (ronda 7): insanium y sus semillas en el acto VI; Elias acepta las semillas de estrella y de huevo |
| Better Fusion Reactor 1.5.9rc1 | La fusión como minijuego: reactividad que hay que seguir | Pasar el Bus del Arca y las quests a sus bloques; sacarle el irradiador | Afuera |
| Mekanism Nuclear Weapons & Explosives 1.7.0 | Bombas industriales, nucleares y de antimateria con radiación | Código cerrado y joven; sin verificar si respeta reclamos y ruinas | Afuera |
| Hardcore Revival 21.1.22 | En co-op quedás caído unos segundos para que te levanten | Permiso de distribución sin verificar | Entra (ronda 7), sólo en co-op; el mismo archivo está en Modrinth |
| Controlify 3.0.1 | Soporte de mando y Steam Deck | Ninguno sin mando | Afuera |

## Descartes, por motivo

- **Ya en el pack:** los addons fuertes de AE2 (ExtendedAE, AdvancedAE, Merequester, Ars Énergistique), Mekanism Extras y More Machine, Iron's Gems 'n Jewelry, Starbunclemania, Not Enough Glyphs, Bells & Whistles, Copycats+.
- **Sin versión 1.21.1:** Too Many Glyphs, Ars Artifice, Ars Scalaes, Ars Instrumentum, AgriCraft, Thermal, Crazy e Insane AE2 Addons, Litematica, Axiom.
- **Choque verificado:**
  - Lithium rompe la protección contra explosiones de FTB Chunks.
  - More Culling crashea con Sodium 0.8.
  - Fast Noise corrompe paquetes con biomas de mods.
  - Let Me Despawn crashea con Carry On.
  - Las nukes de MI tienen radio mínimo 140 y borran bloques sin pasar por los reclamos.
  - La radiación de Deep Resonance ignora los reclamos.
- **Duplican lo nuestro:** Create Jetpack, Stuff 'N Additions, Diesel Generators, TFMG, Ore Excavation, Garnished, Interiors, Power Loader, Immersive Petroleum, AE Additions.
- **Rompen el escalonado o el diseño:** celdas infinitas, energía gratis, Evolved Mekanism, armas de Mekanism, Numismatics.

El detalle por familia (conteos, enlaces y versiones) está en los reportes de cada búsqueda, resumidos acá.
