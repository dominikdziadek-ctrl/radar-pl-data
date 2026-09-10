#!/usr/bin/env python3
"""Conservative CANARD→OSM join. Output licensed as derived database ODbL 1.0."""
import json,pathlib,math,collections,sys,re
ROOT=pathlib.Path(__file__).resolve().parents[1]
def distance(a,b):
 lat=(a[0]+b[0])/2;return math.hypot((a[0]-b[0])*111195,(a[1]-b[1])*111195*math.cos(math.radians(lat)))
def on_line(c,geom):
 lat,lon=c;factor=math.cos(math.radians(lat));best=1e30
 for p,q in zip(geom,geom[1:]):
  ax=(p['lon']-lon)*111195*factor;ay=(p['lat']-lat)*111195;bx=(q['lon']-lon)*111195*factor;by=(q['lat']-lat)*111195
  dx,dy=bx-ax,by-ay;den=dx*dx+dy*dy;t=max(0,min(1,-(ax*dx+ay*dy)/den)) if den else 0
  best=min(best,math.hypot(ax+t*dx,ay+t*dy))
 return best
def speed(tags,direction=None):
 # No inference from road class or legal defaults, no evaluation of conditional tags.
 if any(k.startswith('maxspeed') and 'conditional' in k and not any(v in k for v in [':hgv',':bus',':bicycle',':trailer']) for k in tags):return 0
 if tags.get('maxspeed:variable') not in (None,'no'):return 0
 if tags.get('access') in ('no','private') or tags.get('motor_vehicle')=='no' or tags.get('motorcar')=='no':return 0
 def num(v):
  if not v:return 0
  m=re.fullmatch(r'\s*(\d{1,3})(?:\s*km/h)?\s*',v)
  n=int(m[1]) if m else 0
  return n if 10<=n<=140 else 0
 base=tags.get('maxspeed:motorcar',tags.get('maxspeed'))
 if direction:
  value=tags.get('maxspeed:motorcar:'+direction,tags.get('maxspeed:'+direction,base));return num(value)
 f=num(tags.get('maxspeed:motorcar:forward',tags.get('maxspeed:forward',base)))
 b=num(tags.get('maxspeed:motorcar:backward',tags.get('maxspeed:backward',base)))
 return f if f==b else 0

def route_speed(rel,ways,nodes,start,end):
 """Require exact from/to, one connected unbranched path, and complete way-level speed coverage."""
 members=rel.get('members',[]);fr=[nodes.get(m['ref']) for m in members if m['type']=='node' and m['role']=='from'];to=[nodes.get(m['ref']) for m in members if m['type']=='node' and m['role']=='to']
 if len(fr)!=1 or len(to)!=1 or not fr[0] or not to[0]:return None
 f,t=fr[0],to[0]
 if distance(start,(f['lat'],f['lon']))>90 or distance(end,(t['lat'],t['lon']))>90:return None
 ids=[m['ref'] for m in members if m['type']=='way' and m['role']=='section']
 if not ids or any(i not in ways for i in ids):return None
 adj=collections.defaultdict(list);edges={}
 for wid in set(ids):
  w=ways[wid];ns=w.get('nodes',[]);geom=w.get('geometry',[])
  if len(ns)!=len(geom) or len(ns)<2:return None
  for i,(a,b) in enumerate(zip(ns,ns[1:])):
   if a==b:return None
   edge=(wid,i);edges[edge]=(a,b,w,geom[i],geom[i+1]);adj[a].append((b,edge,True));adj[b].append((a,edge,False))
 if f['id'] not in adj or t['id'] not in adj or any(len(v)>2 for v in adj.values()):return None
 current=f['id'];target=t['id'];used=set();values=[];length=0;path=[]
 while current!=target:
  opts=[v for v in adj[current] if v[1] not in used]
  if len(opts)!=1:return None
  nxt,edge,forward=opts[0];used.add(edge);a,b,w,p,q=edges[edge]
  if w['tags'].get('oneway')=='yes' and not forward:return None
  if w['tags'].get('oneway')=='-1' and forward:return None
  value=speed(w['tags'],'forward' if forward else 'backward')
  if not value:return None
  values.append(value);length+=distance((p['lat'],p['lon']),(q['lat'],q['lon']));path.append(edge);current=nxt
  if len(used)>len(edges):return None
 if len(used)!=len(edges) or len(set(values))!=1 or not values:return None
 # Relation's own explicit speed must agree too, when present.
 if any(k.startswith('maxspeed') for k in rel.get('tags',{})) and speed(rel['tags']) not in (values[0],):return None
 if length<distance(start,end)*.95 or length>distance(start,end)*2.5:return None
 return dict(speed=values[0],relation=rel['id'],ways=sorted(set(ids)),length=round(length))

def generate():
 raw=json.load(open(ROOT/'data/osm-evidence.json'));es=raw['elements'];ways={e['id']:e for e in es if e['type']=='way'};nodes={e['id']:e for e in es if e['type']=='node'};rels=[e for e in es if e['type']=='relation'];cs=json.load(open(ROOT/'app/src/main/assets/canard.json'))['cameras'];entries=[];stats=collections.Counter()
 # Build bounding boxes once, with ~100m allowance.
 boxes={i:(min(g['lat'] for g in w['geometry'])-.001,max(g['lat'] for g in w['geometry'])+.001,min(g['lon'] for g in w['geometry'])-.002,max(g['lon'] for g in w['geometry'])+.002) for i,w in ways.items() if w.get('geometry')}
 for c in cs:
  a=(c['lat'],c['lon']);out={k:v for k,v in c.items()};out.update(forward=0,backward=0,reason='Brak jednoznacznych danych OSM',evidence=[])
  if not c['section']:
   near=sorted([(on_line(a,ways[i]['geometry']),i) for i,box in boxes.items() if box[0]<=a[0]<=box[1] and box[2]<=a[1]<=box[3] and ways[i].get('tags',{}).get('highway') in {'motorway','trunk','primary','secondary','tertiary','unclassified','residential','living_street','service','motorway_link','trunk_link','primary_link','secondary_link','tertiary_link'}],key=lambda x:x[0]);near=[x for x in near if x[0]<=85]
   if near and near[0][0]<=25:
    relevant=[x for x in near if x[0]<=max(25,near[0][0]+12)];values={speed(ways[i]['tags']) for d,i in relevant}
    if len(values)==1 and 0 not in values:
     out['forward']=out['backward']=values.pop();out['evidence']=[dict(way=i,distance=round(d,1)) for d,i in relevant];out['reason']='Zgodny limit na drogach przy punkcie; bez warunków';stats['fixed_known']+=1
    else:stats['fixed_ambiguous_or_missing']+=1
   else:stats['fixed_no_close_road']+=1
   # A speed_camera node is direct evidence of the limit at the device, even when
   # the mapper placed it beside (rather than as part of) the roadway.
   cams=sorted([(distance(a,(n['lat'],n['lon'])),n) for n in nodes.values() if n.get('tags',{}).get('highway')=='speed_camera'],key=lambda x:x[0])
   if cams and cams[0][0]<=20:
    close=[(d,n) for d,n in cams if d<=max(20,cams[0][0]+5)]
    vals={speed(n.get('tags',{})) for d,n in close}
    if len(vals)==1 and 0 not in vals:
     val=next(iter(vals))
     if out['forward'] and out['forward']!=val:
      stats['fixed_known']-=1;out['forward']=out['backward']=0;out['reason']='Sprzeczny limit na kamerze i drodze OSM';stats['fixed_conflict']+=1
     elif not out['forward']:
      out['forward']=out['backward']=val;out['reason']='Limit zapisany bezpośrednio na zgodnym punkcie fotoradaru OSM';stats['fixed_node_known']+=1
     out['evidence'].extend(dict(node=n['id'],distance=round(d,1)) for d,n in close)
    elif any('maxspeed' in n.get('tags',{}) for d,n in close):
     if out['forward']:stats['fixed_known']-=1
     out['forward']=out['backward']=0;out['reason']='Niejednoznaczny lub warunkowy limit na kamerze OSM'
  else:
   b=(c['endLat'],c['endLon'])
   for key,start,end in [('forward',a,b),('backward',b,a)]:
    candidates=[r for rel in rels if (r:=route_speed(rel,ways,nodes,start,end))]
    if candidates and len({r['speed'] for r in candidates})==1:
     out[key]=candidates[0]['speed'];out['evidence'].extend(dict(direction=key,**r) for r in candidates);stats['opp_directions_known']+=1
   if out['forward'] or out['backward']:out['reason']='Pełna połączona geometria OPP; jednakowy limit na całej trasie';stats['opp_records_known']+=1
   else:stats['opp_unknown']+=1
  entries.append(out)
 pack=dict(schema=1,date=raw['timestamp'],profile='motorcar-no-trailer',source='OpenStreetMap contributors / Overpass',license='ODbL-1.0',url='https://www.openstreetmap.org/copyright',entries=entries)
 (ROOT/'app/src/main/assets/osm-limits.json').write_text(json.dumps(pack,ensure_ascii=False,separators=(',',':')))
 report=dict(timestamp=raw['timestamp'],stats=dict(stats),fixedKnown=sum(not e['section'] and e['forward']>0 for e in entries),oppKnown=sum(e['section'] and (e['forward']>0 or e['backward']>0) for e in entries),total=len(entries),note='Computed matches, not field-verified signs; absence of a limit does not imply absence of restriction.')
 (ROOT/'data/matching-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':generate()
