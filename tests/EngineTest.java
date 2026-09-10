package pl.radar.offline;
import java.util.*;import java.nio.file.*;import java.util.regex.*;
public class EngineTest {
 static int checks=0;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Engine.Fix f(double lat,double lon,double v,double acc,long t){return new Engine.Fix(lat,lon,v,acc,t);}
 public static void main(String[] args)throws Exception{
  ok(Math.abs(Engine.distance(50,19,50.01,19)-1111.95)<1,"distance");
  Engine e=new Engine(List.of(new Engine.Camera("radar",50.005,19)));
  e.step(f(50,19,20,5,1000));Engine.Result r=e.step(f(50.001,19,20,5,3000));ok(!r.event.isEmpty(),"approaching warning");
  ok(e.step(f(50.002,19,20,5,5000)).event.isEmpty(),"cooldown");
  e.reset();e.step(f(50.004,19,20,5,1000));ok(e.step(f(50.003,19,20,5,3000)).event.isEmpty(),"moving away");
  e.reset();e.step(f(50,19,20,5,1000));ok(e.step(f(50.001,19,20,90,3000)).event.isEmpty(),"bad accuracy");
  e.reset();e.step(f(50,19,20,5,1000));ok(e.step(f(50.004,19,20,5,2000)).event.isEmpty(),"GPS jump");
  for(boolean reverse:new boolean[]{false,true}){
   e=new Engine(List.of(new Engine.Camera("opp",50.01,19,50.02,19)));
   double a=reverse?50.0201:50.0099,b=reverse?50.0199:50.0101;
   e.step(f(a,19,20,5,1000));r=e.step(f(b,19,20,5,3000));ok(r.active&&!r.event.isEmpty(),"entry both directions");
   // long gap cannot establish a crossing
   double near=reverse?50.0101:50.0199,after=reverse?50.0099:50.0201;
   r=e.step(f(near,19,20,5,70000));ok(r.active&&r.uncertain,"gap preserves uncertain OPP");
   r=e.step(f(after,19,20,5,72000));ok(!r.active&&r.event.startsWith("Koniec"),"exit");
  }
  e=new Engine(List.of(new Engine.Camera("opp",50.01,19,50.02,19)));e.step(f(50.0099,19.003,20,5,1000));ok(!e.step(f(50.0101,19.003,20,5,3000)).active,"adjacent road 200m");
  e.reset();e.step(f(50.0099,19,20,5,1000));ok(!e.step(f(50.0101,19,20,5,10000)).active,"no crossing across stale fix");
  e.reset();ok(!e.step(f(50.015,19,20,5,1000)).active,"start inside section no false entry");
  e=new Engine(Arrays.asList(new Engine.Camera("demo-radar",50.005,19),new Engine.Camera("demo-opp",50.01,19,50.02,19)));int radarWarnings=0,entries=0,exits=0;
  for(int i=0;i<=125;i++){r=e.step(f(50+i*0.00018,19,20,5,(i+1)*1000L));if(r.event.startsWith("Fotoradar"))radarWarnings++;if(r.event.startsWith("Wjazd"))entries++;if(r.event.startsWith("Koniec"))exits++;}
  ok(radarWarnings==1&&entries==1&&exits==1,"complete UI demo: one radar, one entry, one exit");
  Engine.Camera limited=new Engine.Camera("limited",50.005,19);limited.limitForward=limited.limitBackward=50;limited.limitDate="2026-09-08";
  e=new Engine(List.of(limited));r=e.step(f(50,19,20,5,1000));ok(r.limit==50,"fixed limit metadata");ok(r.limitDate.equals("2026-09-08"),"limit date retained");
  r=e.step(f(50.001,19,20,90,3000));ok(r.limit==0,"bad GPS hides limit");
  Engine.Camera directional=new Engine.Camera("dir",50.01,19,50.02,19);directional.limitForward=70;directional.limitBackward=0;
  e=new Engine(List.of(directional));e.step(f(50.0099,19,20,5,1000));r=e.step(f(50.0101,19,20,5,3000));ok(r.limit==70&&r.active,"OPP forward limit");
  r=e.step(f(50.012,19,20,5,9000));ok(r.limit==0&&r.uncertain,"gap hides OPP limit");
  e.reset();e.step(f(50.0201,19,20,5,1000));r=e.step(f(50.0199,19,20,5,3000));ok(r.active&&r.limit==0,"unknown reverse not copied from forward");
  e=new Engine(List.of(new Engine.Camera("unknown",50.005,19)));r=e.step(f(50,19,20,5,1000));ok(r.limit==0,"unknown remains unknown");
  if(args.length>0){String html=Files.readString(Path.of(args[0]));for(String key:List.of("fotoradaryPP","fotoradaryOPP")){Matcher m=Pattern.compile(key+":\"([^\"]+)\"").matcher(html);ok(m.find(),"source layer");String decoded=LzString.decode(m.group(1));ok(decoded.startsWith("[{\"id\":"),"real CANARD decode");Files.writeString(Path.of("/tmp/"+key+"-java.json"),decoded);}}
  System.out.println("PASS "+checks+" assertions");
 }
}
