package br.com.estoquebip;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Full-location recount. Old counts are replaced only at final confirmation.
 * SQLite outbox triggers retain deletion and insertion events for audit.
 */
final class LocationRevision {
 private LocationRevision(){}
 static void install(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS inv_anual_revisao_rascunho (id INTEGER PRIMARY KEY AUTOINCREMENT, local TEXT NOT NULL, item TEXT NOT NULL, codigo_lido TEXT, quantidade TEXT NOT NULL, unidade TEXT, pecas_extras TEXT, pack_pc TEXT, total_pc TEXT, criado_em INTEGER NOT NULL)");
 }
 static int count(SQLiteDatabase db,String location){
  try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM inv_anual_revisao_rascunho WHERE local=?",new String[]{location})){return c.moveToFirst()?c.getInt(0):0;}
 }
 static int previous(SQLiteDatabase db,String location){
  try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM registros WHERE local=?",new String[]{location})){return c.moveToFirst()?c.getInt(0):0;}
 }
 static void stage(SQLiteDatabase db,ContentValues record){
  ContentValues v=new ContentValues();
  for(String key:new String[]{"local","item","codigo_lido","quantidade","unidade","pecas_extras","pack_pc","total_pc"})v.put(key,record.getAsString(key));
  v.put("criado_em",System.currentTimeMillis());
  db.insertOrThrow("inv_anual_revisao_rascunho",null,v);
 }
 static void discard(SQLiteDatabase db,String location){db.delete("inv_anual_revisao_rascunho","local=?",new String[]{location});}
 static void replace(SQLiteDatabase db,String location){
  if(location==null||location.trim().isEmpty()||count(db,location)<1)throw new IllegalStateException("Revisão vazia");
  db.beginTransaction();
  try{
   // Deletion trigger captures all superseded counts before replacement.
   db.delete("registros","local=?",new String[]{location});
   try(Cursor c=db.rawQuery("SELECT item,codigo_lido,quantidade,unidade,pecas_extras,pack_pc,total_pc FROM inv_anual_revisao_rascunho WHERE local=? ORDER BY id",new String[]{location})){
    while(c.moveToNext()){
     ContentValues row=new ContentValues();
     row.put("sync_uuid",java.util.UUID.randomUUID().toString());row.put("sync_status","PENDENTE");
     row.put("item",c.getString(0));row.put("codigo_lido",c.getString(1));row.put("local",location);
     row.put("quantidade",c.getString(2));row.put("unidade",c.getString(3));
     row.put("pecas_extras",c.getString(4));row.put("pack_pc",c.getString(5));row.put("total_pc",c.getString(6));
     row.put("data",System.currentTimeMillis());db.insertOrThrow("registros",null,row);
    }
   }
   db.delete("inv_anual_revisao_rascunho","local=?",new String[]{location});
   db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
}
