using BookManagementService.DTOs;
using BookManagementService.Entities;

namespace BookManagementService.Repositories;

public interface IBookRepository
{
    Task<Book?> GetByIdAsync(Guid id);
    Task<bool> ExistsAsync(Guid id);
    Task<Guid> CreateBookAsync(string title, string vectorStr, string? metadata);
    Task<List<BookMatchResult>> MatchBookCoversAsync(string vectorStr, float minSimilarity = 0.7f);
    Task<List<BookMatchResult>> MatchPagesAsync(Guid bookId, string vectorStr, float minSimilarity = 0.7f);
    Task<Guid> CreatePageAsync(Guid bookId, int pageNumber, string vectorStr, bool hasText);
}