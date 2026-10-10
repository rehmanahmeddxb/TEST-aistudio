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
