FROM node:20-alpine AS ui-build
WORKDIR /ui
COPY ui/package.json ui/package-lock.json* ./
RUN npm install
COPY ui/ ./
RUN npm run build

FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# ✅ fix SSL + cert issues
RUN apt-get update && apt-get install -y ca-certificates && update-ca-certificates

COPY requirements.txt .

# ✅ robust install
RUN pip install --upgrade pip \
    && pip install --no-cache-dir \
       --trusted-host pypi.org \
       --trusted-host files.pythonhosted.org \
       -r requirements.txt

COPY app ./app
COPY --from=ui-build /ui/dist ./static

RUN useradd --create-home appuser && chown -R appuser:appuser /app
USER appuser

EXPOSE 8092

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8092"]
