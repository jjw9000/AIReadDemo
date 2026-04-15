using System.ComponentModel.DataAnnotations;

namespace BookManagementService.DTOs;

// Request DTOs
public class MatchRequest
{
    [Required]
    public string ImageBase64 { get; set; } = string.Empty;

    public Guid? BookId { get; set; }  // Optional: if set, match page within this book
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

public class RegisterPageRequest
{
    [Required]
    public string ImageBase64 { get; set; } = string.Empty;

    [Required]
    public Guid BookId { get; set; }

    [Required]
    public int PageNumber { get; set; }

    public bool HasText { get; set; } = false;
}

public class MatchPageRequest
{
    [Required]
    public string ImageBase64 { get; set; } = string.Empty;

    [Required]
    public Guid BookId { get; set; }
}

// Response DTOs
public class BookMatchResult
{
    public Guid Id { get; set; }
    public string Title { get; set; } = string.Empty;
    public float Similarity { get; set; }
    public Dictionary<string, object>? Metadata { get; set; }
    public List<PageMatchResultDto> Pages { get; set; } = new();
}

public class PageMatchResultDto
{
    public int PageNumber { get; set; }
    public string FullText { get; set; } = string.Empty;
}

public class MatchResponse
{
    public bool Success { get; set; }
    public BookMatchResult? Book { get; set; }
    public string? Error { get; set; }
}

public class PageMatchResult
{
    public Guid Id { get; set; }
    public int PageNumber { get; set; }
    public float Similarity { get; set; }
    public bool HasText { get; set; }
}

public class MatchPageResponse
{
    public bool Success { get; set; }
    public PageMatchResult? Page { get; set; }
    public string? Error { get; set; }
}

public class RegisterResponse
{
    public bool Success { get; set; }
    public Guid? Id { get; set; }
    public string? Error { get; set; }
}

public class RegisterPageResponse
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
