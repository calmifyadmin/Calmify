"""
Firestore status update helper for the VRM generation pipeline.
Updates avatar document with progress, status, and error information.
"""

import firebase_admin
from firebase_admin import firestore

if not firebase_admin._apps:
    firebase_admin.initialize_app()


class FirestoreClient:
    def __init__(self):
        self.db = firestore.client()

    def _doc_ref(self, user_id: str, avatar_id: str):
        return self.db.document(f"users/{user_id}/avatars/{avatar_id}")

    def update_status(self, user_id: str, avatar_id: str, status: str, progress: int = 0):
        """Update avatar status and progress percentage."""
        self._doc_ref(user_id, avatar_id).update({
            "status": status,
            "creationProgress": progress,
            "updatedAt": firestore.SERVER_TIMESTAMP,
        })
        print(f"[VRM] {avatar_id} → {status} ({progress}%)")

    def mark_ready(self, user_id: str, avatar_id: str, vrm_url: str, thumb_url: str):
        """Mark avatar as READY with download URLs."""
        self._doc_ref(user_id, avatar_id).update({
            "status": "READY",
            "creationProgress": 100,
            "vrmUrl": vrm_url,
            "thumbnailUrl": thumb_url,
            "updatedAt": firestore.SERVER_TIMESTAMP,
        })
        print(f"[VRM] {avatar_id} → READY")

    def mark_error(self, user_id: str, avatar_id: str, error_message: str):
        """Mark avatar as ERROR with message."""
        self._doc_ref(user_id, avatar_id).update({
            "status": "ERROR",
            "errorMessage": error_message,
            "updatedAt": firestore.SERVER_TIMESTAMP,
        })
        print(f"[VRM] {avatar_id} → ERROR: {error_message}")
