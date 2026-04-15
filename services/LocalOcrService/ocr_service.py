"""
PaddleOCR VL 1.5 Service
基于 PaddleOCR-VL-1.5 的文字识别服务

使用 transformers 库加载 PaddleOCR-VL-1.5 模型
支持 OCR、表格、公式、图表、印章识别
"""

import base64
import logging
import time
from typing import Optional, List
from dataclasses import dataclass
from PIL import Image
import torch

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@dataclass
class TextBlock:
    text: str
    confidence: float = 0.0
    bounding_box: dict = None


@dataclass
class OcrResult:
    success: bool
    text_blocks: List[TextBlock]
    full_text: str
    error_message: Optional[str] = None


class PaddleOcrVlService:
    """
    PaddleOCR-VL-1.5 服务

    使用 transformers 加载模型，支持:
    - ocr: 文字识别
    - table: 表格识别
    - formula: 公式识别
    - chart: 图表识别
    - spotting: 文本定位
    - seal: 印章识别
    """

    def __init__(
        self,
        model_path: str = "PaddlePaddle/PaddleOCR-VL-1.5",
        task: str = "ocr",
        use_gpu: bool = True,
        device: str = None
    ):
        """
        初始化 PaddleOCR-VL-1.5 服务

        Args:
            model_path: 模型路径或 HuggingFace 模型名
            task: 任务类型: ocr, table, formula, chart, spotting, seal
            use_gpu: 是否使用 GPU
            device: 指定设备，如 "cuda:0", "cpu"
        """
        self.model_path = model_path
        self.task = task
        self.use_gpu = use_gpu
        self.device = device or ("cuda" if torch.cuda.is_available() and use_gpu else "cpu")
        self._model = None
        self._processor = None
        self._prompts = {
            "ocr": "OCR:",
            "table": "Table Recognition:",
            "formula": "Formula Recognition:",
            "chart": "Chart Recognition:",
            "spotting": "Spotting:",
            "seal": "Seal Recognition:",
        }

    def initialize(self):
        """初始化模型和处理器"""
        if self._model is not None:
            return

        logger.info(f"初始化 PaddleOCR-VL-1.5, model_path={self.model_path}, device={self.device}")
        start_time = time.time()

        try:
            import sys
            from pathlib import Path

            model_dir = Path(self.model_path).resolve()
            transformers_modules = model_dir / "transformers_modules" / "model"

            # 添加 transformers_modules 到路径
            if str(transformers_modules) not in sys.path:
                sys.path.insert(0, str(transformers_modules))

            from transformers import AutoProcessor
            from modeling_paddleocr_vl import PaddleOCRVLForConditionalGeneration
            from configuration_paddleocr_vl import PaddleOCRVLConfig

            # 加载配置
            config = PaddleOCRVLConfig.from_pretrained(self.model_path)

            # 加载模型
            self._model = PaddleOCRVLForConditionalGeneration.from_pretrained(
                self.model_path,
                config=config,
                torch_dtype=torch.bfloat16,
            ).to(self.device).eval()

            # 加载处理器
            self._processor = AutoProcessor.from_pretrained(
                self.model_path,
                trust_remote_code=True
            )

            elapsed = time.time() - start_time
            logger.info(f"PaddleOCR-VL-1.5 初始化完成，耗时 {elapsed:.2f}秒")
        except Exception as e:
            logger.error(f"PaddleOCR-VL-1.5 初始化失败: {e}")
            raise

    def _preprocess_image(self, image: Image.Image, task: str) -> Image.Image:
        """图像预处理"""
        orig_w, orig_h = image.size

        # spotting 任务需要放大图像
        if task == "spotting":
            spotting_upscale_threshold = 1500
            if orig_w < spotting_upscale_threshold and orig_h < spotting_upscale_threshold:
                process_w, process_h = orig_w * 2, orig_h * 2
                try:
                    resample_filter = Image.Resampling.LANCZOS
                except AttributeError:
                    resample_filter = Image.Image.LANCZOS
                image = image.resize((process_w, process_h), resample_filter)

        return image

    def recognize(self, image_bytes: bytes) -> OcrResult:
        """
        识别图片中的文字

        Args:
            image_bytes: 图片字节数据

        Returns:
            OcrResult: 识别结果
        """
        if self._model is None:
            self.initialize()

        try:
            from PIL import Image
            import io

            # 解码图片
            image = Image.open(io.BytesIO(image_bytes)).convert("RGB")

            # 预处理
            image = self._preprocess_image(image, self.task)

            # 设置 max_pixels
            max_pixels = 2048 * 28 * 28 if self.task == "spotting" else 1280 * 28 * 28

            # 构建消息
            messages = [
                {
                    "role": "user",
                    "content": [
                        {"type": "image", "image": image},
                        {"type": "text", "text": self._prompts.get(self.task, "OCR:")},
                    ]
                }
            ]

            # 处理输入
            inputs = self._processor.apply_chat_template(
                messages,
                add_generation_prompt=True,
                tokenize=True,
                return_dict=True,
                return_tensors="pt",
                images_kwargs={"size": {"shortest_edge": self._processor.image_processor.min_pixels, "longest_edge": max_pixels}},
            ).to(self._model.device)

            # 推理
            outputs = self._model.generate(**inputs, max_new_tokens=1024)

            # 解码
            result = self._processor.decode(outputs[0][inputs["input_ids"].shape[-1]:-1])

            logger.info(f"PaddleOCR-VL-1.5 识别完成")

            # 解析结果
            text_blocks = [TextBlock(text=result.strip(), confidence=1.0)]
            full_text = result.strip()

            return OcrResult(
                success=True,
                text_blocks=text_blocks,
                full_text=full_text
            )

        except Exception as e:
            logger.error(f"PaddleOCR-VL-1.5 识别异常: {e}")
            return OcrResult(
                success=False,
                text_blocks=[],
                full_text="",
                error_message=str(e)
            )

    def recognize_from_base64(self, base64_str: str) -> OcrResult:
        """
        从 Base64 字符串识别图片

        Args:
            base64_str: Base64 编码的图片字符串

        Returns:
            OcrResult: 识别结果
        """
        try:
            # 处理 data URI 格式
            if ',' in base64_str:
                base64_str = base64_str.split(',')[1]

            image_bytes = base64.b64decode(base64_str)
            return self.recognize(image_bytes)
        except Exception as e:
            logger.error(f"Base64 解码异常: {e}")
            return OcrResult(
                success=False,
                text_blocks=[],
                full_text="",
                error_message=f"Base64 解码失败: {e}"
            )


# 全局服务实例
_service: Optional[PaddleOcrVlService] = None


def get_service() -> PaddleOcrVlService:
    """获取全局服务实例"""
    global _service
    if _service is None:
        raise RuntimeError("Service not initialized. Call initialize_service() first.")
    return _service


def initialize_service(
    model_path: str = "PaddlePaddle/PaddleOCR-VL-1.5",
    task: str = "ocr",
    use_gpu: bool = True,
    device: str = None
) -> PaddleOcrVlService:
    """初始化服务

    Args:
        model_path: 模型路径或 HuggingFace 模型名
        task: 任务类型: ocr, table, formula, chart, spotting, seal
        use_gpu: 是否使用 GPU
        device: 指定设备
    """
    global _service
    _service = PaddleOcrVlService(
        model_path=model_path,
        task=task,
        use_gpu=use_gpu,
        device=device
    )
    _service.initialize()
    return _service
