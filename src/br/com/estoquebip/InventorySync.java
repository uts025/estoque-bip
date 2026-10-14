package br.com.estoquebip;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Executors;

/** Explicit synchronization. Never marks an event sent without an exact server acknowledgement. */
final class InventorySync {
 interface Callback { void done(int sent, int remaining, String error); }
 private static final java.util.concurrent.ExecutorService WORK=Executors.newSingleThreadExecutor();
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private InventorySync(){}
 static void send(Context context,SQLiteDatabase db,String campaign,Callback callback){
  if(campaign==null||!campaign.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")){
   callback.done(0,0,"Informe o identificador válido da campanha.");return;
  }
  InventoryIdentity.ensureSession(context,(session,reason)->{
   if(session==null){callback.done(0,0,reason==null?"Login necessário":reason);return;}
   final String bearer=session.optString("access_token","");
   if(bearer.isEmpty()){callback.done(0,0,"Sessão inválida.");return;}
   WORK.execute(()->{
    int sent=0,remaining=-1;String failure=null;
    try{
     List<JSONObject> batch=SyncBatch.pending(db,campaign,InventoryIdentity.deviceId(context),30);
     for(JSONObject event:batch){
      HttpURLConnection conn=null;
      try{
       conn=(HttpURLConnection)new URL(RotinaAuth.PROJECT_URL+"/rest/v1/rpc/inv_anual_receber_evento").openConnection();
       conn.setRequestMethod("POST");conn.setConnectTimeout(10000);conn.setReadTimeout(15000);
       conn.setDoOutput(true);
       conn.setRequestProperty("Content-Type","application/json");
       conn.setRequestProperty("apikey",RotinaAuth.publicAnonKey());
       conn.setRequestProperty("Authorization","Bearer "+bearer);
       byte[] bytes=new JSONObject().put("p_payload",event).toString().getBytes("UTF-8");
       try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
       int status=conn.getResponseCode();
       if(status!=200){failure="Servidor não confirmou evento (HTTP "+status+"). Verifique campanha e permissões.";break;}
       java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();
       try(InputStream in=conn.getInputStream()){
        byte[] buf=new byte[1024];int n;
        while((n=in.read(buf))!=-1){if(data.size()+n>8192)throw new Exception("Resposta extensa");data.write(buf,0,n);}
       }
       JSONObject reply=new JSONObject(data.toString("UTF-8"));
       if(!event.getString("evento_id").equals(reply.optString("evento_id"))||!"confirmado".equals(reply.optString("status"))){
        failure="Confirmação inválida. Eventos preservados.";break;
       }
       OfflineOutbox.acknowledge(db,event.getString("evento_id"));sent++;
      }finally{if(conn!=null)conn.disconnect();}
     }
    }catch(Exception e){failure="Falha na sincronização. Registros permanecem no aparelho.";}
    try(Cursor c=db.rawQuery("SELECT count(*) FROM inv_anual_outbox WHERE acknowledged_at IS NULL",null)){
     if(c.moveToFirst())remaining=c.getInt(0);
    }catch(Exception ignored){}
    int done=sent,left=remaining;String error=failure;
    MAIN.post(()->callback.done(done,left,error));
   });
  });
 }
}
