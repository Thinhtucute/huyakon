# Huyakon FastAPI backend

This service keeps the Hugging Face token on the server. The Android app calls
this API instead of sending the token or calling the Spaces directly.

## Local development

```powershell
cd backend
py -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
# Edit .env and set HF_TOKEN.
uvicorn app.main:app --reload
```

Check the service:

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

## Endpoints

### `POST /v1/analyze`

```json
{
  "image_base64": "<base64 jpeg>",
  "lang": "ja",
  "proximity_speakers": true
}
```

The response is the analyzer Space result.

### `POST /v1/translate`

```json
{
  "script": [
    {"text_id": 1, "text": "こんにちは", "role": "dialogue"}
  ],
  "glossary": ""
}
```

The response is the translation Space result.

The backend performs the Gradio two-step request internally and returns HTTP
502 when the Space is unavailable, rejects the request, or closes without a
complete result.

## Render deployment

Create a Render Web Service from the repository and use:

- Root directory: `backend`
- Build command: `pip install -r requirements.txt`
- Start command: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`

Set `HF_TOKEN` as a Render secret environment variable. Use a dedicated
read/inference token with only the permissions required by the Spaces. Never
put the token in the Android project, source control, or client requests.

`render.yaml` is included for Blueprint deployment. After deployment, update
the Android client's Space base URLs to the Render URL and adapt its request
payloads to `/v1/analyze` and `/v1/translate`.
