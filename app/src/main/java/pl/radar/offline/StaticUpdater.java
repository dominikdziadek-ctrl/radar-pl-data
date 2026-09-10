package pl.radar.offline;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/** Downloads an all-or-previous-version static snapshot published by GitHub Pages. */
public final class StaticUpdater {
 // Replaced with the final GitHub Pages address immediately before publishing v0.4.
 public static final String BASE_URL="https://dominikdziadek-ctrl.github.io/radar-pl-data/";
 private static final int MANIFEST_MAX=32768,COMPRESSED_MAX=1000000,JSON_MAX=3000000;
 public static final class Update {public final Database.Data data;public final boolean changed;Update(Database.Data d,boolean c){data=d;changed=c;}}

 public static Update update(Context context)throws Exception{
  if(!BASE_URL.startsWith("https://")||!BASE_URL.endsWith("/"))throw new IOException("Kanał aktualizacji nie jest jeszcze skonfigurowany");
  JSONObject manifest=new JSONObject(new String(download(new URL(BASE_URL+"manifest.json"),MANIFEST_MAX),"UTF-8"));
  if(manifest.getInt("schema")!=1)throw new IOException("Nieobsługiwana wersja manifestu");
  String version=manifest.getString("version");
  if(!version.matches("[0-9a-f]{16}"))throw new IOException("Nieprawidłowy identyfikator paczki");
  Database.Data current=Database.load(context);String saved=context.getSharedPreferences("app",0).getString("dataVersion","");
  if(version.equals(saved))return new Update(current,false);
  JSONObject cameras=manifest.getJSONObject("canard"),limits=manifest.getJSONObject("limits");
  String cameraHash=cameras.getString("sha256"),limitHash=limits.getString("sha256");
  String savedCameraHash=context.getSharedPreferences("app",0).getString("canardHash","");String savedLimitHash=context.getSharedPreferences("app",0).getString("limitsHash","");
  boolean camerasChanged=!cameraHash.equals(savedCameraHash),limitsChanged=!limitHash.equals(savedLimitHash);
  byte[] cameraJson=camerasChanged?inflate(component(cameras)):null,limitJson=limitsChanged?inflate(component(limits)):null;
  Database.Data candidate=camerasChanged?Database.parse(new String(cameraJson,"UTF-8")):current;if(limitsChanged)LimitCatalog.validate(new String(limitJson,"UTF-8"));
  if(candidate.cameras.size()<current.cameras.size()*0.75||candidate.cameras.size()>current.cameras.size()*1.50)throw new IOException("Podejrzana zmiana liczby urządzeń");
  if(camerasChanged)write(Database.file(context),cameraJson);if(limitsChanged)write(LimitCatalog.file(context),limitJson);
  context.getSharedPreferences("app",0).edit().putString("dataVersion",version).putString("canardHash",cameraHash).putString("limitsHash",limitHash).apply();
  return new Update(Database.load(context),camerasChanged||limitsChanged);
 }
 private static byte[] component(JSONObject descriptor)throws Exception{
  String file=descriptor.getString("file"),hash=descriptor.getString("sha256");int expected=descriptor.getInt("bytes");
  if(!file.matches("[a-z0-9.-]+\\.json\\.gz")||!hash.matches("[0-9a-f]{64}")||expected<1||expected>COMPRESSED_MAX)throw new IOException("Nieprawidłowy opis pliku aktualizacji");
  byte[] data=download(new URL(BASE_URL+file),COMPRESSED_MAX);if(data.length!=expected||!hex(MessageDigest.getInstance("SHA-256").digest(data)).equals(hash))throw new IOException("Niezgodna suma kontrolna aktualizacji");return data;
 }
 private static byte[] download(URL url,int max)throws Exception{
  HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("User-Agent","RadarPL/0.4 personal non-commercial client");
  try{if(connection.getResponseCode()!=200)throw new IOException("HTTP "+connection.getResponseCode());return Database.read(connection.getInputStream(),max);}finally{connection.disconnect();}
 }
 private static byte[] inflate(byte[] compressed)throws Exception{return Database.read(new GZIPInputStream(new ByteArrayInputStream(compressed)),JSON_MAX);}
 private static void write(AtomicFile file,byte[] data)throws IOException{FileOutputStream out=null;try{out=file.startWrite();out.write(data);file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}}
 private static String hex(byte[] data){StringBuilder text=new StringBuilder();for(byte value:data)text.append(String.format(Locale.ROOT,"%02x",value&255));return text.toString();}
}
