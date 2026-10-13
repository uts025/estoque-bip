package br.com.estoquebip;

import android.content.Context;
import org.json.JSONObject;
import java.util.UUID;

/** Identity/device context for a future authorized inventory campaign. */
final class InventoryIdentity {
 private InventoryIdentity() {}
 static String deviceId(Context context) {
  android.content.SharedPreferences prefs=context.getSharedPreferences("inv_anual_device",Context.MODE_PRIVATE);
  String existing=prefs.getString("uuid",null);
  if(existing!=null)return existing;
  String id=UUID.randomUUID().toString();
  if(!prefs.edit().putString("uuid",id).commit())throw new IllegalStateException("Falha ao salvar ID do dispositivo");
  return id;
 }
 static void ensureSession(Context context,RotinaAuth.Callback callback) {
  JSONObject current=SecureSession.load(context);
  if(current==null){callback.complete(null,"Entre com a conta Rotina360.");return;}
  long expiresAt=current.optLong("expires_at",0);
  if(expiresAt>System.currentTimeMillis()/1000L+120){callback.complete(current,null);return;}
  String refresh=current.optString("refresh_token","");
  if(refresh.isEmpty()){callback.complete(null,"Sessão precisa ser renovada.");return;}
  RotinaAuth.refresh(refresh,(updated,error)->{
   if(updated==null){callback.complete(null,error);return;}
   if(!updated.optString("user_id").equals(current.optString("user_id"))){
    SecureSession.clear(context);
    callback.complete(null,"Identidade da sessão alterada. Entre novamente.");return;
   }
   try{SecureSession.save(context,updated);callback.complete(updated,null);}
   catch(Exception e){callback.complete(null,"Não foi possível proteger a sessão renovada.");}
  });
 }
}
