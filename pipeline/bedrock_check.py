"""Quick check that this account can call an LLM on Amazon Bedrock.

Usage:
    python bedrock_check.py                                   # default model
    python bedrock_check.py anthropic.claude-sonnet-4-6       # try another model
"""
import os
import sys
import time

import boto3

from storage import bucket_name  # noqa: F401  (loads .env so AWS_REGION is set)

# Chosen 17 Oct 2026: works on this account via the EU cross-region inference profile.
DEFAULT_MODEL = "eu.amazon.nova-2-lite-v1:0"

model_id = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_MODEL
bedrock = boto3.client("bedrock-runtime", region_name=os.environ.get("AWS_REGION", "eu-north-1"))

# The Converse API is Bedrock's one standard way to talk to any chat model.
start = time.time()
response = bedrock.converse(
    modelId=model_id,
    messages=[{"role": "user", "content": [{"text":
        "In one sentence, what does 'portfolio drift' mean for a wealth manager?"}]}],
    inferenceConfig={"maxTokens": 100, "temperature": 0},
)
elapsed_ms = int((time.time() - start) * 1000)

usage = response["usage"]
print(f"Model:   {model_id}")
print(f"Answer:  {response['output']['message']['content'][0]['text']}")
print(f"Tokens:  {usage['inputTokens']} in, {usage['outputTokens']} out")
print(f"Time:    {elapsed_ms} ms")
