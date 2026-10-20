from typing import Any

from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    image_base64: str = Field(min_length=1)
    lang: str = Field(default="ja", pattern="^(ja|en)$")
    proximity_speakers: bool = True


class TranslateRequest(BaseModel):
    script: list[dict[str, Any]] = Field(min_length=1)
    glossary: str = ""
