FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . /app/scenarioforge
WORKDIR /app
ENV PYTHONPATH=/app
ENTRYPOINT ["python", "-m", "scenarioforge.main"]
CMD ["--template", "mixed_demo"]
