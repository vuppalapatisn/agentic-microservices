# ✅ Stage 1: Build UI
FROM node:20-alpine AS ui-build

WORKDIR /ui

COPY ui/package.json ui/package-lock.json* ./
RUN npm install

COPY ui/ ./
RUN npm run build


# ✅ Stage 2: Backend
FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# ✅ ✅ FIX: Install SSL certificates (CRITICAL for OpenAI)
RUN apt-get update && \
    apt-get install -y ca-certificates && \
    update-ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# ✅ Copy requirements
COPY requirements.txt .

# ✅ ✅ FIX: Proper pip install (was broken before)
RUN pip install --upgrade pip && \
    pip install --no-cache-dir \
    --trusted-host pypi.org \
    --trusted-host files.pythonhosted.org \
    -r requirements.txt

# ✅ Copy application code
COPY app ./app

# ✅ Copy built UI
COPY --from=ui-build /ui/dist ./static

# ✅ Create non-root user
RUN useradd --create-home appuser && chown -R appuser:appuser /app
USER appuser

# ✅ Expose port
EXPOSE 8092

# ✅ Start FastAPI app
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8092"]
