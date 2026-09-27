# Solsticio v8: el plano urbano en bloques (26 de septiembre de 2026)

La ciudadela en terrazas (v7) se reemplaza por la ciudad del plano urbano (`art/concepts/solsticio_plan.py`), que Elias aprobó: el Eje del Sol, dos bulevares en anillo, radiales, calles y pasajes; plazas en los nudos; el Parque del Mediodía con su lago; manzanas con fachada continua y patio; seis barrios. La fase 1 levantó el terreno, la red y la volumetría; la fase 2 la vistió; el pase solarpunk (Elias: «le falta vidrio, vitrales y cobre») le puso encima vidrio, vitrales, cobre, sol y verde, sin tocar la paleta cálida.

- Generador: `art/solsticio/city8.py` (terreno, red, volumetría, cobre y vitrales compartidos, marcadores, controles), `net8.py` (niveles), `landmarks8.py` (hitos y la Cascada del Fin), `dress8.py` (fachadas, interiores y vida de calle), `solar8.py` (el pase solarpunk y el cierre: paneles de vidrio unidos y seguridad de redstone), `export8.py` (plantilla), `grid8.py` (grilla compacta), `render8.py` (renders locales).
- Plantilla: `companion/src/main/resources/data/entrelumen/structure/solsticio/city.nbt`, que se genera con `python art/solsticio/city8.py --export`.

## Relieve

La colina del plano (`height()`) cae un bloque cada tres. Nada queda sobre la pendiente cruda.

- **Calles con perfil diseñado.** Cada calle es plana a lo ancho, tiene descansos y tramos de escalones de uno en uno. Se resuelven por jerarquía: la de menor rango se engancha al nivel de la que cruza, y la mayor deja un descanso en cada cruce. Ningún par de celdas caminables vecinas queda a dos bloques o más.
- **El Eje.** Baja 48 bloques entre la Plaza Mayor y la del Portal en tramos de 5 a 6 escalones, con descansos de al menos 4. El canal de luz baja con él, bajo vidrio.
- **Plazas y lotes.** Las plazas son planas; donde una calle pasa más baja, el borde de la plaza se escalona. Cada lote es una sola plataforma plana: la planta baja de su casa (ver «Casas en la pendiente»).
- **Parque y lago.** El parque desnivela un bloque como máximo. El lago es una sola lámina, con una calzada a nivel para el bulevar.
- **Suelo cerrado.** Queda hueco sólo donde cada cara está tapada. El control de huecos da cero, y la parte de abajo es un domo de roca.

## Casas en la pendiente

Pedido de Elias (27 de septiembre): las casas siguen el relieve, pero diseñadas, no como placas tectónicas inclinadas.

- **Volumen rígido.** Cada casa es un solo volumen con una sola planta baja. Pisos, cornisas y techos son horizontales, y ninguna columna de la casa se adapta al terreno.
- **Filas con ritmo.** Las casas que dan a una misma calle forman filas y suben con ella en un solo escalón: dos bloques en las calles suaves y un piso entero (cuatro) en las empinadas. Cada calle tiene un escalón y una grilla de niveles, así que las cornisas y los aleros arman una escalera limpia. Cada casa toma el escalón más cercano a su puerta: al ras de la calle, uno a tres escalones arriba, o uno o dos abajo del punto más alto de su frente.
- **Muros cortafuego.** Donde una fila sube, el costado de la casa más alta es un muro liso de su piedra, que atraviesa su techo un bloque y lleva una albardilla encima.
- **Base diseñada donde el suelo cae.** Un zócalo de hiladas almohadilladas (ladrillo de piedra y andesita pulida) baja desde el piso hasta el suelo, con una moldura de piedra lisa en el nivel del piso y una banda por piso en las bases altas. En las bases de tres o más bloques hay ventanas de sótano encendidas. En el lado bajo de las calles empinadas, donde la base tiene cuatro bloques o más, se abre una logia con arcos al nivel de la calle.
- **Suelo que sube.** La casa se retira: esa franja del lote queda como terraza de jardín a su nivel, y la cara del suelo más alto hace de muro de contención. Donde hay una calle arriba, una escalera junto al muro baja a la terraza. Donde no hay lugar para la terraza, la pared de la casa retiene el suelo: hiladas ciegas hasta la rasante y ninguna ventana contra la tierra. El terreno nunca entra en la casa y ninguna pared queda cortada.
- **Puertas.** Toda puerta da a su calle:
  - al ras de la vereda, o con un escalón;
  - con una escalinata de rellano y tramo a lo largo de la fachada, cuando la casa queda dos o tres escalones arriba;
  - como puerta baja en la base, con la escalera por dentro, donde afuera no hay lugar;
  - con un pequeño muelle, sobre un canal.

  Nunca hay puertas flotando ni enterradas.
- **Controles del generador.** Imprime estos conteos y no exporta si alguno falla:
  - edificios con una sola planta baja;
  - suelo sobre un piso;
  - terreno dentro de una casa;
  - columnas de pared cortadas;
  - ventanas o puertas bajo la rasante;
  - puertas fuera de suelo caminable;
  - escalones de fila fuera del ritmo.

## Barrios

El color va en techos y detalles, no en fachadas enteras. Cada barrio tiene 3 o 4 variantes de fachada, un material de techo por calle y un ritmo de alturas. Las esquinas suben un piso y llevan un farol. El cobre va encerado en sus cuatro pátinas (fresco, expuesto, a la intemperie, verdín) y se oxida a manchas: cada techo mezcla su pátina con la vecina.

| Barrio | Muros | Techos |
|---|---|---|
| Mercado | arenisca lisa, tallada y ladrillo | terracota naranja (Macaw's Roofs), cobre expuesto y a la intemperie, techos de vidrio |
| Posadas | arenisca amarilla con abeto | abeto, ladrillo, cobre expuesto y a la intemperie, techos de vidrio |
| Jardines | calcita | cobre a la intemperie y verdín, techos de vidrio con jardín en el ático, jardines en azotea |
| Viajeros | calcita y diorita | cobre verdín, prismarina, techos de vidrio |
| Templo | calcita | cerezo, cobre, techos de vidrio, jardines en azotea |
| Talleres | ladrillo, adobe y toba | diente de sierra de cobre con luz norte y lucernarios; la pizarra oscura queda de acento (una calle de cada seis); chimeneas con humo, forjas encendidas en planta baja |

## Calles

- **Pavimentos por jerarquía.** El Eje en cuarzo pulido con bandas de calcita e incrustaciones de oro; los bulevares en ladrillo de piedra y piedra lisa con cordón; las calles en andesita con cordón de toba; los pasajes en adobe, ladrillo de barro y adoquín. No hay bloques que caigan.
- **Faroles.** Cada nueve bloques, con bombilla de cobre bajo un sombrerete de cobre; en los pasajes, farol de pared.
- **Mobiliario.** Bancos, jardineras, fuentes o pozos en los patios.
- **Señales.** Postes en los cruces que nombran la plaza que hay adelante. Son traducibles (`entrelumen.solsticio.place.*` en `en_us` y `es_es`).

## Fachadas e interiores

- **Fachadas.** Cada edificio tiene puerta a su calle, con escalón si el zócalo sube y farol encima. Las ventanas llevan alféizar, postigos o jardineras. Hay balcones en las calles anchas y buhardillas en los faldones que dan a la calle.
- **Vidrieras.** En el Eje, el Mercado y las Posadas, las plantas bajas son vidrieras de tienda con toldo, estandarte, cajones y barriles.
- **Interiores.** Detrás de cada puerta van las habitaciones de `houses.py` (tienda, taberna, cocina, sala, dormitorio, estudio), encajadas en el rectángulo más grande que el lote tiene detrás de la puerta. Hay una escalera de mano entre pisos y faroles bajo cada techo.

## Hitos

- **Palacio del Solsticio.** Cúpula de cobre fresco con nervios de oro y torre del sol. Bajo el óculo, el Salón del Solsticio: el portal, la piedra de viaje y el trono de Aurelia sobre una tarima. En las alas, la sala del consejo y el archivo. Un sol sobre el mar en cada vano (vitral ámbar, oro y turquesa), una banda de sol en el tambor, rayos de oro y ámbar en el sol del frontón, cúpulas de verdín en los pabellones y, en la azotea, un campo de paneles solares alrededor del tambor.
- **Gran Mercado de la Luz.** Nave de vidrio sobre nervios de cobre, con una franja de oro en la clave y pilares de cobre en dos pátinas; una corona de sol en lo alto de cada vano y un sol naciente (núcleo ámbar, rayos de oro, cielo turquesa) en cada testero. Un rellano al nivel de la plaza y una gran escalera que baja a la nave, donde hay 16 puestos: uno por comerciante, con toldo de su color, mostrador y cartel con su nombre. Ahí están los marcadores `shop:*`.
- **Templo del Alba.** Estilóbato, pórtico, bancos, altar con el sol, campanario con campana y aguja de cerezo. Techo de cobre a la intemperie con cresta de cobre fresco; las lancetas van del turquesa al rosa, al oro y al ámbar (el alba); el rosetón es un sol con un anillo rosa.
- **Taller de Terra.** Diente de sierra de cobre con luz norte y lucernarios, forja, bancos, yunques, entrepiso, chimeneas de cobre, el gran engranaje y un molino de Create en una chimenea.
- **Jardín Botánico.** Invernadero con estanque, canteros, bancos y flores de esporas.
- **Torre del Reloj.** Escalera interior, lancetas de sol, galería de rejilla de cobre bajo los cuatro relojes, campana y aguja de verdín.

## Solarpunk

Va encima de la paleta cálida (arenisca, calcita, terracota), así la ciudad sigue soleada y sin aire industrial oscuro.

- **Vidrio.**
  - Galerías con techo de vidrio a los dos lados del Eje, donde hay casas. El vidrio va sobre vigas de cobre a la altura de la banda del segundo piso, por encima de los toldos y de las copas de los cerezos (que se podan para que no lo atraviesen). Columnas de cuarzo con capitel de cobre en el pasto, entre los árboles, y bombillas en las vigas.
  - Una bóveda de vidrio cubre toda la Calle de los Oficios, con nervios de cobre y clave de oro.
  - Techos de vidrio escalonado sobre nervios de cobre en uno de cada cinco lotes, más o menos, con jardín de musgo, azaleas y helechos en el ático.
  - Invernaderos en azoteas planas, en el patio y en los jardincitos donde entran.
  - Doce pasarelas de cobre y vidrio entre casas que comparten piso a los dos lados de una calle.
  - Vidrio claro y, en algunos techos, celeste. Los paneles de vidrio llevan en la plantilla las uniones que el juego les daría.
- **Vitrales.** El sol en ámbar, oro y turquesa, en el palacio, el mercado, el templo, la torre del reloj y las cuatro posadas. Donde un techo a dos aguas da a una plaza, un hastial escalonado lleva un rosetón del sol: sol ámbar, rayos de oro y cielo turquesa en un marco de cobre cincelado.
- **Cobre.**
  - Cornisas de cobre en todos los edificios; techos, cúpulas y pretiles en las cuatro pátinas.
  - Balcones de trampilla y rejilla de cobre, con jardinera en todos, en dos de cada tres casas; en las altas, dos pisos de balcones.
  - Pretiles de rejilla en las azoteas y bombillas de cobre en los faroles.
  - Pararrayos de remate en cumbreras, hastiales, cúpulas y torres.
  - Bajantes de cobre por las medianeras de los talleres hasta barriles de lluvia. Son tubos de fluido de Create, unidos como los une Create.
  - Bordes de cobre en los canales y en las fuentes de los patios.
- **Sol y verde.**
  - Sensores de luz solar como paneles solares en las azoteas planas y en el palacio.
  - Tres molinos de Create estáticos: uno en el Taller de Terra y dos en torretas de talleres. El rodamiento no está armado.
  - Fachadas vivas de azalea, azalea florida y musgo entre las ventanas, enredaderas por las pilastras de los Jardines y bayas luminosas colgando del vidrio.
  - Jardines en azotea.
- **Seguridad.** Una bombilla cambia con un pulso de redstone y una puerta se abre. Por eso ninguna fuente de redstone (sensor de luz, pararrayos) toca una bombilla, puerta, trampilla o campana. El generador lo controla al final y da cero.

Bloques de mods nuevos: `create:fluid_pipe`, `create:sail_frame`, `create:white_sail`, `create:yellow_sail`, `create:orange_sail`, `create:windmill_bearing`. El resto del pase es vanilla.

## Cascada del Fin

Es agua real, en su estado estable:

- el canal del este termina en una pileta de desborde sobre el borde;
- un labio de tres bloques deja caer el agua 24 bloques por la ladera de la isla;
- el agua cae en una pileta de captación sobre una cornisa de roca, donde la columna se detiene.

La barrera de la costa rodea la caída en lugar de cortarla.

## Marcadores

Se conserva todo el contrato de `CityLayout`:

- `arrival` en la Plaza del Portal, mirando al Eje;
- `town_hall_portal` y `town_hall_waystone` bajo la cúpula;
- `trading_hall` en la nave;
- cuatro `player_plot` en cuadrados libres de 16×16 cerca de la Plaza de los Viajeros;
- `mayor` en el trono, `inventor` en el taller, `gardener` en el invernadero, `priest` en el altar;
- 16 `shop:*` en los puestos;
- 4 `sidequest:*_inn` en tabernas;
- los 3 `easter:*`: taberna secreta bajo la Plazoleta del Pan, jardín en el techo del palacio y reloj de sol en su plaza;
- `resident` en casas.

## Referencia (gancho de diseño)

Inspeccionadas en render el 26 de septiembre, desde `minecraft_1.21.1_client.jar` (`G:/Elias/Codex/Entrelumen-work/neoform-cache/artifacts`):

- `data/minecraft/structure/village/plains/houses/plains_big_house_1.nbt`, de 7×11×11. Marco de troncos en esquinas y pilares, relleno de otro material, techo a dos aguas de escalones con alero y vidrios en el relleno. De ahí salen las pilastras en medianeras y esquinas, las bandas por piso y los techos de escalones de un material por barrio.
- `data/minecraft/structure/village/desert/houses/desert_medium_house_2.nbt`, de 11×8×7. Techos planos con pretil y terraza. De ahí salen las azoteas con pretil y los jardines en azotea.

La textura de los bloques del pack sale de su propio arte; los renders con texturas de mods quedan locales (`E:/Elias/Codex/Entrelumen-ssd/solsticio8/`).
