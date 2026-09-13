# Botones adicionales del menú principal

Los botones **W** y **Menu List** pertenecen a **Cumulus Menus 2.0.7**, dependencia incluida por Aether 1.5.10, no a la barra de edición de FancyMenu. Son utilidades de selección de menú y vista del último mundo; no indicadores de depuración. Se pueden ocultar individualmente sin desactivar Aether ni la API del menú.

## Parche mínimo propuesto

Integrar estas dos claves en `pack/config/cumulus_menus-client.toml`, conservando las demás preferencias:

```toml
[Menu]
"Enables menu selection button" = false

["World Preview"]
"Enables toggle world button" = false
```

El archivo generado por nuestro cliente contiene ambas en `true`. También tiene `"Enable Menu API" = true`, `"Active Menu" = "cumulus_menus:minecraft"`, `"Enables world preview" = false` y `"Enables quick load button" = true`. No hace falta cambiar esas cuatro preferencias para ocultar los dos botones observados. Quick Load sólo aparece con vista de mundo habilitada; no se modifica en este parche acotado.

## Evidencia exacta

- JAR instalado: `aether-1.21.1-1.5.10-neoforge.jar` → `META-INF/jarjar/cumulus_menus-1.21.1-2.0.7-neoforge.jar`.
- `assets/cumulus_menus/lang/en_us.json`: `gui.cumulus_menus.button.menu_list` = `Menu List`; `gui.cumulus_menus.menu.button.world_preview` = `W`.
- `CumulusConfig$Client`: define los grupos `Menu` / `World Preview` y las claves TOML exactas mostradas, ambas booleanas con valor predeterminado true.
- `com.aetherteam.cumulus.client.event.hooks.MenuHooks`: comprueba `enable_menu_list_button` antes de añadir el selector; pasa `enable_world_preview_button` a `DynamicMenuButton.setDisplayConfigs` para controlar la visibilidad del botón de vista del mundo.
- Fuente de valores actuales: `G:/curseforge/Instances/ENTRELUMEN/config/cumulus_menus-client.toml`, sólo leída. No se consultaron configs ATM.

## Verificación

TOML generado y fragmento propuesto parseados correctamente con `tomllib`; ambos valores actuales y destinos contrastados. Bytecode inspeccionado con `javap` del JAR real, incluida su dependencia anidada.

Pendiente del integrador: aplicar el fragmento en fuente, instalarlo con el cliente cerrado y comprobar al abrir el menú que desaparecieron ambos botones, que permanecen los botones principales y que FancyMenu conserva su presentación. No se realizó automatización UI ni se escribió en el perfil activo.
