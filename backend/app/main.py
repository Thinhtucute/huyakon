from contextlib import asynccontextmanager
import json
from typing import Any

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware

from .config import get_settings
from .gradio import GradioClient, GradioError
from .schemas import AnalyzeRequest, TranslateRequest


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    async with httpx.AsyncClient() as client:
        app.state.gradio = GradioClient(
            client=client,
            token=settings.hf_token,
            timeout=settings.request_timeout_seconds,
        )
        yield


settings = get_settings()
app = FastAPI(title="Huyakon Backend", version="1.0.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_credentials=False,
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/v1/analyze")
async def analyze(payload: AnalyzeRequest, request: Request) -> Any:
    try:
        return await request.app.state.gradio.call(
            settings.analyzer_call_url,
            [payload.image_base64, payload.lang, payload.proximity_speakers],
        )
    except GradioError as error:
        raise HTTPException(status_code=502, detail=str(error)) from error


@app.post("/v1/translate")
async def translate(payload: TranslateRequest, request: Request) -> Any:
    script_json = json.dumps(payload.script, ensure_ascii=False)
    try:
        return await request.app.state.gradio.call(
            settings.translator_call_url,
            [script_json, payload.glossary],
        )
    except GradioError as error:
        raise HTTPException(status_code=502, detail=str(error)) from error
