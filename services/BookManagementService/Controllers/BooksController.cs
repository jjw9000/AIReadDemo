using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using BookManagementService.Data;
using BookManagementService.DTOs;
using BookManagementService.Entities;
using BookManagementService.Services;
using Npgsql;

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
            var vectorStr = "[" + string.Join(",", queryEmbedding) + "]";

            // Use raw SQL for vector similarity search (like reference project)
            var conn = (NpgsqlConnection)_context.Database.GetDbConnection();
            if (conn.State != System.Data.ConnectionState.Open) await conn.OpenAsync();

            await using var cmd = new NpgsqlCommand();
            cmd.Connection = conn;
            cmd.CommandText = @"
                SELECT id, title, 1 - (cover_embedding <=> @vector::vector) AS similarity
                FROM books
                WHERE cover_embedding IS NOT NULL
                ORDER BY cover_embedding <=> @vector::vector
                LIMIT 5";
            cmd.Parameters.AddWithValue("@vector", vectorStr);

            var results = new List<BookMatchResult>();
            await using var reader = await cmd.ExecuteReaderAsync();
            while (await reader.ReadAsync())
            {
                var similarity = (float)(double)reader["similarity"];
                if (similarity > 0.7f)
                {
                    results.Add(new BookMatchResult
                    {
                        Id = reader.GetGuid(0),
                        Title = reader.GetString(1),
                        Similarity = similarity
                    });
                }
            }

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
            var vectorStr = "[" + string.Join(",", embedding) + "]";

            // Use raw SQL to insert with vector
            var conn = (NpgsqlConnection)_context.Database.GetDbConnection();
            if (conn.State != System.Data.ConnectionState.Open) await conn.OpenAsync();

            await using var cmd = new NpgsqlCommand();
            cmd.Connection = conn;
            cmd.CommandText = @"
                INSERT INTO books (id, title, cover_embedding, metadata, created_at, updated_at)
                VALUES (@id, @title, @vector::vector, @metadata, @createdAt, @updatedAt)
                RETURNING id";
            var bookId = Guid.NewGuid();
            cmd.Parameters.AddWithValue("@id", bookId);
            cmd.Parameters.AddWithValue("@title", request.Title);
            cmd.Parameters.AddWithValue("@vector", vectorStr);
            cmd.Parameters.AddWithValue("@metadata", request.Metadata != null
                ? System.Text.Json.JsonSerializer.Serialize(request.Metadata)
                : (object)DBNull.Value);
            cmd.Parameters.AddWithValue("@createdAt", DateTime.UtcNow);
            cmd.Parameters.AddWithValue("@updatedAt", DateTime.UtcNow);

            var result = await cmd.ExecuteScalarAsync();
            var insertedId = (Guid)result!;

            _logger.LogInformation("Book registered with ID: {Id}", insertedId);

            return Ok(new RegisterResponse
            {
                Success = true,
                Id = insertedId
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
}
