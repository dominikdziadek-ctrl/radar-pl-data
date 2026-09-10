#!/usr/bin/env python3
"""Extract minimal source evidence from a Poland Geofabrik OSM PBF; no server required."""
import osmium,json,sys,pathlib,math,time
ROOT=pathlib.Path(__file__).resolve().parents[1];OUT=ROOT/'data';OUT.mkdir(exist_ok=True)
cs=json.load(open(ROOT/'app/src/main/assets/canard.json'))['cameras'];pp=[c for c in cs if not c['section']]
# Spatial cell index of CANARD fixed-camera neighborhoods.
grid={}
for c in pp:
 y,x=c['lat'],c['lon']
 for i in range(math.floor((y-.002)*100),math.floor((y+.002)*100)+1):
  for j in range(math.floor((x-.003)*100),math.floor((x+.003)*100)+1):grid.setdefault((i,j),[]).append(c)
class Relations(osmium.SimpleHandler):
 def __init__(self):super().__init__();self.rels=[];self.ways=set()
 def relation(self,r):
  if r.tags.get('enforcement')=='average_speed':
   members=[dict(type={'w':'way','n':'node','r':'relation'}[m.type],ref=m.ref,role=m.role) for m in r.members]
   self.rels.append(dict(type='relation',id=r.id,tags=dict(r.tags),members=members));self.ways.update(m.ref for m in r.members if m.type=='w')
def point_distance(c,geom):
 lat=c['lat'];lon=c['lon'];factor=math.cos(math.radians(lat));best=1e20
 for p,q in zip(geom,geom[1:]):
  ax=(p['lon']-lon)*111195*factor;ay=(p['lat']-lat)*111195;bx=(q['lon']-lon)*111195*factor;by=(q['lat']-lat)*111195
  dx=bx-ax;dy=by-ay;t=max(0,min(1,-(ax*dx+ay*dy)/(dx*dx+dy*dy))) if dx*dx+dy*dy else 0
  best=min(best,math.hypot(ax+t*dx,ay+t*dy))
 return best
class Ways(osmium.SimpleHandler):
 def __init__(self,rels):super().__init__();self.needed=rels.ways;self.nodeids={m['ref'] for r in rels.rels for m in r['members'] if m['type']=='node'};self.roads={};self.nodes={};self.cameras=[];self.count=0
 def node(self,n):
  if n.id in self.nodeids:self.nodes[n.id]=dict(type='node',id=n.id,lat=n.location.lat,lon=n.location.lon,tags=dict(n.tags))
  if n.tags.get('highway')=='speed_camera':self.cameras.append(dict(type='node',id=n.id,lat=n.location.lat,lon=n.location.lon,tags=dict(n.tags)))
 def way(self,w):
  road=w.tags.get('highway','');needed=w.id in self.needed
  if not needed and road not in ['motorway','trunk','primary','secondary','tertiary','unclassified','residential','living_street','service','motorway_link','trunk_link','primary_link','secondary_link','tertiary_link']:return
  try:geom=[dict(lat=n.lat,lon=n.lon) for n in w.nodes]
  except osmium.InvalidLocationError:return
  if len(geom)<2:return
  if not needed:
   miny=min(p['lat'] for p in geom);maxy=max(p['lat'] for p in geom);minx=min(p['lon'] for p in geom);maxx=max(p['lon'] for p in geom)
   candidates={}
   # Avoid huge loops for unusually long ways.
   if (maxy-miny)*(maxx-minx)>.3:
    candidates={c['id']:c for c in pp if miny-.002<=c['lat']<=maxy+.002 and minx-.003<=c['lon']<=maxx+.003}
   else:
    for i in range(math.floor(miny*100),math.floor(maxy*100)+1):
     for j in range(math.floor(minx*100),math.floor(maxx*100)+1):
      for c in grid.get((i,j),[]):candidates[c['id']]=c
   if not candidates or not any(point_distance(c,geom)<=85 for c in candidates.values()):return
  self.roads[w.id]=dict(type='way',id=w.id,tags=dict(w.tags),nodes=[n.ref for n in w.nodes],geometry=geom)
  self.count+=1
  if self.count%1000==0:print('retained',self.count,flush=True)
if __name__=='__main__':
 pbf=sys.argv[1];start=time.time();r=Relations();r.apply_file(pbf);print('OPP relations',len(r.rels),'ways',len(r.ways),flush=True)
 w=Ways(r);w.apply_file(pbf,locations=True,idx='flex_mem');print('EXTRACTED',len(w.roads),len(w.nodes),len(w.cameras),'seconds',round(time.time()-start),flush=True)
 hdr=osmium.io.Reader(pbf).header();date=hdr.get('osmosis_replication_timestamp')
 raw=dict(source='https://download.geofabrik.de/europe/poland.html',timestamp=date,elements=r.rels+list(w.roads.values())+list(w.nodes.values())+w.cameras)
 (OUT/'osm-evidence.json').write_text(json.dumps(raw,ensure_ascii=False,separators=(',',':')))
