import os

os.environ.setdefault(
    "KMP_DUPLICATE_LIB_OK",
    "TRUE",
)

import time

from app.core.config import settings
from app.core.database import (
    close_database_engine,
    create_database_engine,
    verify_database_connection,
)
from app.engines.delta_engine import DeltaEngine
from app.repositories.case_repository import CaseRepository


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

        query_embedding = delta_engine.encode_one(
            case_fact
        )

        start = time.perf_counter()

        candidates = repository.search_one_table(
            core_table="core_typical",
            query_embedding=query_embedding,
            top_k=5,

            # 第一次先不加筛选条件
            filters=None
        )

        elapsed_ms = (
            time.perf_counter() - start
        ) * 1000

        print("返回数量：", len(candidates))
        print("单表检索耗时：", round(elapsed_ms, 2), "ms")

        for index, candidate in enumerate(
            candidates,
            start=1,
        ):
            print("-" * 70)
            print("排名：", index)
            print("ID：", candidate["id"])
            print("标题：", candidate["title"])
            print(
                "事实相似度：",
                candidate["fact_similarity"],
            )
            print(
                "来源表：",
                candidate["core_table"],
            )
            print(
                "向量维度：",
                len(candidate["embedding"]),
            )

        assert len(candidates) <= 5

        for candidate in candidates:
            assert candidate["core_table"] == "core_typical"
            assert candidate["content_table"] == "content_typical"
            assert len(candidate["embedding"]) == 768

        print("\n单表向量检索测试成功")

    finally:
        delta_engine.close()
        close_database_engine(db_engine)


if __name__ == "__main__":
    main()