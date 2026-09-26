# El Envés: el descenso generativo

Plano del controlador, 26 de septiembre de 2026. Pedido de Elias:

- una dungeon generativa como las de Diablo 2/3 o la de *The Other* en ATM10;
- pisos indestructibles y escaleras que hay que encontrar;
- un mapa que se va revelando;
- 4 o 5 niveles con un jefe al final;
- loot que escala con la dificultad y que obliga a equiparse: nada de «refined obsidian y listo»;
- pocos enemigos fuertes y divertidos, nada de tormentas de entidades ni spawners, y nada de basura en el inventario.

Nada de esto está implementado. El prototipo del generador y las vistas de revisión existen:

- `art/dungeon/drlg.py` genera los pisos y dibuja el mapa con niebla;
- `art/dungeon/tiles.py` hace las salas del tileset Osarios y arma un piso entero.

## La decisión: layout generativo sobre salas de autor

Es el método de Diablo II. Hay dos niveles y cada uno hace lo que mejor le sale:

| Qué | Cómo | Costo |
|---|---|---|
| Forma del piso | Un generador de grilla (11×11 celdas) crece como un caminante con memoria: sigue de largo casi siempre, a veces abre una rama y al final cierra algunos bucles. Después elige la salida lejos, en un callejón, y reparte los roles. | Un algoritmo chico, sin arte |
| Aspecto de cada celda | Una plantilla de 19×19×12 por tileset × rol × máscara de puertas × variante, hecha por código: forma de la sala (cuadrada con pilares, octógono, cruz), piso, zócalos, nichos, luz. Se generan las 15 máscaras, así que el juego nunca rota nada. | Un script por tileset |

Sale variado, porque cada descenso es otro, y bonito, porque cada sala está dirigida. Es barato de hacer: no hay un motor de tallado de voxels en tiempo de ejecución, sólo pegar plantillas. Un motor 100% procedural daría menos control del arte por más trabajo.

Invariantes que el port a Java hereda del prototipo, probados con 200 semillas:
- todo el piso es alcanzable;
- la salida es un callejón lejano (≥ 80% de la distancia máxima);
- la bóveda y el santuario quedan fuera del camino principal;
- el piso k+1 empieza justo debajo de la escalera del piso k, que baja de verdad en espiral y atraviesa la losa.

## Estructura del descenso

| Piso | Tileset | Salas | Carácter |
|---|---|---|---|
| I | Osarios | ~26 | Toba y calcita, nichos con huesos y velas, criptas de pilares |
| II | Cisternas | ~31 | Canales de agua, caños de cobre, pasarelas |
| III | Fundición | ~36 | Piedra negra, basalto, canales de lava, cadenas |
| IV | Geodas | ~41 | Amatista, calcita, basalto liso, cristales que brillan |
| V | El Eclipse | fijo | Acceso corto, antesala del campeón y arena de 3×3 celdas; al fondo, la salida |

Roles por piso:
- **inicio:** la bajada por donde llegaste;
- **escalera:** oculta en un callejón lejano; sellada hasta prender los sellos;
- **sellos (Elias, 26/9: «más salas, más laberíntico, que tengas que sí o sí explorar»):** 2 en los pisos I–II y 3 en los III–IV, en callejones fuera del camino principal y lo más lejos posible entre sí. Si el piso no deja escondites, el generador brota una rama ciega nueva. Los sellos se ven en el mapa recién cuando los encontrás;
- **guardia:** la sala antes de la escalera, con un campeón;
- **encuentros:** alrededor del 40% de las salas;
- **santuario:** en el 70% de los pisos, una bendición temporal;
- **bóveda:** un callejón lateral cerrado por un acertijo;
- **salas quietas:** lore, ambiente y alguna trampa leve.

**Dimensión propia:** `entrelumen:enves`. Es vacía, sin cielo, sin clima y sin spawn natural. Cada descenso ocupa una parcela a 2048 bloques de las otras, con sus cinco pisos apilados. La parcela se libera diez minutos después de que sale el último del grupo. Un piso se genera recién cuando alguien llega a la guardia del anterior, repartido entre ticks (unos 75 mil bloques por piso).

## Mapa con niebla

- JourneyMap y el minimapa de FTB Chunks quedan apagados dentro del Envés. Si no, revelan el piso entero apenas cargan los chunks.
- En su lugar, un minimapa propio con estética de Atlas, en pergamino:
  - lo pisado se ve nítido;
  - lo que se asoma por una puerta, borroso;
  - el resto es niebla de tinta.
- La escalera sólo aparece cuando la encontrás.
- El servidor manda al grupo la grilla y lo explorado, apenas unos bytes.
- El mismo mapa, grande, se abre en la pantalla del Atlas.

## Encuentros

- **Sin spawners.** Cada sala de encuentro tiene puntos marcados y el grupo se arma la primera vez que alguien del equipo entra: 1 élite más 1–2 escoltas, o 2 élites. Una sala limpia queda limpia.
- **Pool de enemigos por tileset:** vanilla con equipo, más los enemigos medianos de L_Ender's Cataclysm y Mowzie's Mobs que ya están en el pack. Por ejemplo, en la Fundición un Ignited Revenant con dos escoltas.
- **Afijos de élite, al estilo de Diablo:** Veloz, Blindado, Vampírico, Ardiente, Perforante y Espectral. Hay uno en el piso I y hasta tres en el piso IV. El campeón de la guardia lleva tres, más escoltas.
- **Sin basura:** los enemigos del Envés sólo sueltan experiencia. Un élite tiene una chance chica de soltar una gema o material de rareza; un campeón suelta una pieza con afijos.

## Dificultad y loot

- **La dificultad es el World Tier de Apotheosis que ya fija la historia** (Frontier en el acto III, hasta Pinnacle en el VI). En la entrada se puede elegir un tier menor para farmear.
- **Vida y daño enemigos** escalan por tier y por piso.
- **Contra el tanque puro:** parte del daño atraviesa la armadura (`armor_pierce` y `armor_shred` de Apothic Attributes). Con armadura de obsidiana refinada sola no alcanza: hacen falta afijos, protección, sustain, daño o magia.
- **Loot:**
  - cofres de Lootr, por jugador;
  - piezas con afijos de Apotheosis cuya rareza sube con el tier y el piso;
  - gemas;
  - la bóveda paga más;
  - el cofre del jefe da tres piezas de la rareza alta del tier.
- **Propuesta:** un curio único por jefe, como los objetos que se persiguen en Diablo.

## Reglas del Envés

- **Todo indestructible.** No se rompe ni se pone nada. Las explosiones no rompen bloques.
- **Nada que saltee el laberinto:**
  - sin vuelo (jetpacks, vuelo de mods);
  - sin perlas ni chorus;
  - sin waystones, `/home` ni `/rtp`.
  Los techos de 9 bloques tampoco dejan volar.
- **Intento, caídas y muerte (Elias, 26/9):**
  - la puerta se abre con una ofrenda de **1 bloque de netherita**, y cada ofrenda es un intento;
  - adentro se conserva el inventario y se reaparece al inicio del piso;
  - el grupo comparte una bolsa de caídas de 3 por integrante (dos jugadores, seis caídas), sin importar quién las gaste;
  - cuando la bolsa se vacía, todos vuelven afuera, la puerta se cierra y pide otra ofrenda.

## Jefe (Elias, 26/9)

Uno propio. Para la v1.0, y probablemente más allá, va un reemplazo provisorio: un **Wither blanco, luminoso**. El modelo propio queda para el futuro y no frena el lanzamiento.

- **Movimiento:** casi no vuela. Levita unos bloques sobre el piso y se desliza.
- **Ataques:**
  - calaveras;
  - una **embestida muy telegrafiada**: carga con aviso claro, marca en el piso el recorrido, embiste en línea recta y queda expuesto unos segundos.
- **Sin grifeo.**
- **Escalado:** vida y daño según el World Tier.
- **Loot:** el cofre del Envés con la rareza alta del tier.
- **Textura:** original, pintada sobre el UV del Wither.

## Acertijos y bóvedas

Reusan los mecanismos de las ruinas: braseros en orden, espejos, palancas, ofrendas y el orden de piedras. Regla de diseño de Elias (26/9): divertidos y no obvios; ni aburridos, ni cliché, ni excesivamente difíciles. La pista siempre está en la sala o en la de al lado, nunca en una wiki.

## Entrada (Elias, 26/9)

**La Escalera Sellada**, en la ruina inicial. Está desde el minuto uno y el Atlas no la sabe leer.
- Se abre con el World Tier Frontier (acto III).
- La puerta pide la ofrenda del intento.
- Se puede elegir cualquier tier hasta el actual.

## Plan de implementación

1. Dimensión, parcelas, ciclo de vida y persistencia; port del generador con los invariantes como tests.
2. Plantillas:
   - yo hago el arte de Cisternas, Fundición, Geodas, El Eclipse y las salas especiales;
   - un exportador escribe los NBT con marcadores (puntos de encuentro, cofres, santuario, mecanismo de bóveda, espiral).
3. Colocación por celdas repartida entre ticks; protección y reglas del Envés.
4. Encuentros con tablas por datapack, afijos y escalado; loot tables con Apotheosis y Lootr.
5. Minimapa con niebla, más la vista grande en el Atlas; apagar JourneyMap y FTB Chunks en la dimensión.
6. Piso del jefe, pool, salida, recompensas y logro.
7. Entrada y selección de dificultad, regla de muerte, GameTests y QA de pack completo.

Los puntos 1–3 y 5 son un worker; el 4 y el 6, otro, en paralelo, sobre la misma interfaz de marcadores.

## Para decidir

- El nombre del descenso y el del jefe.
- Si hay curios únicos del jefe.
