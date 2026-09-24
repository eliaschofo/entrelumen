"""Curated decorative mod blocks for Solsticio: a solarpunk city of calcite, copper, glass and gardens.

Each entry: (mod, block id, category, short Spanish label, options). Categories: path, bridge, window,
lamp, roof, planter, furniture, decor. Options:
    preview  property overrides used only to pick the model for textures and the sample sheet
             (the recorded default state stays the game's own default)
    render   force a preview shape: cube, slab, thin, pane, cross
    sprite   'icon' to draw the cross sprite with the item icon (blocks drawn by a block entity)
    note     free text copied into the palette (placement caveats)
"""

S = []


def add(mod, cat, *entries):
    for e in entries:
        bid, label = e[0], e[1]
        opts = e[2] if len(e) > 2 else {}
        S.append((mod, bid, cat, label, opts))


# ---------------- Macaw's Paths and Pavings ----------------
add('mcwpaths', 'path',
    ('diorite_clover_paving', 'trébol diorita'),
    ('diorite_basket_weave_paving', 'cestería diorita'),
    ('diorite_honeycomb_paving', 'panal diorita'),
    ('diorite_diamond_paving', 'rombos diorita'),
    ('diorite_flagstone', 'laja diorita'),
    ('diorite_running_bond_path', 'senda soga diorita'),
    ('diorite_flagstone_stairs', 'escalón laja diorita'),
    ('sandstone_honeycomb_paving', 'panal arenisca'),
    ('mossy_stone_running_bond_path', 'senda musgosa'),
    ('stone_strewn_rocky_path', 'senda de piedras'),
    ('gravel_path_block', 'senda de grava'),
    ('birch_planks_path', 'senda tablas abedul'))

# ---------------- Macaw's Bridges ----------------
add('mcwbridges', 'bridge',
    ('balustrade_diorite_bridge', 'puente balaustrada'),
    ('diorite_bridge', 'puente diorita'),
    ('diorite_bridge_pier', 'pilar de puente'),
    ('diorite_bridge_stair', 'rampa de puente'),
    ('glass_bridge', 'puente de vidrio'),
    ('rope_birch_bridge', 'puente de cuerda'))
add('mcwbridges', 'lamp',
    ('bridge_lantern', 'farol de puente'))

# ---------------- Macaw's Stairs and Balconies ----------------
add('mcwstairs', 'bridge',
    ('quartz_balcony', 'balcón cuarzo', {'preview': {'south': 'true', 'east': 'true'}, 'note': 'Railing only on the sides set to true; all false shows nothing.'}),
    ('quartz_railing', 'baranda cuarzo'),
    ('quartz_platform', 'plataforma cuarzo'),
    ('quartz_terrace_stairs', 'escalera terraza'),
    ('birch_balcony', 'balcón abedul', {'preview': {'south': 'true', 'east': 'true'}, 'note': 'Railing only on the sides set to true; all false shows nothing.'}))

# ---------------- Macaw's Fences and Walls ----------------
add('mcwfences', 'bridge',
    ('railing_quartz_wall', 'muro baranda cuarzo'),
    ('ornate_metal_fence', 'reja ornamental'),
    ('gothic_metal_fence', 'reja gótica'),
    ('majestic_metal_fence_gate', 'portón majestuoso'),
    ('birch_picket_fence', 'cerca de estacas'))
add('mcwfences', 'decor',
    ('quartz_pillar_wall', 'muro pilar cuarzo'))
add('mcwfences', 'planter',
    ('quartz_grass_topped_wall', 'muro con césped'),
    ('flowering_azalea_hedge', 'seto azalea en flor'))

# ---------------- Macaw's Windows ----------------
add('mcwwindows', 'window',
    ('quartz_window', 'ventana cuarzo', {'note': 'part=base is a single window; the other parts tile bigger windows.'}),
    ('quartz_four_window', 'ventana parteluz'),
    ('quartz_pane_window', 'ventana paño cuarzo'),
    ('metal_window', 'ventana metal', {'note': 'part=base is a single window; the other parts tile bigger windows.'}),
    ('stone_brick_gothic', 'ventana gótica'),
    ('prismarine_brick_gothic', 'gótica prismarina'),
    ('white_mosaic_glass', 'mosaico blanco'),
    ('light_blue_mosaic_glass_pane', 'mosaico celeste'),
    ('cyan_mosaic_glass_pane', 'mosaico cian'),
    ('yellow_mosaic_glass_pane', 'mosaico amarillo'),
    ('birch_shutter', 'postigo abedul'),
    ('birch_louvered_shutter', 'persiana abedul'))

# ---------------- Macaw's Doors and Trapdoors ----------------
add('mcwdoors', 'decor',
    ('birch_glass_door', 'puerta vidrio abedul'),
    ('birch_cottage_door', 'puerta cottage'))
add('mcwtrpdoors', 'decor',
    ('birch_blossom_trapdoor', 'trampilla floral'),
    ('birch_glass_trapdoor', 'trampilla vidrio'),
    ('birch_barred_trapdoor', 'ventila de barrotes'))

# ---------------- Macaw's Lights and Lamps ----------------
add('mcwlights', 'lamp',
    ('classic_street_lamp', 'farol clásico', {'preview': {'part': 'base'}, 'note': 'part=base is the one-block lamp; bottom, middle and top stack a tall post.'}),
    ('double_street_lamp', 'farol doble', {'preview': {'part': 'base'}, 'note': 'part=base is the one-block lamp; bottom, middle and top stack a tall post.'}),
    ('garden_light', 'luz de jardín'),
    ('round_garden_light', 'luz jardín redonda'),
    ('tower_garden_light', 'torre de luz'),
    ('covered_lantern', 'farol cubierto'),
    ('bell_lantern', 'farol campana'),
    ('wall_lantern', 'aplique farol'),
    ('covered_wall_lantern', 'aplique cubierto'),
    ('copper_chandelier', 'araña de cobre'),
    ('copper_wall_candle_holder', 'candelero cobre'),
    ('copper_triple_candle_holder', 'candelabro cobre'),
    ('copper_chain', 'cadena de cobre'),
    ('white_paper_lamp', 'farol de papel'))

# ---------------- Macaw's Roofs ----------------
add('mcwroofs', 'roof',
    ('prismarine_brick_roof', 'techo verdín'),
    ('prismarine_brick_steep_roof', 'techo verdín empinado'),
    ('prismarine_brick_top_roof', 'cumbrera verdín'),
    ('prismarine_brick_attic_roof', 'buhardilla verdín'),
    ('prismarine_brick_lower_roof', 'alero verdín'),
    ('orange_terracotta_roof', 'techo cobrizo'),
    ('orange_terracotta_steep_roof', 'techo cobrizo empin.'),
    ('white_roof', 'teja blanca'),
    ('gutter_base_white', 'canaleta blanca'),
    ('gutter_middle_white', 'canaleta media'),
    ('cyan_striped_awning', 'toldo rayado cian'),
    ('yellow_striped_awning', 'toldo rayado amarillo'))

# ---------------- Supplementaries ----------------
add('supplementaries', 'planter',
    ('flower_box', 'jardinera'),
    ('planter', 'macetero'))
add('supplementaries', 'decor',
    ('globe', 'globo terráqueo', {'sprite': 'icon'}),
    ('pedestal', 'pedestal'),
    ('wind_vane', 'veleta', {'sprite': 'icon', 'render': 'cross'}),
    ('clock_block', 'reloj'),
    ('hourglass', 'reloj de arena'),
    ('urn', 'urna'),
    ('jar', 'frasco'),
    ('item_shelf', 'repisa'),
    ('notice_board', 'tablón de avisos'),
    ('flag_light_blue', 'bandera celeste'),
    ('wicker_fence', 'cerca de mimbre'))
add('supplementaries', 'roof',
    ('awning_white', 'toldo blanco'),
    ('awning_light_blue', 'toldo celeste'))
add('supplementaries', 'lamp',
    ('sconce', 'palmatoria'),
    ('sconce_wall', 'aplique vela'),
    ('stone_lamp', 'lámpara de piedra'))

# ---------------- Amendments ----------------
add('amendments', 'planter',
    ('hanging_flower_pot', 'maceta colgante'))

# ---------------- Handcrafted ----------------
add('handcrafted', 'furniture',
    ('birch_counter', 'mostrador abedul'),
    ('birch_shelf', 'estante abedul'),
    ('birch_chair', 'silla abedul'),
    ('birch_table', 'mesa abedul'),
    ('birch_side_table', 'mesita abedul'),
    ('birch_bench', 'banco abedul'),
    ('birch_cupboard', 'alacena abedul'),
    ('birch_drawer', 'cajonera abedul'),
    ('birch_couch', 'sofá abedul'),
    ('white_crockery_combo', 'vajilla blanca'))
add('handcrafted', 'decor',
    ('calcite_pillar_trim', 'moldura pilar calcita'),
    ('calcite_corner_trim', 'moldura esquina calc.'))
add('handcrafted', 'planter',
    ('white_glazed_medium_pot', 'vasija blanca'),
    ('white_glazed_wide_pot', 'maceta ancha blanca'),
    ('terracotta_thick_pot', 'maceta terracota'),
    ('blue_glazed_thin_pot', 'florero azul'))

# ---------------- MrCrayfish's Furniture: Refurbished ----------------
add('refurbished_furniture', 'furniture',
    ('birch_table', 'mesa abedul (R)'),
    ('birch_storage_jar', 'tarro abedul'),
    ('birch_crate', 'cajón abedul'),
    ('white_sofa', 'sofá blanco'),
    ('white_lamp', 'lámpara blanca'))
add('refurbished_furniture', 'decor',
    ('birch_mail_box', 'buzón abedul'),
    ('post_box', 'buzón de correo'),
    ('birch_lattice_fence', 'celosía abedul'),
    ('birch_lattice_fence_gate', 'portón celosía'))
add('refurbished_furniture', 'path',
    ('diorite_stepping_stones', 'pisaderas diorita'))

# ---------------- Chipped ----------------
add('chipped', 'decor',
    ('calcite_bricks', 'ladrillo calcita'),
    ('calcite_pillar', 'pilar calcita'),
    ('ornate_calcite_pillar', 'pilar ornado calcita'),
    ('checkered_calcite_tiles', 'damero calcita'),
    ('polished_calcite', 'calcita pulida'),
    ('embossed_waxed_oxidized_copper', 'cobre verde repujado'),
    ('plated_waxed_weathered_copper', 'cobre chapado'),
    ('engraved_waxed_copper_block', 'cobre grabado'))
add('chipped', 'path',
    ('flat_calcite_tiles', 'baldosa calcita'))
add('chipped', 'window',
    ('clear_leaded_glass_pane', 'vidrio emplomado'),
    ('large_diamond_leaded_glass_pane', 'emplomado rombos'),
    ('arched_leaded_glass_pane_pillar', 'emplomado en arco'),
    ('ornate_light_blue_stained_glass_pane', 'vitral ornado celeste'),
    ('circular_yellow_stained_glass_pane', 'vitral rosetón amar.'),
    ('large_diamond_cyan_stained_glass_pane', 'vitral rombos cian'),
    ('arched_light_blue_stained_glass_pane_pillar', 'vitral arco celeste'))
add('chipped', 'lamp',
    ('ornate_sea_lantern', 'linterna marina orn.'),
    ('fancy_glowstone_lantern', 'farol luminita'),
    ('big_lantern', 'farol grande'),
    ('glass_pearlescent_froglight', 'froglight vidriado'))

# ---------------- Rechiseled ----------------
add('rechiseled', 'decor',
    ('quartz_block_chiseled_pillar', 'pilar cuarzo cincel.'),
    ('quartz_block_small_tiles', 'teselas cuarzo'),
    ('copper_block_circles', 'cobre círculos'))

# ---------------- FramedBlocks (shapes; they need a camo block entity) ----------------
FRAMED_NOTE = ('Frame without camo: give the block entity a camo state (e.g. minecraft:calcite) in the '
               'structure NBT, otherwise it shows the bare wooden frame.')
add('framedblocks', 'decor',
    ('framed_lattice_block', 'celosía enmarcada', {'note': FRAMED_NOTE}),
    ('framed_pillar', 'pilar enmarcado', {'note': FRAMED_NOTE}))
add('framedblocks', 'planter',
    ('framed_flower_pot', 'maceta enmarcada', {'note': FRAMED_NOTE}))
