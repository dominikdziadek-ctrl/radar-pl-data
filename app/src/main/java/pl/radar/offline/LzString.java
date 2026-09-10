package pl.radar.offline;
import java.util.*;
/** Decoder for LZ-string decompressFromBase64, used by CANARD's public map. */
public final class LzString {
 static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
 static final class Bits {
  String s;int i=0,mask=32,val;
  Bits(String s){this.s=s;val=ALPHABET.indexOf(s.charAt(0));}
  int read(int n){int v=0;for(int k=0;k<n;k++){if((val&mask)!=0)v|=1<<k;mask>>=1;if(mask==0){mask=32;i++;val=i<s.length()?ALPHABET.indexOf(s.charAt(i)):0;if(i>s.length()+1)throw new IllegalArgumentException("Urwana kompresja");}}return v;}
 }
 public static String decode(String s){
  if(s==null||s.isEmpty()||s.length()>2000000)throw new IllegalArgumentException("Nieprawidłowe dane");
  Bits b=new Bits(s);List<String>d=new ArrayList<>();d.add("");d.add("");d.add("");
  int first=b.read(2);if(first==2)return "";String w=String.valueOf((char)b.read(first==0?8:16));d.add(w);
  StringBuilder out=new StringBuilder(w);int enlarge=4,n=3;
  while(out.length()<4000000){int c=b.read(n);if(c==0||c==1){d.add(String.valueOf((char)b.read(c==0?8:16)));c=d.size()-1;enlarge--;}
   else if(c==2)return out.toString();
   if(enlarge==0){enlarge=1<<n;n++;}String e;
   if(c<d.size())e=d.get(c);else if(c==d.size())e=w+w.charAt(0);else throw new IllegalArgumentException("Błąd kompresji");
   out.append(e);d.add(w+e.charAt(0));enlarge--;w=e;if(enlarge==0){enlarge=1<<n;n++;}if(n>22)throw new IllegalArgumentException("Za duży słownik");
  }throw new IllegalArgumentException("Za dużo danych");
 }
}
