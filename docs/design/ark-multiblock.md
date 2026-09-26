# El Arca como astrolabio (multibloque, 25 de septiembre de 2026)

Elias eligió el astrolabio en lugar del barco. Lo genera `art/structures/ark_multiblock.py`, que escribe la definición en `art/structures/out/ark_multiblock.json`; el companion la carga desde `data/entrelumen/ark_multiblock.json`.

**Forma.** Mide unos 21×17×21 y tiene 783 posiciones, de las que 342 son obligatorias. Todo se consigue en el acto I: cobre, calcita, ladrillos de piedra y faroles.
- Una plataforma redonda de calcita, de radio 9, con los rayos del sol en cobre cortado, borde de ladrillos de piedra y cuatro escalones. La calcita es obligatoria; los rayos y el borde son decoración.
- En el centro, el controlador sobre una columna de calcita: es el sol, con rayos de pararrayos y una corona de rejillas de cobre.
- El anillo del ecuador, de cobre cortado y radio 7, a la altura de los módulos.
- Los seis módulos, como planetas sobre el ecuador, cada uno en su pilar de calcita con un farol arriba:
  - Habitabilidad a 90°, en el eje sur;
  - Exploración a 30°;
  - Naturaleza a 330°;
  - Arcano a 270°, en el eje norte;
  - Logística a 210°;
  - Ingeniería a 150°.
- Dos arcos meridianos de cobre cortado que se cruzan sobre el sol, como una cúpula, con el gnomon y un farol arriba.

**Reglas de coincidencia para el código.**
- El cobre coincide con cualquier oxidación y con su versión encerada.
- Los bloques marcados `required: false` son decoración y no bloquean el Arca.
- `slot` marca dónde va cada módulo y el controlador.
- Las posiciones son relativas al controlador.

**Guía.** Se muestra un fantasma de la estructura: con la API de Patchouli si está en el pack, si no con partículas propias.
