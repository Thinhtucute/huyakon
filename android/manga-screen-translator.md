# Huyakon - Manga Screen Translator

Personal-use Android app (Kotlin) that translates Japanese manga **on whatever is currently on screen**. Tap a floating button (FAB) over any manga reader → the screen is captured → a Hugging Face Space finds the text and reads it → a local LLM (Qwen) translates it to English → the translation is drawn over the speech bubbles.

- **Scope:** Personal use only. No ads, no commercial release (the detection models are non-commercial licensed), no additional token or API costs.
- **Language pair:** Japanese → English only.
- **Target device / Minimum specs:** Xiaomi 13T (Dimensity 8200 Ultra, Mali-G610 MC6, 8 or 12 GB RAM).
- **Also a portfolio piece** for an Android developer application (see [Job-posting mapping](#6-requirements-summary)).

## Status (2026-10-15)

| Piece | State |
|---|---|
| **Analyzer Space** | **Working and wired**. Takes a base64 JPEG through `analyze_b64` and returns ordered OCR script lines plus geometry. |
| **Translation Space** | **Working and wired**. Takes the analyzer script through `translate_script` and returns translated flat lines. |
| **Android app** | **Milestone 2 implemented**: capture → analyzer Space → translator Space → translated state. The UI preview and the external translation overlay render translated lines. |

### Hugging Face Spaces

- [Magi v3 analyzer Space](https://thinhtucute-huyakon-magiv3.hf.space) — image analysis, OCR, reading order, boxes, and speaker metadata.
- [JP → EN translation Space](https://thinhtucute-huyakon-translation.hf.space) — `translate_script` translation endpoint.

The Android app calls the two Spaces directly for now. A FastAPI backend may be added later to centralize orchestration, authentication, retries, caching, and rate limiting; the domain interfaces should remain unchanged when that boundary is introduced.

### Current repo reality check

The codebase now contains the first end-to-end pipeline slice:

- `app/src/main/java/com/example/huyakon/service/OverlayService.kt` owns the foreground service lifecycle and the capture session.
- `app/src/main/java/com/example/huyakon/platform/capture/ScreenCaptureController.kt` captures a JPEG frame via `MediaProjection`.
- `app/src/main/java/com/example/huyakon/platform/overlay/OverlayFabController.kt` shows a floating `訳` button.
- `app/src/main/java/com/example/huyakon/presentation/main/*` renders the app and the preview state.
- `app/src/main/java/com/example/huyakon/data/remote/SpaceAPI.kt` calls the analyzer Space.
- `app/src/main/java/com/example/huyakon/data/remote/TranslationApi.kt` calls the translation Space.
- `app/src/main/java/com/example/huyakon/data/translator/SpaceTranslator.kt` maps translated results back to local text boxes by `text_id`.

The direct two-Space flow now includes the external overlay rendering. `TranslationOverlayController` uses analyzer `bubbles` boxes, rather than OCR `texts` boxes, as the translation borders and applies a small inset before drawing. It uses opaque white fills to cover the Japanese source text, requests Comic Sans for English text, keeps the FAB above the translation layer, and toggles each translation panel independently on touch. Tapping an English panel temporarily reveals the original Japanese; tapping the same panel again restores the cached English result without another Space request. After a completed translation, tapping the `完了` FAB removes the translation overlay and returns touch input to the underlying app. The movable 訳 FAB is also the external status indicator: it shows capture (`撮影中`), analysis (`解析中`), translation (`翻訳中`), completion (`完了`), and an error label that includes a shortened cause (`エラー: ...`). Hold and drag the FAB to reposition it; a normal tap starts a translation. The app and foreground notification show the same capture/upload, analyzer, translation, and completion stages and retain the full error cause. Remaining work is production hardening and real-device validation. The domain boundary remains `ScreenCapturer`, `Translator`, and `AnalysisRepository`, so a future FastAPI backend can replace the direct Space clients without changing the use case.

---

## 1. How it works

```
 [Manga reader app on screen]
            |
            |  tap FAB (overlay)
            v
 [Foreground service] -- hide overlay -- capture frame (MediaProjection)
            |
            |  JPEG -> base64
            v
 [Hugging Face analyzer Space, ZeroGPU]   endpoint: analyze_b64
   1. Magi v3            -> panels, characters (+ clusters), speech-bubble tails
   2. ogkalu ONNX det.   -> text boxes (bubble_text / free_text), bubble outlines
   3. manga-ocr          -> Japanese text per text box
   4. rules              -> roles (dialogue / free_text / watermark), reading order, speakers
            |
            |  JSON: `script` = ordered lines with role / speaker / confidence
            v
 [Hugging Face translation Space, ZeroGPU] endpoint: translate_script
            |
            |  flat translations matched by text_id
            v
            |
            v
 [Overlay] draw English over each text box (scaled to screen), tap to dismiss
```

1. **FAB tap.** The overlay hides itself first, so the screenshot does not contain the FAB or older translations.
2. **Capture.** One `MediaProjection` + `VirtualDisplay` + `ImageReader` is kept alive inside a foreground service for the session; frames are grabbed on demand.
3. **Detect + read (Space).** See section 3.
4. **Translate.** The ordered Japanese lines go to the translation Space (section 4).
5. **Overlay.** Boxes are scaled by `screenSize / result.image` and the English text is drawn over each box.

---

## 2. Models

| Stage | Model | Runs on |
|---|---|---|
| Panels, characters (+ cluster labels), speech-bubble **tails** | **Magi v3** (`ragavsachdeva/magiv3`, Florence-2-based, ~0.8B) | HF Space (ZeroGPU) |
| Text boxes, bubble outlines, `kind` (bubble text vs free text) | **ogkalu/comic-text-and-bubble-detector** (RT-DETR, ONNX, CPU). Check its license. | HF Space |
| Japanese text recognition | **manga-ocr** | HF Space (CPU at startup, GPU inside the request) |
| Translation | **Hy-MT2 1.8B JP Manga fine-tune** (`fumetodev/Hy-MT2-1.8B-JP-Manga-Finetune-v5-GGUF`) | Hugging Face translation Space (ZeroGPU) |

---

## 3. Analyzer backend: Hugging Face Space (`magiv3-hybrid/app.py`)

- **Space:** Gradio SDK, **ZeroGPU**, public. Free accounts can only create Gradio ZeroGPU Spaces (no CPU basic, no Docker), max 2.
- **Setup notes:**
  - Don't keep a new Space's default README pins (Gradio 6 / Python 3.12): Gradio 6 needs a newer `huggingface_hub` than Magi's older `transformers` allows. Gradio 5.49.1 + Python 3.10 built cleanly.
  - ZeroGPU only accepts torch ≥ 2.8.0 (torch 2.8.0 was used).
  - The working run used `transformers` 4.49.0 and float32 (`MAGI_DTYPE=fp16` switches to half precision).
  - Besides the Magi and manga-ocr dependencies, add `onnxruntime` (the text detector runs on CPU).
- **How it's put together:**
  - Text boxes and bubble outlines come from the ONNX detector (classes: 0 bubble, 1 text in bubble, 2 free text). On the test page: 9 text boxes, none duplicated, plus 7 bubble outlines.
  - `manga-ocr` runs a warm-up inference in its constructor, which crashes on ZeroGPU's emulated CUDA at startup. Fix: `MangaOcr(force_cpu=True)`, then move it to CUDA inside the `@spaces.GPU` function and back to CPU in `finally`.
- **Quota:** ZeroGPU gives a limited daily GPU allowance; anonymous callers get less than authenticated ones. Cold starts after idle are slow, so use long client timeouts.

### Detection rules

- **Roles:** `dialogue` (text inside a bubble), `free_text` (outside bubbles: SFX, narration), `watermark` (Latin-only text with 4+ letters, or a long thin strip hugging the top/bottom edge). `maybe_sfx` marks very short free text. Watermarks and empty texts are left out of `script`.
- **Reading order:** rows top-to-bottom, right-to-left inside a row, over **all** text boxes (panels are unreliable when one panel covers the page).
- **Speaker, layered per bubble** (texts in the same bubble share a speaker):
  1. **tail** (confidence `high`): the tail matched to the bubble; tip = tail-box corner farthest from the bubble; the character containing or nearest to the tip.
  2. **nearest** (`medium`): closest character to the bubble, within 25% of the short image side, when the runner-up is clearly farther.
  3. **nearest, ambiguous** (`low`): runner-up almost as close. Kept in `texts` with `speaker_candidates`, left out of `script`.
  - Non-dialogue texts get no speaker. Proximity can be turned off.
- **Test-page results:** 2 tails found (the page has 2 visible tails), both speakers correct. 5 speakers by proximity: 2 `medium`, 3 `low`. The crowd bubbles come out `low`, which is the honest answer.
- **Caveat:** proximity is only a hint. `川嶋！` is `medium` with the speaker set to the person being *called*, and overlapping boxes are ambiguous. Tails are the only strong signal.

### API contract (for the app)

Analyzer Space URL: `https://thinhtucute-huyakon-magiv3.hf.space`

Gradio REST, two steps. Endpoint name: `analyze_b64`.

```
POST https://<username>-<space-name>.hf.space/gradio_api/call/analyze_b64
Content-Type: application/json
{"data": ["<base64 jpeg>", "ja", true]}      # image, lang ("ja" | "en"), proximity speaker fallback
-> {"event_id": "abc123"}

GET  https://<username>-<space-name>.hf.space/gradio_api/call/analyze_b64/abc123
-> SSE stream; look for:  event: complete
                          data: [ { ...result... } ]
```

**Input format:** a base64-encoded **JPEG** string. The Space tolerates a `data:image/jpeg;base64,` prefix, line breaks and missing padding, and returns a Gradio error for an empty or undecodable string. (The browser upload on the Space page is UI-only; `analyze_b64` is the one API endpoint.)

Images over 2500 px on the long side are downscaled by the Space (a 13T-sized screen, about 1220×2712, is over that), so **always scale boxes with `result.image`**, not the original size.

**Encoding on the phone** (cuts payload and avoids the downscale):
```kotlin
fun Bitmap.toJpegBase64(maxSide: Int = 2500, quality: Int = 85): String {
    val scale = minOf(1f, maxSide.toFloat() / maxOf(width, height))
    val bmp = if (scale < 1f) Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true) else this
    val out = java.io.ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
}
```
Use JPEG (about 400-900 KB for a screenshot), never PNG, and `NO_WRAP` so there are no line breaks.

| Field | Meaning |
|---|---|
| `image` | `{width, height}` of the image the boxes refer to |
| **`script`** | **Use this for translation.** Flat list in reading order: `order`, `text_id`, `text`, `role`, `maybe_sfx`, `speaker`, `speaker_confidence`, `speaker_source` |
| `texts` | Everything per text: `id`, `bbox`, `text`, `kind`, `score`, `role`, `filtered_reason`, `maybe_sfx`, `essential`, `panel_id`, `bubble_id`, `tail_id`, `speaker_character_id`, `speaker_cluster_id`, `speaker_source`, `speaker_confidence`, `speaker_candidates` |
| `text_order` | Indices into `texts` in reading order |
| `panels`, `characters`, `tails`, `bubbles` | Boxes `[x1,y1,x2,y2]` in pixels; characters carry `cluster_id` |

In `script`, `speaker` is set only when confidence is `high` or `medium`, as `character_<cluster>`. Cluster ids are per page and **not stable across pages**.

---

## 4. Translation: Qwen as the local LLM

**Decision:** use a Qwen instruct model as the local translator. **Status:** not tested yet. Google AI Edge Gallery, the quickest way to try it on the phone, has a bug (reported on GitHub), so start on the PC and revisit the phone later.

### Plan
1. **Test quality on the PC first** with Ollama: `ollama run qwen2.5:1.5b` (the GTX 1650's 4 GB is enough for a model this size). Use the prompt below on the same sample lines.
2. **Phone runtime:** LiteRT-LM's Kotlin API (it lists Qwen among its supported models) or llama.cpp. Pick after the PC test.
3. **App integration:** behind one `Translator` interface in `domain`. Until Qwen runs, a `StubTranslator` keeps the pipeline testable.
4. **If Qwen 1.5B is too weak:** try a small Qwen3 (non-thinking mode), or a hosted/PC translator behind the same interface.

### Cautions for Qwen
- Small Qwen models sometimes drift into Chinese: put "English only" in the prompt and check outputs.
- Qwen3 thinks by default; use its non-thinking mode.
- Chunk to ~10-15 lines per call, temperature near 0.
- Plain `number|text` output (small models break JSON often).

### Building the prompt from `script`
| `script` field | Prompt tag |
|---|---|
| `speaker_confidence: "high"` | `[S<cluster>]` |
| `speaker_confidence: "medium"` | `[S<cluster>?]` (a guess) |
| no speaker (low confidence or none) | no tag |
| `maybe_sfx: true` | `[SFX?]` |

### Translation Space contract

Translation Space URL: `https://thinhtucute-huyakon-translation.hf.space`

The Android client sends the ordered `script` array to `translate_script` as the first Gradio input and an optional glossary as the second input. The endpoint uses the Gradio two-step event flow and returns a `flat` array. The client matches each returned translation to the analyzer line by `text_id`, preserving the analyzer's bounding box and role metadata locally.

```text
POST https://thinhtucute-huyakon-translation.hf.space/gradio_api/call/translate_script
Content-Type: application/json
{"data": ["<JSON-encoded script array>", "<optional glossary>"]}
-> {"event_id": "abc123"}

GET https://thinhtucute-huyakon-translation.hf.space/gradio_api/call/translate_script/abc123
-> SSE stream; read event: complete and its flat translations
```

The future FastAPI backend will sit between the Android app and both Spaces. It is intentionally not part of milestone 2.

### Prompt reference (used by the translation Space; sample uses the test page's 8 lines)

```
You are translating a Japanese manga page into natural English.
Lines are in reading order. OCR may have clipped some text.

Rules:
- Translate every line. Keep the same numbering and the same number of lines.
- Short, natural manga dialogue. No explanations.
- [S#] = speaker ID from detection (fairly reliable). [S#?] = a guess based on
  proximity and may be wrong; if the line itself conflicts with it, ignore it.
  Untagged lines have an unknown speaker (often a crowd member).
- [SFX?] = possibly a sound effect: render it as a short English sound effect in brackets.
- Keep names romanized. Keep nicknames as nicknames.
- Output English only. Output only: number|English

1|[S0?] 亜美ちゃん、ここのチョコ大好き〜！
2|[S0] モデルの間でも流行ってるんだ〜！
3|[S1] さすがばかちー！やればできんじゃん
4|亜美ちゃんが買ったチョコくださーい
5|私も
6|[SFX?] ざわ．．．
7|こっちもお願いします！
8|[S0?] 川嶋！
```

### What to check in the output
- 8 lines out, same order and numbering, **English only**.
- Line 6 comes back as a bracketed sound effect.
- Line 8 reads as an address ("Kawashima!"); the `[S0?]` tag is a trap: the model must not treat it as fact.
- `ばかちー` stays a nickname, names stay names.

---

## 5. Android app

### Architecture: clean architecture + MVVM
Dependencies point inward: `presentation` and `data` depend on `domain`; `domain` depends on nothing (pure Kotlin, no `android.*`).

```
com.example.mangatranslator
├─ domain/                 no android.* imports
│  ├─ model/               ScreenAnalysis, TextLine, TranslatedLine, Speaker
│  ├─ port/                AnalysisRepository, ScreenCapturer, Translator (interfaces)
│  ├─ usecase/             TranslateScreenUseCase (capture → analyze → translate, as a Flow)
│  └─ PipelineState.kt     sealed interface state machine
├─ data/
│  ├─ remote/              SpaceApi, dto/ (JSON shapes), mapper/ (DTO → domain)
│  ├─ local/               Room cache by image hash, DataStore settings
│  ├─ translator/          SpaceTranslator
│  └─ repository/          SpaceAnalysisRepository
├─ platform/               Android-specific implementations of domain interfaces
│  ├─ capture/             ScreenCaptureController (+ ScreenCapturer impl)
│  └─ overlay/             OverlayLifecycleOwner, window handling
├─ presentation/
│  ├─ main/                MainActivity, HomeScreen
│  └─ overlay/             OverlayFab, TranslationOverlay composables, ViewModel
├─ service/                OverlayService (wires everything together)
└─ di/                     Hilt modules (milestone 2+)
```

- **MVVM:** the ViewModel holds `StateFlow<PipelineState>`; composables render it and send events up; state flows down.
- **DTOs vs domain models:** the Space's JSON is parsed into DTOs and mapped to domain models, so a JSON change only touches the mapper.
- **Use case:** `TranslateScreenUseCase` is real orchestration (capture, analyze, translate), so it earns its place; test it with fake implementations.

### Milestone 1: overlay + capture spike
Tap the floating `訳` button → it hides → the screen is captured → JPEG saved → toast with the size; the app shows the last capture.

### Pipeline state machine (sealed interface)
`Idle → Capturing → Detecting → Translating → Showing | Error(retry)`

### Android notes
- **MediaProjection (Android 14+):** consent is per session and the token can't be reused, so keep one projection alive in the service. The service must be foreground with type `mediaProjection` before `getMediaProjection()`, and the stop-callback must be registered before `createVirtualDisplay()`. In the system dialog choose "Entire screen".
- **Hide your own overlay before capture** (this also forces a fresh frame, since frames only arrive when the screen changes).
- **Compose in a service window** needs hand-built lifecycle, saved-state and view-model owners.
- **Xiaomi / HyperOS:** aggressive background killing; enable Autostart, "Display pop-up windows while running in the background", battery "No restrictions", and lock the app in recents.
- **`FLAG_SECURE` apps** return a black frame. **Rotation** is not handled yet.
- **Partial-screen input:** a phone screen is a crop of a page, so panel detection can vary; text boxes come from the ONNX detector.
- **Alternative capture:** `AccessibilityService.takeScreenshot()` (Android 11+) avoids the consent flow but is more fragile.

---

## 6. Requirements summary

### Functional
- Floating FAB over other apps; one tap translates the current screen.
- Capture, detect, translate, and overlay JP→EN text positioned over the original bubbles; tap to dismiss.
- Ignore watermarks; treat SFX and narration (`free_text`) separately from dialogue.
- Reading order preserved in the translation.
- Settings: translator backend, speaker proximity on/off, theme.
- Cache results by image hash.
- Graceful states for Space cold start, quota exhaustion, timeouts, network errors.

### Non-functional / constraints
- Personal use, no ads, no commercial release (Magi is non-commercial).
- Free-tier hosting only (ZeroGPU, 2 Spaces max).
- Detection and OCR run on the Space (internet needed); translation runs locally.
- Must work on a Xiaomi 13T under HyperOS background restrictions.
- Translator behind an interface so models are swappable.

### Job-posting mapping

| Posting requirement | Where it shows up in this app |
|---|---|
| Strong Kotlin, Coroutines, Flow, Kotlin Serialization | Pipeline, Space client, JSON DTOs |
| Jetpack Compose, Material 3, state hoisting | Settings/onboarding UI, overlay composables |
| Clean Architecture + MVVM, Data/Domain/Presentation | Layer structure in section 5 |
| StateFlow, sealed-class state machines | `PipelineState`, ViewModel state |
| Repository pattern | Analysis and translation repositories (remote + Room cache) |
| Navigation, Room, DataStore, Hilt | Settings/history screens, hash cache, settings storage, DI |
| REST (Retrofit or Ktor) | Space client (two-step Gradio call) |
| JUnit + MockK + basic UI testing | Use-case and coordinate-mapping unit tests, MockWebServer for the client, one Compose test on Settings |
| Firebase Analytics, Crashlytics, Remote Config | Events, pipeline failure reports, Space URL config |
| AI coding agents, Git, JIRA, CI/CD | `CLAUDE.md`/`AGENTS.md`, clean commit history, JIRA board, GitHub Actions |
| Debug production issues, end-to-end ownership | Cold starts, quota, OEM background kills, `FLAG_SECURE`, documented as "failure modes" |
| Nice-to-have: WorkManager, ProGuard/R8 + KSP | R8 + KSP on release; WorkManager only if batch translation is added |
| AdMob / ad mediation / Media3 / GLSL | **Not included** (personal use, no natural fit) |

---

## 7. Build order

1. **Milestone 1:** FAB overlay + MediaProjection capture working on the 13T (written, awaiting a run).
2. **Milestone 2:** Space clients (`analyze_b64` and `translate_script`), parse `script`/`texts`, match translations by `text_id`, and render the analyzed preview.
3. **FastAPI backend:** add a server-side orchestration boundary later; keep the Android `Translator` and `AnalysisRepository` ports stable.
4. Settings, Room cache, Hilt, tests, CI, Firebase.
5. README with architecture diagram, demo GIF, and a failure-modes section.

---

## 8. Questions to test later

- Does Qwen2.5-1.5B (or a small Qwen3) translate well enough, stay in English, and ignore the `[S#?]` guess?
- Can a Qwen model run acceptably on the 13T (speed, memory) via LiteRT-LM or llama.cpp?
- How do real phone screenshots behave (partial pages, a different aspect ratio, downscaling by the Space)?
- Real latency per tap (cold start, GPU queue) and the daily ZeroGPU quota are unmeasured.
- License of `ogkalu/comic-text-and-bubble-detector`.
- Furigana is not read (manga-ocr returns the main text only), which makes name consistency harder. Planned for later, after the rest is stable.
