import json
from typing import Any

import psycopg
from psycopg.rows import dict_row

from .models import RetrievalFilters
from .ranking import Candidate


class TypicalCaseRepository:
    def __init__(self, database_url: str, max_candidates: int):
        self._database_url = database_url
        self._max_candidates = max_candidates

    def candidates(self, filters: RetrievalFilters) -> dict[int, Candidate]:
        conditions, parameters = self._conditions(filters)
        parameters.append(self._max_candidates)
        query = f"""
            SELECT tc.typical_case_id, tc.title, tc.case_cause,
                   COALESCE(tc.dispute_focus_json, '[]'::jsonb) AS dispute_focus,
                   concat_ws(' ', tcc.fact, tcc.content) AS searchable_content
            FROM lexpro.typical_case tc
            JOIN lexpro.typical_case_content tcc USING (typical_case_id)
            WHERE {' AND '.join(conditions)}
            ORDER BY tc.judgment_date DESC NULLS LAST, tc.typical_case_id
            LIMIT %s
        """
        with psycopg.connect(self._database_url, row_factory=dict_row) as connection:
            connection.read_only = True
            rows = connection.execute(query, parameters).fetchall()
        return {
            row["typical_case_id"]: Candidate(
                row["typical_case_id"], row["title"], row["case_cause"],
                self._json_list(row["dispute_focus"]), row["searchable_content"] or "",
            )
            for row in rows
        }

    def vector_candidates(
        self, embedding: list[float], filters: RetrievalFilters, limit: int
    ) -> list[tuple[int, float]]:
        conditions, filter_parameters = self._conditions(filters)
        conditions.append("tc.embedding IS NOT NULL")
        vector = "[" + ",".join(f"{value:.8f}" for value in embedding) + "]"
        query = f"""
            SELECT tc.typical_case_id, 1 - (tc.embedding <=> %s::public.vector) AS score
            FROM lexpro.typical_case tc
            WHERE {' AND '.join(conditions)}
            ORDER BY tc.embedding <=> %s::public.vector
            LIMIT %s
        """
        parameters = [vector, *filter_parameters, vector, limit]
        with psycopg.connect(self._database_url, row_factory=dict_row) as connection:
            connection.read_only = True
            rows = connection.execute(query, parameters).fetchall()
        return [(row["typical_case_id"], max(-1.0, min(1.0, float(row["score"])))) for row in rows]

    def candidates_by_ids(self, ids: list[int]) -> dict[int, Candidate]:
        if not ids:
            return {}
        query = """
            SELECT tc.typical_case_id, tc.title, tc.case_cause,
                   COALESCE(tc.dispute_focus_json, '[]'::jsonb) AS dispute_focus,
                   concat_ws(' ', tcc.fact, tcc.content) AS searchable_content
            FROM lexpro.typical_case tc
            JOIN lexpro.typical_case_content tcc USING (typical_case_id)
            WHERE tc.typical_case_id = ANY(%s)
        """
        with psycopg.connect(self._database_url, row_factory=dict_row) as connection:
            connection.read_only = True
            rows = connection.execute(query, (ids,)).fetchall()
        return {
            row["typical_case_id"]: Candidate(
                row["typical_case_id"], row["title"], row["case_cause"],
                self._json_list(row["dispute_focus"]), row["searchable_content"] or "",
            )
            for row in rows
        }

    def _conditions(self, filters: RetrievalFilters) -> tuple[list[str], list[Any]]:
        conditions = ["TRUE"]
        parameters: list[Any] = []
        mappings = [
            (filters.caseCause, "tc.case_cause = %s"),
            (filters.caseType, "tc.case_type = %s"),
            (filters.courtLevel, "tc.court_level = %s"),
        ]
        for value, expression in mappings:
            if value:
                conditions.append(expression)
                parameters.append(value)
        if filters.judgmentYearFrom is not None:
            conditions.append("tc.judgment_date >= make_date(%s, 1, 1)")
            parameters.append(filters.judgmentYearFrom)
        if filters.judgmentYearTo is not None:
            conditions.append("tc.judgment_date < make_date(%s + 1, 1, 1)")
            parameters.append(filters.judgmentYearTo)
        return conditions, parameters

    def _json_list(self, value: Any) -> list[str]:
        if isinstance(value, str):
            value = json.loads(value)
        return [str(item) for item in value] if isinstance(value, list) else []
