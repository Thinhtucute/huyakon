Manga Translation Pipeline
Technical Specification — Magi v3 Analyzer and JP → EN Translator

Document type: System and API integration specification
Platform: Hugging Face Spaces with ZeroGPU
Source language: Japanese
Target language: English
Primary client: Android application
Output format: JSON
Architecture: Two independent Hugging Face Spaces connected through structured JSON

1. System Overview

The manga translation system consists of two independent Hugging Face Spaces.

Magi v3 Manga Analyzer — detects manga page structure, extracts Japanese text, determines reading order, and estimates speaker attribution.
JP → EN Manga Translator — receives the analyzer's JSON output, translates the extracted Japanese lines into English, and returns the translations alongside the source text and metadata.

The two Spaces are responsible for different stages of the process. The analyzer performs visual detection and OCR, while the translator performs text translation.

Neither Space needs to implement the entire pipeline independently.

1.1 Architecture
flowchart TD
    A[Android Application] --> B[Magi v3 Analyzer Space]
    B --> C[Image Detection and OCR]
    C --> D[Structured JSON Output]
    D --> E[JP to EN Translator Space]
    E --> F[Normalize Input and Parse Glossary]
    F --> G[Build Translation Prompts]
    G --> H[Hy-MT2 Manga Translation Model]
    H --> I[Attach Translations to Source Lines]
    I --> J[Translated JSON Output]
    J --> A


The Android application can orchestrate the two API calls or send the analyzer output to the translator through a backend service.

The recommended data flow is:

Upload a manga page to the Magi v3 Analyzer.
Receive the analyzer's JSON response.
Send the complete response, or its script array, to the Translator.
Optionally provide a glossary of Japanese-to-English terminology.
Receive translated lines and their associated metadata.
Display the translations in the Android application.
2. Space 1 — Magi v3 Manga Analyzer
2.1 Purpose

The Magi v3 Analyzer converts a manga page image into structured information suitable for downstream translation.

Its responsibilities include:

Detecting manga panels.
Detecting character regions.
Detecting speech tails.
Detecting text regions and speech-bubble outlines.
Extracting Japanese or English text, depending on the selected mode.
Classifying text regions.
Computing page-level reading order.
Associating dialogue with bubbles and potential speakers.
Producing a flat, ordered script array for translation.

The analyzer does not translate text.

2.2 Models and dependencies
Component	Model or library	Purpose
Manga structure detection	ragavsachdeva/magiv3	Panels, characters, tails
Text and bubble detection	ogkalu/comic-text-and-bubble-detector	Text regions and bubble outlines
Japanese OCR	manga-ocr	Recognition of text crops
English OCR mode	Magi v3 OCR	Recognition through Magi's own OCR
API and UI	Gradio	User interface and API endpoints
GPU execution	Hugging Face Spaces / ZeroGPU	Model inference
2.3 API endpoints
Endpoint: analyze

The primary interface accepts a manga image and processing options.

Parameter	Type	Default	Description
img	Image	Required	Manga page image
lang	String	ja	OCR mode: Japanese or English
gray	Boolean	false	Convert image to grayscale
probe	Boolean	false	Run additional Magi OCR diagnostics
fallback	Boolean	true	Enable nearest-character speaker attribution

The response is a JSON-compatible object containing image metadata, detections, text regions, reading order, and the translation-ready script.

Endpoint: analyze_b64

This endpoint accepts a Base64-encoded JPEG or PNG image for Android integration.

Parameter	Type	Default	Description
b64	String	Required	Base64 image data
lang	String	ja	OCR mode
fallback	Boolean	true	Enable nearest-character speaker fallback

The endpoint downsizes images whose longest side exceeds 2,500 pixels. Returned coordinates refer to the processed image dimensions.

The endpoint currently runs with debug, grayscale conversion, and OCR probing disabled.

2.4 Analyzer output structure

The analyzer returns the following top-level fields:

{
  "image": {
    "width": 1600,
    "height": 2400
  },
  "lang": "ja",
  "panels": [],
  "characters": [],
  "texts": [],
  "tails": [],
  "bubbles": [],
  "text_order": [],
  "script": []
}

Output fields
Field	Description
image	Processed image dimensions
lang	Selected OCR mode
panels	Detected panels and their reading-order ranks
characters	Character bounding boxes and cluster labels
texts	OCR text regions with classification and speaker metadata
tails	Detected speech-tail regions
bubbles	Detected speech-bubble outlines
text_order	Ordered list of text-region IDs
script	Ordered, filtered text entries intended for translation
debug	Optional diagnostic information
2.5 Bounding-box convention

All detections use the following coordinate format:

"bbox": [x1, y1, x2, y2]


Coordinates are pixel positions relative to the processed image:

x1: left edge.
y1: top edge.
x2: right edge.
y2: bottom edge.

The analyzer returns the processed dimensions in image.width and image.height. Consumers must use these values when interpreting the bounding boxes.

2.6 Text classification

Every entry in texts has a role:

Role	Description
dialogue	Text treated as dialogue
free_text	Text outside a detected speech bubble, including narration and possible sound effects
watermark	Text excluded from the translation script by watermark heuristics

The essential flag is false for watermarks and true for other roles.

The maybe_sfx flag identifies short free-text entries that may be sound effects. It is a heuristic, not a definitive classification.

Watermark classification is also heuristic. Consumers should retain the original texts array when the complete page content must remain recoverable.

2.7 Speaker attribution

The analyzer attempts to associate dialogue with a detected character.

Attribution sources:

speaker_source	Meaning
tail	A character was selected using a matched speech tail and estimated tail tip
nearest	A character was selected using geometric proximity
null	No character was assigned

Confidence labels are:

high: tail-based attribution succeeded.
medium: nearest-character fallback was sufficiently unambiguous.
low: the nearest-character result was ambiguous.
null: no speaker was assigned.

These values are rule-based confidence categories, not calibrated probabilities.

The speaker_cluster_id can be converted into a script-level hint such as character_4, provided that the cluster ID is present and attribution confidence is high or medium.

2.8 Reading order and script generation

The analyzer computes reading order across all text boxes, approximately from top to bottom and right to left within each row.

This is independent of panel order because panel detections may not reliably represent the reading structure of complex manga layouts.

The script array includes non-empty, essential text entries in computed reading order.

Example:

{
  "script": [
    {
      "order": 0,
      "text_id": 3,
      "text": "こんにちは",
      "role": "dialogue",
      "maybe_sfx": false,
      "speaker": "character_4",
      "speaker_confidence": "high",
      "speaker_source": "tail"
    }
  ]
}


The script is the recommended input for the translation Space.

It contains the source text and selected contextual metadata, while the complete analyzer response retains the detailed geometry and detection information.

3. Space 2 — JP → EN Manga Translator
3.1 Purpose

The Translator receives the analyzer output and generates English translations for the extracted Japanese lines.

Its responsibilities include:

Accepting the complete analyzer JSON, a script array, a list of text entries, or supported plain-text input.
Parsing an optional Japanese-to-English glossary.
Building a translation prompt for each non-empty source line.
Translating lines in batches using a manga-finetuned language model.
Preserving source text and associated metadata.
Returning translations in both grouped and flat JSON structures.

The Translator does not perform image detection or OCR.

3.2 Model and dependencies
Component	Configuration	Purpose
Translation model	fumetodev/Hy-MT2-1.8B-JP-Manga-Finetune-v5-GGUF repository	Japanese-to-English manga translation
Model loading	AutoModelForCausalLM	Load the model through Transformers
Tokenization	AutoTokenizer	Prepare model input
GPU execution	Hugging Face Spaces / ZeroGPU	Batch inference
UI and API	Gradio	Input forms and API endpoints
Input processing	translator.py	Normalization, glossary parsing, prompt construction

The application code expects a compatible Transformers model in the specified repository. Despite GGUF appearing in the repository name, the supplied code attempts to load the model through Transformers, using native loading first and a trust_remote_code=True fallback.

The actual deployed repository files and loading compatibility must be verified independently.

3.3 API endpoints
Endpoint: translate_script

This endpoint translates multiple lines from a manga page.

Parameter	Type	Description
payload	JSON or text string	Full analyzer result, script array, supported line list, or plain-text lines
glossary	String	Optional Japanese-to-English terminology list

The endpoint returns translated lines in grouped and flat structures, plus timing and model diagnostic metadata.

Endpoint: translate_line

This endpoint translates one Japanese line.

Parameter	Type	Description
text	String	Japanese source text
glossary	String	Optional glossary

It returns two values:

The English translation.
The prompt submitted to the model.

The second value is intended for inspection and debugging.

4. Translator Input Contract
4.1 Recommended input: analyzer script

The preferred payload is the script array from Space 1.

[
  {
    "order": 0,
    "text_id": 0,
    "text": "モデルの間でも流行ってるんだ〜！",
    "role": "dialogue",
    "maybe_sfx": false,
    "speaker": "character_0",
    "speaker_confidence": "high",
    "speaker_source": "tail"
  },
  {
    "order": 1,
    "text_id": 1,
    "text": "さすがばかちー！やればできんじゃん",
    "role": "dialogue",
    "maybe_sfx": false,
    "speaker": "character_1",
    "speaker_confidence": "medium",
    "speaker_source": "nearest"
  }
]


This format is recommended because it provides the text in reading order and includes speaker hints.

The Translator uses the text field as the translation source and carries selected metadata into its output.

4.2 Supported input formats

The current normalize() implementation supports several input shapes.

Format A: Full analyzer response

A dictionary containing a script field:

{
  "image": {
    "width": 1600,
    "height": 2400
  },
  "script": [
    {
      "text_id": 0,
      "text": "こんにちは",
      "speaker": "character_0"
    }
  ]
}


The script array is extracted automatically.

Format B: Direct list of lines
[
  {
    "text_id": 0,
    "text": "こんにちは",
    "speaker": "character_0"
  }
]


The list is wrapped into a default panel structure.

Format C: Panel-grouped lines
[
  {
    "panel": 0,
    "panel_id": null,
    "lines": [
      {
        "text_id": 0,
        "text": "こんにちは",
        "speaker": "character_0"
      }
    ]
  }
]


This structure is accepted directly.

Format D: Plain text
こんにちは
ありがとう！
また会おう。


Each non-empty line is treated as a separate source line.

Integration warning

Although the Translator accepts multiple input formats, the recommended contract between Spaces is the analyzer's script array or the full analyzer response.

The translator's normalization function does not currently understand the analyzer's text_order as a separate input source. It expects script when consuming the full response.

Also, the normalize() branch that accepts a dictionary containing only texts constructs a panel-grouped structure. Its filtering behavior uses ignore, not the analyzer's essential field. Passing the full analyzer result is preferable because its script field already applies the intended filtering rules.

5. Glossary Processing
5.1 Purpose

The glossary allows specific Japanese terms to be translated consistently.

Example:

亜美 = Ami
川嶋 = Kawashima
ばかちー = Bakachii


The glossary is optional.

5.2 Supported syntax

Each glossary entry must occupy one line.

Supported separators:

=
->
=>
→
Tab

Lines beginning with # are treated as comments.

Example:

# Character names
亜美 = Ami
川嶋 -> Kawashima

# Nicknames
ばかちー => Bakachii


Blank lines and invalid entries are ignored.

The parser splits each valid line at the first supported separator and preserves the Japanese and English terms as strings.

5.3 Relevant glossary terms

The Translator includes only glossary entries whose Japanese term appears as an exact substring of the current source line.

For example, if the glossary contains:

亜美 = Ami
川嶋 = Kawashima


and the source line is:

亜美、川嶋くんは？


the prompt includes both glossary entries.

A glossary term that does not occur in the source line is omitted from that line's prompt.

This behavior reduces unnecessary prompt context, but matching is based on substring occurrence rather than Japanese word segmentation or semantic matching.

6. Translation Prompt Construction

For each non-empty source line, the Translator constructs a prompt containing:

An optional glossary block.
Translation instructions.
The original Japanese text.

The core instruction is:

Translate the following text from Japanese into English as natural, concise manga dialogue. Preserve specific nouns, exact meaning, speaker intent, tone, punctuation, and sound effects. Output only the translated result without any explanation.

When relevant glossary entries exist, the prompt starts with:

Reference the following manga translations:
亜美 translates to Ami
川嶋 translates to Kawashima


The instruction and source line follow this block.

If no glossary entries match, the glossary block is omitted.

The model is instructed to output only the translated result, without explanations. The application trims surrounding whitespace from generated output.

Important: The speaker hint is preserved in the JSON output, but the current prompt-building code does not include speaker identity, speaker confidence, role, or surrounding dialogue in the prompt. Translation is performed one source line per prompt, even when multiple lines are generated in a batch.

7. Batch Translation and GPU Processing
7.1 Batch size

The current implementation uses:

BATCH = 16
MAX_NEW = 160


This means up to 16 lines are processed in each model generation batch.

MAX_NEW = 160 is the maximum number of new tokens generated for each line, not a guaranteed output length.

7.2 Generation behavior

The model uses the following generation settings:

Parameter	Value
do_sample	true
temperature	0.15
top_k	20
top_p	0.6
repetition_penalty	1.05
max_new_tokens	160

Prompts are sorted by length before generation to reduce padding overhead. The output order is restored to match the original prompt order.

The application uses left padding for batched decoder-only generation and fills a missing tokenizer padding token with the EOS token.

7.3 Page-level processing

The complete page is translated within one call to translate_payload_batch().

That function prepares prompts for all non-empty lines and calls the batch function once. The batch function then splits the prompts into generation batches of up to 16 lines.

This design reduces repeated GPU allocation overhead and supports page-level translation.

The translator enforces a maximum of 120 non-empty lines per page.

The lower-level translate_payload() function supports a maximum of 200 lines by default, but the current Gradio endpoint uses the batch version with a maximum of 120.

7.4 Prompt cache

The application caches translations by their complete prompt string.

Repeated identical prompts can reuse previous results rather than trigger new generation. The cache is held in process memory and is cleared when it grows beyond 3,000 entries.

The cache is not persistent storage, and it is not guaranteed to be shared between separate Space instances.

8. Translator Output Contract
8.1 Output structure

The translate_script endpoint returns:

{
  "panels": [],
  "flat": [],
  "debug": {
    "seconds": 1.25,
    "lines": 2,
    "eos_generation_config": "2",
    "eos_tokenizer": 2
  }
}


The values above are illustrative.

Field	Description
panels	Normalized input structure with a translation field added to each line
flat	Flat list of translated source lines and selected metadata
debug	Translation timing, translated-line count, and EOS diagnostics
error	Error message when the endpoint catches an exception

On failure, the endpoint returns an object containing error rather than the normal translated result.

8.2 Grouped output

The panels structure preserves the normalized input and adds a translation property to each line.

Example:

{
  "panels": [
    {
      "panel": 0,
      "panel_id": null,
      "lines": [
        {
          "text_id": 0,
          "text": "こんにちは",
          "speaker": "character_0",
          "translation": "Hello."
        }
      ]
    }
  ]
}


The panel field here is the translator's grouping index or value from the supplied input. It is not automatically derived from the analyzer's panel_id.

When the translator wraps a direct list of lines, it assigns panel = 0 and panel_id = null.

8.3 Flat output

The flat array contains one entry per non-empty source line.

Example:

{
  "flat": [
    {
      "text_id": 0,
      "panel": 0,
      "speaker": "character_0",
      "kind": null,
      "text": "こんにちは",
      "translation": "Hello."
    }
  ]
}

Field	Description
text_id	Source text ID, if available
panel	Grouping value from the normalized input
speaker	Speaker hint carried from the input
kind	Input kind value, if present
text	Original Japanese text
translation	Generated English translation

The flat array is the recommended output for consumers that need to render or export translated lines without traversing the nested structure.

Metadata preservation

The Translator currently copies only selected metadata into flat.

Fields such as role, maybe_sfx, speaker_confidence, speaker_source, bbox, and bubble_id are not automatically copied into the flat translation records.

If the Android application needs those fields for rendering or translation behavior, the output contract should be extended to preserve them.

8.4 Empty lines

Empty or whitespace-only lines are assigned:

"translation": ""


They are not included in the flat output.

For the analyzer's normal script output, empty OCR lines have already been excluded, but other supported input formats may contain them.

8.5 Debug metadata

The debug object includes:

Field	Description
seconds	Elapsed time measured by the endpoint
lines	Number of entries in flat
eos_generation_config	String representation of the model generation EOS token ID
eos_tokenizer	Tokenizer EOS token ID

The timing measures the work performed within the endpoint, including translation when needed. Cache hits can reduce actual generation work.

The diagnostic EOS fields are not required for normal Android integration.

9. End-to-End Example

This section illustrates how output from the first Space becomes input to the second.

9.1 Stage 1: Analyzer output

Suppose the Magi v3 Analyzer returns:

{
  "image": {
    "width": 1600,
    "height": 2400
  },
  "lang": "ja",
  "script": [
    {
      "order": 0,
      "text_id": 0,
      "text": "こんにちは",
      "role": "dialogue",
      "maybe_sfx": false,
      "speaker": "character_0",
      "speaker_confidence": "high",
      "speaker_source": "tail"
    },
    {
      "order": 1,
      "text_id": 1,
      "text": "ありがとう！",
      "role": "dialogue",
      "maybe_sfx": false,
      "speaker": "character_1",
      "speaker_confidence": "medium",
      "speaker_source": "nearest"
    }
  ]
}

9.2 Stage 2: Translator request

The Android application sends the complete analyzer response or the script array to translate_script.

An optional glossary can accompany the request:

# Character names
亜美 = Ami
川嶋 = Kawashima


The Translator extracts the script array, normalizes the entries, builds prompts, and generates translations.

9.3 Stage 3: Translator response

An illustrative response:

{
  "panels": [
    {
      "panel": 0,
      "panel_id": null,
      "lines": [
        {
          "order": 0,
          "text_id": 0,
          "text": "こんにちは",
          "role": "dialogue",
          "maybe_sfx": false,
          "speaker": "character_0",
          "speaker_confidence": "high",
          "speaker_source": "tail",
          "translation": "Hello."
        },
        {
          "order": 1,
          "text_id": 1,
          "text": "ありがとう！",
          "role": "dialogue",
          "maybe_sfx": false,
          "speaker": "character_1",
          "speaker_confidence": "medium",
          "speaker_source": "nearest",
          "translation": "Thank you!"
        }
      ]
    }
  ],
  "flat": [
    {
      "text_id": 0,
      "panel": 0,
      "speaker": "character_0",
      "kind": null,
      "text": "こんにちは",
      "translation": "Hello."
    },
    {
      "text_id": 1,
      "panel": 0,
      "speaker": "character_1",
      "kind": null,
      "text": "ありがとう！",
      "translation": "Thank you!"
    }
  ],
  "debug": {
    "seconds": 1.25,
    "lines": 2,
    "eos_generation_config": "2",
    "eos_tokenizer": 2
  }
}


This response illustrates the intended flow. Exact translations, timing, token IDs, and model output will vary.

The original text_id is retained so the application can correlate translations with the analyzer's text regions and their bounding boxes.

10. Important Integration Considerations
10.1 The two Spaces are independent

The current code does not automatically call the Magi v3 Space from the Translator Space.

The Android application or a backend service must pass the analyzer's output to the Translator.

10.2 The recommended handoff is script

The analyzer's script array provides the most direct handoff between the two systems.

It already contains ordered, non-empty, essential text entries and selected speaker metadata.

Passing the full response is also supported and preserves the original detection structure for future extensions.

10.3 Preserve source IDs

The application should preserve text_id throughout the pipeline.

This enables each translation to be associated with the corresponding OCR region and bounding box from the original analyzer response.

The translator does not currently return bounding boxes in flat, so the application should retain the original analyzer response or join the results by text_id.

10.4 Reading order must remain stable

The translator processes lines in the order supplied by the normalized input.

When consuming the analyzer's script array, it preserves its array order. It does not independently reconstruct the manga reading order.

The Android application should not reorder the script unless it has a separate, explicit requirement to do so.

10.5 Glossary behavior is line-specific

Only glossary terms occurring in a given Japanese source line are added to its prompt.

The glossary is not injected as a complete global list, and it does not automatically provide character descriptions or dialogue history.

10.6 Speaker information is metadata, not model context

The Translator preserves speaker hints in its returned JSON, but the current prompt builder does not supply them to the translation model.

Consequently, speaker metadata does not currently guarantee consistent voice, pronoun usage, or character-specific speech patterns.

If character-aware translation is required, the prompt builder must be extended to include relevant speaker context.

10.7 Translation quality is not validated automatically

The current Translator returns generated strings without a separate quality-assurance stage.

It does not independently validate semantic accuracy, glossary compliance, punctuation preservation, or output language.

A successful response therefore indicates that the processing function returned output, not that every translation is correct.

11. Recommended Contract Improvements

The following changes would make the two Spaces easier to integrate and maintain.

Priority 1 — Preserve all required metadata

Extend the Translator's flat output to retain relevant fields such as:

role
maybe_sfx
speaker_confidence
speaker_source
panel_id

This allows the Android application to display or process translations without repeatedly looking up metadata in the analyzer response.

Priority 2 — Define a versioned output contract

Introduce a schema version, for example:

{
  "schema_version": "1.0",
  "translations": []
}


This is a recommended future change, not part of the current implementation.

A versioned contract allows the JSON structure to evolve without silently breaking consumers.

Priority 3 — Make request limits explicit

Document and validate:

Maximum image dimensions and file size.
Maximum number of translation lines.
Maximum generated tokens per line.
Accepted image formats.
Timeout and error behavior.

The analyzer currently downsizes Base64 images based on dimensions. The Translator currently limits page-level batch translation to 120 non-empty lines.

Priority 4 — Return stable error responses

The Translator currently returns {"error": "..."} when translate_script catches an exception.

A future API contract should distinguish invalid input, unsupported payloads, model-loading failures, and inference failures. The HTTP status and structured error fields should be specified if a separate backend wraps the Gradio APIs.

Priority 5 — Test model loading in the deployed environment

The Translator repository name includes GGUF, while the application uses Transformers model loading and BF16 weights.

Verify that the repository actually contains compatible model files and that both loading paths work on the target Space before treating model initialization as guaranteed.

12. Summary of Responsibilities
Capability	Magi v3 Analyzer	JP → EN Translator
Accept manga image	Yes	No
Detect panels	Yes	No
Detect characters	Yes	No
Detect text regions	Yes	No
Perform OCR	Yes	No
Compute reading order	Yes	No
Estimate speakers	Yes	No
Parse glossary	No	Yes
Build translation prompts	No	Yes
Translate Japanese to English	No	Yes
Preserve text IDs	Generates them	Carries them through
Return source text	Yes	Yes
Return English translations	No	Yes
Provide visual bounding boxes	Yes	Not in the current flat output
Provide debugging information	Optional	Yes, for translate_script
Final integration contract

Space 1 — Magi v3 Analyzer

Input: manga page image and analysis options.

Output: structured JSON containing detections, OCR text, reading order, and the script array.

Space 2 — JP → EN Translator

Input: the analyzer's full JSON response or its script array, plus an optional glossary.

Output: translated lines in a grouped panels structure and a flat flat array, with selected source metadata preserved.

Android application

Responsibility: orchestrate both Spaces, retain the original analyzer result, associate translations with source text IDs and bounding boxes, and display the translated content.

The recommended architecture keeps image analysis and translation independent while connecting them through a clearly defined JSON contract.