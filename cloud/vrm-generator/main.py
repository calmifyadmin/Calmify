"""
Calmify VRM Generator — Cloud Run Worker (Multi-Criteria Template Matching)
Selects the best VRM template based on avatar params using weighted scoring,
copies it to user storage.

POST /generate
  Body: { "userId": "...", "avatarId": "...", "vrmParams": {...}, "gender": "..." }
  Returns: { "success": true, "vrmUrl": "...", "templateId": "..." }
"""

import os
import random
import traceback
from flask import Flask, request, jsonify
from google.cloud import storage

app = Flask(__name__)

GCS_BUCKET = os.environ.get("GCS_BUCKET", "calmify-388723.appspot.com")
TEMPLATE_DIR = "assets/templates"

# ═══════════════════════════════════════════════════════════════
# Template Catalog — 28 VRM avatars with structured metadata
# ═══════════════════════════════════════════════════════════════

TEMPLATES = [
    # ── FEMALE ──────────────────────────────────────────────
    {
        "id": "8273846543168004377",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "short_straight",
        "hair_color": "white",
        "skin_tone": "light",
        "outfit_style": "scifi",
        "extras": ["elf_ears", "blue_eyes", "ahoge"],
    },
    {
        "id": "3526288987239655745",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "long_straight",
        "hair_color": "white",
        "skin_tone": "light",
        "outfit_style": "fantasy",
        "extras": ["elf_ears", "pink_eyes", "earring", "gothic"],
    },
    {
        "id": "2531028520127118547",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "medium_straight",
        "hair_color": "white",
        "skin_tone": "light",
        "outfit_style": "casual",
        "extras": ["elf_ears", "blue_eyes", "gothic"],
    },
    {
        "id": "2504890620690570138",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "medium_straight",
        "hair_color": "blue",
        "skin_tone": "light",
        "outfit_style": "fantasy",
        "extras": ["gem", "golden_decorations", "fairy"],
    },
    {
        "id": "1537828537377439118",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "long_straight",
        "hair_color": "blonde",
        "skin_tone": "light",
        "outfit_style": "scifi",
        "extras": ["elf_ears", "green_eyes", "tactical", "holster"],
    },
    {
        "id": "8720284547810328434",
        "gender": "FEMALE",
        "body_type": "medium",
        "hair_style": "medium_wavy",
        "hair_color": "blue",
        "skin_tone": "light",
        "outfit_style": "fantasy",
        "extras": ["purple_eyes", "golden_cape", "magic_orb", "jrpg"],
    },
    {
        "id": "7773565170184198823",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "long_straight",
        "hair_color": "black",
        "skin_tone": "medium",
        "outfit_style": "casual",
        "extras": ["purple_eyes", "hair_clip", "cross_stockings"],
    },
    {
        "id": "3724616530180429462",
        "gender": "FEMALE",
        "body_type": "medium",
        "hair_style": "long_straight",
        "hair_color": "black",
        "skin_tone": "light",
        "outfit_style": "fantasy",
        "extras": ["dark_wings", "red_halo", "red_eyes", "demon", "collar"],
    },
    {
        "id": "7841795605820798007",
        "gender": "FEMALE",
        "body_type": "medium",
        "hair_style": "ponytail",
        "hair_color": "white",
        "skin_tone": "light",
        "outfit_style": "fantasy",
        "extras": ["fox_ears", "kitsune", "blue_eyes", "amulet", "jrpg"],
    },
    {
        "id": "535982637608987520",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "short_straight",
        "hair_color": "gray",
        "skin_tone": "light",
        "outfit_style": "casual",
        "extras": ["red_eyes", "beret", "red_gems", "hakama", "urban"],
    },
    {
        "id": "9080013513856935611",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "long_straight",
        "hair_color": "silver",
        "skin_tone": "light",
        "outfit_style": "fantasy",
        "extras": ["crystal_crown", "orange_eyes", "ice", "cape", "royalty"],
    },
    {
        "id": "4232926477558396674",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "long_straight",
        "hair_color": "silver",
        "skin_tone": "light",
        "outfit_style": "fantasy",
        "extras": ["red_eyes", "roses", "gothic_lolita", "wand", "tail"],
    },
    {
        "id": "3475169946044519461",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "short",
        "hair_color": "black",
        "skin_tone": "light",
        "outfit_style": "fantasy",
        "extras": ["dark_wings", "tengu_mask", "hood", "geta", "yokai"],
    },
    {
        "id": "2111278381696622069",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "short_messy",
        "hair_color": "red",
        "skin_tone": "light",
        "outfit_style": "casual",
        "extras": ["red_eyes", "holster", "tactical", "minimal"],
    },
    {
        "id": "2318155617483698327",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "short_bob",
        "hair_color": "white",
        "skin_tone": "light",
        "outfit_style": "fantasy",
        "extras": ["white_wings", "golden_halo", "angel", "red_eyes"],
    },
    {
        "id": "8885600976542733721",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "short_bob",
        "hair_color": "black",
        "skin_tone": "light",
        "outfit_style": "scifi",
        "extras": ["red_eyes", "holster", "cyborg", "tactical"],
    },
    {
        "id": "5911639371054643930",
        "gender": "FEMALE",
        "body_type": "medium",
        "hair_style": "short_messy",
        "hair_color": "orange",
        "skin_tone": "light",
        "outfit_style": "casual",
        "extras": ["choker", "fingerless_gloves", "hoodie", "streetwear"],
    },
    {
        "id": "3269111164109153748",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "short_bob",
        "hair_color": "gray",
        "skin_tone": "light",
        "outfit_style": "casual",
        "extras": ["harness", "red_highlight", "techwear", "urban"],
    },
    {
        "id": "8703219022208733751",
        "gender": "FEMALE",
        "body_type": "slim",
        "hair_style": "long_straight",
        "hair_color": "red",
        "skin_tone": "light",
        "outfit_style": "casual",
        "extras": ["red_eyes", "crop_top", "jeans", "sneakers"],
    },
    # ── MALE ────────────────────────────────────────────────
    {
        "id": "261302019204370700",
        "gender": "MALE",
        "body_type": "slim",
        "hair_style": "short_messy",
        "hair_color": "white",
        "skin_tone": "light",
        "outfit_style": "casual",
        "extras": ["golden_halo", "heterochromia", "angel", "sportswear"],
    },
    {
        "id": "8411904524788092077",
        "gender": "MALE",
        "body_type": "medium",
        "hair_style": "short_messy",
        "hair_color": "brown",
        "skin_tone": "light",
        "outfit_style": "scifi",
        "extras": ["tactical_gloves", "armor", "tactical"],
    },
    {
        "id": "1623488664090707987",
        "gender": "MALE",
        "body_type": "medium",
        "hair_style": "short_messy",
        "hair_color": "black",
        "skin_tone": "light",
        "outfit_style": "formal",
        "extras": ["glasses", "tie", "office"],
    },
    {
        "id": "7449585967585870961",
        "gender": "MALE",
        "body_type": "medium",
        "hair_style": "short_messy",
        "hair_color": "blue",
        "skin_tone": "light",
        "outfit_style": "formal",
        "extras": ["glasses", "tie", "ahoge"],
    },
    {
        "id": "2531419525577818593",
        "gender": "MALE",
        "body_type": "slim",
        "hair_style": "short_messy",
        "hair_color": "blonde",
        "skin_tone": "medium",
        "outfit_style": "casual",
        "extras": ["yellow_vest", "tie", "preppy", "ahoge"],
    },
    {
        "id": "5839060371486938166",
        "gender": "MALE",
        "body_type": "medium",
        "hair_style": "short_messy",
        "hair_color": "brown",
        "skin_tone": "light",
        "outfit_style": "casual",
        "extras": ["glasses", "red_eyes", "mole", "hoodie", "sneakers"],
    },
    {
        "id": "6830752666059385556",
        "gender": "MALE",
        "body_type": "medium",
        "hair_style": "short_messy",
        "hair_color": "black",
        "skin_tone": "light",
        "outfit_style": "formal",
        "extras": ["gakuran", "golden_buttons", "uniform"],
    },
    {
        "id": "955499331636624676",
        "gender": "MALE",
        "body_type": "slim",
        "hair_style": "medium_messy",
        "hair_color": "black",
        "skin_tone": "light",
        "outfit_style": "casual",
        "extras": ["red_eye", "fingerless_gloves", "hoodie", "mysterious"],
    },
    {
        "id": "2025251289031658296",
        "gender": "MALE",
        "body_type": "medium",
        "hair_style": "short_messy",
        "hair_color": "black",
        "skin_tone": "light",
        "outfit_style": "fantasy",
        "extras": ["white_cape", "golden_decorations", "jrpg", "teal_eyes"],
    },
]

# ═══════════════════════════════════════════════════════════════
# Fuzzy matching maps: Italian wizard values → template tags
# ═══════════════════════════════════════════════════════════════

# Wizard BodyTypes (Italian) → normalized
BODY_TYPE_MAP = {
    "esile": "slim",
    "snello": "slim",
    "atletico": "medium",
    "medio": "medium",
    "robusto": "medium",
    "curvy": "medium",
}

# Wizard OutfitStyles (Italian) → normalized
OUTFIT_MAP = {
    "casual streetwear": "casual",
    "elegante formale": "formal",
    "sportivo": "casual",
    "fantasy": "fantasy",
    "sci-fi / futuristico": "scifi",
    "gothic": "fantasy",
    "bohemian": "casual",
    "minimale": "casual",
    "cyberpunk": "scifi",
    "vintage": "casual",
    "preppy": "formal",
    "grunge": "casual",
}

# Wizard HairStyles (Italian) → normalized
HAIR_STYLE_MAP = {
    "corto classico": "short_straight",
    "corto spettinato": "short_messy",
    "medio mosso": "medium_wavy",
    "lungo liscio": "long_straight",
    "lungo riccio": "long_straight",
    "rasato": "short",
    "mohawk": "short_messy",
    "bob": "short_bob",
    "trecce": "long_straight",
    "coda di cavallo": "ponytail",
    "afro": "short_messy",
    "undercut": "short_messy",
}

# Hair color fuzzy map
HAIR_COLOR_MAP = {
    "nero": "black",
    "castano": "brown",
    "biondo": "blonde",
    "rosso": "red",
    "bianco": "white",
    "grigio": "gray",
    "argento": "silver",
    "blu": "blue",
    "rosa": "red",
    "arancione": "orange",
    "viola": "silver",
    "platino": "white",
}

# Skin tone fuzzy map
SKIN_TONE_MAP = {
    "chiaro": "light",
    "medio": "medium",
    "olivastro": "medium",
    "scuro": "medium",
    "ambrato": "medium",
}

# ═══════════════════════════════════════════════════════════════
# Scoring weights
# ═══════════════════════════════════════════════════════════════

WEIGHTS = {
    "gender": 100,       # Hard filter — massive penalty if wrong
    "outfit_style": 30,  # Most visually impactful
    "hair_color": 20,
    "hair_style": 15,
    "body_type": 10,
    "skin_tone": 5,
    "extras": 3,         # Per matching extra
}


def normalize(value, mapping):
    """Normalize an Italian wizard value to its English tag."""
    if not value:
        return ""
    return mapping.get(value.lower().strip(), value.lower().strip())


def score_template(template, gender, body_type, hair_style, hair_color,
                   skin_tone, outfit_style, extras):
    """Score a template against user preferences. Higher = better match."""
    score = 0

    # Gender: hard filter with heavy penalty
    if template["gender"] == gender:
        score += WEIGHTS["gender"]
    elif gender in ("NON_BINARY", "NOT_SPECIFIED"):
        score += WEIGHTS["gender"] * 0.5  # Accept any template
    else:
        score -= WEIGHTS["gender"]  # Wrong gender, massive penalty

    # Outfit style
    if outfit_style and template["outfit_style"] == outfit_style:
        score += WEIGHTS["outfit_style"]
    elif outfit_style:
        # Partial: casual↔formal are closer than casual↔fantasy
        style_groups = {
            "casual": 0, "formal": 1, "fantasy": 2, "scifi": 3,
        }
        t_group = style_groups.get(template["outfit_style"], -1)
        u_group = style_groups.get(outfit_style, -1)
        if t_group >= 0 and u_group >= 0:
            dist = abs(t_group - u_group)
            if dist == 1:
                score += WEIGHTS["outfit_style"] * 0.3

    # Hair color
    if hair_color and template["hair_color"] == hair_color:
        score += WEIGHTS["hair_color"]
    elif hair_color:
        # Similar colors get partial credit
        similar_colors = {
            ("white", "silver"): 0.7, ("silver", "gray"): 0.7,
            ("white", "gray"): 0.5, ("blonde", "white"): 0.4,
            ("red", "orange"): 0.5, ("brown", "orange"): 0.3,
            ("black", "brown"): 0.3,
        }
        pair = tuple(sorted([template["hair_color"], hair_color]))
        if pair in similar_colors:
            score += WEIGHTS["hair_color"] * similar_colors[pair]

    # Hair style
    if hair_style and template["hair_style"] == hair_style:
        score += WEIGHTS["hair_style"]
    elif hair_style:
        # Group by length
        short = {"short", "short_straight", "short_messy", "short_bob"}
        medium = {"medium_straight", "medium_wavy", "medium_messy"}
        long = {"long_straight", "ponytail"}
        for group in [short, medium, long]:
            if template["hair_style"] in group and hair_style in group:
                score += WEIGHTS["hair_style"] * 0.5
                break

    # Body type
    if body_type and template["body_type"] == body_type:
        score += WEIGHTS["body_type"]

    # Skin tone
    if skin_tone and template["skin_tone"] == skin_tone:
        score += WEIGHTS["skin_tone"]

    # Extras — bonus for each matching extra keyword (Italian→English mapping)
    extras_map = {
        "orecchie elfiche": ["elf_ears"],
        "orecchie da gatto": ["fox_ears", "kitsune"],
        "orecchie di volpe": ["fox_ears", "kitsune"],
        "coda": ["tail"],
        "ali piumate": ["white_wings", "dark_wings"],
        "ali nere": ["dark_wings"],
        "corna": ["demon"],
        "aureola": ["golden_halo", "red_halo", "angel"],
        "occhiali": ["glasses"],
        "cappuccio": ["hood"],
        "maschera": ["tengu_mask"],
        "cicatrici": [],
        "tatuaggi": [],
        "piercing": [],
        "lentiggini": [],
        "neo": ["mole"],
        "eterocromia": ["heterochromia"],
    }
    if extras:
        for extra in extras:
            mapped = extras_map.get(extra.lower().strip(), [])
            if mapped:
                for tag in mapped:
                    if tag in template["extras"]:
                        score += WEIGHTS["extras"]
                        break
            else:
                # Fallback: fuzzy match
                extra_lower = extra.lower().strip()
                for tmpl_extra in template["extras"]:
                    if extra_lower in tmpl_extra or tmpl_extra in extra_lower:
                        score += WEIGHTS["extras"]
                        break

    return score


def select_template(gender, vrm_params):
    """Select best matching template from catalog."""
    body_type = normalize(vrm_params.get("bodyType", ""), BODY_TYPE_MAP)
    hair_style = normalize(vrm_params.get("hairStyle", ""), HAIR_STYLE_MAP)
    hair_color = normalize(vrm_params.get("hairColor", ""), HAIR_COLOR_MAP)
    skin_tone = normalize(vrm_params.get("skinTone", ""), SKIN_TONE_MAP)
    outfit_style = normalize(vrm_params.get("outfitType", ""), OUTFIT_MAP)
    extras = vrm_params.get("extras", [])

    print(f"[VRM] Matching: gender={gender} body={body_type} hair={hair_style}/{hair_color} "
          f"skin={skin_tone} outfit={outfit_style} extras={extras}")

    scored = []
    for tmpl in TEMPLATES:
        s = score_template(tmpl, gender, body_type, hair_style, hair_color,
                           skin_tone, outfit_style, extras)
        scored.append((s, tmpl))
        print(f"  [{tmpl['id'][:8]}...] score={s} "
              f"({tmpl['gender']} {tmpl['outfit_style']} {tmpl['hair_color']} {tmpl['hair_style']})")

    scored.sort(key=lambda x: x[0], reverse=True)

    # If top scores are tied, pick randomly among ties for variety
    top_score = scored[0][0]
    tied = [t for s, t in scored if s == top_score]
    selected = random.choice(tied) if len(tied) > 1 else scored[0][1]

    print(f"[VRM] Selected: {selected['id']} (score={top_score}, "
          f"{selected['gender']} {selected['outfit_style']} {selected['hair_color']})")
    return selected


# ═══════════════════════════════════════════════════════════════
# Flask routes
# ═══════════════════════════════════════════════════════════════

_storage_client = None


def get_client():
    global _storage_client
    if _storage_client is None:
        _storage_client = storage.Client()
    return _storage_client


@app.route("/generate", methods=["POST"])
def generate():
    """Entry point called by Cloud Function proxy."""
    data = request.get_json()
    user_id = data.get("userId")
    avatar_id = data.get("avatarId")
    gender = data.get("gender", "NOT_SPECIFIED")
    vrm_params = data.get("vrmParams", {})

    if not user_id or not avatar_id:
        return jsonify({"error": "Missing userId or avatarId"}), 400

    try:
        # Select best template via multi-criteria scoring
        template = select_template(gender, vrm_params)
        template_path = f"{TEMPLATE_DIR}/{template['id']}.vrm"

        client = get_client()
        bucket = client.bucket(GCS_BUCKET)

        # Copy template to user's avatar path
        dest_path = f"vrm/{user_id}/{avatar_id}.vrm"
        source_blob = bucket.blob(template_path)

        if not source_blob.exists():
            print(f"[VRM] ERROR: Template not found: {template_path}")
            return jsonify({"error": f"Template not found: {template['id']}"}), 500

        bucket.copy_blob(source_blob, bucket, dest_path)

        # Set content type and make public
        dest_blob = bucket.blob(dest_path)
        dest_blob.content_type = "model/gltf-binary"
        dest_blob.patch()
        dest_blob.make_public()

        vrm_url = dest_blob.public_url
        print(f"[VRM] Copied {template['id']} → {dest_path} ({vrm_url})")

        return jsonify({
            "success": True,
            "vrmUrl": vrm_url,
            "templateId": template["id"],
        }), 200

    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route("/templates", methods=["GET"])
def list_templates():
    """Debug endpoint: list all available templates."""
    return jsonify({
        "total": len(TEMPLATES),
        "female": len([t for t in TEMPLATES if t["gender"] == "FEMALE"]),
        "male": len([t for t in TEMPLATES if t["gender"] == "MALE"]),
        "templates": [
            {
                "id": t["id"],
                "gender": t["gender"],
                "outfit": t["outfit_style"],
                "hair": f"{t['hair_color']} {t['hair_style']}",
                "body": t["body_type"],
            }
            for t in TEMPLATES
        ],
    }), 200


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "templates": len(TEMPLATES)}), 200


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8080))
    app.run(host="0.0.0.0", port=port)
