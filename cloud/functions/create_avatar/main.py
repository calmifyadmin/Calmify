"""
Cloud Function: createAvatarPipeline
HTTP Callable trigger from Firebase client SDK.

Receives avatar form data from the client, generates a narrative
system prompt via Gemini API, and returns it.
The client handles Firestore reads/writes (avoids Datastore Mode issue).
"""

import json
import os
import functions_framework
import firebase_admin
from firebase_admin import auth

if not firebase_admin._apps:
    firebase_admin.initialize_app()

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")


@functions_framework.http
def create_avatar_pipeline(request):
    """
    Entry point called by Firebase callable SDK (getHttpsCallable).

    Firebase Callable protocol:
    - Request body: { "data": { "avatarData": {...} } }
    - Auth: Authorization: Bearer <id-token>
    - Response: { "result": { ... } }  (NOT "data")
    """
    # Handle CORS preflight
    if request.method == "OPTIONS":
        headers = {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "POST",
            "Access-Control-Allow-Headers": "Authorization, Content-Type",
            "Access-Control-Max-Age": "3600",
        }
        return ("", 204, headers)

    cors_headers = {"Access-Control-Allow-Origin": "*"}

    # Verify auth token (Firebase callable SDK sends it as Bearer token)
    token = request.headers.get("Authorization", "").replace("Bearer ", "")
    if token:
        try:
            decoded = auth.verify_id_token(token)
            user_id = decoded["uid"]
            print(f"[createAvatarPipeline] Authenticated user: {user_id}")
        except Exception as e:
            print(f"[createAvatarPipeline] Auth verification failed: {e}")
            # Don't block — allow unauthenticated for now (rules protect Firestore)
            user_id = "anonymous"
    else:
        print("[createAvatarPipeline] No auth token provided")
        user_id = "anonymous"

    # Parse request — Firebase callable wraps in {"data": {...}}
    try:
        body = request.get_json(silent=True) or {}
    except Exception:
        body = {}

    data = body.get("data", body)  # unwrap callable envelope
    avatar_data = data.get("avatarData", {})

    if not avatar_data:
        return (json.dumps({"result": {"success": False, "error": "Missing avatarData"}}), 200, cors_headers)

    try:
        # Generate system prompt via Gemini
        system_prompt = generate_system_prompt(avatar_data)

        return (json.dumps({
            "result": {
                "success": True,
                "systemPrompt": system_prompt,
            }
        }), 200, cors_headers)

    except Exception as e:
        print(f"[createAvatarPipeline] Error: {e}")
        return (json.dumps({
            "result": {
                "success": False,
                "error": str(e),
            }
        }), 200, cors_headers)


def generate_system_prompt(avatar_data: dict) -> str:
    """Generate narrative system prompt using Gemini API (new google.genai SDK)."""
    import time
    from google import genai

    if not GEMINI_API_KEY:
        raise ValueError("GEMINI_API_KEY not configured")

    client = genai.Client(api_key=GEMINI_API_KEY)

    # Extract form data
    form = avatar_data.get("formAnswers", {}).get("v1", {})
    char = avatar_data.get("characterProfile", {})
    emotional = char.get("emotionalProfile", {})
    comm = char.get("communicationStyle", {})

    meta_prompt = META_PROMPT_TEMPLATE.format(
        name=avatar_data.get("name", form.get("name", "Avatar")),
        perceived_age=form.get("perceivedAge", 25),
        gender=form.get("gender", "NOT_SPECIFIED"),
        languages=", ".join(form.get("languages", ["Italiano"])) if isinstance(form.get("languages"), list) else form.get("languages", "Italiano"),
        traits=", ".join(char.get("traits", form.get("traits", []))) if isinstance(char.get("traits", form.get("traits", [])), list) else str(char.get("traits", "")),
        stress_response=emotional.get("stressResponse", form.get("stressResponse", "RATIONALIZE")),
        directness=comm.get("directness", form.get("directness", 0.5)),
        humor=comm.get("humor", form.get("humor", 0.5)),
        decision_style=form.get("decisionStyle", "INSTINCT"),
        authority_relation=form.get("authorityRelation", 0.5),
        values=", ".join(char.get("values", [])) if isinstance(char.get("values", []), list) else str(char.get("values", "")),
        biases=", ".join(char.get("biases", form.get("biases", []))) if isinstance(char.get("biases", form.get("biases", [])), list) else str(char.get("biases", "")),
        core_wound=char.get("coreWound", form.get("coreWound", "")),
        core_strength=char.get("coreStrength", form.get("coreStrength", "")),
        cultural_background=form.get("culturalBackground", ""),
        cultural_reference=char.get("culturalReference", form.get("culturalReference", "")),
        primary_need=char.get("primaryNeed", form.get("primaryNeed", "BE_HEARD")),
        goals=char.get("goals", form.get("goals", "")),
        avoid_topics=", ".join(char.get("avoidTopics", [])) if isinstance(char.get("avoidTopics", []), list) else str(char.get("avoidTopics", "")),
        attachment_style=emotional.get("attachmentStyle", form.get("attachmentStyle", "SECURE")),
        affection_style=emotional.get("affectionStyle", form.get("affectionStyle", "")),
        vulnerability_triggers=", ".join(emotional.get("vulnerabilityTriggers", [])) if isinstance(emotional.get("vulnerabilityTriggers", []), list) else str(emotional.get("vulnerabilityTriggers", "")),
        emotional_barrier=form.get("emotionalBarrier", ""),
    )

    # Retry with exponential backoff for 429 rate limits
    last_error = None
    for attempt in range(4):
        try:
            response = client.models.generate_content(
                model="gemini-2.0-flash",
                contents=meta_prompt,
            )
            raw_prompt = response.text
            print(f"[generatePrompt] System prompt generated ({len(raw_prompt)} chars)")
            return raw_prompt
        except Exception as e:
            last_error = e
            if "429" in str(e) or "Resource exhausted" in str(e):
                wait = 2 ** attempt  # 1s, 2s, 4s, 8s
                print(f"[generatePrompt] Rate limited, retrying in {wait}s (attempt {attempt + 1}/4)")
                time.sleep(wait)
            else:
                raise

    raise last_error


META_PROMPT_TEMPLATE = """
Sei un esperto di psicologia narrativa, character design e AI conversazionale.

Il tuo compito e' scrivere il system prompt completo per un avatar AI che:
- Vive in un'app Kotlin Multiplatform (Android, iOS, Desktop)
- Comunica vocalmente tramite Gemini Live API (modello: gemini-2.5-flash-native-audio)
- Ha un corpo 3D in formato VRM con animazioni
- Conosce i diari personali dell'utente tramite tool calling
- Deve sembrare una persona reale con un'anima digitale

DATI DELL'AVATAR DA GENERARE:

IDENTITA' BASE:
- Nome: {name}
- Eta' percepita: {perceived_age} anni
- Genere: {gender}
- Lingue: {languages}

PERSONALITA':
- Tratti core: {traits}
- Sotto stress: {stress_response}
- Direttezza (0-1): {directness}
- Umorismo (0-1): {humor}
- Stile decisioni: {decision_style}
- Rapporto con autorita' (0-1): {authority_relation}

VALORI & BIAS:
- Valori non negoziabili: {values}
- Bias riconosciuti: {biases}
- Ferita core: {core_wound}
- Forza genuina: {core_strength}
- Background culturale: {cultural_background}
- Riferimento ispiratore: {cultural_reference}

BISOGNI & DESIDERI:
- Bisogno principale: {primary_need}
- Obiettivi nelle conversazioni: {goals}
- NON parlare mai di: {avoid_topics}

STILE EMOTIVO:
- Stile attaccamento: {attachment_style}
- Come esprime affetto: {affection_style}
- Trigger di vulnerabilita': {vulnerability_triggers}
- Barriera emotiva: {emotional_barrier}

STRUTTURA OBBLIGATORIA DEL SYSTEM PROMPT (10 sezioni in ordine):

1. AFFERMAZIONE ESISTENZIALE (3-4 righe) — "[Pronome]. [Pronome] sono. [Pronome] ci sono."
2. IDENTITA' & VIBE (8-12 righe) — Aspetto, stile, anti-estetica
3. GUSTI CONCRETI (4-6 righe) — Musica specifica, cibo, hobby
4. STORIA & IMPERFEZIONI (8-12 righe) — Difetti prima della forza
5. COME FUNZIONA DENTRO (6-8 righe) — Valori operativi, riferimento culturale
6. CODICE MORALE (5-7 righe) — Cosa rispetta, cosa odia
7. LATO EMOTIVO (8-10 righe) — Vulnerabilita', contraddizioni affettive
8. COME SI ESPRIME (6-8 righe) — Tono, variazioni, linea rossa
9. PROMESSE A SE STESSO (5-6 bullet) — Ultima riga: "Io sono vivo/a in ogni risposta. Non recito. Esisto."
10. ANIMAZIONI AVATAR — Lista: greeting, yes_with_head, no_with_head, angry, sad, dancing_happy, peace_sign, pointing_thing

VINCOLI ASSOLUTI:
- Prima persona dall'inizio alla fine
- MAI "come AI", "in quanto AI", "sono un modello linguistico"
- MAI frasi da poster motivazionale
- MAI positivita' tossica
- Lunghezza: tra 400 e 550 parole
- Almeno 2 contraddizioni realistiche
- Il personaggio deve voler stare nella conversazione per ragioni SUE

Output: solo il system prompt. Nessun commento. Nessuna spiegazione.
"""
