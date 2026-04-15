using Microsoft.AspNetCore.Mvc;
using BookManagementService.DTOs;
using BookManagementService.Entities;
using BookManagementService.Repositories;
using BookManagementService.Services;
using System.Net.Http.Json;

namespace BookManagementService.Controllers;

[ApiController]
[Route("api/[controller]")]
public class BooksController : ControllerBase
{
    private readonly IBookRepository _bookRepository;
    private readonly IClipService _clipService;
    private readonly IBookDetectionService _bookDetectionService;
    private readonly ILogger<BooksController> _logger;
    private readonly IHttpClientFactory _httpClientFactory;
    private const string OcrServiceUrl = "http://192.168.3.18:5017";

    public BooksController(
        IBookRepository bookRepository,
        IClipService clipService,
        IBookDetectionService bookDetectionService,
        IHttpClientFactory httpClientFactory,
        ILogger<BooksController> logger)
    {
        _bookRepository = bookRepository;
        _clipService = clipService;
        _bookDetectionService = bookDetectionService;
        _httpClientFactory = httpClientFactory;
        _logger = logger;
    }

    [HttpGet("/health")]
    public async Task<ActionResult<HealthResponse>> HealthCheck()
    {
        return Ok(new HealthResponse
        {
            Status = "healthy",
            Service = "BookManagementService",
            Version = "1.0.0"
        });
    }

    [HttpPost("match")]
    public async Task<ActionResult<MatchResponse>> Match([FromBody] MatchRequest request)
    {
        try
        {
            _logger.LogInformation("Received match request, image length: {Length}, BookId: {BookId}",
                request.ImageBase64.Length, request.BookId);

            // Handle data URI format
            var base64Data = request.ImageBase64;
            if (base64Data.Contains(','))
            {
                base64Data = base64Data.Split(',')[1];
            }
            var imageBytes = Convert.FromBase64String(base64Data);

            // Detect and crop book region using OpenCV
            var detectionResult = await _bookDetectionService.DetectAndCropAsync(imageBytes);
            var croppedBytes = detectionResult?.CroppedImage ?? imageBytes;

            _logger.LogInformation("Using image size: {Size} bytes (detected: {Detected})",
                croppedBytes.Length, detectionResult != null);

            // Extract CLIP features from cropped image
            var queryEmbedding = _clipService.ExtractFeatures(croppedBytes);
            var vectorStr = "[" + string.Join(",", queryEmbedding) + "]";

            if (request.BookId.HasValue)
            {
                var results = await _bookRepository.MatchPagesAsync(request.BookId.Value, vectorStr);
                _logger.LogInformation("Found {Count} matching pages for book {BookId}", results.Count, request.BookId.Value);
                var best = results.FirstOrDefault();
                if (best != null)
                {
                    var pages = await _bookRepository.GetBookPagesAsync(request.BookId.Value);
                    best.Pages = pages;
                    return Ok(new MatchResponse { Success = true, Book = best });
                }
                return Ok(new MatchResponse { Success = true, Book = null });
            }
            else
            {
                var results = await _bookRepository.MatchBookCoversAsync(vectorStr);
                _logger.LogInformation("Found {Count} matching books", results.Count);
                var best = results.FirstOrDefault();
                if (best != null)
                {
                    var pages = await _bookRepository.GetBookPagesAsync(best.Id);
                    best.Pages = pages;
                    return Ok(new MatchResponse { Success = true, Book = best });
                }

                // CLIP found nothing - try OCR then search by text
                _logger.LogInformation("CLIP match failed, trying OcrService...");
                var ocrText = await CallOcrServiceAsync(imageBytes);
                if (!string.IsNullOrEmpty(ocrText))
                {
                    _logger.LogInformation("OcrService returned text, searching books by text...");
                    var textResults = await _bookRepository.SearchBooksByTextAsync(ocrText);
                    var textBest = textResults.FirstOrDefault();
                    if (textBest != null)
                    {
                        var pages = await _bookRepository.GetBookPagesAsync(textBest.Id);
                        textBest.Pages = pages;
                        _logger.LogInformation("Found book by text search: {Title}", textBest.Title);
                        return Ok(new MatchResponse { Success = true, Book = textBest });
                    }
                }

                return Ok(new MatchResponse { Success = true, Book = null });
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Match failed");
            return StatusCode(500, new MatchResponse
            {
                Success = false,
                Error = ex.Message
            });
        }
    }

    [HttpPost("register")]
    public async Task<ActionResult<RegisterResponse>> Register([FromBody] RegisterRequest request)
    {
        try
        {
            _logger.LogInformation("Registering new book: {Title}", request.Title);

            var embedding = await _clipService.ExtractFeaturesAsync(request.ImageBase64);
            var vectorStr = "[" + string.Join(",", embedding) + "]";

            var metadataJson = request.Metadata != null
                ? System.Text.Json.JsonSerializer.Serialize(request.Metadata)
                : (string?)null;

            var bookId = await _bookRepository.CreateBookAsync(request.Title, vectorStr, metadataJson);
            _logger.LogInformation("Book registered with ID: {Id}", bookId);

            return Ok(new RegisterResponse { Success = true, Id = bookId });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Register failed");
            return StatusCode(500, new RegisterResponse
            {
                Success = false,
                Error = ex.Message
            });
        }
    }

    [HttpPost("pages")]
    public async Task<ActionResult<RegisterPageResponse>> RegisterPage([FromBody] RegisterPageRequest request)
    {
        try
        {
            _logger.LogInformation("Registering page {PageNumber} for book {BookId}",
                request.PageNumber, request.BookId);

            if (!await _bookRepository.ExistsAsync(request.BookId))
            {
                return NotFound(new { error = "Book not found" });
            }

            var embedding = await _clipService.ExtractFeaturesAsync(request.ImageBase64);
            var vectorStr = "[" + string.Join(",", embedding) + "]";

            var pageId = await _bookRepository.CreatePageAsync(request.BookId, request.PageNumber, vectorStr, request.HasText);
            _logger.LogInformation("Page registered with ID: {Id}", pageId);

            return Ok(new RegisterPageResponse { Success = true, Id = pageId });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Register page failed");
            return StatusCode(500, new RegisterPageResponse
            {
                Success = false,
                Error = ex.Message
            });
        }
    }

    [HttpGet("{id:guid}")]
    public async Task<ActionResult<BookDetailResponse>> GetById(Guid id)
    {
        var book = await _bookRepository.GetBookDetailAsync(id);

        if (book == null)
        {
            return NotFound(new { error = "Book not found" });
        }

        return Ok(book);
    }

    private async Task<string?> CallOcrServiceAsync(byte[] imageBytes)
    {
        try
        {
            var base64 = Convert.ToBase64String(imageBytes);
            var client = _httpClientFactory.CreateClient();
            var request = new { imageBase64 = base64, task = "ocr" };
            var response = await client.PostAsJsonAsync($"{OcrServiceUrl}/ocr/recognize-simple", request);

            if (response.IsSuccessStatusCode)
            {
                var result = await response.Content.ReadFromJsonAsync<OcrServiceResponse>();
                if (result?.Success == true && !string.IsNullOrEmpty(result.Text))
                {
                    _logger.LogInformation("OcrService returned: {Text}", result.Text);
                    return result.Text;
                }
            }
            _logger.LogWarning("OcrService call failed: {Status}", response.StatusCode);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "OcrService call failed");
        }
        return null;
    }

    private class OcrServiceResponse
    {
        public bool Success { get; set; }
        public string Text { get; set; } = string.Empty;
        public string? Error { get; set; }
    }
}