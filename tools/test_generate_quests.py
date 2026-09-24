"""Focused multi-chapter contracts; no gameplay claims."""
import copy,hashlib,json,re,unittest
from pathlib import Path
from generate_quests import ROOT,OUT,generate_all,stable_id,load_chapters
class ChapterContracts(unittest.TestCase):
 def setUp(self):
  self.chapters=load_chapters()
 def test_optional_inventory_branch_preserves_campaign_and_prior_text(self):
  # Since the renumbering of 24 September 2026 the campaign has seven chapters (act V has two).
  prior=generate_all(self.chapters[:7]);out=generate_all(self.chapters)
  branch=self.chapters[7];keys={q['key'] for q in branch['quests']}
  self.assertEqual(branch['chapter'],'inventory_that_remembers')
  self.assertEqual(branch['milestones'],[])
  self.assertTrue(all(q.get('optional') and 'milestone' not in q for q in branch['quests']))
  self.assertTrue(all(not keys.intersection(q['deps']) for c in self.chapters[:7] for q in c['quests']))
  for path,content in prior.items():
   if path.parent == OUT/'lang':
    before=json.loads(content);after=json.loads(out[path])
    self.assertEqual(before,{key:after[key] for key in before})
   else:self.assertEqual(content,out[path])
  compiled=json.loads(out[OUT/'chapters/inventory_that_remembers.snbt'])
  self.assertTrue(all(q['optional'] and q['rewards']==[] for q in compiled['quests']))
  self.assertTrue(all(t.get('consume_items',False)==False for q in compiled['quests'] for t in q['tasks']))
 def synthetic_chapter(self,name,count):
  quests=[]
  for i in range(count):
   key=f'{name}_node_{i:03}'
   quests.append({'key':key,'deps':[quests[-1]['key']] if quests else [],
    'type':'checkmark','optional':True,
    'en_us':[f'English title {i}',f'English chapter {name} node {i}: read this distinct account of the route and confirm this optional lesson after visiting its place in the Atlas.'],
    'es_es':[f'Título español {i}',f'Capítulo {name}, nodo {i}: leé este relato distinto del camino y confirmá esta lección opcional después de encontrar su lugar en el Atlas.'],
    'layout':{'x':0,'y':i*2.5,'group':'route','shape':'square','size':1.0}})
  return {'chapter':name,'title':{'en_us':f'English {name}','es_es':f'Español {name}'},
   'layout_groups':{'route':{'en_us':'Route','es_es':'Camino'}},
   'autofocus':f'{name}_node_000','milestones':[],'quests':quests}
 def test_variable_nonempty_chapters_preserve_all_ids_and_bilingual_text(self):
  chapters=[self.synthetic_chapter('short_route',3),self.synthetic_chapter('long_route',64)]
  files=generate_all(chapters)
  ids=set()
  for chapter in chapters:
   name=chapter['chapter'];compiled=json.loads(files[OUT/'chapters'/(name+'.snbt')])
   self.assertEqual(len(compiled['quests']),len(chapter['quests']))
   self.assertEqual(compiled['autofocus_id'],stable_id('quest:'+chapter['autofocus']))
   self.assertEqual(compiled['id'],stable_id('chapter:'+name))
   ids.add(compiled['id'])
   for source,output in zip(chapter['quests'],compiled['quests']):
    qid=stable_id('quest:'+source['key']);tid=stable_id('task:'+source['key'])
    self.assertEqual((output['id'],output['tasks'][0]['id']),(qid,tid))
    self.assertEqual(output['dependencies'],[stable_id('quest:'+dep) for dep in source['deps']])
    ids.update((qid,tid))
    for locale,label in (('en_us','Route'),('es_es','Camino')):
     values=json.loads(files[OUT/'lang'/(locale+'.snbt')])
     self.assertEqual(values[f'quest.{qid}.title'],source[locale][0])
     self.assertEqual(values[f'quest.{qid}.quest_desc'],[label,'',source[locale][1]])
  self.assertEqual(len(ids),2+2*(3+64))
  for locale in ('en_us','es_es'):
   self.assertEqual(len(json.loads(files[OUT/'lang'/(locale+'.snbt')])),2+2*(3+64))
  self.assertEqual(json.loads(files[ROOT/'content/campaign_task_ids.json']),{})
 def test_empty_chapter_rejected(self):
  chapter=self.synthetic_chapter('empty_route',0)
  with self.assertRaisesRegex(AssertionError,'empty chapter'):generate_all([chapter])
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
    if q['milestone'] in {'aether_arrival','twilight_arrival','bumblezone_arrival','end_arrival','heart_recovered'}:
     # Server observations: dimension arrivals and the Heart a Sun Spirit dropped.
     self.assertEqual(q['deps'],[])
     self.assertNotIn(q['milestone'],projects)
    elif q['milestone']=='solsticio_arrival':
     # Recorded only for a campaign past Solsticio's gate (the Ark activated).
     self.assertEqual([quests[dep]['milestone'] for dep in q['deps']],['last_horizon'])
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
 def assert_prefix_text_append_only(self,count,out):
  # The chapter .snbt digests freeze IDs, tasks, dependencies, icons and layout. Text is authored
  # and was rewritten on purpose (lore of 24 September 2026), so it is not frozen byte for byte.
  # What stays frozen: the locale keys are exactly the ones those IDs imply, and adding later
  # chapters never changes the text of earlier ones.
  prior=generate_all(self.chapters[:count])
  keys=set()
  for data in self.chapters[:count]:
   compiled=json.loads(out[OUT/'chapters'/(data['chapter']+'.snbt')])
   keys.add(f"chapter.{compiled['id']}.title")
   if 'subtitle' in data:keys.add(f"chapter.{compiled['id']}.chapter_subtitle")
   keys.update(f'quest.{q["id"]}.{field}' for q in compiled['quests'] for field in ('title','quest_desc'))
  for lang in ('en_us','es_es'):
   before=json.loads(prior[OUT/'lang'/(lang+'.snbt')]);after=json.loads(out[OUT/'lang'/(lang+'.snbt')])
   self.assertEqual(set(before),keys)
   self.assertEqual(before,{key:after[key] for key in before})
 def test_prior_chapters_frozen_and_text_append_only(self):
  out=generate_all(self.chapters)
  expected={'a_light_among_ruins':'28fdd2969fdd6829c2cad480e2a54b74c9d1ba7ab980e7203831f2fea5304bae',
   'the_lost_crafts':'26dcb9e26fa44e764b56f1667141abae1ea0d37f66ec671b4d2e7a3263aa57e5',
   'routes_of_exchange':'adb774f655ee0442b2ab825a8c634b55b137842b5e137177df7df21a1c46f38c'}
  for chapter,digest in expected.items():
   self.assertEqual(hashlib.sha256(out[OUT/'chapters'/(chapter+'.snbt')].encode()).hexdigest(),digest)
  self.assert_prefix_text_append_only(3,out)
 def test_act_three_ids_unchanged(self):
  c=json.loads(generate_all(self.chapters)[OUT/'chapters/routes_of_exchange.snbt'])
  ids=[c['id']]+[i for q in c['quests'] for i in (q['id'],q['tasks'][0]['id'])]
  self.assertEqual(hashlib.sha256(chr(10).join(ids).encode()).hexdigest(),'1b7c2c78e2260641cbcb7342b2843da49370bdb49e3bae887a71902fdd48a3c0')
 def test_act_four_campaign_and_observers(self):
  chapter=self.chapters[3];self.assertEqual(len(chapter['quests']),31)
  source={q['milestone']:q for q in chapter['quests'] if 'milestone' in q}
  expected={'spectral_archive':['exchange_archive'],'horizon_survey':['exchange_archive','voices_aether','voices_twilight'],
   'pollinator_treaty':['exchange_archive','voices_bumblezone'],'sealed_memory':['exchange_archive','voices_archive'],
   'heliodor_heart':['exchange_archive','voices_sun_spirit'],
   'atlas_voices':['voices_archive','voices_horizon','voices_pollinators','voices_seal','voices_heart'],
   'aether_arrival':[],'twilight_arrival':[],'bumblezone_arrival':[],'heart_recovered':[]}
  self.assertEqual({key:q['deps'] for key,q in source.items()},expected)
  out=generate_all(self.chapters);c=json.loads(out[OUT/'chapters/voices_of_the_atlas.snbt'])
  mapping=json.loads(out[ROOT/'content/campaign_task_ids.json'])
  self.assertEqual(len(json.loads(generate_all(self.chapters[:4])[ROOT/'content/campaign_task_ids.json'])),26)
  for q in c['quests']:
   self.assertEqual(q['rewards'],[]);task=q['tasks'][0]
   if task['type']=='entrelumen:campaign':self.assertEqual(mapping[task['milestone']],{'quest_id':q['id'],'task_id':task['id']})
   elif task['type']=='item':self.assertFalse(task['consume_items'])
   else:self.assertTrue(q['optional'])
 def test_act_four_translation_and_acquisition(self):
  quests={q['key']:q for q in self.chapters[3]['quests']}
  required={'voices_crystal':('four diamonds','cuatro diamantes'),'voices_spirits':('crude scythe','guadaña rudimentaria'),
   'voices_soul_steel':('eight refined','ocho piedras refinadas'),'voices_lens':('two lenses','dos lentes'),
   'voices_chorus':('three paper and one copper ingot','tres papeles y un lingote de cobre'),
   'voices_sun_spirit':('ice crystals','cristales de hielo'),
   'voices_heart':('Heart of Heliodor','Corazón de Heliodor')}
  for key,terms in required.items():
   for locale,term in zip(('en_us','es_es'),terms):self.assertIn(term,quests[key][locale][1])
  for q in quests.values():
   for locale in ('en_us','es_es'):
    for text in q[locale]:
     self.assertNotIn('\ufffd',text);self.assertNotIn('\u00c3',text)
 def test_act_four_cycle_rejected(self):
  next(q for q in self.chapters[2]['quests'] if q['key']=='exchange_archive')['deps']=['voices_chorus']
  with self.assertRaisesRegex(AssertionError,'cycle|reading direction'):generate_all(self.chapters)
 def test_first_106_quests_frozen(self):
  # Act IV gained two quests on 24 September 2026 (the Sun Spirit and the Heart delivery) and
  # voices_chorus now also depends on the delivery. Its chapter digest and the task-map digest were
  # replaced on purpose; every earlier quest keeps its ID, task, icon and layout (checked below).
  from generate_quests import snbt
  out=generate_all(self.chapters)
  expected={'a_light_among_ruins':'28fdd2969fdd6829c2cad480e2a54b74c9d1ba7ab980e7203831f2fea5304bae',
   'the_lost_crafts':'26dcb9e26fa44e764b56f1667141abae1ea0d37f66ec671b4d2e7a3263aa57e5',
   'routes_of_exchange':'adb774f655ee0442b2ab825a8c634b55b137842b5e137177df7df21a1c46f38c',
   'voices_of_the_atlas':'7e5407195c10e48c213b4588b6fc8c06a1b2862d9c531fb14c0a3277b7d8733c'}
  self.assertEqual(sum(len(c['quests']) for c in self.chapters[:4]),106)
  for chapter,digest in expected.items():
   self.assertEqual(hashlib.sha256(out[OUT/'chapters'/(chapter+'.snbt')].encode()).hexdigest(),digest)
  prior=generate_all(self.chapters[:4])
  self.assert_prefix_text_append_only(4,out)
  mapping=json.loads(out[ROOT/'content/campaign_task_ids.json'])
  before=json.loads(prior[ROOT/'content/campaign_task_ids.json'])
  self.assertEqual(hashlib.sha256(snbt({key:mapping[key] for key in before}).encode()).hexdigest(),
                   '9c676c7796077169cd3a6ff46de5a65d7f21d0048c3e818a027662219fa606d3')
 def test_act_four_additions_keep_every_earlier_quest(self):
  # The 29 act IV quests of the 24 September text rewrite are unchanged except for the one new
  # dependency of voices_chorus; the new quests are the Sun Spirit observation and the Heart delivery.
  compiled=json.loads(generate_all(self.chapters)[OUT/'chapters/voices_of_the_atlas.snbt'])
  ids=[stable_id('quest:'+q['key']) for q in self.chapters[3]['quests']]
  self.assertEqual([q['id'] for q in compiled['quests']],ids)
  added={stable_id('quest:voices_sun_spirit'),stable_id('quest:voices_heart')}
  kept=[q for q in compiled['quests'] if q['id'] not in added]
  self.assertEqual(len(kept),29)
  chorus=next(q for q in compiled['quests'] if q['id']==stable_id('quest:voices_chorus'))
  self.assertEqual(chorus['dependencies'][-1],stable_id('quest:voices_heart'))
  heart=next(q for q in compiled['quests'] if q['id']==stable_id('quest:voices_heart'))
  self.assertEqual(heart['tasks'][0],{'id':stable_id('task:voices_heart'),'type':'entrelumen:campaign','milestone':'heliodor_heart'})
  spirit=next(q for q in compiled['quests'] if q['id']==stable_id('quest:voices_sun_spirit'))
  self.assertEqual((spirit['tasks'][0]['milestone'],spirit['dependencies']),('heart_recovered',[]))
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
  mapping=json.loads(generate_all(self.chapters[:5])[ROOT/'content/campaign_task_ids.json']);self.assertEqual(len(mapping),30)
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
 def test_first_130_quests_frozen(self):
  # 128 before 24 September 2026; act IV gained two quests. world_we_build keeps its digest; the
  # voices_of_the_atlas and task-map digests were replaced on purpose (see test_first_106_quests_frozen).
  from generate_quests import snbt
  out=generate_all(self.chapters)
  prior=generate_all(self.chapters[:5])
  self.assertEqual(sum(len(c['quests']) for c in self.chapters[:5]),130)
  hashes={'a_light_among_ruins':'28fdd2969fdd6829c2cad480e2a54b74c9d1ba7ab980e7203831f2fea5304bae',
   'the_lost_crafts':'26dcb9e26fa44e764b56f1667141abae1ea0d37f66ec671b4d2e7a3263aa57e5',
   'routes_of_exchange':'adb774f655ee0442b2ab825a8c634b55b137842b5e137177df7df21a1c46f38c',
   'voices_of_the_atlas':'7e5407195c10e48c213b4588b6fc8c06a1b2862d9c531fb14c0a3277b7d8733c',
   'world_we_build':'f410b503d830631db65c1efdb7ceefe2ad2a27e6c18c6e646ab0807003341ad6'}
  for chapter,digest in hashes.items():
   path=OUT/'chapters'/(chapter+'.snbt')
   self.assertEqual(hashlib.sha256(out[path].encode()).hexdigest(),digest)
   self.assertEqual(out[path],prior[path])
  self.assert_prefix_text_append_only(5,out)
  path=ROOT/'content/campaign_task_ids.json'
  before=json.loads(prior[path]);after=json.loads(out[path])
  self.assertEqual(hashlib.sha256(snbt({key:after[key] for key in before}).encode()).hexdigest(),
                   'fe06d5fabf2770439aaf542286318cf21d67c667056ceb28375ae4d35f34aa9e')
 def test_act_five_activation_authority_and_exact_deliveries(self):
  chapter=self.chapters[5]
  self.assertEqual(chapter['chapter'],'last_horizon')
  self.assertEqual(len(chapter['quests']),27)
  source={q['milestone']:q for q in chapter['quests'] if 'milestone' in q}
  self.assertEqual(set(source),set(chapter['milestones']))
  self.assertEqual(len(source),14)
  projects=json.loads((ROOT/'companion/src/main/resources/data/entrelumen/campaign/projects.json').read_text(encoding='utf-8'))
  designs=json.loads((ROOT/'content/integration-design.json').read_text(encoding='utf-8'))['projects']
  recipes={p['id'].split(':')[1]:p['recipe'] for p in designs if p.get('act')==5}
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
   self.assertEqual(projects[milestone]['act'],5)
   self.assertEqual(recipe['type'],'minecraft:crafting_shapeless')
   self.assertEqual(sum(expected.values()),sum(i['count'] for i in recipe['inputs']))
  self.assertEqual({m:source[m]['deps'] for m in names},
   {'engineering_module':['world_network'],'arcane_module':['world_network'],'nature_module':['world_network'],
    'exploration_module':['world_network','horizon_end_arrival'],
    'logistics_module':['world_network'],'habitation_module':['world_network']})
  self.assertEqual(source['end_arrival']['deps'],[])
  for q in source.values():
   self.assertTrue(all('milestone' in {p['key']:p for c in self.chapters for p in c['quests']}[dep] for dep in q['deps']))
 def test_act_five_activation_mirrors_and_bilingual_instructions(self):
  out=generate_all(self.chapters)
  compiled=json.loads(out[OUT/'chapters/last_horizon.snbt'])
  mapping=json.loads(out[ROOT/'content/campaign_task_ids.json'])
  self.assertEqual(len(compiled['quests']),27)
  # IDs, tasks, dependencies, icons and layout of the Ark chapter did not move with the renumbering.
  self.assertEqual(hashlib.sha256(out[OUT/'chapters/last_horizon.snbt'].encode()).hexdigest(),
                   '6bbf63247e4dde76927f2fbc0ef69a622f0aac3acf08e44d50377b2a7ecd63ec')
  # 42 before 24 September 2026, plus heart_recovered, heliodor_heart and solsticio_arrival.
  self.assertEqual(len(mapping),45)
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
   'horizon_routed':('two additional routing matrices','otras dos matrices de distribución'),
   'horizon_provisioned':('eight more packs of Travel Rations','otras ocho provisiones de viaje'),
   'horizon_charted':('one additional horizon chart','otra carta de horizontes'),
   'horizon_last':('crouch and interact','agachate e interactuá')}
  for key,terms in required.items():
   for locale,term in zip(('en_us','es_es'),terms):self.assertIn(term,source[key][locale][1])
  for q in source.values():
   for locale in ('en_us','es_es'):
    for value in q[locale]:
     self.assertNotIn('\ufffd',value);self.assertNotIn('\u00c3',value)
 # Lore rewrite of 24 September 2026 (docs/design/story-bible.md, docs/design/quest-lore.md).
 def act_text(self,data,locale):
  parts=[data['title'][locale],*data.get('subtitle',{}).get(locale,[])]
  parts+=[labels[locale] for labels in data['layout_groups'].values()]
  for q in data['quests']:parts+=q[locale]
  return '\n'.join(parts)
 def test_act_titles_and_presentation(self):
  # Renumbered 24 September 2026: act V is two chapters (the plan and the activation), VI Solsticio.
  titles=[c['title'] for c in self.chapters[:7]]
  self.assertEqual([(t['en_us'],t['es_es']) for t in titles],
   [('I · A Light Among Ruins','I · Una luz entre ruinas'),('II · The Lost Crafts','II · Los oficios perdidos'),
    ('III · Routes of Exchange','III · Rutas de intercambio'),('IV · Voices of the Atlas','IV · Las voces del Atlas'),
    ('V · The Ark','V · El Arca'),('V · The Activation','V · La activación'),('VI · Solsticio','VI · Solsticio')])
  out=generate_all(self.chapters)
  for data in self.chapters[:7]:
   compiled=json.loads(out[OUT/'chapters'/(data['chapter']+'.snbt')])
   for locale in ('en_us','es_es'):
    self.assertEqual(json.loads(out[OUT/'lang'/(locale+'.snbt')])[f"chapter.{compiled['id']}.chapter_subtitle"],data['subtitle'][locale])
 def acts(self,locale):
  # Acts I-IV are one chapter each, act V joins its two chapters, act VI is Solsticio.
  texts=[self.act_text(c,locale) for c in self.chapters[:4]]
  texts.append(self.act_text(self.chapters[4],locale)+'\n'+self.act_text(self.chapters[5],locale))
  texts.append(self.act_text(self.chapters[6],locale))
  return texts
 def test_acts_tell_the_new_story(self):
  acts={locale:self.acts(locale) for locale in ('en_us','es_es')}
  for locale,texts in acts.items():
   for text in texts:
    self.assertIsNone(re.search(r'\b(Mara|Ivo|Sera)\b',text),'a voice of the old story remains')
    self.assertIn('Heliodor',text)
   # The limbo of light is named only once the Atlas speaks clearly, in act V.
   self.assertTrue(all('Entrelumen' not in text for text in texts[:4]));self.assertIn('Entrelumen',texts[4])
   for name in ('Terra','Juan','Bodhi','Aurelia'):self.assertIn(name,'\n'.join(texts[:4]))
   self.assertIn('Solsticio',texts[5])
  ruins={'en_us':['Atlas Courtyard','Sunken Workshop','Greenhouse Dome','Observatory'],
         'es_es':['Patio del Atlas','Taller hundido','Invernadero-domo','Observatorio']}
  for locale,names in ruins.items():
   for act,name in enumerate(names):self.assertIn(name,acts[locale][act])
  for locale,temple,spirit,heart,key in (('en_us','Temple of the Sacred Light','Sun Spirit','Heart of Heliodor','Light Key'),
                                         ('es_es','Templo de la Luz Sagrada','Espíritu del Sol','Corazón de Heliodor','Llave de Luz')):
   for term in (temple,spirit,heart):self.assertIn(term,acts[locale][3])
   for term in (heart,key):self.assertIn(term,acts[locale][4])
   self.assertIn(key,acts[locale][5])
  # Hints from act I: initials and a golden seal before anyone is named.
  for locale,seal in (('en_us','golden A'),('es_es','A dorada')):
   for hint in ('—T.','—J.','—B.',seal):self.assertIn(hint,acts[locale][0])
 def test_act_four_closes_with_the_heart_and_act_six_opens_with_the_crossing(self):
  projects=json.loads((ROOT/'companion/src/main/resources/data/entrelumen/campaign/projects.json').read_text(encoding='utf-8'))
  self.assertEqual(projects['heliodor_heart'],{'act':4,'requires':['exchange_route','heart_recovered'],
                                                'items':{'entrelumen:heart_of_heliodor':1}})
  self.assertIn('heliodor_heart',projects['atlas_voices']['requires'])
  self.assertEqual(sorted(m for m,p in projects.items() if p['act']==6),[])
  self.assertEqual(sorted(m for m,p in projects.items() if p['act']==5),
   ['arcane_module','engineering_module','exploration_module','habitation_module','logistics_module',
    'nature_module','renewal_engine','resilient_backbone','settlement_supply','world_network'])
  sixth=self.chapters[6]
  self.assertEqual((sixth['chapter'],sixth['milestones'],[q['key'] for q in sixth['quests']]),
                   ('solsticio',['solsticio_arrival'],['solsticio_arrival']))
  entry=sixth['quests'][0]
  self.assertEqual((entry['deps'],entry['milestone'],entry['layout']['shape']),(['horizon_last'],'solsticio_arrival','hexagon'))
  for locale,term in (('en_us','Light Key'),('es_es','Llave de Luz')):self.assertIn(term,entry[locale][1])
 def test_each_quest_says_what_then_why(self):
  for data in self.chapters[:7]:
   for q in data['quests']:
    for locale in ('en_us','es_es'):
     with self.subTest(quest=q['key'],locale=locale):
      what,why=q[locale][1].split('\n\n')
      self.assertTrue(what.strip() and why.strip())
      self.assertLessEqual(len(q[locale][1]),500)
 def test_atlas_interference_clears_after_the_heart(self):
  for data in self.chapters[:4]:
   for locale in ('en_us','es_es'):self.assertRegex(self.act_text(data,locale),'&[km]')
  for locale in ('en_us','es_es'):
   for data in self.chapters[4:7]:self.assertNotIn('&',self.act_text(data,locale))
 def test_formatting_codes_rejected_when_unsafe(self):
  from generate_quests import check_formatting
  check_formatting('A voice: …&kthose&r who… &mhave&r arrived.\n\nPlain.','ok',True)
  for bad in ('stray & sign','&zbad code','ends with &','&mnever reset','&kmuchtoolong&r','&ka&r &kb&r &kc&r','&k&r empty'):
   with self.subTest(text=bad):
    with self.assertRaises(AssertionError):check_formatting(bad,'bad',True)
  with self.assertRaises(AssertionError):check_formatting('&mTitle&r','title',False)
  chapter=copy.deepcopy(self.chapters[0]);chapter['quests'][0]['es_es'][1]+=' &m'
  with self.assertRaisesRegex(AssertionError,'formatting'):generate_all([chapter]+self.chapters[1:])
 def test_project_rewards_named_in_quest_text(self):
  projects=json.loads((ROOT/'companion/src/main/resources/data/entrelumen/campaign/projects.json').read_text(encoding='utf-8'))
  names={locale:json.loads((ROOT/f'companion/src/main/resources/assets/entrelumen/lang/{locale}.json').read_text(encoding='utf-8'))
         for locale in ('en_us','es_es')}
  checked=set()
  for data in self.chapters[:7]:
   for q in data['quests']:
    reward=projects.get(q.get('milestone'),{}).get('reward')
    if not reward:continue
    path=reward.split(':')[1]
    for locale in ('en_us','es_es'):
     name=names[locale].get('item.entrelumen.'+path) or names[locale]['block.entrelumen.'+path]
     with self.subTest(milestone=q['milestone'],locale=locale):self.assertIn(name.lower(),q[locale][1].lower())
    checked.add(path)
  self.assertTrue({'peace_altar','growth_altar','terraform_altar','repose_altar','renewal_altar','time_altar','terra_arm',
                   'engineering_module','arcane_module','nature_module','logistics_module','habitation_module',
                   'exploration_module'}<=checked)
if __name__=='__main__':unittest.main()
