package com.walktrack.gps

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class MainActivity:Activity(){
 private lateinit var map:MapView;private lateinit var store:TrackStore;private lateinit var root:LinearLayout;private lateinit var stats:TextView;private lateinit var status:TextView
 private var follow=true;private var liveScreen=true;private var rangeKm=2.0;private var selected:Session?=null;private var pendingGpx:String?=null
 private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){refresh()}}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);Configuration.getInstance().userAgentValue=packageName;store=TrackStore(this);buildUi();requestPermissions();refresh()}
 private fun button(label:String,action:()->Unit):Button=Button(this).apply{text=label;setOnClickListener{action()};isAllCaps=false}
 private fun text(s:String,size:Float=16f):TextView=TextView(this).apply{text=s;textSize=size;setTextColor(Color.rgb(22,62,53));setPadding(8,12,8,12)}
 private fun buildUi(){root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,8,12,8);setBackgroundColor(Color.rgb(244,249,247))};val scroll=ScrollView(this);scroll.addView(root);setContentView(scroll)
  root.addView(text("WalkTrack GPS 2.0",27f));root.addView(text("原生 GPS 循跡 · OpenStreetMap · 背景記錄",13f))
  val tabs=LinearLayout(this);root.addView(tabs);tabs.addView(button("即時循跡"){showLive()});tabs.addView(button("歷史紀錄"){showHistory()});tabs.addView(button("每週統計"){showWeekly()})
  showLive()
 }
 private fun clearContent(){while(root.childCount>3)root.removeViewAt(3)}
 private fun showLive(){liveScreen=true;clearContent();val options=LinearLayout(this);root.addView(options);val mode=Spinner(this);mode.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("走路","慢跑"));options.addView(mode)
  val weight=EditText(this).apply{setText("77");inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;hint="體重 kg"};options.addView(weight,LinearLayout.LayoutParams(180,-2))
  map=MapView(this).apply{setTileSource(TileSourceFactory.MAPNIK);setMultiTouchControls(true);controller.setZoom(15.0);visibility=View.INVISIBLE};root.addView(map,LinearLayout.LayoutParams(-1,(resources.displayMetrics.heightPixels*0.48).toInt()))
  // User gestures suspend automatic follow; the location button re-enables it.
  map.setOnTouchListener { _,event->if(event.actionMasked==MotionEvent.ACTION_MOVE)follow=false;false }
  val controls=LinearLayout(this);root.addView(controls)
  controls.addView(button("◎ 回到定位"){follow=true;drawLive(true)})
  controls.addView(button("1 km"){rangeKm=1.0;drawLive(true)})
  controls.addView(button("2 km"){rangeKm=2.0;drawLive(true)})
  controls.addView(button("3 km"){rangeKm=3.0;drawLive(true)})
  stats=text("距離 0.00 km | 時間 00:00:00");root.addView(stats)
  status=text("等待 GPS 定位");root.addView(status)
  val actions=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(actions)
  actions.addView(button("開始循跡"){if(!TrackingService.active){if(!hasLocation()){requestPermissions();return@button};val i=Intent(this,TrackingService::class.java).apply{action=TrackingService.START;putExtra("mode",mode.selectedItem.toString());putExtra("weight",weight.text.toString().toDoubleOrNull()?:77.0)};ContextCompat.startForegroundService(this,i)}})
  actions.addView(button("暫停 / 繼續"){if(TrackingService.active)startService(Intent(this,TrackingService::class.java).apply{action=if(TrackingService.paused)TrackingService.RESUME else TrackingService.PAUSE})})
  actions.addView(button("結束並儲存"){if(TrackingService.active)android.app.AlertDialog.Builder(this).setMessage("確定結束並儲存本次運動？").setPositiveButton("儲存"){_,_->startService(Intent(this,TrackingService::class.java).apply{action=TrackingService.STOP})}.setNegativeButton("取消",null).show()})
  refresh()
 }
 private fun drawLive(force:Boolean=false){if(!liveScreen||!::map.isInitialized||!::stats.isInitialized)return;val s=TrackingService;val points=if(s.active)store.fixes(s.id) else emptyList();draw(points)
  val last=points.lastOrNull();if(last!=null){map.visibility=View.VISIBLE};if(last!=null&&(follow||force)){val center=GeoPoint(last.lat,last.lon);map.controller.animateTo(center);zoomRange(center)}
  val sec=s.elapsed/1000;val pace=if(s.distance>10)(sec/(s.distance/1000)/60) else 0.0
  stats.text="距離 %.2f km   時間 %02d:%02d:%02d\n平均配速 %s   估算熱量 %.0f kcal".format(Locale.US,s.distance/1000,sec/3600,(sec/60)%60,sec%60,if(pace>0)"%d:%02d /km".format(pace.toInt(),((pace%1)*60).toInt()) else "--:-- /km",s.weight*(s.distance/1000)*(if(s.mode=="慢跑")1.0 else 0.5))
  status.text=if(!s.active)"未開始（點擊開始循跡）" else if(s.paused)"已暫停" else "正在記錄 · GPS ${last?.let{"±${it.accuracy.toInt()} m"}?:"等待定位"} · ${if(follow)"自動跟隨" else "手動地圖"}"
 }
 private fun zoomRange(center:GeoPoint){ // rangeKm is approximate map width, not a hard geofence
  val latDelta=rangeKm/111.0;val lonDelta=rangeKm/(111.0*cos(Math.toRadians(center.latitude)).coerceAtLeast(0.2));map.zoomToBoundingBox(org.osmdroid.util.BoundingBox(center.latitude+latDelta/2,center.longitude+lonDelta/2,center.latitude-latDelta/2,center.longitude-lonDelta/2),false,40)}
 private fun draw(points:List<Fix>){map.overlays.clear();points.groupBy{it.segment}.values.forEach{segment->if(segment.size>1){val line=Polyline().apply{setPoints(segment.map{GeoPoint(it.lat,it.lon)});outlinePaint.color=Color.rgb(0,126,91);outlinePaint.strokeWidth=9f};map.overlays.add(line)}};points.lastOrNull()?.let{p->map.overlays.add(Marker(map).apply{position=GeoPoint(p.lat,p.lon);title="目前 / 最後位置"})};map.invalidate()}
 private fun refresh(){if(liveScreen&&::map.isInitialized&&::stats.isInitialized)drawLive()}
 private fun showHistory(){liveScreen=false;clearContent();root.addView(text("歷史路線",22f));store.sessions().filter{it.ended>0}.forEach{s->val date=SimpleDateFormat("yyyy/MM/dd HH:mm",Locale.TAIWAN).format(Date(s.started));root.addView(text("${s.mode} · $date · %.2f km · %d 分鐘".format(s.distance/1000,s.elapsed/60000)));val row=LinearLayout(this);root.addView(row);row.addView(button("地圖"){showSession(s)});row.addView(button("匯出 GPX"){selected=s;pendingGpx=TrackStore.gpx(s,store.fixes(s.id));val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/gpx+xml";putExtra(Intent.EXTRA_TITLE,"walktrack-${s.id}.gpx")};startActivityForResult(intent,40)});row.addView(button("刪除"){android.app.AlertDialog.Builder(this).setMessage("刪除這次路線？").setPositiveButton("刪除"){_,_->store.delete(s.id);showHistory()}.setNegativeButton("取消",null).show()})}}
 private fun showSession(s:Session){liveScreen=false;clearContent();root.addView(text("歷史路線 · ${s.mode}",22f));map=MapView(this).apply{setTileSource(TileSourceFactory.MAPNIK);setMultiTouchControls(true)};root.addView(map,LinearLayout.LayoutParams(-1,(resources.displayMetrics.heightPixels*.58).toInt()));val points=store.fixes(s.id);draw(points);if(points.isNotEmpty()){val lats=points.map{it.lat};val lons=points.map{it.lon};map.post{map.zoomToBoundingBox(org.osmdroid.util.BoundingBox(lats.max(),lons.max(),lats.min(),lons.min()),true,55)}};root.addView(text("距離 %.2f km | 時間 %d 分鐘".format(s.distance/1000,s.elapsed/60000)))}
 private fun showWeekly(){liveScreen=false;clearContent();root.addView(text("每週統計（週一開始）",22f));val calendar=Calendar.getInstance().apply{firstDayOfWeek=Calendar.MONDAY;set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)};val from=calendar.timeInMillis;val sessions=store.sessions().filter{it.ended>0&&it.started>=from};root.addView(text("本週 ${sessions.size} 次 | %.2f km | %d 分鐘".format(sessions.sumOf{it.distance}/1000,sessions.sumOf{it.elapsed}/60000),21f));root.addView(text("資料僅保存在本機，建議定期匯出 GPX 備份。"))}
 private fun hasLocation()=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
 private fun requestPermissions(){val p=mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION);if(Build.VERSION.SDK_INT>=33)p.add(Manifest.permission.POST_NOTIFICATIONS);ActivityCompat.requestPermissions(this,p.toTypedArray(),1)}
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==40&&resultCode==RESULT_OK){val u=data?.data;val content=pendingGpx;if(u!=null&&content!=null)try{contentResolver.openOutputStream(u)?.use{it.write(content.toByteArray(Charsets.UTF_8))};Toast.makeText(this,"GPX 已匯出",Toast.LENGTH_SHORT).show()}catch(e:Exception){Toast.makeText(this,"匯出失敗：${e.message}",Toast.LENGTH_LONG).show()};pendingGpx=null}}
 override fun onResume(){super.onResume();if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,android.content.IntentFilter(TrackingService.ACTION_UPDATE),Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,android.content.IntentFilter(TrackingService.ACTION_UPDATE));if(::map.isInitialized)map.onResume();refresh()}
 override fun onPause(){if(::map.isInitialized)map.onPause();unregisterReceiver(receiver);super.onPause()}
}
