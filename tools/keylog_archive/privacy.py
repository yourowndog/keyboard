"""Privacy exclusion for keylog dataset builds.

The phone stops harvesting sensitive fields (``HarvestPrivacy.kt``), but rows
written by older builds are already in the raw archive. This module decides,
deterministically and from metadata only, which rows a dataset build must leave
out so they never reach training or evaluation. Raw chunks are never modified.

Rules mirror the phone's gates, applied to whatever context a row carries:

- ``terminal``: a terminal/SSH app, or a field with no input class (``null``)
- ``password`` / ``email``: password or email-address variation
- ``non_text``: number, phone or date/time field (PINs, one-time codes)
- ``no_learning``: the app set ``noPersonalizedLearning`` (incognito, temp chats)
- ``secret_hint``: the field's hint or label names a secret
- ``same_field``: a legacy row whose field was classified as sensitive by a v4
  WORD_SLOT in the same session (legacy rows lack the input class and hint)
- ``otp_shaped``: the field's class is unknown and the row's text is a long
  digit string (a code or PIN typed somewhere the row cannot classify)
- ``no_context``: the row names no app at all, so nothing can be checked

Legacy (v1-v3) rows put the *variation* in ``inputType`` and carry no input
class, hint or label. They are matched to a v4 slot by ``(stream, sess, field)``
when one exists; otherwise only the app, variation, flags and the digit-shape
rule apply, and the build card counts them as ``unclassified_class``.
"""

from __future__ import annotations

import re

RULES_VERSION = 1

TERMINAL_PACKAGES = frozenset({
    "com.termux",
    "com.termux.x11",
    "jackpal.androidterm",
    "yarolegovich.materialterminal",
    "org.connectbot",
    "com.sonelli.juicessh",
    "com.server.auditor.ssh.client",
    "com.offsec.nhterm",
    "com.googlecode.android_scripting",
})
PASSWORD_VARIATIONS = frozenset({"password", "visible_password", "web_password"})
EMAIL_VARIATIONS = frozenset({"email_address", "web_email_address"})
NON_TEXT_CLASSES = frozenset({"number", "phone", "datetime"})

# Same word list as HarvestPrivacy.kt; whole words, case-insensitive.
SECRET_HINT = re.compile(
    r"\b(pass(word|code|phrase)?|pwd|pin|otp|one[- ]?time|2fa|mfa|verification code|security code|"
    r"auth(entication)? code|cvv|cvc|card number|credit card|iban|routing|account number|ssn|"
    r"social security|api key|secret|token)\b",
    re.IGNORECASE,
)
_DIGIT_WORD = re.compile(r"^\d{4,}$")
_DIGIT_RUN = re.compile(r"(?<!\d)\d{6,}(?!\d)")
_WORD_FIELDS = ("word", "typed", "applied", "before", "after", "rejected")
_TEXT_FIELDS = ("text",)


def context(obj: dict) -> dict:
    """The row's field context, normalised across schema versions.

    ``cls`` is the input class (text/number/phone/datetime/null) or None when the
    row does not record it.
    """
    ctx = obj.get("ctx")
    if obj.get("type") == "WORD_SLOT" and isinstance(ctx, dict):
        return {
            "app": ctx.get("app"),
            "cls": ctx.get("inputType"),
            "variation": ctx.get("inputVariation"),
            "flags": ctx.get("flags") or "",
            "hint": ctx.get("hint"),
            "label": ctx.get("label"),
            "field": ctx.get("fieldId"),
        }
    return {
        "app": obj.get("app"),
        "cls": None,
        # Legacy rows write the variation under "inputType".
        "variation": obj.get("inputType"),
        "flags": obj.get("flags") or "",
        "hint": None,
        "label": None,
        "field": obj.get("field"),
    }


def field_key(stream: str, obj: dict) -> tuple | None:
    ctx = context(obj)
    if ctx["field"] is None or obj.get("sess") is None:
        return None
    return (stream, obj["sess"], ctx["field"])


def context_reason(ctx: dict) -> str | None:
    """Reason from field metadata alone, or None."""
    app = ctx["app"]
    if not app:
        return "no_context"
    if app in TERMINAL_PACKAGES or ctx["cls"] == "null":
        return "terminal"
    variation = (ctx["variation"] or "").lower()
    if variation in PASSWORD_VARIATIONS:
        return "password"
    if variation in EMAIL_VARIATIONS:
        return "email"
    if ctx["cls"] in NON_TEXT_CLASSES:
        return "non_text"
    if "noPersonalizedLearning" in str(ctx["flags"]):
        return "no_learning"
    if any(isinstance(s, str) and SECRET_HINT.search(s) for s in (ctx["hint"], ctx["label"])):
        return "secret_hint"
    return None


def _otp_shaped(obj: dict) -> bool:
    for name in _WORD_FIELDS:
        value = obj.get(name)
        if isinstance(value, str) and _DIGIT_WORD.match(value.strip()):
            return True
    for name in _TEXT_FIELDS:
        value = obj.get(name)
        if isinstance(value, str) and _DIGIT_RUN.search(value):
            return True
    return False


def sensitive_fields(stream: str, objs) -> set:
    """Field keys a v4 WORD_SLOT classified as sensitive (first pass of a build)."""
    out = set()
    for obj in objs:
        if obj.get("type") != "WORD_SLOT":
            continue
        reason = context_reason(context(obj))
        key = field_key(stream, obj)
        if reason and reason != "no_context" and key:
            out.add(key)
    return out


def exclusion_reason(obj: dict, stream: str = "", blocked_fields: set | frozenset = frozenset()) -> str | None:
    """Why this row must stay out of a dataset build, or None to keep it."""
    ctx = context(obj)
    reason = context_reason(ctx)
    if reason:
        return reason
    key = field_key(stream, obj)
    if key is not None and key in blocked_fields:
        return "same_field"
    if ctx["cls"] is None and _otp_shaped(obj):
        return "otp_shaped"
    return None
