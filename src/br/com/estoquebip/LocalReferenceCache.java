package br.com.estoquebip;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Read-only local mirror of annual-inventory reference datasets.
 * Writes are atomic per source snapshot; operational counts are never touched.
 * No Warehouse/E-commerce source is consumed.
 */
final class LocalReferenceCache {
 private LocalReferenceCache() {}
 static void install(SQLiteDatabase db) {
  db.execSQL("CREATE TABLE IF NOT EXISTS inv_anual_referencia ("+
      "fonte TEXT NOT NULL, chave TEXT NOT NULL, descricao TEXT, filial TEXT,"+
      "local TEXT, quantidade TEXT, unidade TEXT, pack_pc TEXT,"+
      "versao TEXT NOT NULL, PRIMARY KEY(fonte,chave))");
  db.execSQL("CREATE TABLE IF NOT EXISTS inv_anual_versao_base ("+
      "fonte TEXT PRIMARY KEY, versao TEXT NOT NULL, atualizado_em INTEGER NOT NULL)");
 }
 static String version(SQLiteDatabase db,String source) {
  try(Cursor c=db.rawQuery("SELECT versao FROM inv_anual_versao_base WHERE fonte=?",new String[]{source})){
   return c.moveToFirst()?c.getString(0):null;
  }
 }
 static void replace(SQLiteDatabase db,String source,String version,Iterable<ContentValues> entries) {
  if(!source.equals("saldo_detalhado")&&!source.equals("cadastro_item")&&!source.equals("locais_manuais")&&!source.equals("packs"))throw new IllegalArgumentException("Fonte desconhecida");
  if(version==null||version.trim().isEmpty())throw new IllegalArgumentException("Versao obrigatoria");
  db.beginTransaction();
  try {
   db.delete("inv_anual_referencia","fonte=?",new String[]{source});
   for(ContentValues original:entries){
    ContentValues entry=new ContentValues(original);
    entry.put("fonte",source);entry.put("versao",version);
    if(entry.getAsString("chave")==null||entry.getAsString("chave").trim().isEmpty())throw new IllegalArgumentException("Chave vazia");
    db.insertOrThrow("inv_anual_referencia",null,entry);
   }
   ContentValues meta=new ContentValues();meta.put("fonte",source);meta.put("versao",version);meta.put("atualizado_em",System.currentTimeMillis());
   db.insertWithOnConflict("inv_anual_versao_base",null,meta,SQLiteDatabase.CONFLICT_REPLACE);
   db.setTransactionSuccessful();
  } finally {db.endTransaction();}
 }
}
