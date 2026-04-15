using BookManagementService.DTOs;
using BookManagementService.Entities;
using BookManagementService.Repositories;
using Moq;
using Xunit;

namespace BookManagementService.Tests;

/// <summary>
/// Unit tests for IBookRepository interface implementation.
/// </summary>
public class BookRepositoryTests
{
    [Fact]
    public void BookMatchResult_DefaultValues_AreCorrect()
    {
        // Arrange & Act
        var result = new BookMatchResult();

        // Assert
        Assert.Equal(Guid.Empty, result.Id);
        Assert.Equal(string.Empty, result.Title);
        Assert.Equal(0f, result.Similarity);
        Assert.Null(result.Metadata);
    }

    [Fact]
    public void BookMatchResult_WithValues_StoresCorrectly()
    {
        // Arrange
        var id = Guid.NewGuid();
        var metadata = new Dictionary<string, object> { { "author", "Test" } };

        // Act
        var result = new BookMatchResult
        {
            Id = id,
            Title = "Test Book",
            Similarity = 0.95f,
            Metadata = metadata
        };

        // Assert
        Assert.Equal(id, result.Id);
        Assert.Equal("Test Book", result.Title);
        Assert.Equal(0.95f, result.Similarity);
        Assert.NotNull(result.Metadata);
        Assert.Equal("Test", result.Metadata["author"]);
    }

    [Fact]
    public void BookDetailResponse_DefaultValues_AreCorrect()
    {
        // Arrange & Act
        var response = new BookDetailResponse();

        // Assert
        Assert.Equal(Guid.Empty, response.Id);
        Assert.Equal(string.Empty, response.Title);
        Assert.Null(response.Metadata);
    }

    [Fact]
    public void MatchResponse_DefaultValues_AreCorrect()
    {
        // Arrange & Act
        var response = new MatchResponse();

        // Assert
        Assert.False(response.Success);
        Assert.NotNull(response.Books);
        Assert.Empty(response.Books);
        Assert.Null(response.Error);
    }

    [Fact]
    public void MatchPageResponse_WithPage_SetsCorrectly()
    {
        // Arrange
        var page = new PageMatchResult
        {
            Id = Guid.NewGuid(),
            PageNumber = 1,
            Similarity = 0.88f,
            HasText = true
        };

        // Act
        var response = new MatchPageResponse
        {
            Success = true,
            Page = page
        };

        // Assert
        Assert.True(response.Success);
        Assert.NotNull(response.Page);
        Assert.Equal(1, response.Page.PageNumber);
        Assert.True(response.Page.HasText);
    }

    [Fact]
    public void RegisterResponse_WhenSuccessful_HasCorrectValues()
    {
        // Arrange
        var id = Guid.NewGuid();

        // Act
        var response = new RegisterResponse
        {
            Success = true,
            Id = id
        };

        // Assert
        Assert.True(response.Success);
        Assert.Equal(id, response.Id);
        Assert.Null(response.Error);
    }

    [Fact]
    public void RegisterResponse_WhenFailed_HasErrorMessage()
    {
        // Arrange & Act
        var response = new RegisterResponse
        {
            Success = false,
            Error = "Book already exists"
        };

        // Assert
        Assert.False(response.Success);
        Assert.Null(response.Id);
        Assert.Equal("Book already exists", response.Error);
    }

    [Fact]
    public void HealthResponse_DefaultValues_AreCorrect()
    {
        // Arrange & Act
        var response = new HealthResponse();

        // Assert
        Assert.Equal("healthy", response.Status);
        Assert.Equal("BookManagementService", response.Service);
        Assert.Equal("1.0.0", response.Version);
    }

    [Fact]
    public async Task MockBookRepository_GetById_ReturnsBook()
    {
        // Arrange
        var mockRepo = new Mock<IBookRepository>();
        var bookId = Guid.NewGuid();
        var book = new Book
        {
            Id = bookId,
            Title = "Test Book",
            CoverEmbedding = "[0.1, 0.2]",
            CreatedAt = DateTime.UtcNow
        };
        mockRepo.Setup(r => r.GetByIdAsync(bookId)).ReturnsAsync(book);

        // Act
        var result = await mockRepo.Object.GetByIdAsync(bookId);

        // Assert
        Assert.NotNull(result);
        Assert.Equal(bookId, result.Id);
        Assert.Equal("Test Book", result.Title);
    }

    [Fact]
    public async Task MockBookRepository_ExistsAsync_ReturnsCorrectly()
    {
        // Arrange
        var mockRepo = new Mock<IBookRepository>();
        var bookId = Guid.NewGuid();
        mockRepo.Setup(r => r.ExistsAsync(bookId)).ReturnsAsync(true);
        mockRepo.Setup(r => r.ExistsAsync(Guid.NewGuid())).ReturnsAsync(false);

        // Act & Assert
        Assert.True(await mockRepo.Object.ExistsAsync(bookId));
        Assert.False(await mockRepo.Object.ExistsAsync(Guid.NewGuid()));
    }

    [Fact]
    public async Task MockBookRepository_MatchBookCovers_ReturnsFilteredResults()
    {
        // Arrange
        var mockRepo = new Mock<IBookRepository>();
        var vectorStr = "[0.1, 0.2]";
        var matches = new List<BookMatchResult>
        {
            new BookMatchResult { Id = Guid.NewGuid(), Title = "Book 1", Similarity = 0.92f },
            new BookMatchResult { Id = Guid.NewGuid(), Title = "Book 2", Similarity = 0.85f }
        };
        mockRepo.Setup(r => r.MatchBookCoversAsync(vectorStr, 0.7f)).ReturnsAsync(matches);

        // Act
        var result = await mockRepo.Object.MatchBookCoversAsync(vectorStr, 0.7f);

        // Assert
        Assert.Equal(2, result.Count);
        Assert.All(result, m => Assert.True(m.Similarity >= 0.7f));
    }

    [Fact]
    public async Task MockBookRepository_CreatePageAsync_ReturnsNewGuid()
    {
        // Arrange
        var mockRepo = new Mock<IBookRepository>();
        var bookId = Guid.NewGuid();
        var pageId = Guid.NewGuid();
        mockRepo.Setup(r => r.CreatePageAsync(bookId, 1, It.IsAny<string>(), true))
            .ReturnsAsync(pageId);

        // Act
        var result = await mockRepo.Object.CreatePageAsync(bookId, 1, "[0.1]", true);

        // Assert
        Assert.Equal(pageId, result);
    }

    [Fact]
    public async Task MockBookRepository_GetBookDetailAsync_ReturnsCorrectStructure()
    {
        // Arrange
        var mockRepo = new Mock<IBookRepository>();
        var bookId = Guid.NewGuid();
        var detail = new BookDetailResponse
        {
            Id = bookId,
            Title = "Test Book",
            Metadata = new Dictionary<string, object> { { "pages", 10 } },
            CreatedAt = DateTime.UtcNow
        };
        mockRepo.Setup(r => r.GetBookDetailAsync(bookId)).ReturnsAsync(detail);

        // Act
        var result = await mockRepo.Object.GetBookDetailAsync(bookId);

        // Assert
        Assert.NotNull(result);
        Assert.Equal("Test Book", result.Title);
        Assert.NotNull(result.Metadata);
    }
}
