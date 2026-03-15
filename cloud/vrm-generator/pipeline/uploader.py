"""
GCS upload/download helper for VRM assets.
Handles downloading base mesh assets and uploading generated VRM + thumbnails.
"""

import os
from google.cloud import storage

BUCKET_NAME = os.environ.get("GCS_BUCKET", "")

_client = None


def _get_client():
    global _client
    if _client is None:
        _client = storage.Client()
    return _client


def download_asset(gcs_path: str, local_path: str):
    """Download a single asset from GCS to local path."""
    client = _get_client()
    bucket = client.bucket(BUCKET_NAME)
    blob = bucket.blob(gcs_path)
    os.makedirs(os.path.dirname(local_path), exist_ok=True)
    blob.download_to_filename(local_path)
    print(f"[GCS] Downloaded gs://{BUCKET_NAME}/{gcs_path} → {local_path}")


def download_base_assets(vrm_params: dict, work_dir: str) -> dict:
    """
    Download all required base assets for avatar assembly.
    Returns a dict of local file paths keyed by asset type.
    """
    assets = {}

    # Body mesh
    body_type = vrm_params.get("bodyType", "AVERAGE").lower()
    body_path = os.path.join(work_dir, "body.glb")
    download_asset(f"assets/bodies/{body_type}.glb", body_path)
    assets["body"] = body_path

    # Hair mesh
    hair_style = vrm_params.get("hairStyle", "SHORT_CLASSIC").lower()
    hair_path = os.path.join(work_dir, "hair.glb")
    download_asset(f"assets/hairstyles/{hair_style}.glb", hair_path)
    assets["hair"] = hair_path

    # Outfit mesh
    outfit_style = vrm_params.get("outfitStyle", "CASUAL_MODERN").lower()
    outfit_path = os.path.join(work_dir, "outfit.glb")
    download_asset(f"assets/outfits/{outfit_style}.glb", outfit_path)
    assets["outfit"] = outfit_path

    # Base rig (VRM skeleton)
    rig_path = os.path.join(work_dir, "rig.glb")
    download_asset("assets/rig/vrm_base_rig.glb", rig_path)
    assets["rig"] = rig_path

    # Optional extras
    extras = vrm_params.get("extras", [])
    assets["extras"] = []
    for extra in extras:
        extra_key = extra.lower()
        extra_path = os.path.join(work_dir, f"extra_{extra_key}.glb")
        try:
            download_asset(f"assets/extras/{extra_key}.glb", extra_path)
            assets["extras"].append(extra_path)
        except Exception as e:
            print(f"[GCS] Warning: extra '{extra_key}' not found, skipping: {e}")

    return assets


def upload_vrm(local_path: str, user_id: str, avatar_id: str) -> str:
    """Upload generated VRM file to Firebase Storage. Returns public URL."""
    gcs_path = f"vrm/{user_id}/{avatar_id}.vrm"
    return _upload_file(local_path, gcs_path, "model/gltf-binary")


def upload_thumbnail(local_path: str, user_id: str, avatar_id: str) -> str:
    """Upload thumbnail PNG to Firebase Storage. Returns public URL."""
    gcs_path = f"thumbnails/{user_id}/{avatar_id}_thumb.png"
    return _upload_file(local_path, gcs_path, "image/png")


def _upload_file(local_path: str, gcs_path: str, content_type: str) -> str:
    """Upload a file to GCS and return its public URL."""
    client = _get_client()
    bucket = client.bucket(BUCKET_NAME)
    blob = bucket.blob(gcs_path)
    blob.upload_from_filename(local_path, content_type=content_type)
    blob.make_public()
    url = blob.public_url
    print(f"[GCS] Uploaded {local_path} → gs://{BUCKET_NAME}/{gcs_path}")
    return url
