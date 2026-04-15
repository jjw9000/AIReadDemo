"""
Convert CLIP ViT-B/32 to ONNX format for use with BookManagementService.

Usage:
    python convert_clip_to_onnx.py

Requirements:
    pip install transformers torch onnx
"""

from transformers import CLIPProcessor, CLIPModel
import torch
import os

def convert_clip_to_onnx():
    model_name = "openai/clip-vit-base-patch32"
    output_path = "models/clip-vit-base-patch32.onnx"

    print(f"Loading CLIP model: {model_name}")
    model = CLIPModel.from_pretrained(model_name)
    processor = CLIPProcessor.from_pretrained(model_name)

    # Export image encoder to ONNX
    print("Exporting image encoder to ONNX...")

    # Create dummy input
    dummy_input = torch.randn(1, 3, 224, 224)

    # Get image encoder
    image_encoder = model.get_image_features

    # Export
    torch.onnx.export(
        image_encoder,
        (dummy_input,),
        output_path,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={
            "input": {0: "batch"},
            "output": {0: "batch"}
        },
        opset_version=14
    )

    print(f"ONNX model exported successfully to: {output_path}")
    print(f"Model size: {os.path.getsize(output_path) / (1024*1024):.2f} MB")

if __name__ == "__main__":
    convert_clip_to_onnx()
