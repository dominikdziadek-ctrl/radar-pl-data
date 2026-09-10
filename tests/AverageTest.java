package pl.radar.offline;
import java.util.*;
public class AverageTest {
 static int checks;static final double M=180/(Math.PI*6371000);
 static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void near(double a,double b,double tolerance,String s){ok(Double.isFinite(a)&&Math.abs(a-b)<tolerance,s+": "+a+" expected "+b);}
 static Engine engine(){return new Engine(List.of(new Engine.Camera("opp",0,0,300*M,0)));}
 static Engine.Fix f(double m,double east,double speed,long t){return new Engine.Fix(m*M,east*M,speed,5,t);}
 static Engine entered(){Engine e=engine();e.step(f(-10,0,10,1000));e.step(f(10,0,10,3000));return e;}
 public static void main(String[] args){
  OppAverage a=new OppAverage();a.begin(1000);a.advance(100,11000);near(a.kmh(),36,.001,"100m in 10s");
  a.advance(0,21000);near(a.kmh(),18,.001,"stop included");a.advance(100,26000);near(a.kmh(),28.8,.001,"distance over time, unequal intervals");
  a.finish();a.advance(900,90000);near(a.kmh(),28.8,.001,"finished result frozen");
  a.begin(100000);ok(Double.isNaN(a.kmh())&&!a.complete(),"new measurement clears old");a.advance(20,102000);ok(Double.isNaN(a.kmh()),"initial 5s guard");
  a.invalidate(110000);a.advance(100,115000);a.finish();ok(a.uncertain()&&Double.isNaN(a.kmh()),"invalid result never revived");a.reset();ok(!a.active()&&!a.complete(),"reset clears summary");
  for(boolean reverse:new boolean[]{false,true}){
   Engine e=engine();Engine.Result r=null;
   for(int i=0;i<=16;i++){double m=-10+20*i;r=e.step(f(reverse?300-m:m,0,10,1000+2000*i));if(i==0)ok(!r.averageActive,"no early average");if(i==1)ok(r.averageActive&&r.oppElapsedMs==1000,"entry interpolated");if(i==5)near(r.averageKmh,36,.01,"running average without limit");}
   ok(r.averageComplete&&!r.averageActive,"exit both directions");near(r.oppMeters,300,.01,"only between gates");ok(r.oppElapsedMs==30000,"exit time interpolated");near(r.averageKmh,36,.01,"completed average");
   r=e.step(f(reverse?-30:330,0,10,35000));near(r.averageKmh,36,.01,"summary retained after exit");e.markGpsUnavailable(50000);r=e.refreshAverage(r);near(r.averageKmh,36,.01,"finished result survives GPS loss");e.reset();r=e.step(f(150,0,10,60000));ok(!r.averageActive&&!r.averageComplete,"start inside does not infer entry");
  }
  Engine e=entered();Engine.Result r=e.step(f(30,0,0,5000));e.step(f(30,0,0,7000));r=e.step(f(31,1,0,9000));near(r.oppMeters,30,.01,"stopped jitter suppressed");near(r.averageKmh,30*3600.0/7000,.01,"stationary time included");
  r=e.step(new Engine.Fix(51*M,0,0,5,11000,false));ok(r.oppMeters>49,"missing speed is not evidence of stop");
  e=entered();e.step(f(30,30,15,5000));r=e.step(f(60,30,15,7000));ok(r.oppMeters>75,"curved path uses sum, not gate distance");near(r.averageKmh,r.oppMeters*3600/5000,.01,"curve average");
  e=entered();r=e.step(f(40,0,10,9000));ok(r.averageActive&&r.averageUncertain&&Double.isNaN(r.averageKmh),"gap invalidates");
  e=entered();r=e.step(new Engine.Fix(30*M,0,10,80,5000));ok(r.averageUncertain&&Double.isNaN(r.averageKmh),"poor accuracy invalidates");
  e=entered();r=e.step(f(290,0,10,4000));ok(r.averageUncertain&&Double.isNaN(r.averageKmh),"GPS jump invalidates");
  e=entered();r=e.step(f(30,0,10,5000));e.markGpsUnavailable(11000);r=e.refreshAverage(r);ok(r.averageUncertain&&Double.isNaN(r.averageKmh)&&r.limit==0,"no-callback stale timer");
  e=entered();r=e.step(f(30,0,10,5000));double before=r.oppMeters;r=e.step(f(31,0,10,5000));near(r.oppMeters,before,.001,"duplicate timestamp ignored");
  e=new Engine(List.of(new Engine.Camera("fixed",0,0)));e.step(f(-10,0,10,1000));r=e.step(f(10,0,10,3000));ok(!r.averageActive&&!r.averageComplete,"fixed camera does not start average");
  System.out.println("PASS "+checks+" average assertions");
 }
}
