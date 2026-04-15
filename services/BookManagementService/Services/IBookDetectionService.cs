namespace BookManagementService.Services;

public interface IBookDetectionService
{
    Task<BookDetectionResult?> DetectAndCropAsync(byte[] imageBytes, float minConfidence = 0.5f);
}

public class BookDetectionResult
{
    public byte[] CroppedImage { get; set; } = Array.Empty<byte>();
    public float Confidence { get; set; }
    public BoundingBox? Box { get; set; }
}

public class BoundingBox
{
    public int X { get; set; }
    public int Y { get; set; }
    public int Width { get; set; }
    public int Height { get; set; }
}