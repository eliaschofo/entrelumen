"""Preview final grids inside a real vanilla inventory at integer scale, beside vanilla items."""
from PIL import Image, ImageDraw
import sys
R='G:/Elias/Codex/Entrelumen-work/art-redo-20260923/'
V=R+'ref/vanilla/assets/minecraft/textures/'
ITEMS=['atlas','raw_lens','survey_notes','signal_core','calibration_frame','energy_coupler','living_matrix','ration_bundle','routing_matrix',
       'propagation_core','power_regulator','inventory_sensor','handling_core','spectral_lens','horizon_chart','ecosystem_capsule','containment_seal','ark_bus',
       'renewal_engine','habitation_contract']
VAN=['book','spyglass','copper_ingot','heart_of_the_sea','bundle','compass_16','ender_eye','filled_map','experience_bottle']
inv=Image.open(V+'gui/container/inventory.png').convert('RGBA').crop((0,0,176,166))
slots=[(8+18*c,84+18*r) for r in range(3) for c in range(9)]
for i,n in enumerate(ITEMS): inv.alpha_composite(Image.open(R+f'grids/{n}.png').convert('RGBA'),slots[i])
for c,n in enumerate(VAN): inv.alpha_composite(Image.open(V+'item/'+n+'.png').convert('RGBA').crop((0,0,16,16)),(8+18*c,142))
S=int(sys.argv[1]) if len(sys.argv)>1 else 3
inv.resize((176*S,166*S),Image.NEAREST).crop((0,76*S,176*S,166*S)).save(R+'lineup.png')
