
import logging
import time
from pathlib import Path

from app.engines.mlp_engine import MlpEngine
PROJECT_ROOT = Path(__file__).resolve().parents[2]

from sqlalchemy.engine import Engine

from app.core.config import settings

# Qwen放在DELTA之前导入，避免Unsloth导入顺序警告
from app.engines.qwen_engine import QwenEngine
from app.engines.delta_engine import DeltaEngine

from app.cache.task_store import TaskStore
from app.repositories.case_repository import (
    CaseRepository,
    get_typical_table_pairs,
)
from app.services.analyze_service import AnalyzeService

from app.core.database import (
    close_database_engine,
    create_database_engine,
    verify_database_connection,
)
from app.services.retrieval_service import RetrievalService

logger = logging.getLogger("uvicorn.error")

from app.services.rerank_service import RerankService

from app.services.recommend_service import (
    RecommendService,
)

class AppResources:
    """
    全局资源管理器。

    数据库连接池、DELTA、Qwen、MLP等只在服务启动时加载一次，
    后续所有接口直接复用。
    """

    def __init__(self) -> None:
        self.db_engine: Engine | None = None

        # 模型资源
        self.delta_engine = None
        self.qwen_engine = None
        self.mlp_engine = None

        # 数据访问与缓存
        self.case_repository = None
        self.task_store = None

        # 业务服务
        self.analyze_service = None
        self.retrieval_service = None
        self.rerank_service = None
        self.recommend_service = None

        self.ready = False

    def load_all(self) -> None:
        """
        服务启动时调用一次。
        """
        logger.info("开始初始化服务资源")

        # 1. 创建数据库连接池
        self.db_engine = create_database_engine()

        # 2. 验证数据库连接
        verify_database_connection(self.db_engine)

        # 3. 加载Qwen
        self.qwen_engine = QwenEngine(
            model_path=settings.qwen_model_path,
            max_seq_length=settings.QWEN_MAX_SEQ_LENGTH,
            max_new_tokens=settings.QWEN_MAX_NEW_TOKENS,
            temperature=settings.QWEN_TEMPERATURE,
            top_p=settings.QWEN_TOP_P,
            repetition_penalty=settings.QWEN_REPETITION_PENALTY,
            load_in_4bit=settings.QWEN_LOAD_IN_4BIT,
        )

        # 4. 加载DELTA
        self.delta_engine = DeltaEngine(
            model_path=settings.delta_model_path,
            device=settings.DELTA_DEVICE,
            batch_size=settings.DELTA_BATCH_SIZE,
            max_length=settings.DELTA_MAX_LENGTH,
        )

        # DELTA模型预热
        self.delta_engine.warm_up()

        # 5. 创建案例数据库Repository
        self.case_repository = CaseRepository(
            db_engine=self.db_engine,
            hnsw_ef_search=settings.HNSW_EF_SEARCH,
        )

        # 6. 预热四张典型案例检索表
        self._warm_up_typical_retrieval()

        # 7. 创建内存任务存储器
        self.task_store = TaskStore()

        # 8. 创建案件分析服务
        self.analyze_service = AnalyzeService(
            delta_engine=self.delta_engine,
            qwen_engine=self.qwen_engine,
            task_store=self.task_store,
        )

        # 后续继续添加：
        # 9. 加载MLP
        # 5. 加载MLP打分器
        mlp_weight_path = settings.mlp_weight_path

        logger.info(
            "开始加载MLP权重：%s",
            mlp_weight_path,
        )

        self.mlp_engine = MlpEngine(
            weight_path=mlp_weight_path,
            device=settings.DELTA_DEVICE,
        )

        self.mlp_engine.warm_up()

        logger.info("MLP加载与预热完成")

        # 10. 创建RetrievalService
        # 创建典型案例数据库检索服务
        # 创建案件分析服务
        # 创建典型案例数据库检索服务
        self.retrieval_service = RetrievalService(
            case_repository=self.case_repository,
            task_store=self.task_store,
        )

        # 11. 创建RerankService
        self.rerank_service = RerankService(
            mlp_engine=self.mlp_engine,
            task_store=self.task_store,
            case_repository=self.case_repository,
        )

        # 12. 创建RecommendService
        # 创建典型案例最终检索编排服务
        self.recommend_service = RecommendService(
            retrieval_service=(
                self.retrieval_service
            ),
            rerank_service=self.rerank_service,
        )
        self.ready = True

        logger.info("服务资源初始化完成")

    def _warm_up_typical_retrieval(self) -> None:
        """
        服务启动时执行接近真实请求的数据库预热。

        预热内容：
        1. 四表无筛选完整检索；
        2. 四表常用刑事条件检索；
        3. 读取并解析候选embedding。

        目的是让第一个用户尽量不承担数据库冷读取耗时。
        """
        if self.delta_engine is None:
            raise RuntimeError(
                "DELTA尚未加载，无法执行数据库预热"
            )

        if self.case_repository is None:
            raise RuntimeError(
                "CaseRepository尚未创建，无法执行数据库预热"
            )

        table_pairs = get_typical_table_pairs()

        warm_up_case_fact = (
            "行为人取得他人银行卡后，"
            "在未经持卡人允许的情况下，"
            "多次使用该银行卡提取现金，"
            "造成持卡人财产损失。"
        )

        logger.info(
            "开始预热典型案例检索表：%s",
            [
                core_table
                for core_table, _ in table_pairs
            ],
        )

        total_start = time.perf_counter()

        # 使用一次DELTA编码，后面两个预热场景复用
        query_embedding = self.delta_engine.encode_one(
            warm_up_case_fact
        )

        warm_up_scenarios = [
            (
                "无筛选完整检索",
                None,
            ),
            (
                "常用刑事筛选检索",
                {
                    "courtlevel": "基层法院",
                    "procedure": "一审",
                    "doctype": "判决书",
                    "casetype": "刑事",
                },
            ),
        ]

        for scenario_name, filters in warm_up_scenarios:
            scenario_start = time.perf_counter()

            candidates = (
                self.case_repository.search_multiple_tables(
                    table_pairs=table_pairs,
                    query_embedding=query_embedding,

                    # 与正式接口的检索规模保持一致
                    top_k=100,
                    per_table_k=100,

                    filters=filters,

                    # 正式接口需要候选embedding供MLP使用，
                    # 预热时也走同样的数据读取和解析路径
                    include_embedding=True,
                )
            )

            scenario_elapsed_ms = (
                                          time.perf_counter() - scenario_start
                                  ) * 1000

            logger.info(
                "数据库预热场景“%s”完成，返回%d条，耗时%.2f ms",
                scenario_name,
                len(candidates),
                scenario_elapsed_ms,
            )

        total_elapsed_ms = (
                                   time.perf_counter() - total_start
                           ) * 1000

        logger.info(
            "典型案例数据库完整预热完成，总耗时%.2f ms",
            total_elapsed_ms,
        )

    def close_all(self) -> None:
        logger.info("开始释放服务资源")

        self.ready = False

        self.recommend_service = None
        self.rerank_service = None
        self.retrieval_service = None
        self.analyze_service = None

        self.task_store = None
        self.case_repository = None

        if self.mlp_engine is not None:
            self.mlp_engine.close()
            self.mlp_engine = None

        if self.delta_engine is not None:
            self.delta_engine.close()
            self.delta_engine = None

        if self.qwen_engine is not None:
            self.qwen_engine.close()
            self.qwen_engine = None

        close_database_engine(self.db_engine)
        self.db_engine = None

        logger.info("服务资源释放完成")
