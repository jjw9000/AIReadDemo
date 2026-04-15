using System.Diagnostics;
using System.Text;
using System.Text.Json;
using OcrService.DTOs;

namespace OcrService.Services;

public class LocalOcrService : IOcrService
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<LocalOcrService> _logger;
    private readonly string _apiUrl;
    private readonly int _timeoutSeconds;

    public LocalOcrService(
        IHttpClientFactory httpClientFactory,
        ILogger<LocalOcrService> logger,
        IConfiguration configuration)
    {
        _httpClientFactory = httpClientFactory;
        _logger = logger;
        _apiUrl = configuration["LocalOcrService:Url"] ?? "http://localhost:5007";
        _timeoutSeconds = configuration.GetValue("LocalOcrService:TimeoutSeconds", 120);
    }

    public async Task<OcrResultData> RecognizeAsync(string imageBase64, string task = "ocr")
    {
        var sw = Stopwatch.StartNew();

        try
        {
            _logger.LogInformation("Starting local OCR, image length: {Length}, task: {Task}",
                imageBase64.Length, task);

            var result = await CallLocalOcrApiAsync(imageBase64, task);

            sw.Stop();
            _logger.LogInformation("Local OCR completed in {ElapsedMs}ms", sw.ElapsedMilliseconds);

            if (string.IsNullOrEmpty(result))
            {
                return new OcrResultData
                {
                    Success = false,
                    ErrorMessage = "Empty response from local OCR service"
                };
            }

            return new OcrResultData
            {
                Success = true,
                TextBlocks = new List<TextBlock>
                {
                    new TextBlock
                    {
                        Text = result.Trim(),
                        Confidence = 1.0f
                    }
                },
                FullText = result.Trim()
            };
        }
        catch (TaskCanceledException)
        {
            _logger.LogWarning("Local OCR timed out after {Timeout}s", _timeoutSeconds);
            return new OcrResultData
            {
                Success = false,
                ErrorMessage = $"Local OCR timed out after {_timeoutSeconds}s"
            };
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Local OCR failed");
            return new OcrResultData
            {
                Success = false,
                ErrorMessage = ex.Message
            };
        }
    }

    public async Task<bool> CheckHealthAsync()
    {
        try
        {
            using var client = _httpClientFactory.CreateClient();
            client.Timeout = TimeSpan.FromSeconds(5);
            var response = await client.GetAsync($"{_apiUrl}/health");
            return response.IsSuccessStatusCode;
        }
        catch
        {
            return false;
        }
    }

    private async Task<string?> CallLocalOcrApiAsync(string imageBase64, string task)
    {
        using var client = _httpClientFactory.CreateClient();
        client.Timeout = TimeSpan.FromSeconds(_timeoutSeconds);

        // Handle data URI format
        if (imageBase64.Contains(','))
        {
            imageBase64 = imageBase64.Split(',')[1];
        }

        var requestBody = new
        {
            image_base64 = imageBase64,
            task = task
        };

        var json = JsonSerializer.Serialize(requestBody);
        var content = new StringContent(json, Encoding.UTF8, "application/json");

        _logger.LogDebug("Calling local OCR API at {Url}/ocr/recognize", _apiUrl);

        var response = await client.PostAsync($"{_apiUrl}/ocr/recognize", content);

        if (!response.IsSuccessStatusCode)
        {
            _logger.LogError("Local OCR API returned {StatusCode}: {Reason}",
                response.StatusCode, response.ReasonPhrase);
            return null;
        }

        var responseBody = await response.Content.ReadAsStringAsync();
        _logger.LogDebug("Local OCR API response: {Response}", responseBody);

        try
        {
            using var doc = JsonDocument.Parse(responseBody);

            var root = doc.RootElement;
            var success = root.TryGetProperty("success", out var successProp) && successProp.GetBoolean();
            var data = root.TryGetProperty("data", out var dataProp) ? dataProp : default;
            var ocrSuccess = data.TryGetProperty("success", out var ocrSuccProp) && ocrSuccProp.GetBoolean();
            var fullText = data.TryGetProperty("fullText", out var textProp) ? textProp.GetString() : null;

            if (success || ocrSuccess)
            {
                _logger.LogInformation("Local OCR result: {Text}", fullText);
                return fullText;
            }

            var message = data.TryGetProperty("message", out var msgProp) ? msgProp.GetString() : "Unknown error";
            _logger.LogWarning("Local OCR failed: {Message}", message);
            return null;
        }
        catch (JsonException ex)
        {
            _logger.LogError(ex, "Failed to parse local OCR response");
            return null;
        }
    }
}