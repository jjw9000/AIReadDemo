using OcrService.DTOs;

namespace OcrService.Services;

public interface IOcrService
{
    Task<OcrResultData> RecognizeAsync(string imageBase64, string task = "ocr");
    Task<bool> CheckHealthAsync();
}