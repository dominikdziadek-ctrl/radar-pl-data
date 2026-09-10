import unittest,sys,pathlib
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/'tools'))
from match_limits import speed,route_speed,on_line
class MatchTests(unittest.TestCase):
 def test_speed(self):
  self.assertEqual(speed({'maxspeed':'50'}),50)
  self.assertEqual(speed({'maxspeed':'90','maxspeed:hgv':'70'}),90)
  self.assertEqual(speed({'maxspeed':'50','maxspeed:conditional':'30 @ wet'}),0)
  self.assertEqual(speed({'maxspeed':'50','maxspeed:forward':'70'}),0)
  self.assertEqual(speed({'maxspeed':'50','maxspeed:forward':'70'},'forward'),70)
  self.assertEqual(speed({'maxspeed':'PL:urban'}),0)
  self.assertEqual(speed({'maxspeed':'signals'}),0)
  self.assertEqual(speed({'maxspeed':'50','maxspeed:variable':'yes'}),0)
  self.assertEqual(speed({'maxspeed':'160'}),0)
  self.assertEqual(speed({'maxspeed':'50','access':'private'}),0)
 def fixture(self):
  nodes={1:dict(id=1,lat=50.,lon=19.),2:dict(id=2,lat=50.01,lon=19.),3:dict(id=3,lat=50.02,lon=19.)}
  ways={10:dict(id=10,tags={'maxspeed':'70','highway':'primary'},nodes=[1,2],geometry=[nodes[1],nodes[2]]),11:dict(id=11,tags={'maxspeed':'70','highway':'primary'},nodes=[2,3],geometry=[nodes[2],nodes[3]])}
  rel=dict(id=1,tags={'enforcement':'average_speed'},members=[dict(type='node',role='from',ref=1),dict(type='node',role='to',ref=3),dict(type='way',role='section',ref=10),dict(type='way',role='section',ref=11)])
  return rel,ways,nodes
 def calc(self,r,w,n):return route_speed(r,w,n,(50,19),(50.02,19))
 def test_uniform(self):
  r,w,n=self.fixture();self.assertEqual(self.calc(r,w,n)['speed'],70)
 def test_no_reverse_assumption(self):
  r,w,n=self.fixture();self.assertIsNone(route_speed(r,w,n,(50.02,19),(50,19)))
 def test_changed_speed(self):
  r,w,n=self.fixture();w[11]['tags']['maxspeed']='50';self.assertIsNone(self.calc(r,w,n))
 def test_missing_way(self):
  r,w,n=self.fixture();del w[11];self.assertIsNone(self.calc(r,w,n))
 def test_missing_limit(self):
  r,w,n=self.fixture();w[11]['tags'].pop('maxspeed');self.assertIsNone(self.calc(r,w,n))
 def test_unmatched_end(self):
  r,w,n=self.fixture();self.assertIsNone(route_speed(r,w,n,(50,19),(50.025,19)))
 def test_direction_violation(self):
  r,w,n=self.fixture();w[11]['tags']['oneway']='-1';self.assertIsNone(self.calc(r,w,n))
 def test_conditional_segment(self):
  r,w,n=self.fixture();w[11]['tags']['maxspeed:conditional']='50 @ wet';self.assertIsNone(self.calc(r,w,n))
 def test_conflicting_relation(self):
  r,w,n=self.fixture();r['tags']['maxspeed']='80';self.assertIsNone(self.calc(r,w,n))
 def test_extra_disconnected_segment(self):
  r,w,n=self.fixture();w[12]=dict(id=12,tags={'maxspeed':'70'},nodes=[4,5],geometry=[dict(lat=51,lon=19),dict(lat=51.01,lon=19)]);r['members'].append(dict(type='way',role='section',ref=12));self.assertIsNone(self.calc(r,w,n))
 def test_lateral_distance(self):
  self.assertGreater(on_line((50.005,19.003),[dict(lat=50,lon=19),dict(lat=50.01,lon=19)]),200)
if __name__=='__main__':unittest.main()
