package pl.radar.offline;
import android.content.Context;import android.util.AtomicFile;import java.io.*;import java.util.*;import org.json.*;

/** Separate OSM snapshot: CANARD updates never relabel OSM limits as newly verified. */
public final class LimitCatalog {
 public static final long MAX_AGE_MS=90L*86400000;
 static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"osm-limits.json"));}
 static JSONObject validate(String text)throws Exception{
  JSONObject p=new JSONObject(text);if(p.getInt("schema")!=1||!"motorcar-no-trailer".equals(p.getString("profile")))throw new IOException("Nieobsługiwana paczka limitów");
  long epoch=epoch(p.getString("date"));if(epoch>System.currentTimeMillis()+86400000||epoch<1577836800000L)throw new IOException("Nieprawidłowa data limitów");
  JSONArray rows=p.getJSONArray("entries");if(rows.length()<1||rows.length()>5000)throw new IOException("Nieprawidłowa liczba limitów");Set<String> ids=new HashSet<>();
  for(int i=0;i<rows.length();i++){JSONObject x=rows.getJSONObject(i);if(!ids.add(x.getString("id")))throw new IOException("Powtórzone ID");
   for(String k:new String[]{"forward","backward"}){int v=x.getInt(k);if(v!=0&&(v<10||v>140))throw new IOException("Nieprawidłowy limit");}
   double a=x.getDouble("lat"),b=x.getDouble("lon");if(!Double.isFinite(a)||!Double.isFinite(b)||a<48||a>56||b<13||b>25)throw new IOException("Nieprawidłowa lokalizacja limitu");
   if(x.getBoolean("section")){double e=x.getDouble("endLat"),f=x.getDouble("endLon");if(!Double.isFinite(e)||!Double.isFinite(f)||e<48||e>56||f<13||f>25)throw new IOException("Nieprawidłowy koniec OPP");}
  }return p;
 }
 static long epoch(String text)throws Exception{return java.time.Instant.parse(text).toEpochMilli();}
 static JSONObject load(Context c)throws Exception{
  JSONObject bundled=validate(new String(Database.read(c.getAssets().open("osm-limits.json"),2000000),"UTF-8"));
  try{JSONObject custom=validate(new String(file(c).readFully(),"UTF-8"));if(epoch(custom.getString("date"))>epoch(bundled.getString("date")))return custom;}catch(Exception ignored){}
  return bundled;
 }
 public static String attach(Context ctx,List<Engine.Camera> cameras){
  for(Engine.Camera c:cameras){c.limitForward=0;c.limitBackward=0;c.limitDate="";c.limitReason="Brak jednoznacznych danych OSM";}
  try{JSONObject p=load(ctx);String date=p.getString("date");boolean stale=System.currentTimeMillis()-epoch(date)>MAX_AGE_MS;Map<String,JSONObject> entries=new HashMap<>();JSONArray rows=p.getJSONArray("entries");for(int i=0;i<rows.length();i++){JSONObject x=rows.getJSONObject(i);entries.put(x.getString("id"),x);}
   for(Engine.Camera c:cameras){JSONObject x=entries.get(c.id);if(x==null)continue;c.limitDate=date.substring(0,10);
    if(stale){c.limitReason="Dane OSM starsze niż 90 dni — limit ukryty";continue;}
    if(!anchorsMatch(c,x.getDouble("lat"),x.getDouble("lon"),x.getBoolean("section"),x.optDouble("endLat"),x.optDouble("endLon"))){c.limitReason="Zmiana lokalizacji CANARD — potrzebna nowa paczka OSM";continue;}
    c.limitForward=x.getInt("forward");c.limitBackward=x.getInt("backward");c.limitReason=x.optString("reason","Dane OSM");
   }return date.substring(0,10);
  }catch(Exception e){return "brak poprawnej paczki OSM";}
 }
 public static boolean anchorsMatch(Engine.Camera c,double lat,double lon,boolean section,double endLat,double endLon){return c.section==section&&Engine.distance(c.lat,c.lon,lat,lon)<=10&&(!section||Engine.distance(c.endLat,c.endLon,endLat,endLon)<=10);}
 public static void install(Context c,InputStream in)throws Exception{
  byte[] b=Database.read(in,2000000);JSONObject p=validate(new String(b,"UTF-8"));JSONObject old=load(c);if(epoch(p.getString("date"))<epoch(old.getString("date")))throw new IOException("Wybrana paczka jest starsza niż obecna");
  AtomicFile af=file(c);FileOutputStream out=null;try{out=af.startWrite();out.write(b);af.finishWrite(out);}catch(IOException e){if(out!=null)af.failWrite(out);throw e;}
 }
}
