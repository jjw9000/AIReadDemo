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
    private readonly string _token;
    private readonly int _timeoutSeconds;

    public PaddleOcrService(
        IHttpClientFactory httpClientFactory,
        ILogger<PaddleOcrService> logger,
        IConfiguration configuration)
    {
        _httpClientFactory = httpClientFactory;
        _logger = logger;
        _apiUrl = configuration["OcrService:PaddleLayoutUrl"] ?? "https://t8r5f9e0udm8c162.aistudio-app.com/layout-parsing";
        _token = configuration["OcrService:PaddleLayoutToken"] ?? "";
        _timeoutSeconds = configuration.GetValue("OcrService:TimeoutSeconds", 120);
    }

    public async Task<OcrResultData> RecognizeAsync(string imageBase64, string task = "ocr")
    {
        var sw = Stopwatch.StartNew();

        try
        {
            _logger.LogInformation("Starting OCR, image length: {Length}, task: {Task}",
                imageBase64.Length, task);

            var result = await CallPaddleLayoutApiAsync(imageBase64);

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
            var response = await client.GetAsync(_apiUrl);
            return response.IsSuccessStatusCode;
        }
        catch
        {
            return false;
        }
    }

    private async Task<string?> CallPaddleLayoutApiAsync(string imageBase64)
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
            file = imageBase64,
            fileType = 1,
            useDocOrientationClassify = false,
            useDocUnwarping = false,
            useChartRecognition = false
        };

        var json = JsonSerializer.Serialize(requestBody);
        var content = new StringContent(json, Encoding.UTF8, "application/json");

        _logger.LogDebug("Calling Paddle Layout API at {Url}", _apiUrl);

        var request = new HttpRequestMessage(HttpMethod.Post, _apiUrl);
        request.Headers.Add("Authorization", $"token {_token}");
        request.Content = content;

        var response = await client.SendAsync(request);

        if (!response.IsSuccessStatusCode)
        {
            _logger.LogError("Paddle Layout API returned {StatusCode}: {Reason}",
                response.StatusCode, response.ReasonPhrase);
            return null;
        }

        var responseBody = await response.Content.ReadAsStringAsync();
        _logger.LogDebug("Paddle Layout API response: {Response}", responseBody);

        try
        {
            using var doc = JsonDocument.Parse(responseBody);

            if (!doc.RootElement.TryGetProperty("result", out var result)) return null;
            if (!result.TryGetProperty("layoutParsingResults", out var items)) return null;

            var sb = new StringBuilder();
            foreach (var item in items.EnumerateArray())
            {
                // Parse from prunedResult.parsing_res_list
                if (item.TryGetProperty("prunedResult", out var prunedResult) &&
                    prunedResult.TryGetProperty("parsing_res_list", out var parsingResList))
                {
                    foreach (var block in parsingResList.EnumerateArray())
                    {
                        if (block.TryGetProperty("block_content", out var contentEl))
                        {
                            var text = contentEl.GetString();
                            if (!string.IsNullOrWhiteSpace(text))
                            {
                                sb.AppendLine(text);
                            }
                        }
                    }
                }
            }

            var fullText = sb.ToString().Trim();
            if (string.IsNullOrWhiteSpace(fullText)) return null;

            // Strip markdown formatting
            return StripMarkdown(fullText);
        }
        catch (JsonException ex)
        {
            _logger.LogError(ex, "Failed to parse Paddle Layout response");
            return null;
        }
    }

    private static string StripMarkdown(string markdown)
    {
        var text = markdown;
        // Remove images ![...](...)
        text = System.Text.RegularExpressions.Regex.Replace(text, @"!\[.*?\]\(.*?\)", "");
        // Remove links [...](...)
        text = System.Text.RegularExpressions.Regex.Replace(text, @"\[([^\]]*)\]\([^\)]*\)", "$1");
        // Remove headers #
        text = System.Text.RegularExpressions.Regex.Replace(text, @"^#{1,6}\s*", "", System.Text.RegularExpressions.RegexOptions.Multiline);
        // Remove bold/italic ** * __ _
        text = System.Text.RegularExpressions.Regex.Replace(text, @"(\*\*|__)(.*?)\1", "$2");
        text = System.Text.RegularExpressions.Regex.Replace(text, @"(\*|_)(.*?)\1", "$2");
        // Remove code blocks ``` and inline code `
        text = System.Text.RegularExpressions.Regex.Replace(text, @"```[\s\S]*?```", "");
        text = System.Text.RegularExpressions.Regex.Replace(text, @"`([^`]*)`", "$1");
        // Merge multiple blank lines
        text = System.Text.RegularExpressions.Regex.Replace(text, @"\n{3,}", "\n\n");
        return text.Trim();
    }
}