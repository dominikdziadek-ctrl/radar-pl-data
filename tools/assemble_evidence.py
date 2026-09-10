import json,pathlib
root=pathlib.Path(__file__).resolve().parents[1];d=root/'data';elements={};dates=[]
for name in ['opp-relations.json','camera-nodes.json','camera-roads.json','nearby-roads.json']:
 p=d/name
 if not p.exists():continue
 raw=json.load(open(p));dates.append(raw['osm3s']['timestamp_osm_base'])
 for e in raw['elements']:elements[(e['type'],e['id'])]=e
out={'source':'https://overpass-api.de/api/interpreter','timestamp':min(dates),'elements':list(elements.values())}
(d/'osm-evidence.json').write_text(json.dumps(out,ensure_ascii=False,separators=(',',':')))
print('Evidence:',len(elements),'elements')
