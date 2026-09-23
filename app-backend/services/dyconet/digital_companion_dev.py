"""Development-only diagnostics for the configured AcademicCloud provider.

This module is never imported by the Flask application and never exposes
provider credentials. Run it manually from the backend environment.
"""

from __future__ import annotations

from contextual_xai_narrator import list_academiccloud_models


def main() -> int:
    try:
        for model_id in list_academiccloud_models():
            print(model_id)
    except Exception as exc:
        # A diagnostic must not print configuration or credentials on failure.
        print(f"AcademicCloud model discovery unavailable: {type(exc).__name__}")
        return 2
    return 0


if __name__ == "__main__":  # pragma: no cover - manual diagnostic
    raise SystemExit(main())
