from __future__ import annotations

import re

from app.schemas import WordFormResponse, WordRelation
from app.word_forms.data import CONTRACTIONS, FIXED_FORM_RELATIONS


class WordFormResolver:
    def resolve(self, surface: str, sentence: str = "") -> WordFormResponse:
        normalized = normalize_surface(surface)
        if not normalized:
            normalized = surface.strip().lower()

        contraction_expansion = CONTRACTIONS.get(normalized)
        if contraction_expansion is not None:
            return WordFormResponse(
                surface=surface,
                normalized=normalized,
                headword=normalized,
                pronunciationTarget=normalized,
                relation=WordRelation(
                    type="contraction_of",
                    target=contraction_expansion,
                    label=f"contraction of {contraction_expansion}",
                ),
                expansion=contraction_expansion,
                confidence="high",
                source="contraction_table",
            )

        fixed_relation = FIXED_FORM_RELATIONS.get(normalized)
        if fixed_relation is not None:
            headword, relation_type, label = fixed_relation
            return WordFormResponse(
                surface=surface,
                normalized=normalized,
                headword=headword,
                pronunciationTarget=normalized,
                relation=WordRelation(
                    type=relation_type,
                    target=headword,
                    label=label,
                ),
                expansion=None,
                confidence="high",
                source="fixed_form_table",
            )

        return WordFormResponse(
            surface=surface,
            normalized=normalized,
            headword=normalized,
            pronunciationTarget=normalized,
            relation=None,
            expansion=None,
            confidence="high",
            source="identity",
        )


def normalize_surface(surface: str) -> str:
    normalized = surface.strip().lower().replace("\u2019", "'")
    match = re.search(r"[a-z]+(?:['-][a-z]+)*", normalized)
    return match.group(0) if match else ""
