"""Focused multi-chapter contracts; no gameplay claims."""
import copy,hashlib,json,unittest
from pathlib import Path
from generate_quests import ROOT,OUT,generate_all
class ChapterContracts(unittest.TestCase):
 def setUp(self):
  self.chapters=[json.loads((ROOT/'content'/n).read_text(encoding='utf-8')) for n in ('first_hour.json','act_two.json','act_three.json')]
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
    self.assertEqual([quests[dep]['milestone'] for dep in q['deps']],projects[q['milestone']].get('requires',[]))
if __name__=='__main__':unittest.main()
