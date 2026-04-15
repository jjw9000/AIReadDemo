using System.Diagnostics;
using System.Text;
using System.Text.Json;
using OcrService.DTOs;

namespace OcrService.Services;

public class PaddleOcrService : IOcrService
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<PaddleOcrService> _logger;
    private readonly string _apiUrl;
    private readonly int _timeoutSeconds;

    public PaddleOcrService(
        IHttpClientFactory httpClientFactory,
        ILogger<PaddleOcrService> logger,
        IConfiguration configuration)
    {
        _httpClientFactory = httpClientFactory;
        _logger = logger;
        _apiUrl = configuration["OcrService:ApiUrl"] ?? "http://localhost:5007/";
        _timeoutSeconds = configuration.GetValue("OcrService:TimeoutSeconds", 120);
    }

    public async Task<OcrResultData> RecognizeAsync(string imageBase64, string task = "ocr")
    {
        var sw = Stopwatch.StartNew();

        try
        {
            _logger.LogInformation("Starting OCR, image length: {Length}, task: {Task}",
                imageBase64.Length, task);

            var result = await CallPaddleVlApiAsync(imageBase64, task);

            sw.Stop();
            _logger.LogInformation("OCR completed in {ElapsedMs}ms", sw.ElapsedMilliseconds);

            if (string.IsNullOrEmpty(result))
            {
                return new OcrResultData
                {
                    Success = false,
                    ErrorMessage = "Empty response from OCR service"
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
            _logger.LogWarning("OCR timed out after {Timeout}s", _timeoutSeconds);
            return new OcrResultData
            {
                Success = false,
                ErrorMessage = $"OCR timed out after {_timeoutSeconds}s"
            };
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "OCR failed");
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

    private async Task<string?> CallPaddleVlApiAsync(string imageBase64, string task)
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

        _logger.LogDebug("Calling Paddle VL API at {Url}", _apiUrl);

        var response = await client.PostAsync($"{_apiUrl}/ocr/recognize-simple", content);

        if (!response.IsSuccessStatusCode)
        {
            _logger.LogError("Paddle VL API returned {StatusCode}: {Reason}",
                response.StatusCode, response.ReasonPhrase);
            return null;
        }

        var responseBody = await response.Content.ReadAsStringAsync();
        _logger.LogDebug("Paddle VL API response: {Response}", responseBody);

        using var doc = JsonDocument.Parse(responseBody);
        var root = doc.RootElement;

        if (root.TryGetProperty("text", out var textProp))
        {
            return textProp.GetString();
        }

        if (root.TryGetProperty("full_text", out var fullTextProp))
        {
            return fullTextProp.GetString();
        }

        return null;
    }
}