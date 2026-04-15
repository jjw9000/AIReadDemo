using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace BookManagementService.Entities;

[Table("pages")]
public class Page
{
    [Key]
    [Column("id")]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    [Column("book_id")]
    public Guid BookId { get; set; }

    [Required]
    [Column("page_number")]
    public int PageNumber { get; set; }

    [Column("page_embedding")]
    public string? PageEmbedding { get; set; }

    [Column("has_text")]
    public bool HasText { get; set; } = false;

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    // Navigation property
    [ForeignKey("BookId")]
    public Book? Book { get; set; }
}
