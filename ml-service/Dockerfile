FROM python:3.12-slim

# OpenMP for numpy/scikit-learn/lightgbm wheels on slim
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .

# CPU-only PyTorch avoids multi‑GB CUDA stacks in dev containers (aarch64/amd64).
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir "torch>=2.2.0" --index-url https://download.pytorch.org/whl/cpu \
    && grep -v '^torch' requirements.txt > /tmp/requirements-no-torch.txt \
    && pip install --no-cache-dir -r /tmp/requirements-no-torch.txt

COPY . .

EXPOSE 5001

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "5001"]
