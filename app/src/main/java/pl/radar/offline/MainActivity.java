package pl.radar.offline;
import android.Manifest;import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.graphics.*;import android.location.LocationManager;import android.media.*;import android.net.*;import android.os.*;import android.view.*;import android.widget.*;import java.util.*;

public class MainActivity extends Activity {
 private final Handler h=new Handler(Looper.getMainLooper());private TextView speed,status,distance,details,source,event,limit,limitInfo,average,averageInfo;private View lamp;private RadarView map;
 private Database.Data db;private Engine demo;private Engine.Result demoResult;private Engine.Fix demoFix;private int tick,refreshCount;private boolean sim=false,updating=false;private String updateText="";private ToneGenerator tone;
 private final int bg=Color.rgb(10,18,30),muted=Color.rgb(154,173,193),accent=Color.rgb(70,226,182);
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
  try{db=Database.load(this);tone=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,85);}catch(Exception e){updateText=e.getMessage();}
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(bg);LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(24,48,24,40);scroll.addView(body);
  text(body,"RADAR PL",24,Color.WHITE);text(body,"PROTOTYP 0.4  /  OFFLINE",12,accent);
  lamp=new View(this);lamp.setBackgroundColor(accent);body.addView(lamp,new LinearLayout.LayoutParams(-1,5));
  speed=text(body,"—",64,Color.WHITE);text(body,"km/h • prędkość GPS",14,muted);
  status=text(body,"Gotowy do testów",23,Color.WHITE);distance=text(body,"Uruchom GPS lub symulację",18,accent);
  limit=text(body,"LIMIT —",32,Color.WHITE);limitInfo=text(body,"Przy pomiarze • OSM • auto osobowe bez przyczepy",13,muted);
  average=text(body,"ŚREDNIA OPP —",32,accent);averageInfo=text(body,"Pomiar rozpocznie się przy wykrytym wjeździe w OPP",14,muted);
  map=new RadarView();body.addView(map,new LinearLayout.LayoutParams(-1,360));
  event=text(body,"",16,accent);details=text(body,"Limity przy pomiarach • odległości w linii prostej",13,muted);
  button(body,"▶  Rozpocznij jazdę z GPS",v->startGps());button(body,"◉  Symulacja: radar → OPP → koniec",v->startDemo());
  button(body,"■  Zatrzymaj / wyzeruj OPP",v->{sim=false;demoResult=null;stopService(new Intent(this,RadarService.class));event.setText("");status.setText("Zatrzymano");});
  button(body,"↻  Aktualizuj kamery i limity",v->update(true));
  button(body,"Wczytaj nowszą paczkę limitów OSM",v->{if(sim||RadarService.current!=null){new AlertDialog.Builder(this).setMessage("Zatrzymaj jazdę/symulację przed wczytaniem paczki limitów.").setPositiveButton("OK",null).show();return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,20);});
  source=text(body,"",12,muted);button(body,"Źródła i ograniczenia prototypu",v->new AlertDialog.Builder(this).setTitle("Radar PL • informacje").setMessage("Kamery: CANARD / GITD\n"+Database.URL+"\n\nLimity: © OpenStreetMap contributors, ODbL 1.0\nhttps://www.openstreetmap.org/copyright\nWyciąg: Overpass API. Profil: samochód osobowy bez przyczepy.\n\nLimit dotyczy miejsca pomiaru, niekoniecznie Twojej obecnej pozycji. Jest wynikiem dopasowania danych mapowych, nie potwierdzeniem aktualnego znaku. Nieznany limit oznacza brak jednoznacznych danych. Nie uzupełniamy domyślnych ani warunkowych ograniczeń. Limit na OPP pokazujemy tylko dla kierunku, w którym znaleźliśmy połączony odcinek z jednakową wartością na całej trasie.\n\nAutomat aktualizuje lokalizacje CANARD codziennie, a limity OSM raz w tygodniu. Aplikacja sprawdza mały manifest po Wi-Fi najwyżej raz na dobę i pobiera tylko zmieniony plik. Poprzednia baza pozostaje po błędzie aktualizacji. Nowszą paczkę JSON można też wczytać z pliku; dane starsze niż 90 dni są ukrywane. Zmiana współrzędnych kamery o ponad 10 m unieważnia powiązanie limitu.\n\nSchemat punktów bez mapy dróg; odległości w linii prostej. Rozpoznanie wjazdu OPP jest przybliżone. Średnia OPP to szacunek: długość śladu GPS podzielona przez czas od wykrytego wjazdu, z postojami. Pierwszy wynik po 5 sekundach. Przerwa GPS ponad 5 sekund, słaba dokładność lub skok pozycji unieważnia wynik tego przejazdu. Wynik po wyjeździe pozostaje do kolejnego OPP lub zatrzymania jazdy. Nie jest to pomiar urzędowy. Start wewnątrz OPP nie odtwarza wcześniejszego przejazdu. Nie wysyłamy pozycji GPS. Możliwe alarmy z sąsiedniej drogi. Sprawdzaj oznakowanie.\n\nSymulacja ma fikcyjne limity 50 i 70 km/h, wyraźnie oznaczone DEMO. Zmiany ustawień i testy obsługuj na postoju.").setPositiveButton("Rozumiem",null).show());
  setContentView(scroll);h.post(refresh);update(false);
 }
 private TextView text(LinearLayout parent,String t,int size,int color){TextView v=new TextView(this);v.setText(t);v.setTextColor(color);v.setTextSize(size);v.setPadding(4,10,4,10);parent.addView(v);return v;}
 private void button(LinearLayout body,String s,View.OnClickListener f){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(f);body.addView(b,new LinearLayout.LayoutParams(-1,-2));}
 private void startGps(){
  sim=false;demoResult=null;if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},10);return;}
  if(!getSystemService(LocationManager.class).isProviderEnabled(LocationManager.GPS_PROVIDER)){new AlertDialog.Builder(this).setMessage("Włącz lokalizację GPS, aby rozpocząć jazdę.").setPositiveButton("Ustawienia",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("Anuluj",null).show();return;}
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
  startForegroundService(new Intent(this,RadarService.class));
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==10){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startGps();else new AlertDialog.Builder(this).setMessage("Do ostrzegania potrzebna jest dokładna lokalizacja. Symulacja działa bez uprawnień GPS.").setPositiveButton("OK",null).show();}}
 private void startDemo(){stopService(new Intent(this,RadarService.class));sim=true;tick=0;event.setText("");demo=new Engine(Arrays.asList(new Engine.Camera("demo-radar",50.005,19),new Engine.Camera("demo-opp",50.01,19,50.02,19)));demo.cameras.get(0).limitForward=demo.cameras.get(0).limitBackward=50;demo.cameras.get(1).limitForward=demo.cameras.get(1).limitBackward=70;
  for(Engine.Camera c:demo.cameras){c.limitDate="DEMO";c.limitReason="Fikcyjny limit wyłącznie do symulacji";}demoResult=null;}
 private final Runnable refresh=new Runnable(){public void run(){
  Engine.Result r=null;Engine.Fix f=null;boolean demoFrame=sim;List<Engine.Camera> points=Collections.emptyList();
  if(sim){double lat=50+tick*0.00018;demoFix=new Engine.Fix(lat,19,20,5,1000L*(tick+1));demoResult=demo.step(demoFix);r=demoResult;f=demoFix;points=demo.cameras;
   if(!r.event.isEmpty()){event.setText("DEMO • "+r.event);if(tone!=null)tone.startTone(ToneGenerator.TONE_PROP_BEEP2,400);}tick++;if(tick>125){sim=false;event.setText("Symulacja zakończona — radar, wjazd i wyjazd OPP");}
  }else if(RadarService.current!=null){RadarService s=RadarService.current;r=s.result;f=s.fix;points=s.engine==null?points:s.engine.cameras;db=s.database;updateText=s.updateStatus;event.setText(s.lastEvent);
   if(s.lastFixAt==0||SystemClock.elapsedRealtime()-s.lastFixAt>5000)f=null;
  }
  if(r==null&&demoResult!=null){r=demoResult;demoFrame=true;points=demo.cameras;}
  showAverage(r,f,demoFrame);
  if(r!=null){
   boolean usable=f!=null&&f.accuracy<=40&&r.distance<1500&&!r.uncertain;
   limit.setText((demoFrame?"DEMO • ":"")+"LIMIT "+(usable&&r.limit>0?r.limit+" km/h":"—"));
   limitInfo.setText((r.nearest!=null&&r.nearest.section?"Na OPP w tym kierunku":"Przy fotoradarze")+" • "+(r.limitDate.isEmpty()?"brak danych OSM":"OSM "+r.limitDate)+"\n"+(usable?r.limitReason:"Brak świeżego dopasowania — limit nieznany"));
   status.setText((demoFrame?"DEMO • ":"")+r.status);distance.setText(Double.isFinite(r.distance)?Math.round(r.distance)+" m "+(r.active?"do drugiego końca OPP":"do najbliższego punktu"):"Oczekiwanie na GPS");lamp.setBackgroundColor(r.active?Color.rgb(255,184,77):!r.event.isEmpty()?Color.rgb(255,104,104):accent);}
  if(r==null){limit.setText("LIMIT —");limitInfo.setText("Uruchom GPS lub symulację. Limity dotyczą miejsca pomiaru.");distance.setText("Uruchom GPS lub symulację");}
  speed.setText(f==null?"—":String.valueOf(Math.round(f.speed*3.6)));details.setText(f==null?"Brak świeżej pozycji GPS":"Dokładność ±"+Math.round(f.accuracy)+" m • limit dotyczy miejsca pomiaru");
  map.fix=f;map.points=points;map.invalidate();source.setText((db==null?"Brak bazy":"CANARD • "+db.cameras.size()+" rekordów • pobrano "+db.date+"\nLimity: "+db.knownLimits()+"/"+db.cameras.size()+" • OSM "+db.limitDate)+"\n"+updateText);
  if(++refreshCount%30==0&&!sim)update(false);
  h.postDelayed(this,1000);
 }};
 private void showAverage(Engine.Result r,Engine.Fix f,boolean demoFrame){
  if(r==null||(!r.averageActive&&!r.averageComplete)){
   average.setText("ŚREDNIA OPP —");averageInfo.setText("Pomiar rozpocznie się przy wykrytym wjeździe w OPP");return;
  }
  boolean incomplete=r.averageUncertain||(r.averageActive&&f==null);
  boolean known=!incomplete&&Double.isFinite(r.averageKmh);
  average.setText((demoFrame?"DEMO • ":"")+(r.averageComplete?"OSTATNI OPP ":"ŚREDNIA OPP ")+(known?String.format(Locale.forLanguageTag("pl"),"%.1f km/h",r.averageKmh):"—"));
  long seconds=r.oppElapsedMs/1000;
  String duration=String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);
  averageInfo.setText((incomplete?"Niepełny ślad GPS — wynik niedostępny":known?(r.averageComplete?"Pomiar zakończony • szacunek GPS":"Od wjazdu • szacunek GPS • postoje wliczone"):"Zbieranie danych — pierwszy wynik po 5 s")+"\n"+Math.round(r.oppMeters)+" m śladu • czas "+duration);
 }
 private void update(boolean manual){
  if(sim){updateText="Zatrzymaj symulację przed aktualizacją";return;}
  if(RadarService.current!=null){RadarService.current.update(manual);return;}if(updating)return;
  ConnectivityManager cm=getSystemService(ConnectivityManager.class);NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
  if(caps==null||!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)){if(manual)updateText="Brak internetu — baza offline pozostaje dostępna";return;}
  if(!manual&&(!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||System.currentTimeMillis()-getSharedPreferences("app",0).getLong("lastUpdateAttempt",0)<86400000))return;
  getSharedPreferences("app",0).edit().putLong("lastUpdateAttempt",System.currentTimeMillis()).apply();updating=true;updateText="Pobieranie CANARD…";
  new Thread(()->{try{StaticUpdater.Update result=StaticUpdater.update(getApplicationContext());h.post(()->{db=result.data;updating=false;updateText=result.changed?"Kamery i limity zaktualizowane":"Baza jest już aktualna";});}catch(Exception e){h.post(()->{updating=false;updateText="Zachowano bazę offline: "+e.getMessage();});}},"data-update").start();
 }
 @Override protected void onActivityResult(int request,int resultCode,Intent data){super.onActivityResult(request,resultCode,data);if(request!=20||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
  if(sim||RadarService.current!=null){updateText="Zatrzymaj jazdę przed importem";return;}
  updating=true;updateText="Sprawdzanie paczki OSM…";
  new Thread(()->{try{LimitCatalog.install(getApplicationContext(),getContentResolver().openInputStream(data.getData()));Database.Data fresh=Database.load(getApplicationContext());h.post(()->{db=fresh;updating=false;updateText="Wczytano limity OSM: "+fresh.limitDate;});}catch(Exception e){h.post(()->{updating=false;updateText="Zachowano limity: "+e.getMessage();});}},"osm-import").start();
 }
 @Override protected void onDestroy(){h.removeCallbacksAndMessages(null);if(tone!=null)tone.release();super.onDestroy();}
 private class RadarView extends View {
  Engine.Fix fix;List<Engine.Camera> points=Collections.emptyList();Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  RadarView(){super(MainActivity.this);}
  @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,rad=Math.min(cx,cy)-25;
   p.setColor(Color.rgb(28,46,66));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(cx,cy,rad,p);c.drawCircle(cx,cy,rad/2,p);c.drawLine(cx-rad,cy,cx+rad,cy,p);c.drawLine(cx,cy-rad,cx,cy+rad,p);p.setStyle(Paint.Style.FILL);p.setTextSize(24);p.setColor(muted);c.drawText("N • 1 km • schemat bez dróg",15,26,p);
   if(fix!=null){for(Engine.Camera x:points){drawPoint(c,x.lat,x.lon,x.section,cx,cy,rad);if(x.section)drawPoint(c,x.endLat,x.endLon,true,cx,cy,rad);}}
   p.setColor(accent);c.drawCircle(cx,cy,9,p);
  }
  void drawPoint(Canvas c,double lat,double lon,boolean section,float cx,float cy,float rad){double north=(lat-fix.lat)*111195,east=(lon-fix.lon)*111195*Math.cos(Math.toRadians(fix.lat));if(Math.hypot(north,east)>1000)return;p.setColor(section?Color.rgb(255,184,77):Color.rgb(255,104,104));c.drawCircle(cx+(float)(east/1000*rad),cy-(float)(north/1000*rad),7,p);}
 }
}
