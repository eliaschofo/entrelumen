# Solsticio, segunda ciudad: ciudadela en terrazas (25 de septiembre de 2026)

Elias recorrió la primera ciudad (`art/solsticio/city6.py`) en el juego. Lo que vio:
- el relieve roto;
- casas de aldea común, vacías;
- un Ayuntamiento poco imponente;
- una forma rara.

El terreno ya está arreglado en `city5.py`: pads fijos, pendiente de un bloque, caras cerradas, alcantarillas y barrera. La ciudad se rehace con esta dirección, elegida por él.

## Forma

- **Ciudadela en terrazas.** La isla sube en 4 o 5 terrazas orgánicas, que siguen curvas de nivel y no círculos, hasta la cima, donde está el Ayuntamiento. Desde afuera se lee una silueta escalonada que culmina en la cúpula y la torre del sol.
- Cada terraza tiene un **paseo** con baranda hacia afuera (balaustrada, faroles, bancos, macetas) y un **anillo continuo de edificios** hacia adentro, pegados, de 3 a 5 pisos, con fachadas distintas.
- Entre terrazas hay **muros de contención** con vitrales, fuentes que caen de terraza en terraza, **escalinatas monumentales** en los ejes y rampas o puentes en los quiebres.
- El río nace cerca de la cima, baja en cascadas por las terrazas y termina en la cascada del borde.
- Por encima de todo sigue la barrera de luz alrededor de la costa.

## Ayuntamiento

- **Palacio con cúpula y torre del sol.** Escalinata de todo el frente, columnata, gran cúpula dorada con óculo de vitral en forma de sol y una torre del sol que se ve desde toda la isla.
- Adentro, la sala del portal bajo la cúpula, con la luz del óculo cayendo sobre el portal. Alrededor, los salones de los cuatro personajes y los lotes de los jugadores en la terraza más alta.
- Escala de referencia: 50 a 60 bloques de frente y 60 o más de alto hasta el sol.

## Densidad y vida

- Sin baldíos. Cada edificio tiene **interior amueblado** según su función:
  - casas: cocina, comedor, dormitorios y biblioteca;
  - tiendas: mostrador, vitrinas y depósito;
  - posadas: salón y cuartos.
- Se usan los mods de decoración del pack: Handcrafted, Macaw's, Supplementaries, Chipped y Another Furniture.
- Calle viva: puestos de mercado, toldos, carros, banderines entre edificios, arcos de flores, bancos, estatuas y relojes de sol.
- Vitrales, vidrieras y soles en todas partes. Tiene que verse hermosa con shaders.

## Técnica

- Mismo contrato de marcadores de `CityLayout` y las mismas reglas de terreno de `city5.py` (nada hueco a la vista).
- Se valida con `city_leaks` (cero huecos al aire), con renders por terraza y, por último, recorriéndola en el cliente con capturas.
