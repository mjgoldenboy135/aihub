# LLM Fine-tuning with LoRA

Fine-tune any HuggingFace causal language model on your own data using **LoRA** (Low-Rank Adaptation). Works with GPT-2, LLaMA, Mistral, Falcon, and more.

## Open in Colab

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/mjgoldenboy135/aihub/blob/main/finetune_colab.ipynb)

## Project Structure

```
aihub/
├── finetune.py              # CLI fine-tuning script
├── finetune_colab.ipynb     # Colab-ready notebook
├── requirements.txt         # Python dependencies
└── data/
    └── sample_dataset.json  # Example training data
```

## Quick Start (Local)

```bash
pip install -r requirements.txt

# Fine-tune GPT-2 on the sample dataset
python finetune.py train --model gpt2 --data data/sample_dataset.json

# For larger models (7B+) use 4-bit quantization
python finetune.py train --model mistralai/Mistral-7B-v0.1 --load-in-4bit

# Run inference
python finetune.py generate --model gpt2 --adapter output/lora-model --prompt "What is deep learning?"
```

## Dataset Format

Create a JSON file with a list of instruction examples:

```json
[
  {
    "instruction": "Your task description here",
    "input": "Optional context or input text",
    "output": "Expected response"
  }
]
```

Leave `"input"` as `""` if no extra context is needed.

## Training Options

| Flag | Default | Description |
|------|---------|-------------|
| `--model` | `gpt2` | HuggingFace model ID |
| `--epochs` | `3` | Training epochs |
| `--batch-size` | `4` | Per-device batch size |
| `--lr` | `2e-4` | Learning rate |
| `--lora-r` | `16` | LoRA rank |
| `--lora-alpha` | `32` | LoRA alpha |
| `--load-in-4bit` | off | 4-bit QLoRA (requires GPU) |
| `--max-seq-len` | `512` | Max token length |

## Recommended Models

| Model | Size | Notes |
|-------|------|-------|
| `gpt2` | 124M | CPU-friendly, great for testing |
| `distilgpt2` | 82M | Smaller, faster GPT-2 |
| `facebook/opt-1.3b` | 1.3B | Good quality, fits on Colab T4 |
| `mistralai/Mistral-7B-v0.1` | 7B | High quality, use `--load-in-4bit` |
| `meta-llama/Llama-2-7b-hf` | 7B | Requires HuggingFace access token |
