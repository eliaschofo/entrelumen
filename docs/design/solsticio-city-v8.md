# Solsticio v8: el plano urbano en bloques (26 de septiembre de 2026)

La ciudadela en terrazas (v7) se reemplaza por la ciudad del plano urbano (`art/concepts/solsticio_plan.py`), que Elias aprobó: el Eje del Sol, dos bulevares en anillo, radiales, calles y pasajes; plazas en los nudos; el Parque del Mediodía con su lago; manzanas con fachada continua y patio; seis barrios. La fase 1 levantó el terreno, la red y la volumetría; la fase 2 la vistió.

- Generador: `art/solsticio/city8.py` (terreno, red, volumetría, marcadores, controles), `net8.py` (niveles), `landmarks8.py` (hitos y la Cascada del Fin), `dress8.py` (fachadas, interiores y vida de calle), `export8.py` (plantilla), `grid8.py` (grilla compacta), `render8.py` (renders locales).
- Plantilla: `companion/src/main/resources/data/entrelumen/structure/solsticio/city.nbt`, que se genera con `python art/solsticio/city8.py --export`.

## Relieve

La colina del plano (`height()`) cae un bloque cada tres. Nada queda sobre la pendiente cruda.

- **Calles con perfil diseñado.** Cada calle es plana a lo ancho, tiene descansos y tramos de escalones de uno en uno. Se resuelven por jerarquía: la de menor rango se engancha al nivel de la que cruza, y la mayor deja un descanso en cada cruce. Ningún par de celdas caminables vecinas queda a dos bloques o más.
- **El Eje.** Baja 48 bloques entre la Plaza Mayor y la del Portal en tramos de 5 a 6 escalones, con descansos de al menos 4. El canal de luz baja con él, bajo vidrio.
- **Plazas y lotes.** Las plazas son planas; donde una calle pasa más baja, el borde de la plaza se escalona. Cada lote toma el nivel más alto de la calle que tiene al frente, sobre un zócalo.
- **Parque y lago.** El parque desnivela un bloque como máximo. El lago es una sola lámina, con una calzada a nivel para el bulevar.
- **Suelo cerrado.** Queda hueco sólo donde cada cara está tapada. El control de huecos da cero, y la parte de abajo es un domo de roca.

## Barrios

El color va en techos y detalles, no en fachadas enteras. Cada barrio tiene 3 o 4 variantes de fachada, dos materiales de techo por calle y un ritmo de alturas. Las esquinas suben un piso y llevan un farol.

| Barrio | Muros | Techos |
|---|---|---|
| Mercado | arenisca lisa, tallada y ladrillo | terracota naranja (Macaw's Roofs), cobre |
| Posadas | arenisca amarilla con abeto | abeto y ladrillo |
| Jardines | calcita | cobre verde y jardines en azotea |
| Viajeros | calcita y diorita | cobre turquesa y prismarina |
| Templo | calcita | cerezo |
| Talleres | ladrillo, adobe y toba | pizarra oscura en diente de sierra con lucernarios, chimeneas con humo, forjas encendidas en planta baja |

## Calles

- **Pavimentos por jerarquía.** El Eje en cuarzo pulido con bandas de calcita e incrustaciones de oro; los bulevares en ladrillo de piedra y piedra lisa con cordón; las calles en andesita con cordón de toba; los pasajes en adobe, ladrillo de barro y adoquín. No hay bloques que caigan.
- **Faroles.** Cada nueve bloques; en los pasajes, farol de pared.
- **Mobiliario.** Bancos, jardineras, fuentes o pozos en los patios.
- **Señales.** Postes en los cruces que nombran la plaza que hay adelante. Son traducibles (`entrelumen.solsticio.place.*` en `en_us` y `es_es`).

## Fachadas e interiores

- **Fachadas.** Cada edificio tiene puerta a su calle, con escalón si el zócalo sube y farol encima. Las ventanas llevan alféizar, postigos o jardineras. Hay balcones en las calles anchas y buhardillas en los faldones que dan a la calle.
- **Vidrieras.** En el Eje, el Mercado y las Posadas, las plantas bajas son vidrieras de tienda con toldo, estandarte, cajones y barriles.
- **Interiores.** Detrás de cada puerta van las habitaciones de `houses.py` (tienda, taberna, cocina, sala, dormitorio, estudio), encajadas en el rectángulo más grande que el lote tiene detrás de la puerta. Hay una escalera de mano entre pisos y faroles bajo cada techo.

## Hitos

- **Palacio del Solsticio.** Cúpula dorada y torre del sol. Bajo el óculo, el Salón del Solsticio: el portal, la piedra de viaje y el trono de Aurelia sobre una tarima. En las alas, la sala del consejo y el archivo.
- **Gran Mercado de la Luz.** Un rellano al nivel de la plaza y una gran escalera que baja a la nave, donde hay 16 puestos: uno por comerciante, con toldo de su color, mostrador y cartel con su nombre. Ahí están los marcadores `shop:*`.
- **Templo del Alba.** Estilóbato, pórtico, rosetón, bancos, altar con el sol, campanario con campana y aguja de cerezo.
- **Taller de Terra.** Forja, bancos, yunques, entrepiso, chimeneas de cobre y el gran engranaje.
- **Jardín Botánico.** Invernadero con estanque, canteros, bancos y flores de esporas.
- **Torre del Reloj.** Escalera interior, galería bajo los cuatro relojes y campana.

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
