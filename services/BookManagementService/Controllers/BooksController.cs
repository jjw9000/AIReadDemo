using Microsoft.AspNetCore.Mvc;
using BookManagementService.DTOs;
using BookManagementService.Entities;
using BookManagementService.Repositories;
using BookManagementService.Services;

namespace BookManagementService.Controllers;

[ApiController]
[Route("api/[controller]")]
public class BooksController : ControllerBase
{
    private readonly IBookRepository _bookRepository;
    private readonly IClipService _clipService;
    private readonly ILogger<BooksController> _logger;

    public BooksController(
        IBookRepository bookRepository,
        IClipService clipService,
        ILogger<BooksController> logger)
    {
        _bookRepository = bookRepository;
        _clipService = clipService;
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

            var queryEmbedding = await _clipService.ExtractFeaturesAsync(request.ImageBase64);
            var vectorStr = "[" + string.Join(",", queryEmbedding) + "]";

            if (request.BookId.HasValue)
            {
                var results = await _bookRepository.MatchPagesAsync(request.BookId.Value, vectorStr);
                _logger.LogInformation("Found {Count} matching pages for book {BookId}", results.Count, request.BookId.Value);
                return Ok(new MatchResponse { Success = true, Books = results });
            }
            else
            {
                var results = await _bookRepository.MatchBookCoversAsync(vectorStr);
                _logger.LogInformation("Found {Count} matching books", results.Count);
                return Ok(new MatchResponse { Success = true, Books = results });
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
        var book = await _bookRepository.GetByIdAsync(id);

        if (book == null)
        {
            return NotFound(new { error = "Book not found" });
        }

        return Ok(new BookDetailResponse
        {
            Id = book.Id,
            Title = book.Title,
            Metadata = string.IsNullOrEmpty(book.Metadata)
                ? null
                : System.Text.Json.JsonSerializer.Deserialize<Dictionary<string, object>>(book.Metadata),
            CreatedAt = book.CreatedAt
        });
    }
}