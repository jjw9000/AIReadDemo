using Microsoft.ML.OnnxRuntime;
using Microsoft.ML.OnnxRuntime.Tensors;
using System.Drawing;
using System.Drawing.Imaging;

namespace BookManagementService.Services;

public class ClipService : IClipService, IDisposable
{
    private readonly InferenceSession _session;
    private readonly ILogger<ClipService> _logger;
    private readonly int _imageSize = 224;

    public ClipService(ILogger<ClipService> logger, IConfiguration configuration)
    {
        _logger = logger;
        var modelPath = configuration["ClipService:ModelPath"] ?? "models/clip-vit-base-patch32.onnx";

        var sessionOptions = new SessionOptions();
        sessionOptions.GraphOptimizationLevel = GraphOptimizationLevel.ORT_ENABLE_ALL;
        _session = new InferenceSession(modelPath, sessionOptions);

        _logger.LogInformation("CLIP ONNX model loaded from {ModelPath}", modelPath);
    }

    public async Task<float[]> ExtractFeaturesAsync(string imageBase64)
    {
        return await Task.Run(() =>
        {
            try
            {
                // Handle data URI format
                if (imageBase64.Contains(','))
                {
                    imageBase64 = imageBase64.Split(',')[1];
                }

                var imageBytes = Convert.FromBase64String(imageBase64);
                using var ms = new MemoryStream(imageBytes);
                using var bitmap = new Bitmap(ms);

                // Preprocess: resize to 224x224 and normalize
                var tensor = PreprocessImage(bitmap);

                // Run inference
                var inputs = new[]
                {
                    NamedOnnxValue.CreateFromTensor("input", tensor)
                };

                using var results = _session.Run(inputs);
                var output = results.FirstOrDefault();

                if (output == null)
                {
                    throw new InvalidOperationException("CLIP inference returned no output");
                }

                var embedding = output.AsEnumerable<float>().ToArray();

                // L2 normalize
                var norm = (float)Math.Sqrt(embedding.Sum(x => x * x));
                for (int i = 0; i < embedding.Length; i++)
                {
                    embedding[i] /= norm;
                }

                _logger.LogDebug("Extracted CLIP features: dimension={Dimension}", embedding.Length);
                return embedding;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to extract CLIP features");
                throw;
            }
        });
    }

    private Tensor<float> PreprocessImage(Bitmap bitmap)
    {
        using var resized = new Bitmap(bitmap, new Size(_imageSize, _imageSize));
        var tensor = new DenseTensor<float>(new[] { 3, _imageSize, _imageSize });

        // ImageNet normalization
        float[] mean = { 0.48145466f, 0.4578275f, 0.40821073f };
        float[] std = { 0.26862954f, 0.26130258f, 0.27577711f };

        for (int y = 0; y < _imageSize; y++)
        {
            for (int x = 0; x < _imageSize; x++)
            {
                var pixel = resized.GetPixel(x, y);
                tensor[0, y, x] = (pixel.R / 255f - mean[0]) / std[0];
                tensor[1, y, x] = (pixel.G / 255f - mean[1]) / std[1];
                tensor[2, y, x] = (pixel.B / 255f - mean[2]) / std[2];
            }
        }

        return tensor;
    }

    public void Dispose()
    {
        _session?.Dispose();
    }
}
