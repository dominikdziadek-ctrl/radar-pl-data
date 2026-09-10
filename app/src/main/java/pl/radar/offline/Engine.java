package pl.radar.offline;
import java.util.*;

/** Platform-independent calculations; coordinates in degrees, speed m/s, time monotonic ms. */
public final class Engine {
 public static final class Camera {
  public final String id; public final double lat,lon,endLat,endLon; public final boolean section; public int limitForward=0,limitBackward=0; public String limitDate="",limitReason="Brak danych OSM";
  public Camera(String id,double lat,double lon){this(id,lat,lon,Double.NaN,Double.NaN);}
  public Camera(String id,double lat,double lon,double a,double b){this.id=id;this.lat=lat;this.lon=lon;endLat=a;endLon=b;section=Double.isFinite(a)&&Double.isFinite(b);}
 }
 public static final class Fix {
  public final double lat,lon,speed,accuracy; public final long time; public final boolean speedValid;
  public Fix(double a,double b,double v,double acc,long t){this(a,b,v,acc,t,true);}
  public Fix(double a,double b,double v,double acc,long t,boolean hasSpeed){lat=a;lon=b;speed=v;accuracy=acc;time=t;speedValid=hasSpeed;}
 }
 public static final class Result {
  public String status="Szukam pomiarów",event=""; public Camera nearest; public double distance=Double.POSITIVE_INFINITY;
  public boolean averageActive,averageComplete,averageUncertain; public double averageKmh=Double.NaN,oppMeters=0;public long oppElapsedMs=0;
  public boolean active,uncertain,reverse; public int limit=0; public String limitDate="",limitReason="Brak jednoznacznych danych OSM";
 }
 private final OppAverage average=new OppAverage();
 public List<Camera> cameras; private Fix previous; private Camera active;
 private boolean reverse,uncertain; private long activeSince; private final Map<String,Long> cooldown=new HashMap<>();
 public Engine(List<Camera> data){cameras=new ArrayList<>(data);}
 public void reset(){previous=null;active=null;uncertain=false;cooldown.clear();average.reset();}
 public boolean isActive(){return active!=null;}
 public static double distance(double a,double b,double c,double d){double x=Math.toRadians(c-a),y=Math.toRadians(d-b);double h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(y/2)*Math.sin(y/2);return 6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));}
 private static double[] xy(double lat,double lon,Fix f){return new double[]{Math.toRadians(lon-f.lon)*6371000*Math.cos(Math.toRadians(f.lat)),Math.toRadians(lat-f.lat)*6371000};}
 private static double crossingFraction(Fix p,Fix f,double lat,double lon){
  if(p==null||f.time-p.time>5000||f.time<=p.time)return Double.NaN;
  double[] a=xy(p.lat,p.lon,f),b=xy(lat,lon,f);double len=a[0]*a[0]+a[1]*a[1];
  if(len<9)return Double.NaN;double t=(b[0]*a[0]+b[1]*a[1])/len;
  return t>=0&&t<=1&&Math.hypot(b[0]-t*a[0],b[1]-t*a[1])<=45?1-t:Double.NaN;
 }
 private static boolean passed(Fix p,Fix f,double lat,double lon){return Double.isFinite(crossingFraction(p,f,lat,lon));}
 private static double traveled(Fix p,Fix f){
  if(p==null)return 0;
  // Avoid counting coordinate jitter while GNSS independently reports a standstill.
  if(p.speedValid&&f.speedValid&&Math.max(p.speed,f.speed)<0.5)return 0;
  return distance(p.lat,p.lon,f.lat,f.lon);
 }
 public void markGpsUnavailable(long now){previous=null;if(active!=null){uncertain=true;average.invalidate(now);}}
 public Result refreshAverage(Result r){if(active!=null){r.uncertain=uncertain;if(uncertain)r.limit=0;}r.averageActive=average.active();r.averageComplete=average.complete();r.averageUncertain=average.uncertain();r.averageKmh=average.kmh();r.oppMeters=average.distance();r.oppElapsedMs=average.elapsed();return r;}
 private static void applyLimit(Result r){if(r.nearest!=null){r.limit=r.reverse?r.nearest.limitBackward:r.nearest.limitForward;r.limitDate=r.nearest.limitDate;r.limitReason=r.nearest.limitReason;}if(r.uncertain)r.limit=0;}
 private boolean ready(String id,long now){return !cooldown.containsKey(id)||now-cooldown.get(id)>180000;}
 public Result step(Fix f){
  Result r=new Result();r.active=active!=null;r.uncertain=uncertain;
  if(!Double.isFinite(f.lat)||!Double.isFinite(f.lon)||Math.abs(f.lat)>90||Math.abs(f.lon)>180||!Double.isFinite(f.accuracy)||f.accuracy>40||f.accuracy<0){r.status="Słaby GPS — ostrzeżenia wstrzymane";markGpsUnavailable(f.time);return refreshAverage(r);}
  if(previous!=null){long dt=f.time-previous.time;if(dt<=0){r.status="Nieaktualna pozycja";return refreshAverage(r);}
   if(dt>5000){markGpsUnavailable(f.time);}
   else if(distance(previous.lat,previous.lon,f.lat,f.lon)/(dt/1000.0)>85){markGpsUnavailable(f.time);r.status="Skok GPS — oczekiwanie na pozycję";return refreshAverage(r);}
  }
  if(active!=null){
   double a=reverse?active.lat:active.endLat,b=reverse?active.lon:active.endLon;
   r.nearest=active;r.reverse=reverse;r.distance=distance(f.lat,f.lon,a,b);r.active=true;r.uncertain=uncertain;
   r.status=uncertain?"OPP — przebieg wymaga sprawdzenia":"OPP — pomiar odcinkowy";
   double fraction=crossingFraction(previous,f,a,b);
   if(Double.isFinite(fraction)){
    long end=previous.time+Math.round((f.time-previous.time)*fraction);average.advance(traveled(previous,f)*fraction,end);average.finish();
    r.event="Koniec OPP — sprawdź oznakowanie";cooldown.put("entry:"+active.id,f.time);active=null;r.active=false;r.status=r.event;
   }else if(f.time-activeSince>7200000){average.invalidate(f.time);average.finish();active=null;r.active=false;r.event="OPP przerwany — przekroczony czas";}
   else if(previous!=null){average.advance(traveled(previous,f),f.time);}else{average.invalidate(f.time);}
   applyLimit(r);if(!r.active)r.limit=0;previous=f;return refreshAverage(r);
  }
  double best=Double.POSITIVE_INFINITY;Camera candidate=null; boolean candidateReverse=false;
  for(Camera c:cameras){
   for(int side=0;side<(c.section?2:1);side++){
    double a=side==0?c.lat:c.endLat,b=side==0?c.lon:c.endLon;
    double d=distance(f.lat,f.lon,a,b);
    if(d<best){best=d;candidate=c;candidateReverse=side==1;}
    if(c.section&&ready("entry:"+c.id,f.time)&&f.speed>=2&&passed(previous,f,a,b)){
     // Endpoints have no direction metadata: require progress toward the paired endpoint.
     double targetA=side==0?c.endLat:c.lat,targetB=side==0?c.endLon:c.lon;
     if(distance(f.lat,f.lon,targetA,targetB)<distance(previous.lat,previous.lon,targetA,targetB)){
      double fraction=crossingFraction(previous,f,a,b);long entry=previous.time+Math.round((f.time-previous.time)*fraction);
      average.begin(entry);average.advance(traveled(previous,f)*(1-fraction),f.time);
      active=c;reverse=side==1;uncertain=false;activeSince=entry;r.event="Wjazd w OPP — sprawdź oznakowanie";
      r.status="OPP — pomiar odcinkowy";r.active=true;r.nearest=c;r.reverse=reverse;r.distance=distance(f.lat,f.lon,targetA,targetB);applyLimit(r);previous=f;return refreshAverage(r);
     }
    }
   }
  }
  r.nearest=candidate;r.reverse=candidateReverse;r.distance=best;r.status=candidate==null?"Brak danych":"Najbliższy "+(candidate.section?"OPP":"fotoradar");
  if(candidate!=null&&previous!=null&&f.speed>=2){
   double a=candidateReverse?candidate.endLat:candidate.lat,b=candidateReverse?candidate.endLon:candidate.lon;
   double old=distance(previous.lat,previous.lon,a,b),movement=distance(previous.lat,previous.lon,f.lat,f.lon);
   boolean approaching=movement>=2&&(old-best)>movement*0.45;
   double radius=Math.max(250,Math.min(1000,f.speed*25));
   String key="warn:"+candidate.id+":"+candidateReverse;
   if(approaching&&best<radius&&ready(key,f.time)){r.event=(candidate.section?"OPP":"Fotoradar")+" za "+Math.round(best/10)*10+" m";cooldown.put(key,f.time);}
  }
  applyLimit(r);previous=f;return refreshAverage(r);
 }
}
