using Microsoft.ML.OnnxRuntime;
using Microsoft.ML.OnnxRuntime.Tensors;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;
using SessionOptions = Microsoft.ML.OnnxRuntime.SessionOptions;
using GraphOptimizationLevel = Microsoft.ML.OnnxRuntime.GraphOptimizationLevel;

namespace BookManagementService.Services;

public class ClipService : IClipService, IDisposable
{
    private InferenceSession? _session;
    private readonly ILogger<ClipService> _logger;
    private readonly string _modelPath;

    // CLIP ViT-L-14 constants
    private const int InputSize = 224;
    private const int EmbeddingDim = 768;

    // ImageNet normalization
    private static readonly float[] Mean = { 0.485f, 0.456f, 0.406f };
    private static readonly float[] Std = { 0.229f, 0.224f, 0.225f };

    public ClipService(ILogger<ClipService> logger, IConfiguration configuration)
    {
        _logger = logger;
        _modelPath = configuration["ClipService:ModelPath"] ?? "models/ViT-L-14-CLIP.onnx";

        InitializeSession();
    }

    private void InitializeSession()
    {
        try
        {
            var sessionOptions = new SessionOptions();
            sessionOptions.GraphOptimizationLevel = GraphOptimizationLevel.ORT_ENABLE_ALL;

            _session = new InferenceSession(_modelPath, sessionOptions);
            _logger.LogInformation("CLIP ONNX model loaded from {ModelPath}", _modelPath);

            if (_session.InputMetadata.Count > 0)
            {
                _logger.LogInformation("Input metadata: {InputKeys}", string.Join(", ", _session.InputMetadata.Keys));
            }
            if (_session.OutputMetadata.Count > 0)
            {
                _logger.LogInformation("Output metadata: {OutputKeys}", string.Join(", ", _session.OutputMetadata.Keys));
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to initialize CLIP ONNX session");
            throw;
        }
    }

    public async Task<float[]> ExtractFeaturesAsync(string imageBase64)
    {
        return await Task.Run(() =>
        {
            if (_session == null)
            {
                throw new InvalidOperationException("CLIP ONNX session not initialized");
            }

            try
            {
                // Handle data URI format
                if (imageBase64.Contains(','))
                {
                    imageBase64 = imageBase64.Split(',')[1];
                }

                var imageBytes = Convert.FromBase64String(imageBase64);
                using var image = Image.Load<Rgb24>(imageBytes);

                // Preprocess image
                var preprocessedData = PreprocessImage(image);

                // Create input tensor [1, 3, 224, 224]
                var inputTensor = new DenseTensor<float>(preprocessedData, new[] { 1, 3, InputSize, InputSize });

                // Determine input name dynamically
                var inputName = _session.InputMetadata.Keys.FirstOrDefault() ?? "image";

                // Run inference
                var inputs = new List<NamedOnnxValue>
                {
                    NamedOnnxValue.CreateFromTensor(inputName, inputTensor)
                };

                using var results = _session.Run(inputs);
                var output = results.First().AsEnumerable<float>().ToArray();

                // L2 normalize
                L2Normalize(output);

                _logger.LogDebug("Extracted CLIP features: dimension={Dimension}", output.Length);
                return output;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to extract CLIP features");
                throw;
            }
        });
    }

    private float[] PreprocessImage(Image<Rgb24> image)
    {
        // Resize to 224x224
        image.Mutate(x => x.Resize(new ResizeOptions
        {
            Size = new Size(InputSize, InputSize),
            Mode = ResizeMode.Crop
        }));

        // Preprocess: HWC -> CHW, normalize with ImageNet mean/std
        var result = new float[3 * InputSize * InputSize];

        image.ProcessPixelRows(pixelAccessor =>
        {
            int idx = 0;
            for (int y = 0; y < InputSize; y++)
            {
                var row = pixelAccessor.GetRowSpan(y);
                for (int x = 0; x < InputSize; x++)
                {
                    var pixel = row[x];
                    result[idx++] = (pixel.R / 255f - Mean[0]) / Std[0];
                    result[idx++] = (pixel.G / 255f - Mean[1]) / Std[1];
                    result[idx++] = (pixel.B / 255f - Mean[2]) / Std[2];
                }
            }
        });

        return result;
    }

    private void L2Normalize(float[] vector)
    {
        float sum = 0;
        foreach (var v in vector)
        {
            sum += v * v;
        }

        float magnitude = MathF.Sqrt(sum);

        if (magnitude > 1e-10f)
        {
            for (int i = 0; i < vector.Length; i++)
            {
                vector[i] /= magnitude;
            }
        }
    }

    public void Dispose()
    {
        _session?.Dispose();
    }
}
