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
            var options = new System.Text.Json.JsonSerializerOptions
            {
                PropertyNameCaseInsensitive = true
            };
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
    [JsonPropertyName("locations")]
    public List<LocationItem> Locations { get; set; } = new();

    [JsonPropertyName("telephony")]
    public List<TelephonyItem> Telephony { get; set; } = new();

    [JsonPropertyName("chats")]
    public List<ChatItem> Chats { get; set; } = new();
}

public class LocationItem
{
    [JsonPropertyName("lat")] public double Lat { get; set; }
    [JsonPropertyName("lng")] public double Lng { get; set; }
    [JsonPropertyName("time")] public string Time { get; set; } = "";
}

public class TelephonyItem
{
    [JsonPropertyName("type")] public string Type { get; set; } = "";
    [JsonPropertyName("addr")] public string Addr { get; set; } = "";
    [JsonPropertyName("content")] public string Content { get; set; } = "";
    [JsonPropertyName("time")] public string Time { get; set; } = "";
}

public class ChatItem
{
    [JsonPropertyName("app")] public string App { get; set; } = "";
    [JsonPropertyName("sender")] public string Sender { get; set; } = "";
    [JsonPropertyName("msg")] public string Msg { get; set; } = "";
    [JsonPropertyName("time")] public string Time { get; set; } = "";
}
