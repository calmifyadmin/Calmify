"""
Avatar assembly orchestrator.
Downloads assets, runs Blender headless, uploads results.
"""

import os
import json
import shutil
import subprocess
import tempfile

from pipeline.uploader import download_base_assets, upload_vrm, upload_thumbnail


BLENDER_BIN = os.environ.get("BLENDER_BIN", "blender")
BLENDER_SCRIPT = os.path.join(os.path.dirname(__file__), "..", "blender_scripts", "assemble_avatar.py")


def run_avatar_pipeline(
    user_id: str,
    avatar_id: str,
    vrm_params: dict,
    fs_client,
) -> tuple[str, str]:
    """
    Full pipeline:
    1. Download base assets from GCS
    2. Run Blender headless to assemble + export VRM + thumbnail
    3. Upload results to Firebase Storage
    4. Return (vrm_url, thumbnail_url)
    """
    work_dir = tempfile.mkdtemp(prefix=f"vrm_{avatar_id}_")

    try:
        # Step 1: Download assets
        fs_client.update_status(user_id, avatar_id, "VRM_GENERATING", progress=35)
        assets = download_base_assets(vrm_params, work_dir)

        # Step 2: Prepare Blender config
        fs_client.update_status(user_id, avatar_id, "VRM_GENERATING", progress=50)

        output_vrm = os.path.join(work_dir, "avatar.vrm")
        output_thumb = os.path.join(work_dir, "thumbnail.png")

        blender_config = {
            "assets": assets,
            "vrmParams": vrm_params,
            "outputVrm": output_vrm,
            "outputThumbnail": output_thumb,
        }

        config_path = os.path.join(work_dir, "config.json")
        with open(config_path, "w") as f:
            json.dump(blender_config, f)

        # Step 3: Run Blender headless
        fs_client.update_status(user_id, avatar_id, "VRM_GENERATING", progress=60)

        result = subprocess.run(
            [
                BLENDER_BIN,
                "--background",
                "--python", BLENDER_SCRIPT,
                "--", config_path,
            ],
            capture_output=True,
            text=True,
            timeout=180,
        )

        if result.returncode != 0:
            raise RuntimeError(
                f"Blender failed (exit {result.returncode}): {result.stderr[-500:]}"
            )

        print(f"[Blender] stdout: {result.stdout[-200:]}")

        # Verify outputs exist
        if not os.path.exists(output_vrm):
            raise FileNotFoundError(f"Blender did not produce VRM: {output_vrm}")

        # Step 4: Upload to Firebase Storage
        fs_client.update_status(user_id, avatar_id, "VRM_UPLOADING", progress=85)

        vrm_url = upload_vrm(output_vrm, user_id, avatar_id)

        thumb_url = ""
        if os.path.exists(output_thumb):
            thumb_url = upload_thumbnail(output_thumb, user_id, avatar_id)

        return vrm_url, thumb_url

    finally:
        # Cleanup temp directory
        shutil.rmtree(work_dir, ignore_errors=True)
