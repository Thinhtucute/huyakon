import json
from typing import Any

import httpx


class GradioError(Exception):
    """Raised when a Gradio Space rejects a request or returns an invalid result."""

class GradioClient:
    def __init__(self, client: httpx.AsyncClient, token: str, timeout: float) -> None:
        self._client = client
        self._headers = {"Authorization": f"Bearer {token}"}
        self._timeout = timeout

    async def call(self, call_url: str, data: list[Any]) -> Any:
        event_id = await self._start(call_url, data)
        return await self._read_result(call_url, event_id)

    async def _start(self, call_url: str, data: list[Any]) -> str:
        try:
            response = await self._client.post(
                call_url,
                json={"data": data},
                headers=self._headers,
                timeout=self._timeout,
            )
        except httpx.HTTPError as error:
            raise GradioError(f"Could not reach the Hugging Face Space: {error}") from error

        payload = self._json(response, "starting the Space request")
        event_id = payload.get("event_id")
        if not isinstance(event_id, str) or not event_id:
            raise GradioError("The Hugging Face Space did not return an event_id")
        return event_id

    async def _read_result(self, call_url: str, event_id: str) -> Any:
        try:
            async with self._client.stream(
                "GET",
                f"{call_url}/{event_id}",
                headers=self._headers,
                timeout=self._timeout,
            ) as response:
                if response.is_error:
                    body = (await response.aread()).decode(errors="replace")
                    raise GradioError(
                        f"The Hugging Face Space returned HTTP {response.status_code}: "
                        f"{body[:500]}"
                    )
                return await self._read_sse(response)
        except httpx.HTTPError as error:
            raise GradioError(f"The Hugging Face Space stream failed: {error}") from error

    async def _read_sse(self, response: httpx.Response) -> Any:
        event: str | None = None
        async for line in response.aiter_lines():
            if line.startswith("event:"):
                event = line.partition(":")[2].strip()
                continue
            if not line.startswith("data:"):
                continue

            raw_data = line.partition(":")[2].strip()
            if event == "error":
                raise GradioError(f"The Hugging Face Space reported an error: {raw_data[:500]}")
            if event == "complete":
                try:
                    payload = json.loads(raw_data)
                except json.JSONDecodeError as error:
                    raise GradioError("The Space returned invalid completion JSON") from error
                return _unwrap_gradio_result(payload)

        raise GradioError("The Hugging Face Space closed without a complete result")

    @staticmethod
    def _json(response: httpx.Response, operation: str) -> dict[str, Any]:
        try:
            response.raise_for_status()
            payload = response.json()
        except httpx.HTTPStatusError as error:
            body = response.text[:500]
            raise GradioError(
                f"The Hugging Face Space returned HTTP {response.status_code} while {operation}: "
                f"{body}"
            ) from error
        except (httpx.HTTPError, json.JSONDecodeError) as error:
            raise GradioError(f"Invalid response from the Space while {operation}") from error

        if not isinstance(payload, dict):
            raise GradioError(f"Invalid response from the Space while {operation}")
        return payload


def _unwrap_gradio_result(payload: Any) -> Any:
    if isinstance(payload, list):
        if not payload:
            raise GradioError("The Hugging Face Space returned an empty result")
        return payload[0]
    if isinstance(payload, (dict, str)):
        if isinstance(payload, str):
            try:
                return json.loads(payload)
            except json.JSONDecodeError as error:
                raise GradioError("The Space returned invalid result JSON") from error
        return payload
    raise GradioError("The Hugging Face Space returned an unsupported result shape")
