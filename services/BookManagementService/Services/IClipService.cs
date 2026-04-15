namespace BookManagementService.Services;

public interface IClipService
{
    Task<float[]> ExtractFeaturesAsync(string imageBase64);
}
