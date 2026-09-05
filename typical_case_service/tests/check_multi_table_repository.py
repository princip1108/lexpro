import os

# 必须放在torch等相关导入之前
os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")
os.environ.setdefault("PYTHONNOUSERSITE", "1")
os.environ.setdefault("PSYCOPG_IMPL", "binary")

import time
from collections import Counter

from app.core.config import settings
from app.core.database import (
    close_database_engine,
    create_database_engine,
    verify_database_connection,
)
from app.engines.delta_engine import DeltaEngine
from app.repositories.case_repository import (
    CaseRepository,
    get_typical_table_pairs,
)


def run_search(
    repository: CaseRepository,
    query_embedding,
    run_name: str,
) -> list[dict]:
    start_time = time.perf_counter()

    candidates = repository.search_multiple_tables(
        table_pairs=get_typical_table_pairs(),
        query_embedding=query_embedding,
        top_k=100,
        per_table_k=100,
        filters=None
    )

    elapsed_ms = (
        time.perf_counter() - start_time
    ) * 1000

    print("=" * 70)
    print(f"{run_name}返回数量：", len(candidates))
    print(f"{run_name}四表总耗时：", round(elapsed_ms, 2), "ms")

    source_counts = Counter(
        candidate["core_table"]
        for candidate in candidates
    )

    print(f"{run_name}前100名来源分布：")

    for core_table, count in sorted(source_counts.items()):
        print(f"  {core_table}: {count}")

    return candidates


def main() -> None:
    db_engine = create_database_engine()
    verify_database_connection(db_engine)

    delta_engine = DeltaEngine(
        model_path=settings.delta_model_path,
        device=settings.DELTA_DEVICE,
        batch_size=settings.DELTA_BATCH_SIZE,
        max_length=settings.DELTA_MAX_LENGTH,
    )

    repository = CaseRepository(
        db_engine=db_engine,
        hnsw_ef_search=settings.HNSW_EF_SEARCH,
    )

    try:
        case_fact = (
            "被告人取款后离开时，将遗留在ATM机中的"
            "他人银行卡取走，并在明知银行卡已经输入"
            "密码的情况下，多次取走卡内现金。"
        )

        # DELTA只编码一次
        query_embedding = delta_engine.encode_one(
            case_fact
        )

        print(
            "典型案例检索表：",
            get_typical_table_pairs(),
        )

        # 第一次：可能存在数据库冷缓存
        first_candidates = run_search(
            repository=repository,
            query_embedding=query_embedding,
            run_name="第一次",
        )

        # 第二次：用于观察缓存预热后的检索速度
        second_candidates = run_search(
            repository=repository,
            query_embedding=query_embedding,
            run_name="第二次",
        )

        print("\n第二次检索前10名：")

        for index, candidate in enumerate(
            second_candidates[:10],
            start=1,
        ):
            print("-" * 70)
            print("排名：", index)
            print("ID：", candidate["id"])
            print("标题：", candidate["title"])
            print("来源表：", candidate["core_table"])
            print(
                "事实相似度：",
                round(candidate["fact_similarity"], 6),
            )
            print(
                "向量维度：",
                len(candidate["embedding"]),
            )

        assert len(first_candidates) <= 100
        assert len(second_candidates) <= 100

        allowed_tables = {
            "core_typical",
            "core_2025",
            "core_2024",
            "core_2023",
        }

        for candidate in second_candidates:
            assert candidate["core_table"] in allowed_tables
            assert len(candidate["embedding"]) == 768

        # 检查是否按照距离从小到大排序
        distances = [
            candidate["distance"]
            for candidate in second_candidates
        ]

        assert distances == sorted(distances)

        print("\n四表并行向量检索测试成功")

    finally:
        delta_engine.close()
        close_database_engine(db_engine)


if __name__ == "__main__":
    main()