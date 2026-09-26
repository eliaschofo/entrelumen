# El Arca como astrolabio (multibloque, 25 de septiembre de 2026)

Elias eligió el astrolabio en lugar del barco. Lo genera `art/structures/ark_multiblock.py`, que escribe la definición en `art/structures/out/ark_multiblock.json`; el companion la carga desde `data/entrelumen/ark_multiblock.json`.

**Forma (revisión final del 25/9, Elias: «todo en la tierra, con el arco pero sin adornos ni bloques raros; sólo bloques cúbicos, y los módulos y el controlador apoyados en el piso»).**
- 91 bloques, todos obligatorios: 53 ladrillos de piedra, 19 de cobre cortado, 8 de cobre cincelado, 4 bloques de cobre, los 6 módulos y el controlador. Es poco cobre, unos 280 lingotes. (Elias: «estaba bien el cobre; que no sea monótono ni feo»).
- El controlador va en el centro, apoyado en el piso.
- Los 6 módulos, también apoyados en el piso, van en un círculo de radio 5:
  - Habitabilidad a 90°, al sur;
  - Exploración a 30°;
  - Naturaleza a 330°;
  - Arcano a 270°, al norte;
  - Logística a 210°;
  - Ingeniería a 150°.
- La órbita es un anillo de ladrillos de piedra puesto en el suelo, a la altura del piso.
- Bajo cada módulo y bajo el controlador va un zócalo de cobre cincelado, a nivel del piso.
- Dos arcos de radio 7 se cruzan sobre el controlador. Tienen franjas de cobre cortado y ladrillo, como las dovelas de un arco clásico, pies de bloque de cobre y la clave de cobre cincelado.

**Reglas de coincidencia para el código.**
- El cobre coincide con cualquier oxidación y con su versión encerada.
- Los bloques marcados `required: false` son decoración y no bloquean el Arca.
- `slot` marca dónde va cada módulo y el controlador.
- Las posiciones son relativas al controlador.

**Guía (Elias).** El Atlas tiene un botón que proyecta el Arca como fantasma 3D en el mundo, anclado donde se va a construir. Usa la visualización de multibloques de Patchouli, que viene en el pack; si faltara, se usan partículas propias. 
