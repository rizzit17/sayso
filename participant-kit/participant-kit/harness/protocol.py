"""Event/action vocabulary and action validation. Human-readable spec: docs/PROTOCOL.md."""

from __future__ import annotations
from typing import Any, Dict, List

INCOMING_EVENT_TYPES = {
    "tool_manifest",       # always first: schemas of every callable tool
    "user_speech_chunk",   # clean text chunk
    "user_audio_chunk",    # raw audio: audio_ref (+ duration_ms, end_of_turn); no transcript
    "video_frame",         # raw frame: image_ref (+ frame_id, optional device_hint); no caption
    "interruption",        # user barged in / changed their mind
    "tool_result",         # result of an earlier tool_call
    "scenario_end",        # no more user events
}

OUTGOING_ACTIONS = {
    "filler_speech",
    "tool_call",
    "cancel_tool",
    "clarification_request",
    "final_response",
}

SPOKEN_ACTIONS = {"filler_speech", "clarification_request", "final_response"}


def validate_action(action: Any) -> List[str]:
    """Return protocol problems ([] = valid). Bad actions are logged, never fatal."""
    errors: List[str] = []
    if not isinstance(action, dict):
        return ["action must be a dict"]

    kind = action.get("action")
    if kind not in OUTGOING_ACTIONS:
        errors.append(f"unknown action type: {kind!r}")

    payload = action.get("payload")
    if not isinstance(payload, dict):
        errors.append("payload must be a dict")
        payload = {}

    if kind == "tool_call":
        if not isinstance(payload.get("api_name"), str):
            errors.append("tool_call payload requires string 'api_name'")
        if not isinstance(payload.get("args"), dict):
            errors.append("tool_call payload requires dict 'args'")

    if kind == "cancel_tool":
        if not isinstance(payload.get("call_id"), str):
            errors.append("cancel_tool payload requires string 'call_id'")

    if kind in SPOKEN_ACTIONS:
        if not isinstance(payload.get("text"), str) or not payload.get("text", "").strip():
            errors.append(f"{kind} payload requires non-empty string 'text'")

    if kind == "final_response" and "state_snapshot" not in action:
        errors.append("final_response must carry a top-level 'state_snapshot' dict")

    return errors


def get_path(obj: Dict[str, Any], dotted: str) -> Any:
    cur: Any = obj
    for part in dotted.split("."):
        if not isinstance(cur, dict) or part not in cur:
            return None
        cur = cur[part]
    return cur


def norm(value: Any) -> str:
    return str(value).strip().lower()
