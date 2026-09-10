package pl.radar.offline;
import android.content.Context;import android.util.AtomicFile;import org.json.*;import java.io.*;import java.net.*;import java.util.*;import java.util.regex.*;
public final class Database {
 public static final String URL="https://www.canard.gitd.gov.pl/cms/o-nas/mapa-urzadzen";
 public static final class Data {public List<Engine.Camera> cameras; public String date,limitDate=""; Data(List<Engine.Camera> c,String d){cameras=c;date=d;} public int knownLimits(){int n=0;for(Engine.Camera c:cameras)if(c.limitForward>0||c.limitBackward>0)n++;return n;}}
 static byte[] read(InputStream in,int max)throws IOException{try(InputStream stream=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=stream.read(b))!=-1){if(out.size()+n>max)throw new IOException("Plik przekracza limit");out.write(b,0,n);}return out.toByteArray();}}
 static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"canard.json"));}
 public static Data load(Context c)throws Exception{
  Data data;try{data=parse(new String(file(c).readFully(),"UTF-8"));}catch(Exception e){data=parse(new String(read(c.getAssets().open("canard.json"),2000000),"UTF-8"));}
  data.limitDate=LimitCatalog.attach(c,data.cameras);return data;
 }
 static Data parse(String text)throws Exception{
  JSONObject o=new JSONObject(text);JSONArray rows=o.getJSONArray("cameras");List<Engine.Camera> cs=new ArrayList<>();Set<String> ids=new HashSet<>();
  for(int i=0;i<rows.length();i++){JSONObject x=rows.getJSONObject(i);String id=x.getString("id");double a=x.getDouble("lat"),b=x.getDouble("lon");if(a<48||a>56||b<13||b>25||!ids.add(id))throw new IOException("Nieprawidłowe współrzędne lub duplikat");
   if(x.optBoolean("section")){double ea=x.getDouble("endLat"),eb=x.getDouble("endLon");double len=Engine.distance(a,b,ea,eb);if(ea<48||ea>56||eb<13||eb>25||len<100||len>100000)throw new IOException("Nieprawidłowy odcinek");cs.add(new Engine.Camera(id,a,b,ea,eb));}
   else cs.add(new Engine.Camera(id,a,b));}
  if(cs.size()<100||cs.size()>5000)throw new IOException("Podejrzana liczba rekordów");return new Data(cs,o.getString("date"));
 }
 public static synchronized Data update(Context ctx)throws Exception{
  HttpURLConnection con=(HttpURLConnection)new java.net.URL(URL).openConnection();con.setConnectTimeout(15000);con.setReadTimeout(20000);con.setRequestProperty("User-Agent","RadarPL-prototype/0.2 (personal offline data refresh)");String html;
  try{if(con.getResponseCode()!=200)throw new IOException("HTTP "+con.getResponseCode());html=new String(read(con.getInputStream(),3000000),"UTF-8");}finally{con.disconnect();}
  JSONArray all=new JSONArray();for(String key:new String[]{"fotoradaryPP","fotoradaryOPP"}){
   Matcher m=Pattern.compile(key+"\\s*:\\s*\"([^\"]+)\"").matcher(html);if(!m.find())throw new IOException("Zmienił się format strony CANARD");
   JSONArray src=new JSONArray(LzString.decode(m.group(1)));if(src.length()<10)throw new IOException("Niepełna warstwa CANARD");
   for(int i=0;i<src.length();i++){JSONObject x=src.getJSONObject(i),o=new JSONObject();o.put("id",key+":"+x.getString("id"));o.put("lat",x.getDouble("lat"));o.put("lon",x.getDouble("lon"));o.put("section",key.endsWith("OPP"));
    if(key.endsWith("OPP")){o.put("endLat",x.getDouble("lok2PktSzerokosc"));o.put("endLon",x.getDouble("lok2PktDlugosc"));}all.put(o);}
  }
  JSONObject result=new JSONObject();result.put("date",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).format(new Date()));result.put("cameras",all);
  String text=result.toString();Data data=parse(text);Data old=load(ctx);if(data.cameras.size()<old.cameras.size()*0.75)throw new IOException("Baza zmniejszyła się o ponad 25% — zachowano poprzednią");
  AtomicFile af=file(ctx);FileOutputStream out=null;try{out=af.startWrite();out.write(text.getBytes("UTF-8"));af.finishWrite(out);}catch(IOException e){if(out!=null)af.failWrite(out);throw e;}
  data.limitDate=LimitCatalog.attach(ctx,data.cameras);return data;
 }
}
