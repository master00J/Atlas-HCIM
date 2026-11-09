#!/usr/bin/env python3
"""Regression checks for Guide Overlay 2.0 import shape and exact step key hashing."""
import csv,json,re,unittest
from pathlib import Path
from build_atlas import ROOT,GUIDE_ID,ENDGAME_ID,PLACES,javahash,candidate_pin,steps_in
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
  data=json.loads((ROOT/'guide'/'HCIM_ATLAS_VOLLEDIGE_ROUTE.json').read_text('utf8'))
  self.assertEqual(sum(len(sec['steps']) for ch in data['chapters'] for sec in ch['sections']),3225)
if __name__=='__main__':unittest.main(verbosity=2)
