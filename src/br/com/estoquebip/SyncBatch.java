package br.com.estoquebip;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Builds versioned events without performing network requests or exposing secrets. */
final class SyncBatch {
 private SyncBatch() {}
 static List<JSONObject> pending(SQLiteDatabase db, String campaignId, String deviceId, int limit) throws Exception {
  if(campaignId==null||deviceId==null||campaignId.isEmpty()||deviceId.isEmpty())
   throw new IllegalArgumentException("Campanha e dispositivo obrigatorios");
  List<JSONObject> events = new ArrayList<>();
  try(Cursor c=db.rawQuery(
   "SELECT event_uuid,count_uuid,action,item,codigo_lido,local,quantidade,unidade,pecas_extras,pack_pc,total_pc,recorded_at,changed_at FROM inv_anual_outbox WHERE acknowledged_at IS NULL ORDER BY queued_at,rowid LIMIT ?",
   new String[]{Integer.toString(Math.max(1,Math.min(limit,100)))})) {
    while(c.moveToNext()){
     JSONObject event=new JSONObject();
     event.put("evento_id",c.getString(0));event.put("contagem_id",c.getString(1));
     event.put("tipo",c.getString(2));event.put("campanha_id",campaignId);event.put("dispositivo_id",deviceId);
     event.put("produto_codigo",c.getString(3));
     event.put("codigo_lido",c.isNull(4)?c.getString(3):c.getString(4));
     event.put("local_codigo",c.getString(5));event.put("quantidade",c.getString(6));
     event.put("unidade",c.getString(7));event.put("pecas_extras",c.getString(8));
     event.put("pack_pc",c.getString(9));event.put("total_pc",c.getString(10));
     event.put("registrado_em",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",java.util.Locale.US).format(new java.util.Date(c.getLong(11))));
     event.put("versao",1); // Placeholder: real per-count versions must be persisted before enabling delivery.
     events.add(event);
    }
  }
  return events;
 }
}
