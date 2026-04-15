using System.Net;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Configuration;
using Moq;
using Moq.Protected;
using OcrService.DTOs;
using OcrService.Services;
using Xunit;

namespace OcrService.Tests;

/// <summary>
/// Unit tests for LocalOcrService.
/// Uses HttpMessageHandler mocking instead of IHttpClientFactory extension methods.
/// </summary>
public class LocalOcrServiceTests
{
    private readonly IConfiguration _configuration;
    private readonly Mock<ILogger<LocalOcrService>> _mockLogger;

    public LocalOcrServiceTests()
    {
        _mockLogger = new Mock<ILogger<LocalOcrService>>();

        var configBuilder = new ConfigurationBuilder();
        configBuilder.AddInMemoryCollection(new Dictionary<string, string?>
        {
            ["LocalOcrService:Url"] = "http://localhost:5007",
            ["LocalOcrService:TimeoutSeconds"] = "120"
        });
        _configuration = configBuilder.Build();
    }

    private LocalOcrService CreateService(HttpMessageHandler handler)
    {
        var httpClient = new HttpClient(handler);
        var mockHttpClientFactory = new Mock<IHttpClientFactory>();
        mockHttpClientFactory.Setup(f => f.CreateClient(It.IsAny<string>())).Returns(httpClient);

        return new LocalOcrService(
            mockHttpClientFactory.Object,
            _mockLogger.Object,
            _configuration);
    }

    [Fact]
    public async Task RecognizeAsync_WithSuccessfulResponse_ReturnsSuccess()
    {
        // Arrange
        var imageBase64 = "dGVzdA=="; // "test" in base64
        var ocrResponse = new { success = true, data = new { success = true, fullText = "Hello World" } };
        var jsonResponse = JsonSerializer.Serialize(ocrResponse);

        var handler = CreateMockHandler(HttpStatusCode.OK, jsonResponse);
        var service = CreateService(handler);

        // Act
        var result = await service.RecognizeAsync(imageBase64);

        // Assert
        Assert.True(result.Success);
        Assert.Equal("Hello World", result.FullText);
        Assert.Single(result.TextBlocks);
        Assert.Equal("Hello World", result.TextBlocks[0].Text);
    }

    [Fact]
    public async Task RecognizeAsync_WithFailedOcr_ReturnsFailure()
    {
        // Arrange
        var imageBase64 = "dGVzdA==";
        var ocrResponse = new { success = false, data = new { success = false, message = "No text found" } };
        var jsonResponse = JsonSerializer.Serialize(ocrResponse);

        var handler = CreateMockHandler(HttpStatusCode.OK, jsonResponse);
        var service = CreateService(handler);

        // Act
        var result = await service.RecognizeAsync(imageBase64);

        // Assert
        Assert.False(result.Success);
        Assert.NotNull(result.ErrorMessage);
    }

    [Fact]
    public async Task RecognizeAsync_WithHttpError_ReturnsFailure()
    {
        // Arrange
        var imageBase64 = "dGVzdA==";

        var handler = CreateMockHandler(HttpStatusCode.InternalServerError, "");
        var service = CreateService(handler);

        // Act
        var result = await service.RecognizeAsync(imageBase64);

        // Assert
        Assert.False(result.Success);
    }

    [Fact]
    public async Task RecognizeAsync_WithDataUri_StripsPrefix()
    {
        // Arrange
        var imageBase64 = "data:image/png;base64,dGVzdA=="; // data URI format

        string? capturedBody = null;
        var handler = new Mock<HttpMessageHandler>(MockBehavior.Loose);
        handler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .Callback<HttpRequestMessage, CancellationToken>((req, _) =>
            {
                using var reader = new StreamReader(req.Content!.ReadAsStreamAsync().Result);
                capturedBody = reader.ReadToEndAsync().Result;
            })
            .ReturnsAsync(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(
                    JsonSerializer.Serialize(new { success = true, data = new { success = true, fullText = "ok" } }),
                    System.Text.Encoding.UTF8,
                    "application/json")
            });

        var service = CreateService(handler.Object);

        // Act
        var result = await service.RecognizeAsync(imageBase64);

        // Assert
        Assert.True(result.Success);
        Assert.NotNull(capturedBody);
        var doc = JsonDocument.Parse(capturedBody!);
        Assert.Equal("dGVzdA==", doc.RootElement.GetProperty("image_base64").GetString());
    }

    [Fact]
    public async Task RecognizeAsync_TrimsFullText()
    {
        // Arrange
        var imageBase64 = "dGVzdA==";
        var ocrResponse = new { success = true, data = new { success = true, fullText = "  Hello World  " } };
        var jsonResponse = JsonSerializer.Serialize(ocrResponse);

        var handler = CreateMockHandler(HttpStatusCode.OK, jsonResponse);
        var service = CreateService(handler);

        // Act
        var result = await service.RecognizeAsync(imageBase64);

        // Assert
        Assert.True(result.Success);
        Assert.Equal("Hello World", result.FullText);
    }

    [Fact]
    public async Task CheckHealthAsync_WhenHealthy_ReturnsTrue()
    {
        // Arrange
        var handler = CreateMockHandler(HttpStatusCode.OK, "{\"status\":\"ok\"}");
        var service = CreateService(handler);

        // Act
        var result = await service.CheckHealthAsync();

        // Assert
        Assert.True(result);
    }

    [Fact]
    public async Task CheckHealthAsync_WhenUnhealthy_ReturnsFalse()
    {
        // Arrange
        var handler = CreateMockHandler(HttpStatusCode.ServiceUnavailable, "");
        var service = CreateService(handler);

        // Act
        var result = await service.CheckHealthAsync();

        // Assert
        Assert.False(result);
    }

    [Fact]
    public async Task CheckHealthAsync_WhenThrows_ReturnsFalse()
    {
        // Arrange
        var handler = new Mock<HttpMessageHandler>(MockBehavior.Loose);
        handler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ThrowsAsync(new HttpRequestException("Connection refused"));

        var service = CreateService(handler.Object);

        // Act
        var result = await service.CheckHealthAsync();

        // Assert
        Assert.False(result);
    }

    private static HttpMessageHandler CreateMockHandler(HttpStatusCode statusCode, string content)
    {
        var handler = new Mock<HttpMessageHandler>(MockBehavior.Loose);
        handler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(new HttpResponseMessage(statusCode)
            {
                Content = new StringContent(content, System.Text.Encoding.UTF8, "application/json")
            });
        return handler.Object;
    }
}
