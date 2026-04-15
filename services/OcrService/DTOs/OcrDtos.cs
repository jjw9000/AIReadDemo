using System.ComponentModel.DataAnnotations;

namespace OcrService.DTOs;

public class OcrRequest
{
    [Required]
    public string ImageBase64 { get; set; } = string.Empty;

    public string Task { get; set; } = "ocr";
}

public class TextBlock
{
    public string Text { get; set; } = string.Empty;
    public float Confidence { get; set; }
    public BoundingBox? BoundingBox { get; set; }
}

public class BoundingBox
{
    public int X { get; set; }
    public int Y { get; set; }
    public int Width { get; set; }
    public int Height { get; set; }
}

public class OcrResultData
{
    public bool Success { get; set; }
    public List<TextBlock> TextBlocks { get; set; } = new();
    public string FullText { get; set; } = string.Empty;
    public string? ErrorMessage { get; set; }
}

public class OcrResponse
{
    public bool Success { get; set; }
    public OcrResultData? Data { get; set; }
    public string? Message { get; set; }
    public int? Code { get; set; }
}

public class SimpleOcrResponse
{
    public bool Success { get; set; }
    public string Text { get; set; } = string.Empty;
    public string? Error { get; set; }
    public int BlockCount { get; set; }
}

public class HealthResponse
{
    public string Status { get; set; } = "healthy";
    public string Service { get; set; } = "OcrService";
    public string Version { get; set; } = "1.0.0";
    public string Model { get; set; } = "PaddleOCR-VL-1.5";
}