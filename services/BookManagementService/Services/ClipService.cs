using Microsoft.ML.OnnxRuntime;
using Microsoft.ML.OnnxRuntime.Tensors;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;

namespace BookManagementService.Services;

public class ClipService : IClipService, IDisposable
{
    private readonly InferenceSession _session;
    private readonly ILogger<ClipService> _logger;
    private readonly int _imageSize = 224;

    public ClipService(ILogger<ClipService> logger, IConfiguration configuration)
    {
        _logger = logger;
        var modelPath = configuration["ClipService:ModelPath"] ?? "models/ViT-L-14-CLIP.onnx";

        var sessionOptions = new Microsoft.ML.OnnxRuntime.SessionOptions();
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
                using var image = Image.Load<Rgb24>(imageBytes);

                // Resize to 224x224
                image.Mutate(x => x.Resize(new ResizeOptions
                {
                    Size = new Size(_imageSize, _imageSize),
                    Mode = ResizeMode.Crop
                }));

                // Preprocess: normalize
                var tensor = PreprocessImage(image);

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

    private Tensor<float> PreprocessImage(Image<Rgb24> image)
    {
        var tensor = new DenseTensor<float>(new[] { 3, _imageSize, _imageSize });

        // ImageNet normalization
        float[] mean = { 0.48145466f, 0.4578275f, 0.40821073f };
        float[] std = { 0.26862954f, 0.26130258f, 0.27577711f };

        for (int y = 0; y < _imageSize; y++)
        {
            for (int x = 0; x < _imageSize; x++)
            {
                var pixel = image[x, y];
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
