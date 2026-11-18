package com.walktrack.gps

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

data class Fix(val lat: Double, val lon: Double, val time: Long, val accuracy: Float, val segment: Int)
data class Session(val id: Long, val started: Long, val ended: Long, val mode: String, val weight: Double, val distance: Double, val elapsed: Long)
class TrackStore(ctx: Context): SQLiteOpenHelper(ctx, "walktrack.db", null, 1) {
 override fun onCreate(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT, started INTEGER, ended INTEGER, mode TEXT, weight REAL, distance REAL, elapsed INTEGER)")
  db.execSQL("CREATE TABLE fixes(id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER, lat REAL, lon REAL, time INTEGER, accuracy REAL, segment INTEGER)")
 }
 override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
 fun begin(mode: String, weight: Double): Long { val v=android.content.ContentValues().apply { put("started",System.currentTimeMillis());put("ended",0);put("mode",mode);put("weight",weight);put("distance",0.0);put("elapsed",0) };return writableDatabase.insert("sessions",null,v) }
 fun add(id:Long,f:Fix) { val v=android.content.ContentValues().apply { put("session_id",id);put("lat",f.lat);put("lon",f.lon);put("time",f.time);put("accuracy",f.accuracy);put("segment",f.segment) };writableDatabase.insert("fixes",null,v) }
 fun update(id:Long,dist:Double,elapsed:Long,ended:Boolean=false) { val v=android.content.ContentValues().apply {put("distance",dist);put("elapsed",elapsed);if(ended)put("ended",System.currentTimeMillis())};writableDatabase.update("sessions",v,"id=?",arrayOf(id.toString())) }
 fun sessions():List<Session> { val out= mutableListOf<Session>();readableDatabase.rawQuery("SELECT id,started,ended,mode,weight,distance,elapsed FROM sessions ORDER BY started DESC",null).use { c->while(c.moveToNext())out.add(Session(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),c.getDouble(4),c.getDouble(5),c.getLong(6))) };return out }
 fun fixes(id:Long):List<Fix> {val out= mutableListOf<Fix>();readableDatabase.rawQuery("SELECT lat,lon,time,accuracy,segment FROM fixes WHERE session_id=? ORDER BY id",arrayOf(id.toString())).use { c->while(c.moveToNext())out.add(Fix(c.getDouble(0),c.getDouble(1),c.getLong(2),c.getFloat(3),c.getInt(4))) };return out}
 fun delete(id:Long){writableDatabase.delete("fixes","session_id=?",arrayOf(id.toString()));writableDatabase.delete("sessions","id=?",arrayOf(id.toString()))}
 companion object {
  fun meters(a:Fix,b:Fix):Double {val r=6371000.0;val dLat=Math.toRadians(b.lat-a.lat);val dLon=Math.toRadians(b.lon-a.lon);val x=sin(dLat/2).pow(2.0)+cos(Math.toRadians(a.lat))*cos(Math.toRadians(b.lat))*sin(dLon/2).pow(2.0);return 2*r*asin(sqrt(x.coerceIn(0.0,1.0)))}
  fun gpx(s:Session, points:List<Fix>):String {val fmt=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US).apply{timeZone=java.util.TimeZone.getTimeZone("UTC")};return buildString {append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"WalkTrack GPS\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>WalkTrack ${s.id}</name>");var last=-1;points.forEach{p->if(p.segment!=last){if(last!=-1)append("</trkseg>");append("<trkseg>");last=p.segment};append("<trkpt lat=\"${p.lat}\" lon=\"${p.lon}\"><time>${fmt.format(Date(p.time))}</time></trkpt>")};if(last!=-1)append("</trkseg>");append("</trk></gpx>")}}
 }
}
