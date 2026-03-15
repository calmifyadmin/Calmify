"""
Cloud Function: generateSystemPrompt
Generates the narrative system prompt for an avatar using Gemini API.

Uses the Master System Prompt template (10 sections, 400-550 words)
to create a unique character from the form data.
"""

import os
import functions_framework
import firebase_admin
from firebase_admin import firestore
import google.generativeai as genai

# Init Firebase Admin
if not firebase_admin._apps:
    firebase_admin.initialize_app()

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
if GEMINI_API_KEY:
    genai.configure(api_key=GEMINI_API_KEY)


# ═══════════════════════════════════════════
# META-PROMPT TEMPLATE (from MASTER_SYSTEM_PROMPT.md Part I)
# ═══════════════════════════════════════════

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


def generate_system_prompt_for_avatar(user_id: str, avatar_id: str, avatar_data: dict):
    """
    Generates the narrative system prompt and saves it to Firestore.
    Can be called directly or via HTTP trigger.
    """
    db = firestore.client()
    doc_ref = db.document(f"users/{user_id}/avatars/{avatar_id}")

    # Extract form data
    form = avatar_data.get("formAnswers", {}).get("v1", {})
    char = avatar_data.get("characterProfile", {})
    emotional = char.get("emotionalProfile", {})
    comm = char.get("communicationStyle", {})

    # Build the meta-prompt with form data
    meta_prompt = META_PROMPT_TEMPLATE.format(
        name=avatar_data.get("name", form.get("name", "Avatar")),
        perceived_age=form.get("perceivedAge", 25),
        gender=form.get("gender", "NOT_SPECIFIED"),
        languages=", ".join(form.get("languages", ["Italiano"])),
        traits=", ".join(char.get("traits", form.get("traits", []))),
        stress_response=emotional.get("stressResponse", form.get("stressResponse", "RATIONALIZE")),
        directness=comm.get("directness", form.get("directness", 0.5)),
        humor=comm.get("humor", form.get("humor", 0.5)),
        decision_style=form.get("decisionStyle", "INSTINCT"),
        authority_relation=form.get("authorityRelation", 0.5),
        values=", ".join(char.get("values", [])) or form.get("values", ""),
        biases=", ".join(char.get("biases", form.get("biases", []))),
        core_wound=char.get("coreWound", form.get("coreWound", "")),
        core_strength=char.get("coreStrength", form.get("coreStrength", "")),
        cultural_background=form.get("culturalBackground", ""),
        cultural_reference=char.get("culturalReference", form.get("culturalReference", "")),
        primary_need=char.get("primaryNeed", form.get("primaryNeed", "BE_HEARD")),
        goals=char.get("goals", form.get("goals", "")),
        avoid_topics=", ".join(char.get("avoidTopics", [])) or form.get("avoidTopics", ""),
        attachment_style=emotional.get("attachmentStyle", form.get("attachmentStyle", "SECURE")),
        affection_style=emotional.get("affectionStyle", form.get("affectionStyle", "")),
        vulnerability_triggers=", ".join(emotional.get("vulnerabilityTriggers", [])) or form.get("vulnerabilityTriggers", ""),
        emotional_barrier=form.get("emotionalBarrier", ""),
    )

    # Call Gemini API to generate the narrative system prompt
    model = genai.GenerativeModel("gemini-2.0-flash")
    response = model.generate_content(meta_prompt)
    raw_prompt = response.text

    # Save to Firestore
    doc_ref.update({
        "systemPrompt": {
            "raw": raw_prompt,
            "version": 1,
            "generatedAt": firestore.SERVER_TIMESTAMP,
        },
        "status": "PROMPT_READY",
        "updatedAt": firestore.SERVER_TIMESTAMP,
    })

    print(f"[generatePrompt] System prompt generated for avatar {avatar_id} ({len(raw_prompt)} chars)")
    return raw_prompt


@functions_framework.http
def generate_system_prompt(request):
    """HTTP entry point for standalone invocation."""
    data = request.get_json()
    user_id = data.get("userId")
    avatar_id = data.get("avatarId")

    if not user_id or not avatar_id:
        return {"error": "Missing userId or avatarId"}, 400

    db = firestore.client()
    doc = db.document(f"users/{user_id}/avatars/{avatar_id}").get()
    if not doc.exists:
        return {"error": "Avatar not found"}, 404

    raw_prompt = generate_system_prompt_for_avatar(user_id, avatar_id, doc.to_dict())
    return {"success": True, "promptLength": len(raw_prompt)}, 200
