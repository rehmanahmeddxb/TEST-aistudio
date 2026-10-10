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
