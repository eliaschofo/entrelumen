# El Arca como astrolabio (multibloque, 25 de septiembre de 2026)

Elias eligió el astrolabio en lugar del barco. Lo genera `art/structures/ark_multiblock.py`, que escribe la definición en `art/structures/out/ark_multiblock.json`; el companion la carga desde `data/entrelumen/ark_multiblock.json`.

**Forma (final, 25/9, en varias rondas con Elias):** todo apoyado en la tierra, sólo bloques cúbicos más un borde de escaleras, sin adornos, con un poco de cobre, amatista y piedra.
- **Plataforma:** un piso redondo de radio 8, elevado un nivel sobre el suelo, con un borde de escaleras de ladrillo de piedra alrededor. El piso es de ladrillo de piedra con un borde de **amatista pulida** (`rechiseled:amethyst_block_polished`, que se hace con el cincel de Rechiseled desde bloques de amatista; Elias) y tiene:
  - los 8 rayos del sol en toba pulida;
  - la órbita en calcita, de radio 5;
  - 4 bloques de amatista en las diagonales.
- **Controlador:** en el centro, apoyado en el piso, sobre un zócalo de cobre cincelado.
- **Módulos:** apoyados en el piso sobre la órbita, cada uno con su zócalo de cobre cincelado:
  - Habitabilidad a 90°, al sur;
  - Exploración a 30°;
  - Naturaleza a 330°;
  - Arcano a 270°, al norte;
  - Logística a 210°;
  - Ingeniería a 150°.
- **Arcos:** dos, de radio 7, que se cruzan sobre el controlador, con franjas de cobre cortado y ladrillo de toba, pies de calcita y clave de amatista.
- **Columnas:** cuatro en las diagonales, en los cuadrantes que no tienen apoyo de arco (Elias):
  - base chica acampanada: ocho escaleras de toba pulida alrededor del pie, con sus esquinas;
  - fuste de un solo material, calcita;
  - capitel acampanado de cuatro escaleras invertidas de toba pulida, sin esquinas; el beacon va apoyado directo sobre el capitel;
  - en la punta, un **lugar para un beacon**, opcional (ver `ark-modules-v2.md`).
- **Cuenta:** 427 bloques obligatorios (116 stone_bricks, 72 stone_brick_stairs, 62 calcite, 48 amethyst_block_polished, 48 polished_tuff_stairs, 23 tuff_bricks, 20 polished_tuff, 19 cut_copper, 7 chiseled_copper, 5 amethyst_block, 1 arcane_module, 1 logistics_module, 1 nature_module, 1 ark_controller, 1 engineering_module, 1 exploration_module, 1 habitation_module), más 4 lugares opcionales para beacons.

**Reglas de coincidencia para el código.**
- **Escaleras (para que sea fácil de construir):** en cada posición de escalera vale cualquier escalera del material pedido con el `half` correcto, sin importar `facing` ni `shape`. El juego arma solo las esquinas según las vecinas. El borde de la plataforma es un anillo continuo (8-conectado). Cada escalera lleva la forma que le corresponde por geometría: recta donde el piso está de un lado, esquina interior en las muescas y esquina exterior en las puntas. Así queda en el fantasma. En las escaleras de Rechiseled vale también la variante `_connecting`.
- El cobre coincide con cualquier oxidación y con su versión encerada.
- Los bloques marcados `required: false` son decoración y no bloquean el Arca.
- `slot` marca dónde va cada módulo y el controlador.
- Las posiciones son relativas al controlador.

**Guía (Elias).** El Atlas tiene un botón que proyecta el Arca como fantasma 3D en el mundo, anclado donde se va a construir. Usa la visualización de multibloques de Patchouli, que viene en el pack; si faltara, se usan partículas propias. 
