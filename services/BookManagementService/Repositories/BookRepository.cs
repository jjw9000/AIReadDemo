using BookManagementService.Data;
using BookManagementService.DTOs;
using BookManagementService.Entities;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace BookManagementService.Repositories;

public class BookRepository : IBookRepository
{
    private readonly BookDbContext _context;

    public BookRepository(BookDbContext context)
    {
        _context = context;
    }

    public async Task<Book?> GetByIdAsync(Guid id)
    {
        return await _context.Books.FindAsync(id);
    }

    public async Task<BookDetailResponse?> GetBookDetailAsync(Guid id)
    {
        var conn = (NpgsqlConnection)_context.Database.GetDbConnection();
        if (conn.State != System.Data.ConnectionState.Open) await conn.OpenAsync();

        await using var cmd = new NpgsqlCommand();
        cmd.Connection = conn;
        cmd.CommandText = "SELECT id, title, metadata, created_at FROM books WHERE id = @id";
        cmd.Parameters.AddWithValue("@id", id);

        await using var reader = await cmd.ExecuteReaderAsync();
        if (await reader.ReadAsync())
        {
            return new BookDetailResponse
            {
                Id = reader.GetGuid(0),
                Title = reader.GetString(1),
                Metadata = reader.IsDBNull(2) ? null
                    : System.Text.Json.JsonSerializer.Deserialize<Dictionary<string, object>>(reader.GetString(2)),
                CreatedAt = reader.GetDateTime(3)
            };
        }
        return null;
    }

    public async Task<bool> ExistsAsync(Guid id)
    {
        return await _context.Books.AnyAsync(b => b.Id == id);
    }

    public async Task<Guid> CreateBookAsync(string title, string vectorStr, string? metadata)
    {
        var conn = (NpgsqlConnection)_context.Database.GetDbConnection();
        if (conn.State != System.Data.ConnectionState.Open) await conn.OpenAsync();

        var bookId = Guid.NewGuid();
        await using var cmd = new NpgsqlCommand();
        cmd.Connection = conn;
        cmd.CommandText = @"
            INSERT INTO books (id, title, cover_embedding, metadata, created_at, updated_at)
            VALUES (@id, @title, @vector::vector, @metadata, @createdAt, @updatedAt)";
        cmd.Parameters.AddWithValue("@id", bookId);
        cmd.Parameters.AddWithValue("@title", title);
        cmd.Parameters.AddWithValue("@vector", vectorStr);
        cmd.Parameters.AddWithValue("@metadata", metadata != null
            ? System.Text.Json.JsonSerializer.Serialize(metadata)
            : (object)DBNull.Value);
        cmd.Parameters.AddWithValue("@createdAt", DateTime.UtcNow);
        cmd.Parameters.AddWithValue("@updatedAt", DateTime.UtcNow);

        await cmd.ExecuteNonQueryAsync();
        return bookId;
    }

    public async Task<List<BookMatchResult>> MatchBookCoversAsync(string vectorStr, float minSimilarity = 0.7f)
    {
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
            if (similarity > minSimilarity)
            {
                results.Add(new BookMatchResult
                {
                    Id = reader.GetGuid(0),
                    Title = reader.GetString(1),
                    Similarity = similarity
                });
            }
        }
        return results;
    }

    public async Task<List<BookMatchResult>> MatchPagesAsync(Guid bookId, string vectorStr, float minSimilarity = 0.7f)
    {
        var conn = (NpgsqlConnection)_context.Database.GetDbConnection();
        if (conn.State != System.Data.ConnectionState.Open) await conn.OpenAsync();

        await using var cmd = new NpgsqlCommand();
        cmd.Connection = conn;
        cmd.CommandText = @"
            SELECT id, page_number, 1 - (page_embedding <=> @vector::vector) AS similarity, has_text
            FROM pages
            WHERE book_id = @bookId AND page_embedding IS NOT NULL
            ORDER BY page_embedding <=> @vector::vector
            LIMIT 5";
        cmd.Parameters.AddWithValue("@vector", vectorStr);
        cmd.Parameters.AddWithValue("@bookId", bookId);

        var results = new List<BookMatchResult>();
        await using var reader = await cmd.ExecuteReaderAsync();
        while (await reader.ReadAsync())
        {
            var similarity = (float)(double)reader["similarity"];
            if (similarity > minSimilarity)
            {
                results.Add(new BookMatchResult
                {
                    Id = reader.GetGuid(0),
                    Title = $"Page {reader.GetInt32(1)}",
                    Similarity = similarity
                });
            }
        }
        return results;
    }

    public async Task<Guid> CreatePageAsync(Guid bookId, int pageNumber, string vectorStr, bool hasText)
    {
        var conn = (NpgsqlConnection)_context.Database.GetDbConnection();
        if (conn.State != System.Data.ConnectionState.Open) await conn.OpenAsync();

        // Ensure full_text column exists (migration)
        await EnsureFullTextColumnAsync(conn);

        var pageId = Guid.NewGuid();
        await using var cmd = new NpgsqlCommand();
        cmd.Connection = conn;
        cmd.CommandText = @"
            INSERT INTO pages (id, book_id, page_number, page_embedding, has_text, created_at)
            VALUES (@id, @bookId, @pageNumber, @vector::vector, @hasText, @createdAt)
            ON CONFLICT (book_id, page_number)
            DO UPDATE SET page_embedding = @vector::vector, has_text = @hasText, created_at = @createdAt";
        cmd.Parameters.AddWithValue("@id", pageId);
        cmd.Parameters.AddWithValue("@bookId", bookId);
        cmd.Parameters.AddWithValue("@pageNumber", pageNumber);
        cmd.Parameters.AddWithValue("@vector", vectorStr);
        cmd.Parameters.AddWithValue("@hasText", hasText);
        cmd.Parameters.AddWithValue("@createdAt", DateTime.UtcNow);

        await cmd.ExecuteNonQueryAsync();
        return pageId;
    }

    public async Task<List<PageMatchResultDto>> GetBookPagesAsync(Guid bookId)
    {
        var conn = (NpgsqlConnection)_context.Database.GetDbConnection();
        if (conn.State != System.Data.ConnectionState.Open) await conn.OpenAsync();

        // Ensure full_text column exists (migration)
        await EnsureFullTextColumnAsync(conn);

        await using var cmd = new NpgsqlCommand();
        cmd.Connection = conn;
        cmd.CommandText = @"
            SELECT page_number,
                   COALESCE(full_text, '') as full_text,
                   has_text
            FROM pages
            WHERE book_id = @bookId
            ORDER BY page_number";
        cmd.Parameters.AddWithValue("@bookId", bookId);

        var results = new List<PageMatchResultDto>();
        await using var reader = await cmd.ExecuteReaderAsync();
        while (await reader.ReadAsync())
        {
            results.Add(new PageMatchResultDto
            {
                PageNumber = reader.GetInt32(0),
                FullText = reader.GetString(1)
            });
        }
        return results;
    }

    public async Task<List<BookMatchResult>> SearchBooksByTextAsync(string searchText, float minSimilarity = 0.3f)
    {
        if (string.IsNullOrWhiteSpace(searchText))
            return new List<BookMatchResult>();

        var conn = (NpgsqlConnection)_context.Database.GetDbConnection();
        if (conn.State != System.Data.ConnectionState.Open) await conn.OpenAsync();

        // Ensure full_text column exists
        await EnsureFullTextColumnAsync(conn);

        // Search pages by full_text using ILIKE for fuzzy matching
        // Split search text into tokens for better matching
        var tokens = searchText.Split(' ', StringSplitOptions.RemoveEmptyEntries)
            .Where(t => t.Length >= 2)
            .ToArray();

        if (tokens.Length == 0)
            return new List<BookMatchResult>();

        // Build search pattern - each token must appear somewhere in the text
        var patterns = tokens.Select(t => $"%{t}%").ToArray();
        var whereClause = string.Join(" AND ", patterns.Select((_, i) => $"full_text ILIKE @p{i}"));

        await using var cmd = new NpgsqlCommand();
        cmd.Connection = conn;
        cmd.CommandText = $@"
            SELECT DISTINCT b.id, b.title,
                   COALESCE(full_text, '') as page_text,
                   1.0 as similarity
            FROM books b
            JOIN pages p ON b.id = p.book_id
            WHERE {whereClause}
            LIMIT 10";

        for (int i = 0; i < patterns.Length; i++)
        {
            cmd.Parameters.AddWithValue($"@p{i}", patterns[i]);
        }

        var results = new List<BookMatchResult>();
        await using var reader = await cmd.ExecuteReaderAsync();
        while (await reader.ReadAsync())
        {
            results.Add(new BookMatchResult
            {
                Id = reader.GetGuid(0),
                Title = reader.GetString(1),
                Similarity = (float)(double)reader["similarity"]
            });
        }
        return results;
    }

    private async Task EnsureFullTextColumnAsync(NpgsqlConnection conn)
    {
        try
        {
            await using var checkCmd = new NpgsqlCommand(@"
                SELECT 1 FROM information_schema.columns
                WHERE table_name = 'pages' AND column_name = 'full_text'", conn);
            var exists = await checkCmd.ExecuteScalarAsync();
            if (exists == null)
            {
                await using var alterCmd = new NpgsqlCommand(@"
                    ALTER TABLE pages ADD COLUMN full_text TEXT", conn);
                await alterCmd.ExecuteNonQueryAsync();
            }
        }
        catch
        {
            // Column might already exist, ignore
        }
    }
}