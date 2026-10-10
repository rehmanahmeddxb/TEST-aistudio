import os
import textwrap
from pathlib import Path

ROOT = Path("childcare-app")

def w(rel_path: str, content: str):
    full = ROOT / rel_path
    full.parent.mkdir(parents=True, exist_ok=True)
    # Use explicit newline handling so YAML doesn't get mangled
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        f.write(textwrap.dedent(content).lstrip("\n"))
    print(f"  created: {full}")

# ---------------------------------------------------------------- helper for YAML (NO dedent, raw string)
def w_raw(rel_path: str, content: str):
    full = ROOT / rel_path
    full.parent.mkdir(parents=True, exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"  created: {full}")


# ================================================================
# 1. GitHub Actions workflow  -- written FIRST and verified
# ================================================================
WORKFLOW_YAML = """\
name: Build Child, Parent APKs and Backend Bundle

on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]
  workflow_dispatch:

env:
  DOTNET_VERSION: '8.0.x'
  CONFIGURATION: 'Release'

jobs:
  build-child:
    name: Build Child APK
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup .NET
        uses: actions/setup-dotnet@v4
        with:
          dotnet-version: ${{ env.DOTNET_VERSION }}

      - name: Install MAUI workload
        run: dotnet workload install maui-android

      - name: Restore
        run: dotnet restore ChildApp/ChildApp.csproj

      - name: Publish Child APK
        run: dotnet publish ChildApp/ChildApp.csproj -f net8.0-android -c ${{ env.CONFIGURATION }} -p:AndroidPackageFormat=apk -o ./out-child

      - name: Upload Child APK
        uses: actions/upload-artifact@v4
        with:
          name: child-apk
          path: ./out-child/**/*.apk
          if-no-files-found: error

  build-parent:
    name: Build Parent APK
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup .NET
        uses: actions/setup-dotnet@v4
        with:
          dotnet-version: ${{ env.DOTNET_VERSION }}

      - name: Install MAUI workload
        run: dotnet workload install maui-android

      - name: Restore
        run: dotnet restore ParentApp/ParentApp.csproj

      - name: Publish Parent APK
        run: dotnet publish ParentApp/ParentApp.csproj -f net8.0-android -c ${{ env.CONFIGURATION }} -p:AndroidPackageFormat=apk -o ./out-parent

      - name: Upload Parent APK
        uses: actions/upload-artifact@v4
        with:
          name: parent-apk
          path: ./out-parent/**/*.apk
          if-no-files-found: error

  package-backend:
    name: Package Backend
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.11'

      - name: Install deps (sanity check)
        run: |
          cd backend
          python -m pip install --upgrade pip
          pip install -r requirements.txt

      - name: Create backend zip
        run: |
          cd backend
          zip -r ../backend.zip . -x "*.db" -x "__pycache__/*"

      - name: Upload Backend bundle
        uses: actions/upload-artifact@v4
        with:
          name: backend-bundle
          path: backend.zip
          if-no-files-found: error
"""

w_raw(".github/workflows/build.yml", WORKFLOW_YAML)


# ================================================================
# 2. Backend
# ================================================================
w("backend/app.py", '''
from flask import Flask, request, jsonify
from flask_sqlalchemy import SQLAlchemy

app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///security_system.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
db = SQLAlchemy(app)


class LocationLog(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    device_uid = db.Column(db.String(100), nullable=False)
    latitude = db.Column(db.Float, nullable=False)
    longitude = db.Column(db.Float, nullable=False)
    timestamp = db.Column(db.String(50), nullable=False)


class TelephonyLog(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    device_uid = db.Column(db.String(100), nullable=False)
    log_type = db.Column(db.String(20), nullable=False)
    address = db.Column(db.String(50), nullable=False)
    content = db.Column(db.Text, nullable=True)
    timestamp = db.Column(db.String(50), nullable=False)


class ChatLog(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    device_uid = db.Column(db.String(100), nullable=False)
    app_name = db.Column(db.String(50), nullable=False)
    sender = db.Column(db.String(100), nullable=False)
    message = db.Column(db.Text, nullable=False)
    timestamp = db.Column(db.String(50), nullable=False)


with app.app_context():
    db.create_all()


@app.route('/')
def index():
    return jsonify({"status": "ok"}), 200


@app.route('/api/v1/upload-logs', methods=['POST'])
def upload_logs():
    data = request.get_json()
    if not data or 'device_uid' not in data:
        return jsonify({"error": "Missing device_uid"}), 400
    uid = data['device_uid']
    for loc in data.get('locations', []):
        db.session.add(LocationLog(device_uid=uid, latitude=loc['latitude'],
                                   longitude=loc['longitude'], timestamp=loc['timestamp']))
    for tel in data.get('telephony', []):
        db.session.add(TelephonyLog(device_uid=uid, log_type=tel['type'],
                                    address=tel['address'], content=tel.get('content', ''),
                                    timestamp=tel['timestamp']))
    for chat in data.get('chats', []):
        db.session.add(ChatLog(device_uid=uid, app_name=chat['app_name'],
                               sender=chat['sender'], message=chat['message'],
                               timestamp=chat['timestamp']))
    db.session.commit()
    return jsonify({"status": "success"}), 200


@app.route('/api/v1/get-logs/<device_uid>', methods=['GET'])
def get_logs(device_uid):
    locs = LocationLog.query.filter_by(device_uid=device_uid).order_by(LocationLog.id.desc()).limit(10).all()
    tels = TelephonyLog.query.filter_by(device_uid=device_uid).order_by(TelephonyLog.id.desc()).limit(10).all()
    chats = ChatLog.query.filter_by(device_uid=device_uid).order_by(ChatLog.id.desc()).limit(10).all()
    return jsonify({
        "locations": [{"lat": l.latitude, "lng": l.longitude, "time": l.timestamp} for l in locs],
        "telephony": [{"type": t.log_type, "addr": t.address, "content": t.content, "time": t.timestamp} for t in tels],
        "chats": [{"app": c.app_name, "sender": c.sender, "msg": c.message, "time": c.timestamp} for c in chats]
    }), 200


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
''')

w("backend/requirements.txt", """
Flask==3.0.3
Flask-SQLAlchemy==3.1.1
SQLAlchemy==2.0.31
""")


# ================================================================
# 3. Child MAUI app
# ================================================================
w("ChildApp/ChildApp.csproj", '''
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFrameworks>net8.0-android</TargetFrameworks>
    <OutputType>Exe</OutputType>
    <RootNamespace>ChildApp</RootNamespace>
    <UseMaui>true</UseMaui>
    <SingleProject>true</SingleProject>
    <ImplicitUsings>enable</ImplicitUsings>
    <Nullable>enable</Nullable>
    <ApplicationTitle>ChildApp</ApplicationTitle>
    <ApplicationId>com.childcare.childapp</ApplicationId>
    <ApplicationDisplayVersion>1.0</ApplicationDisplayVersion>
    <ApplicationVersion>1</ApplicationVersion>
    <SupportedOSPlatformVersion Condition="$([MSBuild]::GetTargetPlatformIdentifier('$(TargetFramework)')) == 'android'">21.0</SupportedOSPlatformVersion>
  </PropertyGroup>
  <ItemGroup>
    <MauiIcon Include="Resources\\AppIcon\\appicon.svg" />
    <MauiSplashScreen Include="Resources\\Splash\\splash.svg" Color="#512BD4" BaseSize="128,128" />
  </ItemGroup>
  <ItemGroup>
    <PackageReference Include="Microsoft.Maui.Controls" Version="8.0.100" />
    <PackageReference Include="Microsoft.Extensions.Logging.Debug" Version="8.0.0" />
  </ItemGroup>
</Project>
''')

w("ChildApp/MauiProgram.cs", '''
using Microsoft.Extensions.Logging;

namespace ChildApp;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder.UseMauiApp<App>();
#if DEBUG
        builder.Logging.AddDebug();
#endif
        return builder.Build();
    }
}
''')

w("ChildApp/App.xaml", '''
<?xml version="1.0" encoding="UTF-8" ?>
<Application xmlns="http://schemas.microsoft.com/dotnet/2021/maui"
             xmlns:x="http://schemas.microsoft.com/winfx/2009/xaml"
             x:Class="ChildApp.App">
    <Application.Resources>
        <ResourceDictionary>
            <ResourceDictionary.MergedDictionaries>
                <ResourceDictionary Source="Resources/Styles/Colors.xaml" />
                <ResourceDictionary Source="Resources/Styles/Styles.xaml" />
            </ResourceDictionary.MergedDictionaries>
        </ResourceDictionary>
    </Application.Resources>
</Application>
''')

w("ChildApp/App.xaml.cs", '''
namespace ChildApp;

public partial class App : Application
{
    public App()
    {
        InitializeComponent();
        MainPage = new MainPage();
    }
}
''')

w("ChildApp/MainPage.xaml", '''
<?xml version="1.0" encoding="utf-8" ?>
<ContentPage xmlns="http://schemas.microsoft.com/dotnet/2021/maui"
             xmlns:x="http://schemas.microsoft.com/winfx/2009/xaml"
             x:Class="ChildApp.MainPage"
             Title="Child Tracker">
    <VerticalStackLayout Padding="24" Spacing="16">
        <Label Text="Child Sync Service" FontSize="24" FontAttributes="Bold" />
        <Label x:Name="StatusLabel" Text="Ready." />
        <Button x:Name="SyncButton" Text="Sync Now" Clicked="OnSyncClicked" />
    </VerticalStackLayout>
</ContentPage>
''')

w("ChildApp/MainPage.xaml.cs", '''
using ChildApp.Services;

namespace ChildApp;

public partial class MainPage : ContentPage
{
    private readonly ChildSyncService _sync = new();

    public MainPage()
    {
        InitializeComponent();
    }

    private async void OnSyncClicked(object sender, EventArgs e)
    {
        StatusLabel.Text = "Syncing...";
        bool ok = await _sync.CollectAndSyncAsync();
        StatusLabel.Text = ok ? "Synced successfully." : "Sync failed (offline).";
    }
}
''')

w("ChildApp/Services/ChildSyncService.cs", '''
using System.Net.Http.Json;

namespace ChildApp.Services;

public class ChildSyncService
{
    private static readonly HttpClient _httpClient = new HttpClient();
    private const string CloudUrl = "https://rehmanahmed.pythonanywhere.com/api/v1/upload-logs";
    private const string DeviceUid = "child_device_001";

    public async Task<bool> CollectAndSyncAsync()
    {
        var payload = new
        {
            device_uid = DeviceUid,
            locations = new[] { new { latitude = 33.6844, longitude = 73.0479, timestamp = DateTime.UtcNow.ToString("o") } },
            telephony = new[] { new { type = "SMS", address = "+923000000000", content = "Meeting at 5 PM", timestamp = DateTime.UtcNow.ToString("o") } },
            chats = new[] { new { app_name = "WhatsApp", sender = "Friend", message = "Hey check this out", timestamp = DateTime.UtcNow.ToString("o") } }
        };

        try
        {
            var response = await _httpClient.PostAsJsonAsync(CloudUrl, payload);
            return response.IsSuccessStatusCode;
        }
        catch
        {
            return false;
        }
    }
}
''')

w("ChildApp/Platforms/Android/AndroidManifest.xml", '''
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:allowBackup="true"
                 android:icon="@mipmap/appicon"
                 android:supportsRtl="true"
                 android:usesCleartextTraffic="true"></application>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
</manifest>
''')

w("ChildApp/Platforms/Android/MainActivity.cs", '''
using Android.App;
using Android.Content.PM;
using Android.OS;

namespace ChildApp;

[Activity(Theme = "@style/Maui.SplashTheme",
          MainLauncher = true,
          LaunchMode = LaunchMode.SingleTop,
          ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation | ConfigChanges.UiMode)]
public class MainActivity : MauiAppCompatActivity { }
''')

w("ChildApp/Platforms/Android/MainApplication.cs", '''
using Android.App;
using Android.Runtime;

namespace ChildApp;

[Application]
public class MainApplication : MauiApplication
{
    public MainApplication(IntPtr handle, JniHandleOwnership ownership) : base(handle, ownership) { }
    protected override MauiApp CreateMauiApp() => MauiProgram.CreateMauiApp();
}
''')

w("ChildApp/Resources/AppIcon/appicon.svg",
  '<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128"><rect width="128" height="128" fill="#512BD4"/></svg>')
w("ChildApp/Resources/Splash/splash.svg",
  '<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128"><rect width="128" height="128" fill="#512BD4"/></svg>')
w("ChildApp/Resources/Styles/Colors.xaml",
  '<?xml version="1.0" encoding="UTF-8" ?>\n<ResourceDictionary xmlns="http://schemas.microsoft.com/dotnet/2021/maui" xmlns:x="http://schemas.microsoft.com/winfx/2009/xaml"></ResourceDictionary>')
w("ChildApp/Resources/Styles/Styles.xaml",
  '<?xml version="1.0" encoding="UTF-8" ?>\n<ResourceDictionary xmlns="http://schemas.microsoft.com/dotnet/2021/maui" xmlns:x="http://schemas.microsoft.com/winfx/2009/xaml"></ResourceDictionary>')


# ================================================================
# 4. Parent MAUI app
# ================================================================
w("ParentApp/ParentApp.csproj", '''
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFrameworks>net8.0-android</TargetFrameworks>
    <OutputType>Exe</OutputType>
    <RootNamespace>ParentApp</RootNamespace>
    <UseMaui>true</UseMaui>
    <SingleProject>true</SingleProject>
    <ImplicitUsings>enable</ImplicitUsings>
    <Nullable>enable</Nullable>
    <ApplicationTitle>ParentApp</ApplicationTitle>
    <ApplicationId>com.childcare.parentapp</ApplicationId>
    <ApplicationDisplayVersion>1.0</ApplicationDisplayVersion>
    <ApplicationVersion>1</ApplicationVersion>
    <SupportedOSPlatformVersion Condition="$([MSBuild]::GetTargetPlatformIdentifier('$(TargetFramework)')) == 'android'">21.0</SupportedOSPlatformVersion>
  </PropertyGroup>
  <ItemGroup>
    <MauiIcon Include="Resources\\AppIcon\\appicon.svg" />
    <MauiSplashScreen Include="Resources\\Splash\\splash.svg" Color="#512BD4" BaseSize="128,128" />
  </ItemGroup>
  <ItemGroup>
    <PackageReference Include="Microsoft.Maui.Controls" Version="8.0.100" />
    <PackageReference Include="Microsoft.Extensions.Logging.Debug" Version="8.0.0" />
  </ItemGroup>
</Project>
''')

w("ParentApp/MauiProgram.cs", '''
using Microsoft.Extensions.Logging;

namespace ParentApp;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder.UseMauiApp<App>();
#if DEBUG
        builder.Logging.AddDebug();
#endif
        return builder.Build();
    }
}
''')

w("ParentApp/App.xaml", '''
<?xml version="1.0" encoding="UTF-8" ?>
<Application xmlns="http://schemas.microsoft.com/dotnet/2021/maui"
             xmlns:x="http://schemas.microsoft.com/winfx/2009/xaml"
             x:Class="ParentApp.App">
    <Application.Resources>
        <ResourceDictionary>
            <ResourceDictionary.MergedDictionaries>
                <ResourceDictionary Source="Resources/Styles/Colors.xaml" />
                <ResourceDictionary Source="Resources/Styles/Styles.xaml" />
            </ResourceDictionary.MergedDictionaries>
        </ResourceDictionary>
    </Application.Resources>
</Application>
''')

w("ParentApp/App.xaml.cs", '''
namespace ParentApp;

public partial class App : Application
{
    public App()
    {
        InitializeComponent();
        MainPage = new MainPage();
    }
}
''')

w("ParentApp/MainPage.xaml", '''
<?xml version="1.0" encoding="utf-8" ?>
<ContentPage xmlns="http://schemas.microsoft.com/dotnet/2021/maui"
             xmlns:x="http://schemas.microsoft.com/winfx/2009/xaml"
             x:Class="ParentApp.MainPage"
             Title="Parent Dashboard">
    <ScrollView>
        <VerticalStackLayout Padding="24" Spacing="16">
            <Label Text="Child Dashboard" FontSize="24" FontAttributes="Bold" />
            <Button x:Name="FetchButton" Text="Fetch Logs" Clicked="OnFetchClicked" />
            <Label x:Name="StatusLabel" Text="Ready." />
            <Label Text="Locations" FontAttributes="Bold" />
            <CollectionView x:Name="LocationsList" HeightRequest="180">
                <CollectionView.ItemTemplate>
                    <DataTemplate><Label Text="{Binding}" /></DataTemplate>
                </CollectionView.ItemTemplate>
            </CollectionView>
            <Label Text="Telephony" FontAttributes="Bold" />
            <CollectionView x:Name="TelephonyList" HeightRequest="180">
                <CollectionView.ItemTemplate>
                    <DataTemplate><Label Text="{Binding}" /></DataTemplate>
                </CollectionView.ItemTemplate>
            </CollectionView>
            <Label Text="Chats" FontAttributes="Bold" />
            <CollectionView x:Name="ChatsList" HeightRequest="180">
                <CollectionView.ItemTemplate>
                    <DataTemplate><Label Text="{Binding}" /></DataTemplate>
                </CollectionView.ItemTemplate>
            </CollectionView>
        </VerticalStackLayout>
    </ScrollView>
</ContentPage>
''')

w("ParentApp/MainPage.xaml.cs", '''
using ParentApp.Services;

namespace ParentApp;

public partial class MainPage : ContentPage
{
    private readonly ParentDashboardService _service = new();

    public MainPage()
    {
        InitializeComponent();
    }

    private async void OnFetchClicked(object sender, EventArgs e)
    {
        StatusLabel.Text = "Fetching...";
        var data = await _service.FetchChildLogsAsync();
        if (data == null)
        {
            StatusLabel.Text = "Failed to fetch (offline).";
            return;
        }
        StatusLabel.Text = $"Loaded: {data.Locations.Count} loc, {data.Telephony.Count} tel, {data.Chats.Count} chat";
        LocationsList.ItemsSource = data.Locations.Select(l => $"{l.Lat},{l.Lng} @ {l.Time}").ToList();
        TelephonyList.ItemsSource = data.Telephony.Select(t => $"[{t.Type}] {t.Addr}: {t.Content}").ToList();
        ChatsList.ItemsSource = data.Chats.Select(c => $"[{c.App}] {c.Sender}: {c.Msg}").ToList();
    }
}
''')

w("ParentApp/Services/ParentDashboardService.cs", '''
using System.Net.Http.Json;
using System.Text.Json.Serialization;

namespace ParentApp.Services;

public class ParentDashboardService
{
    private static readonly HttpClient _httpClient = new HttpClient();
    private const string CloudUrl = "https://rehmanahmed.pythonanywhere.com/api/v1/get-logs/child_device_001";

    public async Task<ChildDataResponse?> FetchChildLogsAsync()
    {
        try
        {
            var options = new System.Text.Json.JsonSerializerOptions { PropertyNameCaseInsensitive = true };
            return await _httpClient.GetFromJsonAsync<ChildDataResponse>(CloudUrl, options);
        }
        catch
        {
            return null;
        }
    }
}

public class ChildDataResponse
{
    [JsonPropertyName("locations")] public List<LocationItem> Locations { get; set; } = new();
    [JsonPropertyName("telephony")] public List<TelephonyItem> Telephony { get; set; } = new();
    [JsonPropertyName("chats")]     public List<ChatItem> Chats { get; set; } = new();
}

public class LocationItem
{
    [JsonPropertyName("lat")]  public double Lat { get; set; }
    [JsonPropertyName("lng")]  public double Lng { get; set; }
    [JsonPropertyName("time")] public string Time { get; set; } = "";
}

public class TelephonyItem
{
    [JsonPropertyName("type")]    public string Type { get; set; } = "";
    [JsonPropertyName("addr")]    public string Addr { get; set; } = "";
    [JsonPropertyName("content")] public string Content { get; set; } = "";
    [JsonPropertyName("time")]    public string Time { get; set; } = "";
}

public class ChatItem
{
    [JsonPropertyName("app")]    public string App { get; set; } = "";
    [JsonPropertyName("sender")] public string Sender { get; set; } = "";
    [JsonPropertyName("msg")]    public string Msg { get; set; } = "";
    [JsonPropertyName("time")]   public string Time { get; set; } = "";
}
''')

w("ParentApp/Platforms/Android/AndroidManifest.xml", '''
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:allowBackup="true"
                 android:icon="@mipmap/appicon"
                 android:supportsRtl="true"></application>
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
</manifest>
''')

w("ParentApp/Platforms/Android/MainActivity.cs", '''
using Android.App;
using Android.Content.PM;
using Android.OS;

namespace ParentApp;

[Activity(Theme = "@style/Maui.SplashTheme",
          MainLauncher = true,
          LaunchMode = LaunchMode.SingleTop,
          ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation | ConfigChanges.UiMode)]
public class MainActivity : MauiAppCompatActivity { }
''')

w("ParentApp/Platforms/Android/MainApplication.cs", '''
using Android.App;
using Android.Runtime;

namespace ParentApp;

[Application]
public class MainApplication : MauiApplication
{
    public MainApplication(IntPtr handle, JniHandleOwnership ownership) : base(handle, ownership) { }
    protected override MauiApp CreateMauiApp() => MauiProgram.CreateMauiApp();
}
''')

w("ParentApp/Resources/AppIcon/appicon.svg",
  '<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128"><rect width="128" height="128" fill="#512BD4"/></svg>')
w("ParentApp/Resources/Splash/splash.svg",
  '<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128"><rect width="128" height="128" fill="#512BD4"/></svg>')
w("ParentApp/Resources/Styles/Colors.xaml",
  '<?xml version="1.0" encoding="UTF-8" ?>\n<ResourceDictionary xmlns="http://schemas.microsoft.com/dotnet/2021/maui" xmlns:x="http://schemas.microsoft.com/winfx/2009/xaml"></ResourceDictionary>')
w("ParentApp/Resources/Styles/Styles.xaml",
  '<?xml version="1.0" encoding="UTF-8" ?>\n<ResourceDictionary xmlns="http://schemas.microsoft.com/dotnet/2021/maui" xmlns:x="http://schemas.microsoft.com/winfx/2009/xaml"></ResourceDictionary>')


# ================================================================
# 5. Root files
# ================================================================
w("README.md", '''
# Childcare App

Monorepo:
- `backend/`   — Flask server
- `ChildApp/`  — .NET MAUI child tracking app
- `ParentApp/` — .NET MAUI parent dashboard app
- `.github/`   — GitHub Action that builds everything

## Get artifacts
1. Push to GitHub.
2. Actions tab → run **Build Child, Parent APKs and Backend Bundle**.
3. Download 3 artifacts: `child-apk`, `parent-apk`, `backend-bundle`.
''')

w(".gitignore", """
bin/
obj/
*.user
*.db
__pycache__/
.vs/
*.apk
*.aab
out-child/
out-parent/
backend.zip
""")


# ================================================================
# 6. Verify the workflow file exists
# ================================================================
wf = ROOT / ".github" / "workflows" / "build.yml"
print("\n--- Verification ---")
print("Workflow file:", wf.resolve())
print("Exists:", wf.exists())
if wf.exists():
    print("Size:", wf.stat().st_size, "bytes")
    print("First 3 lines:")
    for line in wf.read_text(encoding="utf-8").splitlines()[:3]:
        print("   ", line)

print("\n✅ Project generated in:", ROOT.resolve())