# El Arca como astrolabio (multibloque, 25 de septiembre de 2026)

Elias eligió el astrolabio en lugar del barco. Lo genera `art/structures/ark_multiblock.py`, que escribe la definición en `art/structures/out/ark_multiblock.json`; el companion la carga desde `data/entrelumen/ark_multiblock.json`.

**Forma (revisión del 25/9: «que no sea jodido de construir ni caro»).** El Arca tiene dos capas.
- **Núcleo obligatorio:** 52 piezas baratas, que se construyen en minutos.
  - El controlador sobre una columna de 3 ladrillos de piedra: es el sol.
  - Los 6 módulos como planetas, cada uno sobre 2 ladrillos de piedra, en un círculo de radio 5:
    - Habitabilidad a 90°, en el eje sur;
    - Exploración a 30°;
    - Naturaleza a 330°;
    - Arcano a 270°, en el eje norte;
    - Logística a 210°;
    - Ingeniería a 150°.
  - El anillo del ecuador, de losas de piedra lisa, a la altura de los módulos.
- **Decoración sugerida:** la guía la muestra, pero el Arca nunca la exige.
  - Un disco de calcita con los rayos del sol en cobre.
  - Faroles sobre los planetas y rayos de pararrayos en el sol.
  - Dos arcos meridianos de cobre que se cruzan arriba, con el gnomon y un farol.

**Reglas de coincidencia para el código.**
- El cobre coincide con cualquier oxidación y con su versión encerada.
- Los bloques marcados `required: false` son decoración y no bloquean el Arca.
- `slot` marca dónde va cada módulo y el controlador.
- Las posiciones son relativas al controlador.

**Guía (Elias).** El Atlas tiene un botón que proyecta el Arca como fantasma 3D en el mundo, anclado donde se va a construir. Usa la visualización de multibloques de Patchouli, que viene en el pack; si faltara, se usan partículas propias. El núcleo se ve en un color y la decoración en otro, más tenue.
