package br.com.estoquebip;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reuses existing Rotina360 auth-login without modifying that endpoint. */
final class RotinaAuth {
 static final String PROJECT_URL="https://acrfztgyuuisjtxbgxih.supabase.co";
 // Public anonymous JWT required by legacy verify_jwt gateway. NOT a service role key.
 private static final String ANON_KEY="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFjcmZ6dGd5dXVpc2p0eGJneGloIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODE5MzY5NzUsImV4cCI6MjA5NzUxMjk3NX0.pwr-jnIQYL2mi5QSS0d1WoEhC_RYZPbEv6mZzwG2yDs";
 private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
 interface Callback { void complete(JSONObject session,String error); }
 static void login(String identifier,String password,Callback callback) {
  EXECUTOR.execute(()->{
   JSONObject result=null;String error=null;HttpURLConnection connection=null;
   try{
    connection=(HttpURLConnection)new URL(PROJECT_URL+"/functions/v1/auth-login").openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
    connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
    connection.setRequestProperty("apikey",ANON_KEY);
    connection.setRequestProperty("Authorization","Bearer "+ANON_KEY);
    // The endpoint may require a publishable key at its gateway; configure
    // this separately instead of embedding a service-role credential.
    JSONObject body=new JSONObject().put("identificador",identifier).put("senha",password);
    try(OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}
    int status=connection.getResponseCode();
    InputStream input=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
    java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();
    if(input!=null)try(InputStream in=input){byte[] buffer=new byte[2048];int n;while((n=in.read(buffer))!=-1){if(data.size()+n>32768)throw new Exception("Resposta excede limite");data.write(buffer,0,n);}}
    JSONObject response=new JSONObject(data.toString("UTF-8"));
    if(status==200&&response.has("access_token")&&response.has("refresh_token")&&response.has("user_id"))result=response;
    else error=status==429?"Muitas tentativas. Aguarde e tente novamente.":status==401?"Credenciais inválidas.":"Não foi possível autenticar ("+status+").";
   }catch(Exception ex){error="Falha de conexão com o Rotina360."; }
   finally{if(connection!=null)connection.disconnect();}
   final JSONObject finalResult=result;final String finalError=error;
   new Handler(Looper.getMainLooper()).post(()->callback.complete(finalResult,finalError));
  });
 }
}
