import threading
import uuid
from typing import Any


class TaskStore:
    """
    单进程内存任务存储器。

    当前FastAPI服务使用单worker，
    因此先使用内存保存接口之间的中间结果。
    """

    def __init__(self) -> None:
        self._analysis_tasks: dict[str, dict[str, Any]] = {}
        self._retrieval_tasks: dict[str, dict[str, Any]] = {}

        # 防止多个请求同时读写字典
        self._lock = threading.RLock()
        # 测试是不手动输入anysisid
        self._latest_analysis_id: str | None = None

    def save_analysis(
            self,
            data: dict[str, Any],
    ) -> str:
        analysis_id = str(uuid.uuid4())

        with self._lock:
            self._analysis_tasks[analysis_id] = data
            self._latest_analysis_id = analysis_id

        return analysis_id


    def get_analysis(
        self,
        analysis_id: str,
    ) -> dict[str, Any]:
        """
        根据analysis_id读取案件分析结果。
        """
        with self._lock:
            data = self._analysis_tasks.get(analysis_id)

        if data is None:
            raise KeyError(
                f"analysis_id不存在或已经失效：{analysis_id}"
            )

        return data

    def get_latest_analysis_id(self) -> str:
        """
        获取最近一次案件分析的analysis_id。

        仅用于Swagger或单用户测试场景。
        正式前端仍建议显式传递analysis_id。
        """
        with self._lock:
            analysis_id = self._latest_analysis_id

        if analysis_id is None:
            raise KeyError("当前没有可用的案件分析结果，请先调用/analyze")

        return analysis_id

    def save_retrieval(
        self,
        data: dict[str, Any],
    ) -> str:
        """
        保存数据库检索结果，返回retrieval_id。
        """
        retrieval_id = str(uuid.uuid4())

        with self._lock:
            self._retrieval_tasks[retrieval_id] = data

        return retrieval_id

    def get_retrieval(
        self,
        retrieval_id: str,
    ) -> dict[str, Any]:
        """
        根据retrieval_id读取数据库检索结果。
        """
        with self._lock:
            data = self._retrieval_tasks.get(retrieval_id)

        if data is None:
            raise KeyError(
                f"retrieval_id不存在或已经失效：{retrieval_id}"
            )

        return data