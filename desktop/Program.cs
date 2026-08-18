using System;
using System.Drawing;
using System.IO;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;
namespace NovaAI {
internal static class Program { [STAThread] static void Main(){Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);Application.Run(new NovaWindow());}}
internal sealed class NovaWindow:Form {
 readonly WebView2 webView=new WebView2(); CoreWebView2Environment environment; ProjectHost projectHost;
 public NovaWindow(){Text="NOVA AI";Icon=Icon.ExtractAssociatedIcon(Application.ExecutablePath);BackColor=Color.FromArgb(8,9,12);MinimumSize=new Size(900,620);Size=new Size(1440,900);StartPosition=FormStartPosition.CenterScreen;webView.Dock=DockStyle.Fill;webView.DefaultBackgroundColor=Color.FromArgb(8,9,12);Controls.Add(webView);Shown+=async(_,__)=>await InitializeAsync();}
 async Task InitializeAsync(){try{string userData=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"NOVA AI","WebView2");Directory.CreateDirectory(userData);environment=await CoreWebView2Environment.CreateAsync(null,userData);await webView.EnsureCoreWebView2Async(environment);string appFolder=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"app");if(!File.Exists(Path.Combine(appFolder,"index.html")))throw new FileNotFoundException("Не найдены файлы интерфейса NOVA AI.");webView.CoreWebView2.SetVirtualHostNameToFolderMapping("nova.local",appFolder,CoreWebView2HostResourceAccessKind.Allow);webView.CoreWebView2.Settings.IsStatusBarEnabled=false;webView.CoreWebView2.Settings.IsZoomControlEnabled=true;await webView.CoreWebView2.AddScriptToExecuteOnDocumentCreatedAsync("window.NOVA_DESKTOP=true;");projectHost=new ProjectHost(this,webView);webView.CoreWebView2.PermissionRequested+=(_,e)=>{if(e.PermissionKind==CoreWebView2PermissionKind.Microphone){e.State=CoreWebView2PermissionState.Allow;e.Handled=true;}};webView.CoreWebView2.NewWindowRequested+=HandleNewWindow;webView.Source=new Uri("https://nova.local/index.html");}catch(Exception ex){MessageBox.Show("Не удалось запустить NOVA AI.\n\n"+ex.Message,"NOVA AI",MessageBoxButtons.OK,MessageBoxIcon.Error);Close();}}
 async void HandleNewWindow(object sender,CoreWebView2NewWindowRequestedEventArgs e){var deferral=e.GetDeferral();try{var popup=new AuthWindow();await popup.PrepareAsync(environment);e.NewWindow=popup.Browser.CoreWebView2;e.Handled=true;popup.Show(this);}catch(Exception ex){e.Handled=true;MessageBox.Show("Не удалось открыть авторизацию.\n\n"+ex.Message,"NOVA AI",MessageBoxButtons.OK,MessageBoxIcon.Warning);}finally{deferral.Complete();}}
}
internal sealed class AuthWindow:Form {internal readonly WebView2 Browser=new WebView2();internal AuthWindow(){Text="Вход в NOVA AI";Icon=Icon.ExtractAssociatedIcon(Application.ExecutablePath);BackColor=Color.White;Size=new Size(520,720);MinimumSize=new Size(420,560);StartPosition=FormStartPosition.CenterParent;Browser.Dock=DockStyle.Fill;Controls.Add(Browser);}internal async Task PrepareAsync(CoreWebView2Environment environment){await Browser.EnsureCoreWebView2Async(environment);Browser.CoreWebView2.Settings.IsStatusBarEnabled=false;Browser.CoreWebView2.WindowCloseRequested+=(_,__)=>Close();}}
}
