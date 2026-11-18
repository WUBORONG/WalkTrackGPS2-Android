package com.walktrack.gps

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class TrackingService: Service() {
 companion object {const val CHANNEL="walktrack_location";const val ACTION_UPDATE="com.walktrack.gps.UPDATE";const val START="start";const val PAUSE="pause";const val RESUME="resume";const val STOP="stop";var active=false;var paused=false;var id=0L;var distance=0.0;var elapsed=0L;var last:Fix?=null;var segment=0;var startedAt=0L;var accumulated=0L;var mode="走路";var weight=77.0 }
 private lateinit var client:FusedLocationProviderClient
 private lateinit var store:TrackStore
 private val handler=Handler(Looper.getMainLooper())
 private val ticker=object:Runnable {override fun run(){if(active){elapsed=accumulated+(if(paused)0 else System.currentTimeMillis()-startedAt);store.update(id,distance,elapsed);broadcast();handler.postDelayed(this,1000)}}}
 private val callback=object:LocationCallback(){override fun onLocationResult(result:LocationResult){result.locations.forEach{record(it)}}}
 override fun onCreate(){super.onCreate();client=LocationServices.getFusedLocationProviderClient(this);store=TrackStore(this);val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel(CHANNEL,"GPS 循跡",NotificationManager.IMPORTANCE_LOW))}
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  when(intent?.action){START->{if(!active){mode=intent.getStringExtra("mode")?:"走路";weight=intent.getDoubleExtra("weight",77.0);id=store.begin(mode,weight);distance=0.0;elapsed=0;accumulated=0;segment=0;last=null;paused=false;active=true;startedAt=System.currentTimeMillis();startForeground(7,notification());requestUpdates();handler.post(ticker)}}
   PAUSE->{if(active&&!paused){accumulated+=System.currentTimeMillis()-startedAt;paused=true;last=null;segment++;broadcast()}}
   RESUME->{if(active&&paused){paused=false;startedAt=System.currentTimeMillis();last=null;segment++;broadcast()}}
   STOP->{if(active){elapsed=accumulated+(if(paused)0 else System.currentTimeMillis()-startedAt);store.update(id,distance,elapsed,true);active=false;client.removeLocationUpdates(callback);handler.removeCallbacks(ticker);broadcast();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}}
  }
  return START_NOT_STICKY
 }
 private fun notification():Notification {val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("WalkTrack GPS 正在記錄").setContentText("鎖定螢幕仍持續循跡，點擊返回 App").setOngoing(true).setContentIntent(open).build()}
 private fun requestUpdates(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;val request=LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,3000L).setMinUpdateIntervalMillis(1500L).setMinUpdateDistanceMeters(3f).build();client.requestLocationUpdates(request,callback,Looper.getMainLooper())}
 private fun record(l:Location){if(!active||paused||!l.hasAccuracy()||l.accuracy>35f)return;val f=Fix(l.latitude,l.longitude,l.time,l.accuracy,segment);val prev=last
  if(prev!=null){val dt=(f.time-prev.time)/1000.0;if(dt<=0)return;val m=TrackStore.meters(prev,f)
   if(dt>15){segment++;last=null;store.add(id,f.copy(segment=segment));last=f.copy(segment=segment);broadcast();return}
   if(m<3)return
   if(m/dt>8.0)return // reject implausible speed for walking/running
   distance+=m
  }
  last=f;store.add(id,f);store.update(id,distance,elapsed);broadcast()
 }
 private fun broadcast(){sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName))}
 override fun onDestroy(){handler.removeCallbacks(ticker);client.removeLocationUpdates(callback);super.onDestroy()}
}
