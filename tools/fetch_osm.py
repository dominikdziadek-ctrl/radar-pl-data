#!/usr/bin/env python3
"""Research/batch extraction, not an application backend. Run rarely; obey Overpass limits."""
import urllib.request,urllib.parse,urllib.error,json,pathlib,sys,time,os,tempfile
from match_limits import distance
ROOT=pathlib.Path(__file__).resolve().parents[1];OUT=ROOT/'data';OUT.mkdir(exist_ok=True)
URL=os.getenv('OVERPASS_URL','https://overpass-api.de/api/interpreter');REFRESH='--refresh' in sys.argv

def query(q,name):
 p=OUT/name
 if p.exists() and not REFRESH:return json.load(open(p))
 req=urllib.request.Request(URL,data=urllib.parse.urlencode({'data':q}).encode(),headers={'Accept':'application/json','User-Agent':os.getenv('RADARPL_USER_AGENT','RadarPL/0.4 personal non-commercial updater')})
 error=None
 for attempt in range(3):
  try:
   with urllib.request.urlopen(req,timeout=120) as r:
    if r.status!=200:raise RuntimeError('HTTP '+str(r.status))
    raw=r.read(15_000_001)
   if len(raw)>15_000_000:raise RuntimeError('Overpass response exceeds safety limit')
   d=json.loads(raw)
   if 'remark' in d:raise RuntimeError(d['remark'])
   if not d.get('osm3s',{}).get('timestamp_osm_base') or not isinstance(d.get('elements'),list):raise RuntimeError('Incomplete Overpass response')
   with tempfile.NamedTemporaryFile('w',encoding='utf-8',dir=OUT,delete=False) as f:json.dump(d,f,ensure_ascii=False);tmp=pathlib.Path(f.name)
   tmp.replace(p);print(name,len(d.get('elements',[])),flush=True);return d
  except (urllib.error.URLError,TimeoutError,RuntimeError,json.JSONDecodeError) as exc:
   error=exc
   if attempt<2:time.sleep(30)
 raise RuntimeError('Overpass failed after 3 attempts: '+str(error))

if __name__=='__main__':
 cs=json.load(open(ROOT/'app/src/main/assets/canard.json'))['cameras']
 nodes=query('[out:json][timeout:40];node["highway"="speed_camera"](48.9,14,55,24.2);out body;','camera-nodes.json')['elements']
 query('[out:json][timeout:60];rel["enforcement"="average_speed"](48.9,14,55,24.2);out body geom;>;out body geom;','opp-relations.json')
 ids=set()
 for c in cs:
  if c['section']:continue
  near=sorted(((distance((c['lat'],c['lon']),(n['lat'],n['lon'])),n['id']) for n in nodes))
  if near and near[0][0]<=25:ids.add(near[0][1])
 prefix='[out:json][timeout:60];node(id:'+','.join(map(str,sorted(ids)))+')->.cams;'
 query(prefix+'way(bn.cams)["highway"];out body geom;','camera-roads.json')
 query(prefix+'node(around.cams:40)->.near;way(bn.near)["highway"];out body geom;','nearby-roads.json')
 print('Next: python tools/assemble_evidence.py && python tools/match_limits.py')
