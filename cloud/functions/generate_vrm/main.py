"""
Cloud Function: generateVrmAvatar
HTTP Callable trigger from Firebase client SDK.

Proxies VRM generation requests to the Cloud Run VRM Generator service.
The client calls this via FirebaseFunctions.getHttpsCallable("generateVrmAvatar").
"""

import json
import os
import requests
import functions_framework
import firebase_admin
from firebase_admin import auth

if not firebase_admin._apps:
    firebase_admin.initialize_app()

VRM_GENERATOR_URL = os.environ.get(
    "VRM_GENERATOR_URL",
    "https://vrm-generator-23546263069.europe-west1.run.app"
)


@functions_framework.http
def generate_vrm_avatar(request):
    """
    Entry point called by Firebase callable SDK.
    Firebase Callable protocol: response must use {"result": {...}}
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

    # Verify auth (non-blocking — log but don't reject)
    token = request.headers.get("Authorization", "").replace("Bearer ", "")
    uid = "anonymous"
    if token:
        try:
            decoded = auth.verify_id_token(token)
            uid = decoded["uid"]
            print(f"[generateVrmAvatar] Authenticated user: {uid}")
        except Exception as e:
            print(f"[generateVrmAvatar] Auth warning: {e}")

    # Parse request — Firebase callable wraps in {"data": {...}}
    try:
        body = request.get_json(silent=True) or {}
    except Exception:
        body = {}

    data = body.get("data", body)

    user_id = data.get("userId", uid)
    avatar_id = data.get("avatarId")
    gender = data.get("gender", "NOT_SPECIFIED")
    vrm_params = data.get("vrmParams", {})

    if not avatar_id:
        return (json.dumps({"result": {"success": False, "error": "Missing avatarId"}}), 200, cors_headers)

    try:
        # Forward request to Cloud Run VRM Generator
        print(f"[generateVrmAvatar] Calling Cloud Run: {VRM_GENERATOR_URL}/generate")
        print(f"[generateVrmAvatar] vrmParams: {json.dumps(vrm_params)}")
        resp = requests.post(
            f"{VRM_GENERATOR_URL}/generate",
            json={
                "userId": user_id,
                "avatarId": avatar_id,
                "gender": gender,
                "vrmParams": vrm_params,
            },
            timeout=120,
        )

        print(f"[generateVrmAvatar] Cloud Run status: {resp.status_code}")
        print(f"[generateVrmAvatar] Cloud Run body (first 500): {resp.text[:500]}")

        if resp.status_code != 200:
            # Try to parse JSON error, fallback to raw text
            try:
                error_msg = resp.json().get("error", resp.text[:200])
            except Exception:
                error_msg = resp.text[:200]
            return (json.dumps({"result": {"success": False, "error": error_msg}}), 200, cors_headers)

        try:
            result = resp.json()
        except Exception:
            return (json.dumps({"result": {"success": False, "error": f"Invalid JSON from VRM service: {resp.text[:200]}"}}), 200, cors_headers)

        vrm_url = result.get("vrmUrl", "")

        print(f"[generateVrmAvatar] VRM generated: {vrm_url}")
        return (json.dumps({
            "result": {
                "success": True,
                "vrmUrl": vrm_url,
            }
        }), 200, cors_headers)

    except requests.Timeout:
        return (json.dumps({"result": {"success": False, "error": "VRM generation timed out"}}), 200, cors_headers)
    except Exception as e:
        print(f"[generateVrmAvatar] Error: {e}")
        return (json.dumps({"result": {"success": False, "error": str(e)}}), 200, cors_headers)
