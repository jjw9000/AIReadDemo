using Microsoft.AspNetCore.Mvc;
using OcrService.DTOs;
using OcrService.Services;

namespace OcrService.Controllers;

[ApiController]
[Route("ocr")]
public class OcrController : ControllerBase
{
    private readonly IOcrService _ocrService;
    private readonly ILogger<OcrController> _logger;

    public OcrController(IOcrService ocrService, ILogger<OcrController> logger)
    {
        _ocrService = ocrService;
        _logger = logger;
    }

    [HttpGet("/health")]
    public async Task<ActionResult<HealthResponse>> HealthCheck()
    {
        return Ok(new HealthResponse
        {
            Status = "healthy",
            Service = "OcrService",
            Version = "1.0.0",
            Model = "PaddleOCR-VL-1.5"
        });
    }

    [HttpPost("recognize")]
    public async Task<ActionResult<OcrResponse>> Recognize([FromBody] OcrRequest request)
    {
        try
        {
            _logger.LogInformation("Received OCR request, image length: {Length}, task: {Task}",
                request.ImageBase64.Length, request.Task);

            var result = await _ocrService.RecognizeAsync(request.ImageBase64, request.Task);

            if (!result.Success)
            {
                return Ok(new OcrResponse
                {
                    Success = false,
                    Data = result,
                    Message = result.ErrorMessage
                });
            }

            return Ok(new OcrResponse
            {
                Success = true,
                Data = result
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "OCR processing error");
            return StatusCode(500, new OcrResponse
            {
                Success = false,
                Message = ex.Message
            });
        }
    }

    [HttpPost("recognize-simple")]
    public async Task<ActionResult<SimpleOcrResponse>> RecognizeSimple([FromBody] OcrRequest request)
    {
        try
        {
            var result = await _ocrService.RecognizeAsync(request.ImageBase64, request.Task);

            if (!result.Success)
            {
                return Ok(new SimpleOcrResponse
                {
                    Success = false,
                    Error = result.ErrorMessage
                });
            }

            return Ok(new SimpleOcrResponse
            {
                Success = true,
                Text = result.FullText,
                BlockCount = result.TextBlocks.Count
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "OCR processing error");
            return StatusCode(500, new SimpleOcrResponse
            {
                Success = false,
                Error = ex.Message
            });
        }
    }
}