using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using BookManagementService.Data;
using BookManagementService.DTOs;
using BookManagementService.Entities;
using BookManagementService.Services;

namespace BookManagementService.Controllers;

[ApiController]
[Route("api/[controller]")]
public class BooksController : ControllerBase
{
    private readonly BookDbContext _context;
    private readonly IClipService _clipService;
    private readonly ILogger<BooksController> _logger;

    public BooksController(
        BookDbContext context,
        IClipService clipService,
        ILogger<BooksController> logger)
    {
        _context = context;
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
            _logger.LogInformation("Received match request, image length: {Length}", request.ImageBase64.Length);

            // Extract features from image
            var queryEmbedding = await _clipService.ExtractFeaturesAsync(request.ImageBase64);

            // Search in database using cosine similarity
            var books = await _context.Books
                .Where(b => b.CoverEmbedding != null)
                .ToListAsync();

            var results = books
                .Select(b => new
                {
                    Book = b,
                    Similarity = CosineSimilarity(queryEmbedding, b.CoverEmbedding!)
                })
                .Where(x => x.Similarity > 0.7f)
                .OrderByDescending(x => x.Similarity)
                .Take(5)
                .Select(x => new BookMatchResult
                {
                    Id = x.Book.Id,
                    Title = x.Book.Title,
                    Similarity = x.Similarity,
                    Metadata = string.IsNullOrEmpty(x.Book.Metadata)
                        ? null
                        : System.Text.Json.JsonSerializer.Deserialize<Dictionary<string, object>>(x.Book.Metadata)
                })
                .ToList();

            _logger.LogInformation("Found {Count} matching books", results.Count);

            return Ok(new MatchResponse
            {
                Success = true,
                Books = results
            });
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

            // Extract features from image
            var embedding = await _clipService.ExtractFeaturesAsync(request.ImageBase64);

            var book = new Book
            {
                Title = request.Title,
                CoverEmbedding = embedding,
                Metadata = request.Metadata != null
                    ? System.Text.Json.JsonSerializer.Serialize(request.Metadata)
                    : null,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            };

            _context.Books.Add(book);
            await _context.SaveChangesAsync();

            _logger.LogInformation("Book registered with ID: {Id}", book.Id);

            return Ok(new RegisterResponse
            {
                Success = true,
                Id = book.Id
            });
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

    [HttpGet("{id:guid}")]
    public async Task<ActionResult<BookDetailResponse>> GetById(Guid id)
    {
        var book = await _context.Books.FindAsync(id);

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

    private static float CosineSimilarity(float[] a, float[] b)
    {
        if (a.Length != b.Length)
            return 0;

        float dotProduct = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < a.Length; i++)
        {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        return dotProduct / ((float)Math.Sqrt(normA) * (float)Math.Sqrt(normB));
    }
}
