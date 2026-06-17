"""
LLM Fine-tuning with LoRA (Low-Rank Adaptation)
Supports: GPT-2, LLaMA, Mistral, Falcon, and any HuggingFace causal LM
"""

import json
import argparse
from pathlib import Path

import torch
from datasets import Dataset
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    TrainingArguments,
    BitsAndBytesConfig,
)
from peft import LoraConfig, get_peft_model, TaskType
from trl import SFTTrainer


# ── Prompt formatting ────────────────────────────────────────────────────────

def format_prompt(example: dict) -> str:
    if example.get("input"):
        return (
            f"### Instruction:\n{example['instruction']}\n\n"
            f"### Input:\n{example['input']}\n\n"
            f"### Response:\n{example['output']}"
        )
    return (
        f"### Instruction:\n{example['instruction']}\n\n"
        f"### Response:\n{example['output']}"
    )


# ── Dataset loading ──────────────────────────────────────────────────────────

def load_dataset_from_json(path: str) -> Dataset:
    with open(path) as f:
        data = json.load(f)
    records = [{"text": format_prompt(ex)} for ex in data]
    return Dataset.from_list(records)


# ── Model + tokenizer ────────────────────────────────────────────────────────

def load_model_and_tokenizer(model_name: str, load_in_4bit: bool = False):
    bnb_config = None
    if load_in_4bit:
        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_use_double_quant=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.bfloat16,
        )

    model = AutoModelForCausalLM.from_pretrained(
        model_name,
        quantization_config=bnb_config,
        device_map="auto",
        trust_remote_code=True,
    )
    model.config.use_cache = False

    tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    tokenizer.padding_side = "right"

    return model, tokenizer


# ── LoRA configuration ───────────────────────────────────────────────────────

def apply_lora(model, r: int = 16, alpha: int = 32, dropout: float = 0.05):
    lora_config = LoraConfig(
        task_type=TaskType.CAUSAL_LM,
        r=r,
        lora_alpha=alpha,
        lora_dropout=dropout,
        target_modules=["q_proj", "v_proj"],  # adjust per architecture
        bias="none",
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()
    return model


# ── Training ─────────────────────────────────────────────────────────────────

def train(args):
    print(f"Loading dataset from: {args.data}")
    dataset = load_dataset_from_json(args.data)
    print(f"  {len(dataset)} examples loaded")

    print(f"Loading model: {args.model}")
    model, tokenizer = load_model_and_tokenizer(args.model, args.load_in_4bit)

    model = apply_lora(model, r=args.lora_r, alpha=args.lora_alpha)

    training_args = TrainingArguments(
        output_dir=args.output_dir,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.lr,
        fp16=not args.bf16,
        bf16=args.bf16,
        logging_steps=10,
        save_steps=100,
        save_total_limit=2,
        warmup_ratio=0.03,
        lr_scheduler_type="cosine",
        report_to="none",
    )

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=dataset,
        dataset_text_field="text",
        max_seq_length=args.max_seq_len,
        args=training_args,
    )

    print("Starting training...")
    trainer.train()

    print(f"Saving model to: {args.output_dir}")
    trainer.save_model(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    print("Done!")


# ── Inference ────────────────────────────────────────────────────────────────

def generate(args):
    from peft import PeftModel

    print(f"Loading base model: {args.model}")
    base_model, tokenizer = load_model_and_tokenizer(args.model, args.load_in_4bit)

    print(f"Loading LoRA adapter from: {args.adapter}")
    model = PeftModel.from_pretrained(base_model, args.adapter)
    model.eval()

    prompt = (
        f"### Instruction:\n{args.prompt}\n\n### Response:\n"
        if not args.input
        else f"### Instruction:\n{args.prompt}\n\n### Input:\n{args.input}\n\n### Response:\n"
    )

    inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
    with torch.no_grad():
        outputs = model.generate(
            **inputs,
            max_new_tokens=args.max_new_tokens,
            temperature=args.temperature,
            do_sample=args.temperature > 0,
            top_p=0.9,
            repetition_penalty=1.1,
        )

    response = tokenizer.decode(outputs[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True)
    print("\n--- Response ---")
    print(response)


# ── CLI ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Fine-tune an LLM with LoRA")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # train
    train_p = subparsers.add_parser("train", help="Fine-tune a model")
    train_p.add_argument("--model", default="gpt2", help="HuggingFace model ID or local path")
    train_p.add_argument("--data", default="data/sample_dataset.json")
    train_p.add_argument("--output-dir", default="output/lora-model")
    train_p.add_argument("--epochs", type=int, default=3)
    train_p.add_argument("--batch-size", type=int, default=4)
    train_p.add_argument("--grad-accum", type=int, default=4)
    train_p.add_argument("--lr", type=float, default=2e-4)
    train_p.add_argument("--lora-r", type=int, default=16)
    train_p.add_argument("--lora-alpha", type=int, default=32)
    train_p.add_argument("--max-seq-len", type=int, default=512)
    train_p.add_argument("--load-in-4bit", action="store_true")
    train_p.add_argument("--bf16", action="store_true")

    # generate
    gen_p = subparsers.add_parser("generate", help="Run inference with a fine-tuned model")
    gen_p.add_argument("--model", default="gpt2")
    gen_p.add_argument("--adapter", required=True, help="Path to LoRA adapter")
    gen_p.add_argument("--prompt", required=True)
    gen_p.add_argument("--input", default="")
    gen_p.add_argument("--max-new-tokens", type=int, default=200)
    gen_p.add_argument("--temperature", type=float, default=0.7)
    gen_p.add_argument("--load-in-4bit", action="store_true")

    args = parser.parse_args()

    if args.command == "train":
        train(args)
    elif args.command == "generate":
        generate(args)


if __name__ == "__main__":
    main()
