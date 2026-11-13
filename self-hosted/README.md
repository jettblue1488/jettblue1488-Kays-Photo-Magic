# kayla.AI.7717 — self-hosted engine (experimental)

This is a real, separately runnable image-generation backend, not a new foundation model trained from scratch. It uses the open-weight SD-Turbo model, subject to its license and acceptable-use terms. Check those terms before using or distributing.

## Setup on your own computer

A computer with a compatible NVIDIA GPU is recommended. CPU inference may be extremely slow. Python 3.10+ required.

```bash
cd self-hosted
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export KAYLA_TOKEN="a-long-random-secret"
uvicorn server:app --host 127.0.0.1 --port 7717
```

The model downloads on first run, then can be cached for offline use. Test:

```bash
curl -X POST http://127.0.0.1:7717/generate \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"a sparkling purple flower"}' --output image.png
```

Do not expose port 7717 publicly or put tokens in GitHub. To access it from your Android phone, use a trusted private VPN or authenticated HTTPS reverse proxy. The Android app is **not yet wired to this endpoint**. This backend does **not** run in GitHub Actions, and it has **not** been performance-tested on the SN515AR 4 GB phone.

Offline-on-phone inference, image-to-image editing, secure pairing, content safety, and fallback remain separate development tasks. The code requires a model download and hardware that can run it.
