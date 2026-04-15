"""
PaddleOCR VL 1.5 FastAPI Service
基于 PaddleOCR-VL-1.5 的文字识别服务

支持: OCR、表格、公式、图表、印章识别
"""

import os
import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, ConfigDict

from ocr_service import initialize_service, get_service

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


# 请求模型 - 支持 camelCase 和 snake_case
class OcrRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    image_base64: str = Field(validation_alias="imageBase64")
    task: Optional[str] = Field(default="ocr", validation_alias="task")


# 响应模型
class TextBlock(BaseModel):
    text: str
    confidence: float
    bounding_box: Optional[dict] = None


class OcrResultData(BaseModel):
    success: bool
    text_blocks: list
    full_text: str
    error_message: Optional[str] = None


class OcrResponse(BaseModel):
    success: bool
    data: Optional[OcrResultData] = None
    message: Optional[str] = None
    code: Optional[int] = None


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    model: str


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    # 获取配置（支持环境变量）
    model_path = os.getenv("OCR_MODEL_PATH", "D:\\project\\reader\\src\\services\\OcrService\\model")
    task = os.getenv("OCR_TASK", "ocr")
    use_gpu = os.getenv("OCR_USE_GPU", "false").lower() == "true"
    device = os.getenv("OCR_DEVICE", None)

    # 启动时初始化 OCR 服务
    logger.info("启动 PaddleOCR-VL-1.5 服务...")
    logger.info(f"  model_path: {model_path}")
    logger.info(f"  task: {task}")
    logger.info(f"  use_gpu: {use_gpu}")
    logger.info(f"  device: {device}")

    try:
        initialize_service(
            model_path=model_path,
            task=task,
            use_gpu=use_gpu,
            device=device
        )
        logger.info("PaddleOCR-VL-1.5 服务初始化完成")
    except Exception as e:
        logger.error(f"初始化失败: {e}")
        raise
    yield
    # 关闭时清理资源
    logger.info("关闭 PaddleOCR-VL-1.5 服务")


app = FastAPI(
    title="PaddleOCR-VL-1.5 Service",
    description="基于 PaddleOCR-VL-1.5 的文字识别服务，支持 OCR、表格、公式、图表、印章识别",
    version="1.0.0",
    lifespan=lifespan
)

# CORS 配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health_check():
    """健康检查"""
    return HealthResponse(
        status="healthy",
        service="PaddleOCR-VL-1.5",
        version="1.0.0",
        model=os.getenv("OCR_MODEL_PATH", "PaddlePaddle/PaddleOCR-VL-1.5")
    )


@app.post("/ocr/recognize", response_model=OcrResponse)
async def recognize_text(request: OcrRequest):
    """
    识别图片中的文字

    Args:
        request: 包含 Base64 编码图片的请求

    Returns:
        OcrResponse: 识别结果
    """
    try:
        logger.info(f"收到 OCR 请求，图片长度: {len(request.image_base64)}, task: {request.task}")

        service = get_service()
        result = service.recognize_from_base64(request.image_base64)

        if not result.success:
            return OcrResponse(
                success=False,
                data=OcrResultData(
                    success=False,
                    text_blocks=[],
                    full_text="",
                    error_message=result.error_message
                )
            )

        text_blocks = [
            TextBlock(
                text=block.text,
                confidence=block.confidence,
                bounding_box=block.bounding_box
            )
            for block in result.text_blocks
        ]

        return OcrResponse(
            success=True,
            data=OcrResultData(
                success=True,
                text_blocks=text_blocks,
                full_text=result.full_text
            )
        )

    except Exception as e:
        logger.error(f"OCR 处理异常: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ocr/recognize-simple")
async def recognize_simple(request: OcrRequest):
    """
    简化版识别接口，返回纯文本

    Returns:
        包含识别文字的简单响应
    """
    try:
        service = get_service()
        result = service.recognize_from_base64(request.image_base64)

        if not result.success:
            return {"success": False, "text": "", "error": result.error_message}

        return {
            "success": True,
            "text": result.full_text,
            "block_count": len(result.text_blocks)
        }

    except Exception as e:
        logger.error(f"OCR 处理异常: {e}")
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5017)
