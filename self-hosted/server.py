"""Kayla self-hosted image generator. Run on a computer you control, not on GitHub Actions."""
import io
import os
import secrets
from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field

app = FastAPI(title="kayla.AI.7717 local image server")
pipe = None
TOKEN = os.environ.get("KAYLA_TOKEN")
MODEL = os.environ.get("KAYLA_MODEL", "stabilityai/sd-turbo")

class Generate(BaseModel):
    prompt: str = Field(min_length=1, max_length=2000)
    width: int = Field(default=512, ge=256, le=768)
    height: int = Field(default=512, ge=256, le=768)

@app.get("/health")
def health():
    return {"name": "kayla.AI.7717", "ready": pipe is not None}

@app.on_event("startup")
def startup():
    global pipe
    if not TOKEN:
        raise RuntimeError("Set KAYLA_TOKEN before starting server")
    import torch
    from diffusers import AutoPipelineForText2Image
    device = "cuda" if torch.cuda.is_available() else "cpu"
    dtype = torch.float16 if device == "cuda" else torch.float32
    pipe = AutoPipelineForText2Image.from_pretrained(MODEL, torch_dtype=dtype)
    pipe = pipe.to(device)
    pipe.set_progress_bar_config(disable=True)

@app.post("/generate")
def generate(req: Generate, authorization: str | None = Header(default=None)):
    if not TOKEN or not secrets.compare_digest(authorization or "", "Bearer " + TOKEN):
        raise HTTPException(401, "Unauthorized")
    if pipe is None:
        raise HTTPException(503, "Model not ready")
    image = pipe(prompt=req.prompt, width=req.width, height=req.height,
                 num_inference_steps=2, guidance_scale=0.0).images[0]
    output = io.BytesIO()
    image.save(output, format="PNG")
    return Response(output.getvalue(), media_type="image/png")
