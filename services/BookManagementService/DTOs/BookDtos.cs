using System.ComponentModel.DataAnnotations;

namespace BookManagementService.DTOs;

// Request DTOs
public class MatchRequest
{
    [Required]
    public string ImageBase64 { get; set; } = string.Empty;
}

public class RegisterRequest
{
    [Required]
    public string ImageBase64 { get; set; } = string.Empty;

    [Required]
    [MaxLength(255)]
    public string Title { get; set; } = string.Empty;

    public Dictionary<string, object>? Metadata { get; set; }
}

// Response DTOs
public class BookMatchResult
{
    public Guid Id { get; set; }
    public string Title { get; set; } = string.Empty;
    public float Similarity { get; set; }
    public Dictionary<string, object>? Metadata { get; set; }
}

public class MatchResponse
{
    public bool Success { get; set; }
    public List<BookMatchResult> Books { get; set; } = new();
    public string? Error { get; set; }
}

public class RegisterResponse
{
    public bool Success { get; set; }
    public Guid? Id { get; set; }
    public string? Error { get; set; }
}

public class BookDetailResponse
{
    public Guid Id { get; set; }
    public string Title { get; set; } = string.Empty;
    public Dictionary<string, object>? Metadata { get; set; }
    public DateTime CreatedAt { get; set; }
}

public class HealthResponse
{
    public string Status { get; set; } = "healthy";
    public string Service { get; set; } = "BookManagementService";
    public string Version { get; set; } = "1.0.0";
}
