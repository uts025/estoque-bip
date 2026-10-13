package br.com.estoquebip;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Tokens encrypted at rest; passwords are never persisted. */
final class SecureSession {
 private static final String ALIAS="estoquebip.rotina360.session.v1";
 private static final String PREFS="inv_anual_secure";
 private SecureSession() {}
 private static SecretKey key() throws Exception {
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
  KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setRandomizedEncryptionRequired(true).build());
  return generator.generateKey();
 }
 static void save(Context context,JSONObject session) throws Exception {
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
  byte[] value=cipher.doFinal(session.toString().getBytes("UTF-8"));
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
    .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
    .putString("payload",Base64.encodeToString(value,Base64.NO_WRAP)).commit();
 }
 static JSONObject load(Context context) {
  try{
   String iv=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("iv",null);
   String payload=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("payload",null);
   if(iv==null||payload==null)return null;
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
   return new JSONObject(new String(cipher.doFinal(Base64.decode(payload,Base64.NO_WRAP)),"UTF-8"));
  }catch(Exception e){return null;}
 }
 static void clear(Context context) {
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().commit();
 }
}
