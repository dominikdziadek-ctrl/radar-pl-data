#!/usr/bin/env python3
"""Dependency-free APK build. Needs JDK 17, Android SDK platform 35/build-tools 35.0.0."""
import os, pathlib, subprocess, zipfile
root=pathlib.Path(__file__).resolve().parent
sdk=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
if not sdk: raise SystemExit('Set ANDROID_SDK_ROOT to Android SDK directory.')
sdk=pathlib.Path(sdk);bt=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-35/android.jar'
out=root/'build/manual';out.mkdir(parents=True,exist_ok=True)
classes=out/'classes';classes.mkdir(exist_ok=True);dex=out/'dex';dex.mkdir(exist_ok=True)
def run(*a): subprocess.run([str(v) for v in a],check=True,cwd=root)
java=pathlib.Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else 'java'
run(java,'com.sun.tools.javac.Main','-source','17','-target','17','-classpath',android,'-d',classes,*sorted((root/'app/src/main/java').rglob('*.java')))
with zipfile.ZipFile(out/'classes.jar','w',zipfile.ZIP_DEFLATED) as z:
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
run(java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',android,'--min-api','26','--output',dex,out/'classes.jar')
run(bt/'aapt2','link','--manifest',root/'app/src/main/AndroidManifest.xml','-I',android,'--min-sdk-version','26','--target-sdk-version','35','-A',root/'app/src/main/assets','-o',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for f in dex.glob('*.dex'):z.write(f,f.name)
run(bt/'zipalign','-f','-p','4',out/'unsigned.apk',out/'aligned.apk')
run(java,'-jar',bt/'lib/apksigner.jar','sign','--ks',root/'signing/debug.keystore','--ks-pass','pass:android','--key-pass','pass:android','--out',out/'RadarPL-0.4.0.apk',out/'aligned.apk')
run(java,'-jar',bt/'lib/apksigner.jar','verify','--verbose',out/'RadarPL-0.4.0.apk')
print(out/'RadarPL-0.4.0.apk')
