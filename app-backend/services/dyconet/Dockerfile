FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# The HTTP entry point imports the complete HOTCO-CT/context/XAI service.
# Keep this image independent from the developer checkout's virtualenv and
# analysis artefacts (excluded by .dockerignore), while shipping every runtime
# module required by the Flask application.
COPY *.py ./
COPY artifacts ./artifacts

EXPOSE 5000
CMD ["gunicorn", "-b", "0.0.0.0:5000", "--workers", "1", "--threads", "1", "--timeout", "120", "main:app"]
