#!/usr/bin/env python3
"""Regression checks for Guide Overlay 2.0 import shape and exact step key hashing."""
import csv,json,re,unittest
from pathlib import Path
from build_atlas import ROOT,GUIDE_ID,ENDGAME_ID,PLACES,javahash,candidate_pin,steps_in
import route_2026
def texts(section):
 return [''.join(r.get('text','') for r in st['content']) for st in section['steps']]
def steps(guide):
 return sum(len(sec['steps']) for ch in guide['chapters'] for sec in ch['sections'])
def section(guide,chapter_prefix,title):
 found=[s for c in guide['chapters'] if c['title'].startswith(chapter_prefix) for s in c['sections'] if s['title']==title]
 assert len(found)==1,(chapter_prefix,title,len(found))
 return texts(found[0])
def all_texts(guide,chapter_prefix=''):
 return [t for c in guide['chapters'] if c['title'].startswith(chapter_prefix) for s in c['sections'] for t in texts(s)]
class AtlasTests(unittest.TestCase):
 def test_java_hashcode_example(self):
  self.assertEqual(javahash('abc'),'17862') # java: 97*31^2+98*31+99=96354=0x17862
 def test_guides_are_readable(self):
  for path in ['HCIM_ATLAS_VOLLEDIGE_ROUTE.json','HCIM_ATLAS_ENDGAME.json']:
   guide=json.loads((ROOT/'guide'/path).read_text('utf8'))
   self.assertTrue(guide['chapters']);self.assertTrue(guide['chapters'][0]['sections'])
 def test_no_pins_for_npcs_or_composites(self):
  for txt in ['Head south and start the Waterfall Quest by talking to Almera',
              'Bank at West bank and withdraw Food for Melzar\'s Maze',
              'Head north to Fishing Guild to avoid Moss Giants']:
   pin,_=candidate_pin(txt)
   self.assertIsNone(pin,txt)
 def test_curated_basic_routes(self):
  for txt,expected in [
   ('Head to the Wizards Tower',"Wizards' Tower"),
   ('Bank at Draynor and Deposit all','Draynor Village Bank'),
   ('Bank at Lumbridge castle top floor and deposit all','Lumbridge Castle bank (2e verdieping)'),
   ('Go to Varrock East Bank','Varrock East Bank'),
   ('Take Ship to Karamja','Karamja')]:
   p,reason=candidate_pin(txt);self.assertIsNotNone(p,txt);self.assertEqual(p['name'],expected)
 def test_pins_match_valid_guide_keys(self):
  for suffix,gid in [('VOLLEDIGE_ROUTE',GUIDE_ID),('ENDGAME',ENDGAME_ID)]:
   jsonfilename='HCIM_ATLAS_VOLLEDIGE_ROUTE.json' if gid==GUIDE_ID else 'HCIM_ATLAS_ENDGAME.json'
   data=json.loads((ROOT/'guide'/jsonfilename).read_text('utf8'))
   all_keys={key for _,_,_,_,_,key in steps_in(data)}
   pins=json.loads((ROOT/'guide'/f'HCIM_ATLAS_PINNEN_{suffix}.json').read_text('utf8'))
   self.assertEqual(pins['schemaVersion'],1)
   self.assertEqual(set(pins['guides']),{gid})
   self.assertLess((ROOT/'guide'/f'HCIM_ATLAS_PINNEN_{suffix}.json').stat().st_size,128*1024)
   self.assertTrue(set(pins['guides'][gid]).issubset(all_keys))
   for key,p in pins['guides'][gid].items():
    self.assertRegex(key,r'^C\d+\.S\d+#[0-9a-f]+#\d+$')
    self.assertEqual(len(p['waypoints']),1)
    point=p['waypoints'][0]
    self.assertGreater(point['x'],0);self.assertGreater(point['y'],0)
    self.assertIn(point['plane'],range(0,4))
    self.assertEqual(point['radius'],5)
 def test_no_pins_for_nonmovement_bank_note(self):
  p,_=candidate_pin('Keep the Shrimps in your bank for later [Family Crest Quest]')
  self.assertIsNone(p)
 def test_step_count_never_lost(self):
  for src,out in [('OSRS_HCIM_GuideOverlay_VOLLEDIGE_ROUTE_2026.json','HCIM_ATLAS_VOLLEDIGE_ROUTE.json'),
                  ('OSRS_HCIM_GuideOverlay_ENDGAME_2026.json','HCIM_ATLAS_ENDGAME.json')]:
   source=json.loads((ROOT/'guide'/src).read_text('utf8'))
   built=json.loads((ROOT/'guide'/out).read_text('utf8'))
   self.assertEqual(source['chapters'],built['chapters'],out)
  self.assertGreater(steps(json.loads((ROOT/'guide'/'HCIM_ATLAS_VOLLEDIGE_ROUTE.json').read_text('utf8'))),3200)
 def test_2026_source_is_reproducible(self):
  full,endgame=route_2026.build(write=False)
  self.assertEqual(full,json.loads((ROOT/'guide'/'OSRS_HCIM_GuideOverlay_VOLLEDIGE_ROUTE_2026.json').read_text('utf8')))
  self.assertEqual(endgame,json.loads((ROOT/'guide'/'OSRS_HCIM_GuideOverlay_ENDGAME_2026.json').read_text('utf8')))
class Route2026Tests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.route=json.loads((ROOT/'guide'/'HCIM_ATLAS_VOLLEDIGE_ROUTE.json').read_text('utf8'))
 def first(self,lines,needle):
  hits=[i for i,t in enumerate(lines) if needle in t]
  self.assertTrue(hits,needle);return hits[0]
 def test_context_notes_do_not_block_starting_out(self):
  start=section(self.route,'Episode 1 ','Starting Out')
  self.assertEqual(start[0],'All Settings -> Disable level-up')
  self.assertFalse(any(t.startswith('Route note — 2026 update:') for t in start))
  # Safety warnings deliberately remain manual checkpoints in the HCIM route.
  self.assertTrue(any('Stronghold of Security is NOT a safe death' in t for t in section(self.route,'Episode 1 ','Bank 2')))
  self.assertFalse(any('Varrock circuit with many quest starts' in t for t in section(self.route,'Episode 1 ','Bank 3')))
  # Bank 1 must not ask for the leather gloves/boots before they are picked up.
  bank1=section(self.route,'Episode 1 ','Bank 1')
  self.assertFalse([t for t in bank1[:self.first(bank1,'Take Leather Gloves')] if re.search(r'leather gloves|leather boots',t,re.I)])
 def test_brutus_warning_precedes_fight(self):
  bank=section(self.route,'Episode 4 ','Bank 130')
  self.assertLess(self.first(bank,"HCIM: Brutus's special attacks"),
                  self.first(bank,'Open the pen in the north-east corner of the cow farm and fight the bull'))
  self.assertLess(self.first(bank,'Open the pen in the north-east corner of the cow farm and fight the bull'),
                  self.first(bank,'Speak to Cassius to complete the Ides of Milk'))
 def test_starting_out_uses_mudfoot_instead_of_tutors(self):
  start=section(self.route,'Episode 1 ','Starting Out')
  self.assertFalse([t for t in all_texts(self.route) if re.search(r'combat tutor',t,re.I) and not t.startswith('Route note')])
  order=[self.first(start,n) for n in ["start Cook's Assistant","start Mudfoot's Big Adventure",'start The Ides of Milk',
         "complete Mudfoot's Big Adventure","complete Cook's Assistant",'start Rune Mysteries','give him the Milk sample']]
  self.assertEqual(order,sorted(order))
 def test_moved_quests_appear_once_in_their_new_bank(self):
  for chapter,bank,needle in [('Episode 2 ','Bank 28',"Complete The Writing's on the Wall"),
                              ('Episode 2 ','Bank 29',"Complete Twilight's Promise"),
                              ('Episode 2 ','Bank 60B','Complete Tower of Life'),
                              ('Episode 1 ','Bank 9','Complete Pandemonium')]:
   self.assertTrue([t for t in section(self.route,chapter,bank) if t.startswith(needle)],(bank,needle))
   self.assertEqual(len([t for t in all_texts(self.route,'Episode') if t.startswith(needle)]),1,needle)
  self.assertFalse([t for t in all_texts(self.route,'Episode 6 ') if 'Twilight' in t and t.startswith('Complete')])
 def test_writings_on_the_wall_before_fruit_stalls(self):
  bank28=section(self.route,'Episode 2 ','Bank 28')
  self.assertLess(self.first(bank28,"Complete The Writing's on the Wall"),self.first(bank28,'until 31,512 Thieving Experience'))
  self.assertFalse([t for t in all_texts(self.route) if '34512' in t])
 def test_lower_grind_targets(self):
  self.assertTrue([t for t in section(self.route,'Episode 2 ','Bank 53') if 'until 97,833 Mining XP' in t])
 def test_ides_of_milk_talk_steps_moved_to_starting_out(self):
  for chapter,bank in [('Episode 2 ','Bank 26'),('Episode 2 ','Bank 52'),('Episode 3 ','Bank 81')]:
   self.assertFalse([t for t in section(self.route,chapter,bank) if re.search(r'Cassius|Gillie Groats|milk sample',t,re.I)],bank)
  self.assertTrue([t for t in section(self.route,'Episode 4 ','Bank 130') if t.startswith('Speak to Cassius to complete')])
 def test_sailing_blocks_and_new_quests(self):
  self.assertTrue(section(self.route,'Episode 3 ','Sailing A — 1 to 30 Sailing (optional)'))
  self.assertTrue(section(self.route,'Episode 10 ','Sailing C — 52 to 62 Sailing and Fallen From Grace'))
  bank176=section(self.route,'Episode 6 ','Bank 176')
  self.assertLess(self.first(bank176,'Complete Troubled Tortugans'),self.first(bank176,'Complete The Red Reef'))
 def test_count_check_dialogue_numbers_removed(self):
  self.assertFalse([t for t in all_texts(self.route) if 'Count Check' in t and re.search(r'\(\d+(?:,\d+)*\)',t)])
 def test_route_is_english(self):
  dutch=re.compile(r'\b(?:Routeadvies|LET OP|voor|zodat|wanneer|jouw|niet|bewaar|eerst|alleen|gevaarlijk|veilige?|hoofdstuk)\b',re.I)
  for guide in [self.route,json.loads((ROOT/'guide'/'HCIM_ATLAS_ENDGAME.json').read_text('utf8'))]:
   for c in guide['chapters']:
    self.assertIsNone(dutch.search(c['title']),c['title'])
    for s in c['sections']:
     self.assertIsNone(dutch.search(s['title']),s['title'])
     for t in texts(s):self.assertIsNone(dutch.search(t),t)
 def test_new_talk_steps_keep_the_npc_name_clean(self):
  # Guide Overlay drops the NPC target when "by" or "just" follows the name.
  base=set(all_texts(json.loads((ROOT/'guide'/'source'/'B0aty_HCIM_Guide_V3_base.json').read_text('utf8'))))
  bad=[t for t in all_texts(self.route) if t not in base and re.search(r"\b[Tt]alk to (?:the )?(?:[A-Z][\w'’]*\s)+(?:by|just)\b",t)]
  self.assertFalse(bad)
 def test_new_steps_fit_guide_overlay_splitter(self):
  base=json.loads((ROOT/'guide'/'source'/'B0aty_HCIM_Guide_V3_base.json').read_text('utf8'))
  old=set(all_texts(base))
  new=[t for t in all_texts(self.route) if t not in old]
  self.assertGreater(len(new),100)
  for t in new:self.assertLessEqual(len(t),300,t)
class NpcPinTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.route=json.loads((ROOT/'guide'/'HCIM_ATLAS_VOLLEDIGE_ROUTE.json').read_text('utf8'))
  cls.pins=json.loads((ROOT/'guide'/'HCIM_ATLAS_PINNEN_VOLLEDIGE_ROUTE.json').read_text('utf8'))['guides'][GUIDE_ID]
  cls.by_text={}
  for ch,sec,idx,step,t,key in steps_in(cls.route):cls.by_text.setdefault((sec['title'],t),key)
 def pin(self,section,start):
  keys=[k for (s,t),k in self.by_text.items() if s==section and t.startswith(start)]
  self.assertEqual(len(keys),1,(section,start))
  self.assertIn(keys[0],self.pins,(section,start))
  w=self.pins[keys[0]]['waypoints'][0];return (w['x'],w['y'],w['plane'])
 def test_new_npcs_get_quest_helper_tiles(self):
  self.assertEqual(self.pin('Starting Out','Talk to Cassius at the pond'),(3171,3277,0))
  self.assertEqual(self.pin('Bank 29','Talk to Ennius Tullus'),(1687,3141,0))
  self.assertEqual(self.pin('Sailing C — 52 to 62 Sailing and Fallen From Grace','Sail to Wyrmscraig'),(2576,2255,0))
 def test_shared_names_point_at_the_right_npc(self):
  # "Betty" is also the Port Sarim rune seller and "Veos" also stands in Port Sarim.
  x,y,_=self.pin('Bank 28',"Run south-west to Land's End and talk to Betty")
  self.assertLess(x,1600)
  self.assertEqual(self.pin('Bank 2','Talk to Veos (4,1)'),(1824,3690,0))
  self.assertEqual(self.pin('Bank 130','Talk to the Cook in Blue moon Inn'),(3230,3401,0))
 def test_verified_pins_are_sourced_and_never_in_the_wilderness(self):
  verified=json.loads((ROOT/'guide'/'source'/'verified_npc_pins.json').read_text('utf8'))['pins']
  self.assertGreater(len(verified),40)
  for p in verified:
   self.assertTrue(p['source'].startswith('Quest Helper'),p)
   self.assertNotRegex(p['text'],r'(?i)wilderness')
   # Wilderness is north of y=3520 on the surface between x=2944 and x=3392.
   self.assertFalse(p['plane']==0 and 2944<=p['x']<=3392 and 3520<p['y']<4000,p)
 def test_pin_file_fits_the_plugin_limit(self):
  self.assertLess((ROOT/'guide'/'HCIM_ATLAS_PINNEN_VOLLEDIGE_ROUTE.json').stat().st_size,120000)
if __name__=='__main__':unittest.main(verbosity=2)
