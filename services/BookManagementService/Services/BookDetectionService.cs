using OpenCvSharp;

namespace BookManagementService.Services;

/// <summary>
/// OpenCV-based book detection service.
/// Uses Canny edge detection + contour analysis to find the largest rectangular region (book page).
/// </summary>
public class BookDetectionService : IBookDetectionService
{
    private readonly ILogger<BookDetectionService> _logger;

    public BookDetectionService(ILogger<BookDetectionService> logger)
    {
        _logger = logger;
    }

    /// <summary>
    /// Detects and crops a book region from the given image using OpenCV.
    /// </summary>
    public async Task<BookDetectionResult?> DetectAndCropAsync(byte[] imageBytes, float minConfidence = 0.5f)
    {
        return await Task.Run(() =>
        {
            try
            {
                // Convert to OpenCvSharp Mat
                using var mat = BytesToMat(imageBytes);

                // Detect book region using OpenCV
                var bookRect = DetectLargestRect(mat);

                if (bookRect == null)
                {
                    _logger.LogInformation("No book region detected, using full image");
                    return null;
                }

                _logger.LogInformation("Detected region: x={X}, y={Y}, w={Width}, h={Height}",
                    bookRect.Value.X, bookRect.Value.Y, bookRect.Value.Width, bookRect.Value.Height);

                // Crop the detected region
                using var cropped = new Mat(mat, bookRect.Value);

                // Convert cropped Mat to JPEG bytes
                var resultBytes = MatToJpegBytes(cropped);

                return new BookDetectionResult
                {
                    CroppedImage = resultBytes,
                    Confidence = 1.0f,
                    Box = new BoundingBox
                    {
                        X = bookRect.Value.X,
                        Y = bookRect.Value.Y,
                        Width = bookRect.Value.Width,
                        Height = bookRect.Value.Height
                    }
                };
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Book detection failed");
                return null;
            }
        });
    }

    /// <summary>
    /// Detects the largest rectangular region in the image using OpenCV edge detection + contours.
    /// </summary>
    private Rect? DetectLargestRect(Mat src)
    {
        // 1. Convert to grayscale
        using var gray = new Mat();
        Cv2.CvtColor(src, gray, ColorConversionCodes.BGR2GRAY);

        // 2. Edge detection with Canny
        using var edges = new Mat();
        Cv2.Canny(gray, edges, 50, 150);

        // 3. Morphological dilation to strengthen edges
        using var kernel = Cv2.GetStructuringElement(MorphShapes.Rect, new OpenCvSharp.Size(3, 3));
        using var morph = new Mat();
        Cv2.Dilate(edges, morph, kernel, iterations: 1);

        // 4. Find contours
        Point[][] contours;
        HierarchyIndex[] hierarchy;
        Cv2.FindContours(morph, out contours, out hierarchy, RetrievalModes.External, ContourApproximationModes.ApproxSimple);

        if (contours.Length == 0)
            return null;

        // 5. Find the largest contour by area
        var largest = contours
            .Select((c, i) => new { Index = i, Area = Cv2.ContourArea(contours[i]), Contour = c })
            .OrderByDescending(x => x.Area)
            .First();

        _logger.LogDebug("Largest contour area={Area:F0}, total contours={Count}", largest.Area, contours.Length);

        // 6. Get bounding rectangle
        var boundingRect = Cv2.BoundingRect(largest.Contour);

        // 7. Validate: reject if too small relative to image
        var imageArea = src.Width * (double)src.Height;
        var rectArea = boundingRect.Width * (double)boundingRect.Height;
        var areaRatio = rectArea / imageArea;

        // Reject if rect covers less than 15% of image area
        if (areaRatio < 0.15)
        {
            _logger.LogDebug("Rect too small ({Ratio:P0} < 15%), rejecting", areaRatio);
            return null;
        }

        // Reject if rect is too small in absolute terms
        if (boundingRect.Width < 100 || boundingRect.Height < 100)
        {
            _logger.LogDebug("Rect too small in pixels ({Width}x{Height}), rejecting", boundingRect.Width, boundingRect.Height);
            return null;
        }

        return boundingRect;
    }

    private Mat BytesToMat(byte[] imageBytes)
    {
        return Mat.ImDecode(imageBytes);
    }

    private byte[] MatToJpegBytes(Mat mat)
    {
        Cv2.ImEncode(".jpg", mat, out var bytes, new[] { new ImageEncodingParam(ImwriteFlags.JpegQuality, 90) });
        return bytes;
    }
}