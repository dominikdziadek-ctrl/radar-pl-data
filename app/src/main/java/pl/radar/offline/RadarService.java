package pl.radar.offline;
import android.Manifest;import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.location.*;import android.media.*;import android.net.*;import android.os.*;

public class RadarService extends Service implements LocationListener {
 public static RadarService current;
 public Engine engine;public Engine.Result result=new Engine.Result();public Engine.Fix fix;public Database.Data database;
 public String updateStatus="",lastEvent="";public long lastFixAt;public boolean updating=false;
 private LocationManager locations;private ToneGenerator tone;private PowerManager.WakeLock wake;
 private final Handler handler=new Handler(Looper.getMainLooper());private ConnectivityManager connectivity;private ConnectivityManager.NetworkCallback networkCallback;
 @Override public void onCreate(){super.onCreate();current=this;try{database=Database.load(this);engine=new Engine(database.cameras);}catch(Exception e){result.status="Błąd bazy: "+e.getMessage();}
  try{tone=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,85);}catch(Exception ignored){}
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("drive","Ostrzeganie podczas jazdy",NotificationManager.IMPORTANCE_LOW));
 }
 private Notification notification(String message){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RadarService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,"drive").setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle("Radar PL — GPS aktywny").setContentText(message).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Zatrzymaj",stop).build()).build();
 }
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent!=null&&"STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED||engine==null){stopSelf();return START_NOT_STICKY;}
  try{
   startForeground(1,notification("Oczekiwanie na GPS"));
   if(locations==null){locations=(LocationManager)getSystemService(LOCATION_SERVICE);locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);
    wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"RadarPL:drive");wake.acquire(4*60*60*1000L);
    connectivity=getSystemService(ConnectivityManager.class);networkCallback=new ConnectivityManager.NetworkCallback(){@Override public void onCapabilitiesChanged(Network n,NetworkCapabilities c){if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))handler.post(()->update(false));}};
    connectivity.registerDefaultNetworkCallback(networkCallback);handler.post(staleCheck);
   }
  }catch(Exception e){result.status="Nie można uruchomić GPS: "+e.getMessage();stopSelf();}
  return START_NOT_STICKY;
 }
 private final Runnable staleCheck=new Runnable(){public void run(){if(lastFixAt>0&&SystemClock.elapsedRealtime()-lastFixAt>5000){if(engine!=null){engine.markGpsUnavailable(SystemClock.elapsedRealtime());engine.refreshAverage(result);}result.status="Brak świeżego GPS — ostrzeżenia wstrzymane";}handler.postDelayed(this,3000);}};
 @Override public void onLocationChanged(Location l){
  long now=SystemClock.elapsedRealtime(),fixTime=l.getElapsedRealtimeNanos()/1000000;if(now-fixTime>5000||fixTime>now)return;
  lastFixAt=fixTime;fix=new Engine.Fix(l.getLatitude(),l.getLongitude(),l.hasSpeed()?l.getSpeed():0,l.hasAccuracy()?l.getAccuracy():999,fixTime,l.hasSpeed());
  result=engine.step(fix);if(!result.event.isEmpty()){lastEvent=result.event;if(tone!=null)tone.startTone(result.active?ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD:ToneGenerator.TONE_PROP_BEEP2,650);getSystemService(NotificationManager.class).notify(1,notification(lastEvent));}
 }
 public void update(boolean manual){
  if(updating)return;
  if(!manual){ConnectivityManager cm=getSystemService(ConnectivityManager.class);NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))return;}
  if(engine!=null&&engine.isActive()){updateStatus="Aktualizacja po zakończeniu OPP";return;}
  if(fix!=null&&SystemClock.elapsedRealtime()-lastFixAt<10000&&fix.speed>1.5){updateStatus="Aktualizuj na postoju";return;}
  long last=getSharedPreferences("app",0).getLong("lastUpdateAttempt",0);
  if(!manual&&System.currentTimeMillis()-last<86400000)return;
  getSharedPreferences("app",0).edit().putLong("lastUpdateAttempt",System.currentTimeMillis()).apply();updating=true;updateStatus="Pobieranie CANARD…";
  new Thread(()->{try{StaticUpdater.Update update=StaticUpdater.update(this);handler.post(()->{database=update.data;if(!engine.isActive()&&(fix==null||fix.speed<1.5))engine.cameras=new java.util.ArrayList<>(update.data.cameras);updateStatus=(update.changed?"Pobrano nową paczkę: ":"Baza jest aktualna: ")+update.data.cameras.size()+" rekordów; "+update.data.date;updating=false;});}
   catch(Exception e){handler.post(()->{updateStatus="Zachowano poprzednią bazę: "+e.getMessage();updating=false;});}},"canard-update").start();
 }
 @Override public void onProviderDisabled(String p){if(engine!=null){engine.markGpsUnavailable(SystemClock.elapsedRealtime());engine.refreshAverage(result);}result.status="GPS wyłączony — włącz lokalizację";}
 @Override public IBinder onBind(Intent i){return null;}
 @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(locations!=null)locations.removeUpdates(this);if(networkCallback!=null)connectivity.unregisterNetworkCallback(networkCallback);if(wake!=null&&wake.isHeld())wake.release();if(tone!=null)tone.release();current=null;super.onDestroy();}
}
