# El Arca como astrolabio (multibloque, 25 de septiembre de 2026)

Elias eligió el astrolabio en lugar del barco. Lo genera `art/structures/ark_multiblock.py`, que escribe la definición en `art/structures/out/ark_multiblock.json`; el companion la carga desde `data/entrelumen/ark_multiblock.json`.

**Forma (final, 25/9, en varias rondas con Elias):** todo apoyado en la tierra, sólo bloques cúbicos más un borde de escaleras, sin adornos, con un poco de cobre, amatista y piedra.
- **Plataforma:** un piso redondo de radio 8, elevado un nivel sobre el suelo, con un borde de escaleras de ladrillo de piedra alrededor. El piso es de ladrillo de piedra con borde de ladrillo de toba y tiene:
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
  - base acampanada de toba pulida (3×3) con un escalón en cruz de calcita;
  - fuste de un solo material, calcita;
  - capitel de cobre cincelado;
  - en la punta, un **lugar para un beacon**, opcional (ver `ark-modules-v2.md`).
- **Cuenta:** 411 bloques obligatorios (116 stone_bricks, 74 calcite, 71 tuff_bricks, 56 polished_tuff, 52 stone_brick_stairs, 19 cut_copper, 11 chiseled_copper, 5 amethyst_block, 1 arcane_module, 1 logistics_module, 1 nature_module, 1 ark_controller, 1 engineering_module, 1 exploration_module, 1 habitation_module), más 4 lugares opcionales para beacons.

**Reglas de coincidencia para el código.**
- El cobre coincide con cualquier oxidación y con su versión encerada.
- Los bloques marcados `required: false` son decoración y no bloquean el Arca.
- `slot` marca dónde va cada módulo y el controlador.
- Las posiciones son relativas al controlador.

**Guía (Elias).** El Atlas tiene un botón que proyecta el Arca como fantasma 3D en el mundo, anclado donde se va a construir. Usa la visualización de multibloques de Patchouli, que viene en el pack; si faltara, se usan partículas propias. 
