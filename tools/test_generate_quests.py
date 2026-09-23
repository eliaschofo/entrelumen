"""Focused multi-chapter contracts; no gameplay claims."""
import copy,hashlib,json,unittest
from pathlib import Path
from generate_quests import ROOT,OUT,generate_all
class ChapterContracts(unittest.TestCase):
 def setUp(self):
  self.chapters=[json.loads((ROOT/'content'/n).read_text(encoding='utf-8')) for n in ('first_hour.json','act_two.json','act_three.json','act_four.json','act_five.json','act_six.json')]
 def test_first_hour_ids_unchanged(self):
  c=json.loads(generate_all(self.chapters)[OUT/'chapters/a_light_among_ruins.snbt'])
  ids=[c['id']]+[i for q in c['quests'] for i in (q['id'],q['tasks'][0]['id'])]
  self.assertEqual(hashlib.sha256('\n'.join(ids).encode()).hexdigest(),'68fc43aa8b72cbd5cde51c478773e0563be3af222d8560c6a9ceb19fb1c127ec')
 def test_cross_chapter_cycle_rejected(self):
  next(q for q in self.chapters[0]['quests'] if q['key']=='atlas')['deps']=['crafts_welcome']
  with self.assertRaisesRegex(AssertionError,'cycle|reading direction'):generate_all(self.chapters)
 def test_unknown_cross_chapter_dependency_rejected(self):
  self.chapters[1]['quests'][0]['deps']=['absent_quest']
  with self.assertRaisesRegex(AssertionError,'missing dependency'):generate_all(self.chapters)
 def test_duplicate_global_key_rejected(self):
  self.chapters[1]['quests'][0]['key']='arrival'
  with self.assertRaisesRegex(AssertionError,'global quest key'):generate_all(self.chapters)
 def test_placeholder_parity_rejected(self):
  self.chapters[1]['quests'][0]['es_es'][1]+=' %s'
  with self.assertRaisesRegex(AssertionError,'placeholder mismatch'):generate_all(self.chapters)
 def test_act_two_spanish_mechanical_coverage_and_encoding(self):
  quests={q['key']:q for q in self.chapters[1]['quests']}
  requirements={'crafts_sourcegem':['amatista','lapisl\u00e1zuli','archwood','oro'],
                'crafts_magebloom':['diamante','oro','sourcestone','cero'],
                'crafts_rations':['Dos sopas','dos filetes','zanahoria','papa','remolacha','hojas verdes']}
  for key,words in requirements.items():
   for word in words:self.assertIn(word,quests[key]['es_es'][1])
  for q in quests.values():
   for text in q['es_es']:
    self.assertNotIn('\u00c3',text)
    self.assertNotIn('\ufffd',text)
 def test_authority_graph_and_no_rewards(self):
  out=generate_all(self.chapters);second=json.loads(out[OUT/'chapters/the_lost_crafts.snbt'])
  self.assertEqual(len(second['quests']),22)
  self.assertEqual(sum(q['tasks'][0]['type']=='entrelumen:campaign' for q in second['quests']),5)
  self.assertTrue(all(q['rewards']==[] for q in second['quests']))
  source={q['milestone']:q for q in self.chapters[1]['quests'] if 'milestone' in q}
  for m in ('precision_bench','crystal_grid','travelling_pantry'):self.assertEqual(source[m]['deps'],['signal'])
  self.assertEqual(source['living_workshop']['deps'],['signal','crafts_precision'])
  self.assertEqual(set(source['lost_workshop']['deps']),{'crafts_precision','crafts_crystal','crafts_living','crafts_pantry'})
 def test_act_two_ids_unchanged(self):
  c=json.loads(generate_all(self.chapters)[OUT/'chapters/the_lost_crafts.snbt'])
  ids=[c['id']]+[i for q in c['quests'] for i in (q['id'],q['tasks'][0]['id'])]
  self.assertEqual(hashlib.sha256('\n'.join(ids).encode()).hexdigest(),'c0ef7483bc21ddffd23b31879b60964130b3e3de065dfb62adb2870bf26a3618')
 def test_act_three_authority_and_gift_route(self):
  source={q['milestone']:q for q in self.chapters[2]['quests'] if 'milestone' in q}
  expected={'signal_exchange':['crafts_archive'],'nursery_protocol':['crafts_archive'],
   'distributed_power':['crafts_archive'],'measured_logistics':['crafts_archive','exchange_signal'],
   'workshop_hands':['crafts_archive','exchange_power'],
   'exchange_route':['exchange_signal','exchange_nursery','exchange_power','exchange_logistics','exchange_hands']}
  self.assertEqual({m:q['deps'] for m,q in source.items()},expected)
  all_quests={q['key']:q for c in self.chapters for q in c['quests']}
  def authority_only(key):
   self.assertIn('milestone',all_quests[key])
   for dep in all_quests[key]['deps']:authority_only(dep)
  for q in all_quests.values():
   if 'milestone' in q:authority_only(q['key'])
  files=generate_all(self.chapters)
  c=json.loads(files[OUT/'chapters/routes_of_exchange.snbt'])
  self.assertEqual(len(c['quests']),27)
  self.assertTrue(all(q['rewards']==[] for q in c['quests']))
  for q in c['quests']:
   task=q['tasks'][0]
   if task['type']=='item':self.assertFalse(task['consume_items'])
   if task['type']=='checkmark':self.assertTrue(q['optional'])
  mapping=json.loads(files[ROOT/'content/campaign_task_ids.json'])
  for q in c['quests']:
   task=q['tasks'][0]
   if task['type']=='entrelumen:campaign':self.assertEqual(mapping[task['milestone']],{'quest_id':q['id'],'task_id':task['id']})
 def test_act_three_bilingual_acquisition_and_encoding(self):
  quests={q['key']:q for q in self.chapters[2]['quests']}
  required={'exchange_processors':('without Silk Touch','sin Toque de Seda'),
   'exchange_pcb':('eight emeralds','ocho esmeraldas'),
   'exchange_wax':('ordinary Minecraft honeycomb','panal común de Minecraft'),
   'exchange_prudentium':('eight additional','otros ocho'),
   'exchange_menril':('1000 mB','1000 mB'),
   'exchange_mechanism':('probabilistic','probabilístico'),
   'exchange_archive':('three paper and one copper ingot','tres papeles y un lingote de cobre')}
  for key,terms in required.items():
   for locale,term in zip(('en_us','es_es'),terms):self.assertIn(term,quests[key][locale][1])
  for q in quests.values():
   for locale in ('en_us','es_es'):
    for text in q[locale]:
     self.assertNotIn('\ufffd',text)
     self.assertNotIn('\u00c3',text)
 def test_act_three_placeholder_mismatch_rejected(self):
  self.chapters[2]['quests'][0]['es_es'][1]+=' %s'
  with self.assertRaisesRegex(AssertionError,'placeholder mismatch'):generate_all(self.chapters)
 def test_all_campaign_dependencies_match_server(self):
  projects=json.loads((ROOT/'companion/src/main/resources/data/entrelumen/campaign/projects.json').read_text(encoding='utf-8'))
  quests={q['key']:q for chapter in self.chapters for q in chapter['quests']}
  for q in quests.values():
   if 'milestone' not in q:continue
   with self.subTest(milestone=q['milestone']):
    self.assertTrue(all('milestone' in quests[dep] for dep in q['deps']))
    if q['milestone'] in {'aether_arrival','twilight_arrival','bumblezone_arrival','end_arrival'}:
     self.assertEqual(q['deps'],[])
     self.assertNotIn(q['milestone'],projects)
    elif q['milestone'] in {'ark_calibrated','ark_contained','ark_renewed','ark_routed','ark_provisioned','ark_charted','last_horizon'}:
     self.assertNotIn(q['milestone'],projects)
     expected={
      'ark_calibrated':['engineering_module','arcane_module','nature_module','logistics_module','habitation_module','exploration_module'],
      'ark_contained':['ark_calibrated'],'ark_renewed':['ark_contained'],'ark_routed':['ark_renewed'],
      'ark_provisioned':['ark_routed'],'ark_charted':['ark_provisioned'],
      'last_horizon':['ark_charted','world_network','end_arrival']}
     self.assertEqual([quests[dep]['milestone'] for dep in q['deps']],expected[q['milestone']])
    else:self.assertEqual([quests[dep]['milestone'] for dep in q['deps']],projects[q['milestone']].get('requires',[]))
 def test_prior_chapters_and_translations_unchanged(self):
  out=generate_all(self.chapters)
  expected={'a_light_among_ruins':'28fdd2969fdd6829c2cad480e2a54b74c9d1ba7ab980e7203831f2fea5304bae',
   'the_lost_crafts':'26dcb9e26fa44e764b56f1667141abae1ea0d37f66ec671b4d2e7a3263aa57e5',
   'routes_of_exchange':'adb774f655ee0442b2ab825a8c634b55b137842b5e137177df7df21a1c46f38c'}
  for chapter,digest in expected.items():
   self.assertEqual(hashlib.sha256(out[OUT/'chapters'/(chapter+'.snbt')].encode()).hexdigest(),digest)
  from generate_quests import snbt
  for lang,digest in [('en_us','9ff5879a802da2c1da600724aaf04f86675d2edfafdd25f7d34c4ba2b09b5ecb'),('es_es','8c4c1bf1fd2a349a4dc920f9052ad988bb6856fa92b27cf796469ff14cf28f25')]:
   prior=json.loads(generate_all(self.chapters[:3])[OUT/'lang'/(lang+'.snbt')])
   actual=json.loads(out[OUT/'lang'/(lang+'.snbt')])
   self.assertEqual(hashlib.sha256(snbt({key:actual[key] for key in prior}).encode()).hexdigest(),digest)
 def test_act_three_ids_unchanged(self):
  c=json.loads(generate_all(self.chapters)[OUT/'chapters/routes_of_exchange.snbt'])
  ids=[c['id']]+[i for q in c['quests'] for i in (q['id'],q['tasks'][0]['id'])]
  self.assertEqual(hashlib.sha256(chr(10).join(ids).encode()).hexdigest(),'1b7c2c78e2260641cbcb7342b2843da49370bdb49e3bae887a71902fdd48a3c0')
 def test_act_four_campaign_and_observers(self):
  chapter=self.chapters[3];self.assertEqual(len(chapter['quests']),29)
  source={q['milestone']:q for q in chapter['quests'] if 'milestone' in q}
  expected={'spectral_archive':['exchange_archive'],'horizon_survey':['exchange_archive','voices_aether','voices_twilight'],
   'pollinator_treaty':['exchange_archive','voices_bumblezone'],'sealed_memory':['exchange_archive','voices_archive'],
   'atlas_voices':['voices_archive','voices_horizon','voices_pollinators','voices_seal'],
   'aether_arrival':[],'twilight_arrival':[],'bumblezone_arrival':[]}
  self.assertEqual({key:q['deps'] for key,q in source.items()},expected)
  out=generate_all(self.chapters);c=json.loads(out[OUT/'chapters/voices_of_the_atlas.snbt'])
  mapping=json.loads(out[ROOT/'content/campaign_task_ids.json'])
  self.assertEqual(len(json.loads(generate_all(self.chapters[:4])[ROOT/'content/campaign_task_ids.json'])),24)
  for q in c['quests']:
   self.assertEqual(q['rewards'],[]);task=q['tasks'][0]
   if task['type']=='entrelumen:campaign':self.assertEqual(mapping[task['milestone']],{'quest_id':q['id'],'task_id':task['id']})
   elif task['type']=='item':self.assertFalse(task['consume_items'])
   else:self.assertTrue(q['optional'])
 def test_act_four_translation_and_acquisition(self):
  quests={q['key']:q for q in self.chapters[3]['quests']}
  required={'voices_crystal':('four diamonds','cuatro diamantes'),'voices_spirits':('crude scythe','guadaña rudimentaria'),
   'voices_soul_steel':('eight refined','ocho piedras refinadas'),'voices_lens':('two lenses','dos lentes'),
   'voices_chorus':('three paper and one copper ingot','tres papeles y un lingote de cobre')}
  for key,terms in required.items():
   for locale,term in zip(('en_us','es_es'),terms):self.assertIn(term,quests[key][locale][1])
  for q in quests.values():
   for locale in ('en_us','es_es'):
    for text in q[locale]:
     self.assertNotIn('\ufffd',text);self.assertNotIn('\u00c3',text)
 def test_act_four_cycle_rejected(self):
  next(q for q in self.chapters[2]['quests'] if q['key']=='exchange_archive')['deps']=['voices_chorus']
  with self.assertRaisesRegex(AssertionError,'cycle|reading direction'):generate_all(self.chapters)
 def test_first_104_quests_and_text_frozen(self):
  from generate_quests import snbt
  out=generate_all(self.chapters)
  expected={'a_light_among_ruins':'28fdd2969fdd6829c2cad480e2a54b74c9d1ba7ab980e7203831f2fea5304bae',
   'the_lost_crafts':'26dcb9e26fa44e764b56f1667141abae1ea0d37f66ec671b4d2e7a3263aa57e5',
   'routes_of_exchange':'adb774f655ee0442b2ab825a8c634b55b137842b5e137177df7df21a1c46f38c',
   'voices_of_the_atlas':'1c120b93b38a38bcf5e9756ca3979d6e5e67aa5b090e630f8a91f3313dde3ad2'}
  self.assertEqual(sum(len(c['quests']) for c in self.chapters[:4]),104)
  for chapter,digest in expected.items():
   self.assertEqual(hashlib.sha256(out[OUT/'chapters'/(chapter+'.snbt')].encode()).hexdigest(),digest)
  prior=generate_all(self.chapters[:4])
  for lang,digest in [('en_us','bffc5c1d1fec63222008d6a24a88b3cb2b3c46aadff3d2f76c990c8803ba9c78'),
                      ('es_es','c9a5d503529a3bdcc05f332c491be5ec64302b0451286f6c81fc7e31750ebf4a')]:
   before=json.loads(prior[OUT/'lang'/(lang+'.snbt')]);after=json.loads(out[OUT/'lang'/(lang+'.snbt')])
   self.assertEqual(hashlib.sha256(snbt({key:after[key] for key in before}).encode()).hexdigest(),digest)
  mapping=json.loads(out[ROOT/'content/campaign_task_ids.json'])
  before=json.loads(prior[ROOT/'content/campaign_task_ids.json'])
  self.assertEqual(hashlib.sha256(snbt({key:mapping[key] for key in before}).encode()).hexdigest(),
                   'dbc4bf5f3cb8df145aef5204a41841053ad81b42be776fc3d1b14958f02a6f22')
 def test_act_five_authority_recipe_counts_and_rewards(self):
  chapter=self.chapters[4];self.assertEqual(len(chapter['quests']),24)
  projects=json.loads((ROOT/'companion/src/main/resources/data/entrelumen/campaign/projects.json').read_text(encoding='utf-8'))
  source={q['milestone']:q for q in chapter['quests'] if 'milestone' in q}
  self.assertEqual({m:q['deps'] for m,q in source.items()},
   {'resilient_backbone':['voices_chorus'],'renewal_engine':['voices_chorus'],
    'settlement_supply':['voices_chorus'],'world_network':['world_backbone','world_renewal','world_settlement']})
  self.assertEqual({m:projects[m]['items'] for m in source},
   {'resilient_backbone':{'entrelumen:ark_bus':1},'renewal_engine':{'entrelumen:renewal_engine':1},
    'settlement_supply':{'entrelumen:habitation_contract':1},
    'world_network':{'minecraft:paper':3,'minecraft:copper_ingot':1}})
  expected_items={'world_atomic_alloy':2,'world_circuit_boards':2,'world_handling_cores':2,'world_inventory_sensor':1,
   'world_ark_bus':1,'world_sky_ingots':2,'world_imperium':2,'world_capsules':2,'world_propagation':2,
   'world_renewal_item':1,'world_fish_stew':2,'world_mixed_salad':2,'world_calculation':1,'world_rations':2,
   'world_contract_item':1}
  self.assertEqual({q['key']:q['count'] for q in chapter['quests'] if 'item' in q},expected_items)
  designs=json.loads((ROOT/'content/integration-design.json').read_text(encoding='utf-8'))['projects']
  designs={p['id'].split(':')[1]:p['recipe'] for p in designs if p.get('act')==5}
  material_quests={q['item']:q['count'] for q in chapter['quests'] if 'item' in q}
  for milestone,slots in [('resilient_backbone',7),('renewal_engine',8),('settlement_supply',7)]:
   recipe=designs[milestone]
   self.assertEqual(recipe['type'],'minecraft:crafting_shapeless')
   self.assertEqual(sum(i['count'] for i in recipe['inputs']),slots)
   for ingredient in recipe['inputs']:
    self.assertEqual(material_quests[ingredient['id']],ingredient['count'])
  out=generate_all(self.chapters);compiled=json.loads(out[OUT/'chapters/world_we_build.snbt'])
  self.assertEqual(len(compiled['quests']),24)
  self.assertEqual(sum(q['tasks'][0]['type']=='entrelumen:campaign' for q in compiled['quests']),4)
  self.assertTrue(all(q['rewards']==[] for q in compiled['quests']))
  for q in compiled['quests']:
   task=q['tasks'][0]
   if task['type']=='item':self.assertFalse(task['consume_items'])
   if task['type']=='checkmark':self.assertTrue(q['optional'])
  mapping=json.loads(generate_all(self.chapters[:5])[ROOT/'content/campaign_task_ids.json']);self.assertEqual(len(mapping),28)
  for q in compiled['quests']:
   task=q['tasks'][0]
   if task['type']=='entrelumen:campaign':
    self.assertEqual(mapping[task['milestone']],{'quest_id':q['id'],'task_id':task['id']})
 def test_act_five_bilingual_material_and_truthful_exercises(self):
  source={q['key']:q for q in self.chapters[4]['quests']}
  required={'world_atomic_alloy':('40 refined-obsidian infusion','40 de infusión de obsidiana refinada'),
   'world_circuit_boards':('UV Light Box','UV Light Box'),
   'world_sky_ingots':('Overworld and Nether aura bottles','Overworld y Nether'),
   'world_imperium':('128 inferium','128 inferium'),
   'world_fish_stew':('tomato sauce and onion','salsa de tomate y cebolla'),
   'world_mixed_salad':('tomato, beetroot and bowl','tomate, remolacha y cuenco'),
   'world_ark_bus':('seven slots','siete espacios'),
   'world_renewal_item':('Eight slots','Ocho espacios'),
   'world_contract_item':('four bowls','cuatro cuencos'),
   'world_network':('three paper and one copper ingot','tres papeles y un lingote de cobre'),
   'world_optional_panel':('ComputerCraft','ComputerCraft')}
  for key,terms in required.items():
   for locale,term in zip(('en_us','es_es'),terms):self.assertIn(term,source[key][locale][1])
  for key in ('world_buffer_trial','world_renewal_trial','world_habitation_trial','world_optional_panel'):
   self.assertEqual(source[key]['type'],'checkmark');self.assertTrue(source[key]['optional'])
   self.assertIn('self-reported',source[key]['en_us'][1]);self.assertIn('autoevaluado',source[key]['es_es'][1])
  for q in source.values():
   for locale in ('en_us','es_es'):
    for value in q[locale]:
     self.assertNotIn('\ufffd',value);self.assertNotIn('\u00c3',value)
 def test_act_five_optional_practice_cannot_gate_campaign(self):
  quests={q['key']:q for chapter in self.chapters for q in chapter['quests']}
  def ancestors(key):
   for dep in quests[key]['deps']:
    self.assertIn('milestone',quests[dep],f'{key} gated by tutorial {dep}')
    ancestors(dep)
  for q in self.chapters[4]['quests']:
   if 'milestone' in q:ancestors(q['key'])
 def test_first_128_quests_frozen(self):
  from generate_quests import snbt
  out=generate_all(self.chapters)
  prior=generate_all(self.chapters[:5])
  self.assertEqual(sum(len(c['quests']) for c in self.chapters[:5]),128)
  hashes={'a_light_among_ruins':'28fdd2969fdd6829c2cad480e2a54b74c9d1ba7ab980e7203831f2fea5304bae',
   'the_lost_crafts':'26dcb9e26fa44e764b56f1667141abae1ea0d37f66ec671b4d2e7a3263aa57e5',
   'routes_of_exchange':'adb774f655ee0442b2ab825a8c634b55b137842b5e137177df7df21a1c46f38c',
   'voices_of_the_atlas':'1c120b93b38a38bcf5e9756ca3979d6e5e67aa5b090e630f8a91f3313dde3ad2',
   'world_we_build':'f410b503d830631db65c1efdb7ceefe2ad2a27e6c18c6e646ab0807003341ad6'}
  for chapter,digest in hashes.items():
   path=OUT/'chapters'/(chapter+'.snbt')
   self.assertEqual(hashlib.sha256(out[path].encode()).hexdigest(),digest)
   self.assertEqual(out[path],prior[path])
  for lang,digest in [('en_us','41e8b7af973281c564ee5b05ccb26e966c3ad3ba7762ee3ead621d1e658b8385'),
                      ('es_es','3ee79458cf984118be71330b874afc6dd39b4c15458d5944d62ae80dda475829')]:
   path=OUT/'lang'/(lang+'.snbt')
   before=json.loads(prior[path]);after=json.loads(out[path])
   self.assertEqual(hashlib.sha256(snbt({key:after[key] for key in before}).encode()).hexdigest(),digest)
  path=ROOT/'content/campaign_task_ids.json'
  before=json.loads(prior[path]);after=json.loads(out[path])
  self.assertEqual(hashlib.sha256(snbt({key:after[key] for key in before}).encode()).hexdigest(),
                   '095c83ab710a2f76683054f73aebb924f3c96dce807370f48c457085187d54d1')
 def test_act_six_authority_and_exact_deliveries(self):
  chapter=self.chapters[5]
  self.assertEqual(chapter['chapter'],'last_horizon')
  self.assertEqual(len(chapter['quests']),27)
  source={q['milestone']:q for q in chapter['quests'] if 'milestone' in q}
  self.assertEqual(set(source),set(chapter['milestones']))
  self.assertEqual(len(source),14)
  projects=json.loads((ROOT/'companion/src/main/resources/data/entrelumen/campaign/projects.json').read_text(encoding='utf-8'))
  designs=json.loads((ROOT/'content/integration-design.json').read_text(encoding='utf-8'))['projects']
  recipes={p['id'].split(':')[1]:p['recipe'] for p in designs if p.get('act')==6}
  names={'engineering_module':'ark_engineering','arcane_module':'ark_arcana','nature_module':'ark_nature',
         'exploration_module':'ark_exploration','logistics_module':'ark_logistics','habitation_module':'ark_habitation'}
  exact={
   'engineering_module':{'entrelumen:calibration_frame':2,'entrelumen:energy_coupler':2,'entrelumen:ark_bus':1,'mekanism:alloy_atomic':2},
   'arcane_module':{'entrelumen:spectral_lens':2,'entrelumen:containment_seal':2,'occultism:iesnium_ingot':2},
   'nature_module':{'entrelumen:renewal_engine':1,'entrelumen:ecosystem_capsule':2,'entrelumen:living_matrix':2},
   'exploration_module':{'entrelumen:horizon_chart':1,'entrelumen:spectral_lens':1,'twilightforest:steeleaf_ingot':2,'aether:zanite_gemstone':2},
   'logistics_module':{'entrelumen:routing_matrix':2,'entrelumen:handling_core':2,'entrelumen:ark_bus':1},
   'habitation_module':{'entrelumen:habitation_contract':1,'entrelumen:ration_bundle':2,'entrelumen:living_matrix':2}}
  for milestone,recipe_name in names.items():
   recipe=recipes[recipe_name]
   expected={item['id']:item['count'] for item in recipe['inputs']}
   self.assertEqual(expected,exact[milestone])
   self.assertEqual(projects[milestone]['items'],expected)
   self.assertEqual(projects[milestone]['reward'],'entrelumen:'+milestone)
   self.assertEqual(recipe['type'],'minecraft:crafting_shapeless')
   self.assertEqual(sum(expected.values()),sum(i['count'] for i in recipe['inputs']))
  self.assertEqual({m:source[m]['deps'] for m in names},
   {'engineering_module':['world_network'],'arcane_module':['world_network'],'nature_module':['world_network'],
    'exploration_module':['world_network','horizon_end_arrival'],
    'logistics_module':['world_network'],'habitation_module':['world_network']})
  self.assertEqual(source['end_arrival']['deps'],[])
  for q in source.values():
   self.assertTrue(all('milestone' in {p['key']:p for c in self.chapters for p in c['quests']}[dep] for dep in q['deps']))
 def test_act_six_mirrors_and_bilingual_instructions(self):
  out=generate_all(self.chapters)
  compiled=json.loads(out[OUT/'chapters/last_horizon.snbt'])
  mapping=json.loads(out[ROOT/'content/campaign_task_ids.json'])
  self.assertEqual(len(compiled['quests']),27)
  self.assertEqual(len(mapping),42)
  self.assertTrue(all(q['rewards']==[] for q in compiled['quests']))
  source={q['key']:q for q in self.chapters[5]['quests']}
  for q in compiled['quests']:
   task=q['tasks'][0]
   if task['type']=='entrelumen:campaign':
    self.assertEqual(mapping[task['milestone']],{'quest_id':q['id'],'task_id':task['id']})
   elif task['type']=='item':self.assertFalse(task['consume_items'])
   else:self.assertEqual((task['type'],q.get('optional')),('checkmark',True))
  required={'horizon_controller':('lodestone','magnetita'),
   'horizon_engineering':('two energy couplers','dos acopladores de energía'),
   'horizon_arcane':('two Occultism iesnium ingots','dos lingotes de iesnium de Occultism'),
   'horizon_exploration':('two Twilight Forest steeleaf ingots','dos lingotes de steeleaf de Twilight Forest'),
   'horizon_end_arrival':('server witnesses real entry','servidor observa la entrada'),
   'horizon_calibrated':('four additional calibration frames','otros cuatro marcos de calibración'),
   'horizon_contained':('two additional containment seals','otros dos sellos de contención'),
   'horizon_renewed':('two additional ecosystem capsules','otras dos cápsulas de ecosistema'),
   'horizon_routed':('two additional routing matrices','otras dos matrices de enrutamiento'),
   'horizon_provisioned':('eight additional ration bundles','otros ocho paquetes de raciones'),
   'horizon_charted':('one additional horizon chart','otra carta del horizonte'),
   'horizon_last':('crouch and interact','agachate e interactuá')}
  for key,terms in required.items():
   for locale,term in zip(('en_us','es_es'),terms):self.assertIn(term,source[key][locale][1])
  for q in source.values():
   for locale in ('en_us','es_es'):
    for value in q[locale]:
     self.assertNotIn('\ufffd',value);self.assertNotIn('\u00c3',value)
if __name__=='__main__':unittest.main()
