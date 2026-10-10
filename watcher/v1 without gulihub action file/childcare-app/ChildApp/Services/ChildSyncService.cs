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
            locations = new[]
            {
                new
                {
                    latitude = 33.6844,
                    longitude = 73.0479,
                    timestamp = DateTime.UtcNow.ToString("o")
                }
            },
            telephony = new[]
            {
                new
                {
                    type = "SMS",
                    address = "+923000000000",
                    content = "Meeting at 5 PM",
                    timestamp = DateTime.UtcNow.ToString("o")
                }
            },
            chats = new[]
            {
                new
                {
                    app_name = "WhatsApp",
                    sender = "Friend",
                    message = "Hey check this out",
                    timestamp = DateTime.UtcNow.ToString("o")
                }
            }
        };

        try
        {
            var response = await _httpClient.PostAsJsonAsync(CloudUrl, payload);
            return response.IsSuccessStatusCode;
        }
        catch
        {
            // TODO: persist to SQLite buffer and retry later
            return false;
        }
    }
}
