FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY pyproject.toml /app/
COPY overlay_license /app/overlay_license
RUN pip install --no-cache-dir . && useradd -u 10001 -m license
USER 10001:10001
EXPOSE 8080
CMD ["uvicorn", "overlay_license.api:get_app", "--factory", "--host", "0.0.0.0", "--port", "8080", "--no-access-log"]
