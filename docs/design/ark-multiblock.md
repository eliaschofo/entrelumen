# El Arca como astrolabio (multibloque, 25 de septiembre de 2026)

Elias eligió el astrolabio en lugar del barco. Lo genera `art/structures/ark_multiblock.py`, que escribe la definición en `art/structures/out/ark_multiblock.json`; el companion la carga desde `data/entrelumen/ark_multiblock.json`.

**Forma (final, 25/9, en varias rondas con Elias):** todo apoyado en la tierra, sólo bloques cúbicos más un borde de escaleras, sin adornos, con un poco de cobre, amatista y piedra.
- **Plataforma:** un piso redondo de radio 8, elevado un nivel sobre el suelo, con un borde de escaleras de ladrillo de piedra alrededor. El piso es de ladrillo de piedra con un borde de **amatista pulida** (`rechiseled:amethyst_block_polished`, que se hace con el cincel de Rechiseled desde bloques de amatista; Elias) y tiene:
  - los 8 rayos del sol en toba pulida;
  - la órbita en calcita, de radio 5;
  - 4 bloques de amatista en las diagonales.
- **Controlador:** en el centro, apoyado en el piso, sobre un zócalo de cobre cincelado.
- **Módulos:** apoyados en el piso, cerca del controlador: cuatro en las diagonales y dos en el eje norte-sur (Elias: «cuatro y dos, pero más cerquita del controlador»). Cada uno tiene su zócalo de cobre cincelado:
  - Habitabilidad en (0, 3), al sur;
  - Arcano en (0, −3), al norte;
  - Exploración en (2, 2);
  - Naturaleza en (2, −2);
  - Logística en (−2, 2);
  - Ingeniería en (−2, −2).
