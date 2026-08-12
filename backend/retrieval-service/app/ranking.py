import re
from collections import Counter
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class Candidate:
    typical_case_id: int
    title: str
    case_cause: str | None
    dispute_focus: list[str]
    content: str


def normalize_text(value: str | None) -> str:
    return re.sub(r"[^0-9A-Za-z\u4e00-\u9fff]+", "", value or "").lower()


def terms(value: str | None) -> Counter[str]:
    normalized = normalize_text(value)
    if len(normalized) < 2:
        return Counter([normalized]) if normalized else Counter()
    return Counter(normalized[index:index + 2] for index in range(len(normalized) - 1))


def lexical_score(query: str, candidate: Candidate) -> float:
    query_terms = terms(query)
    candidate_terms = terms(" ".join([candidate.title, candidate.case_cause or "", candidate.content]))
    if not query_terms or not candidate_terms:
        return 0.0
    intersection = sum((query_terms & candidate_terms).values())
    return min(1.0, (2.0 * intersection) / (sum(query_terms.values()) + sum(candidate_terms.values())))


def focus_overlap(query_focus: list[str], candidate_focus: list[str]) -> float:
    query = {normalize_text(item) for item in query_focus if normalize_text(item)}
    candidate = {normalize_text(item) for item in candidate_focus if normalize_text(item)}
    if not query or not candidate:
        return 0.0
    return len(query & candidate) / len(query | candidate)


def fuse_and_rerank(
    query: str,
    query_focus: list[str],
    candidates: dict[int, Candidate],
    lexical: list[tuple[int, float]],
    vector: list[tuple[int, float]],
    limit: int,
) -> list[dict[str, Any]]:
    lexical_rank = {case_id: rank for rank, (case_id, _) in enumerate(lexical, 1)}
    vector_rank = {case_id: rank for rank, (case_id, _) in enumerate(vector, 1)}
    lexical_scores = dict(lexical)
    vector_scores = dict(vector)
    fused: list[tuple[int, float, list[dict[str, Any]]]] = []

    for case_id in set(lexical_rank) | set(vector_rank):
        candidate = candidates.get(case_id)
        if candidate is None:
            continue
        rrf = 0.0
        if case_id in lexical_rank:
            rrf += 1.0 / (60 + lexical_rank[case_id])
        if case_id in vector_rank:
            rrf += 1.0 / (60 + vector_rank[case_id])
        normalized_rrf = min(1.0, rrf / (2.0 / 61.0))
        overlap = focus_overlap(query_focus, candidate.dispute_focus)
        cause_match = bool(candidate.case_cause and normalize_text(candidate.case_cause) in normalize_text(query))
        score = min(1.0, normalized_rrf * 0.75 + overlap * 0.2 + (0.05 if cause_match else 0.0))
        reasons: list[dict[str, Any]] = []
        if cause_match:
            reasons.append({"type": "CASE_CAUSE_MATCH", "text": candidate.case_cause})
        if overlap > 0:
            reasons.append({"type": "DISPUTE_FOCUS_MATCH", "score": round(overlap, 6)})
        reasons.append({"type": "LEXICAL_SIMILARITY", "score": round(lexical_scores.get(case_id, 0.0), 6)})
        if case_id in vector_scores:
            reasons.append({"type": "VECTOR_SIMILARITY", "score": round(vector_scores[case_id], 6)})
        fused.append((case_id, score, reasons))

    fused.sort(key=lambda item: (-item[1], item[0]))
    return [
        {
            "typicalCaseId": case_id,
            "rank": rank,
            "score": round(score, 6),
            "lexicalScore": round(lexical_scores.get(case_id, 0.0), 6),
            "vectorScore": round(vector_scores[case_id], 6) if case_id in vector_scores else None,
            "reasons": reasons,
        }
        for rank, (case_id, score, reasons) in enumerate(fused[:limit], 1)
    ]
