package pl.radar.offline;

/** Independent OPP distance/time accumulator; no Android dependency. */
public final class OppAverage {
 private long start,last;private double meters;private boolean active,complete,uncertain;
 public void reset(){start=last=0;meters=0;active=complete=uncertain=false;}
 public void begin(long time){reset();start=last=time;active=true;}
 public void advance(double distance,long time){
  if(!active)return;
  if(time<last||!Double.isFinite(distance)||distance<0){uncertain=true;return;}
  meters+=distance;last=time;
 }
 public void invalidate(long time){if(active){uncertain=true;last=Math.max(last,time);}}
 public void finish(){if(active){active=false;complete=true;}}
 public boolean active(){return active;}
 public boolean complete(){return complete;}
 public boolean uncertain(){return uncertain;}
 public long elapsed(){return Math.max(0,last-start);}
 public double distance(){return meters;}
 public double kmh(){return (active||complete)&&!uncertain&&elapsed()>=5000?meters*3600.0/elapsed():Double.NaN;}
}
