package ai.nova.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

final class AndroidUpdater {
    private static final String MANIFEST_URL="https://raw.githubusercontent.com/Niostv/NOVA-AI-Releases/main/latest-android.json";
    private final Activity activity; private final WebView web; private final SharedPreferences prefs;
    private JSONObject latest; private File apk;
    AndroidUpdater(Activity activity,WebView web){this.activity=activity;this.web=web;this.prefs=activity.getSharedPreferences("nova-updater",0);}

    @JavascriptInterface public void getInfo(){sendInfo(null);}
    @JavascriptInterface public void check(boolean manual){new Thread(()->checkNow(manual)).start();}
    @JavascriptInterface public void setAutoCheck(boolean enabled){prefs.edit().putBoolean("auto",enabled).apply();sendInfo(null);}
    @JavascriptInterface public void download(){new Thread(this::downloadNow).start();}
    @JavascriptInterface public void install(){activity.runOnUiThread(this::openInstaller);}

    private void checkNow(boolean manual){try{HttpURLConnection c=open(MANIFEST_URL+"?t="+System.currentTimeMillis());String raw=read(c.getInputStream());latest=new JSONObject(raw);require(latest.optInt("versionCode",0)>0,"Неверный versionCode");require(latest.optString("apkUrl").startsWith("https://"),"APK должен загружаться по HTTPS");require(latest.optString("sha256").matches("(?i)[0-9a-f]{64}"),"Неверная SHA-256 сумма");prefs.edit().putLong("lastCheck",System.currentTimeMillis()).apply();sendInfo(manual&&!available()?"Обновление не найдено":null);}catch(Exception e){sendError(e.getMessage());}}
    private void downloadNow(){try{require(latest!=null,"Сначала проверьте обновления");URL url=new URL(latest.getString("apkUrl"));HttpURLConnection c=open(url.toString());int total=c.getContentLength();File dir=activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);if(dir==null)dir=activity.getCacheDir();apk=new File(dir,"NOVA-AI-Android-"+latest.getString("versionName")+".apk");try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(apk)){byte[] b=new byte[65536];long done=0;int n,last=-1;while((n=in.read(b))!=-1){out.write(b,0,n);done+=n;int p=total>0?(int)(done*100/total):0;if(p!=last){last=p;progress(p);}}}require(total<=0||apk.length()==total,"APK скачан не полностью");require(sha256(apk).equalsIgnoreCase(latest.getString("sha256")),"Контрольная сумма APK не совпала");verifyPackage(apk);progress(100);sendInfo(null);}catch(Exception e){if(apk!=null)apk.delete();apk=null;sendError(e.getMessage());}}
    private void verifyPackage(File file)throws Exception{PackageManager pm=activity.getPackageManager();PackageInfo pi=pm.getPackageArchiveInfo(file.getAbsolutePath(),0);require(pi!=null,"APK повреждён");require(activity.getPackageName().equals(pi.packageName),"APK принадлежит другому приложению");long code=Build.VERSION.SDK_INT>=28?pi.getLongVersionCode():pi.versionCode;require(code>=latest.getLong("versionCode"),"В APK находится старая версия");}
    private void openInstaller(){try{if(apk==null||!apk.exists()){sendError("Обновление ещё не скачано");return;}if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));sendError("Разрешите установку из NOVA AI, затем нажмите «Установить обновление» ещё раз");return;}Uri uri=Uri.parse("content://"+activity.getPackageName()+".updates/"+apk.getName());Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);activity.startActivity(i);}catch(Exception e){sendError(e.getMessage());}}
    private void sendInfo(String message){try{PackageInfo pi=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);long current=Build.VERSION.SDK_INT>=28?pi.getLongVersionCode():pi.versionCode;JSONObject o=new JSONObject();o.put("currentName",pi.versionName);o.put("currentCode",current);o.put("latestName",latest==null?JSONObject.NULL:latest.optString("versionName"));o.put("latestCode",latest==null?0:latest.optInt("versionCode"));o.put("available",latest!=null&&latest.optInt("versionCode")>current);o.put("downloaded",apk!=null&&apk.exists());o.put("autoCheck",prefs.getBoolean("auto",true));o.put("notes",latest==null?"":latest.optString("notes"));if(message!=null)o.put("message",message);emit("info",o);}catch(Exception e){sendError(e.getMessage());}}
    private void progress(int p){try{JSONObject o=new JSONObject();o.put("progress",p);emit("progress",o);}catch(Exception ignored){}}
    private void sendError(String text){try{JSONObject o=new JSONObject();o.put("message",text==null?"Ошибка обновления":text);emit("error",o);}catch(Exception ignored){}}
    private void emit(String type,JSONObject data){activity.runOnUiThread(()->web.evaluateJavascript("window.NovaAndroidUpdater&&window.NovaAndroidUpdater.receive("+JSONObject.quote(type)+","+data.toString()+")",null));}
    private boolean available()throws Exception{PackageInfo pi=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);long code=Build.VERSION.SDK_INT>=28?pi.getLongVersionCode():pi.versionCode;return latest.optInt("versionCode")>code;}
    private HttpURLConnection open(String value)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(value).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setUseCaches(false);c.setInstanceFollowRedirects(true);c.setRequestProperty("Cache-Control","no-cache");c.setRequestProperty("Accept","application/json, application/vnd.android.package-archive, */*");c.setRequestProperty("User-Agent","NOVA-AI-Android-Updater/1.1");require(c.getResponseCode()>=200&&c.getResponseCode()<300,"Сервер обновлений вернул "+c.getResponseCode());return c;}
    private static String read(InputStream in)throws Exception{try(InputStream x=in){byte[] b=new byte[8192];StringBuilder s=new StringBuilder();int n;while((n=x.read(b))!=-1)s.append(new String(b,0,n,"UTF-8"));return s.toString();}}
    private static String sha256(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format("%02X",x));return s.toString();}
    private static void require(boolean ok,String message)throws Exception{if(!ok)throw new Exception(message);}
}
