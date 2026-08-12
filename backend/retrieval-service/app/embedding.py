from threading import Lock

from .config import Settings


class EmbeddingModel:
    def __init__(self, settings: Settings):
        self._settings = settings
        self._model = None
        self._lock = Lock()

    def encode(self, texts: list[str]) -> list[list[float]]:
        model = self._load()
        vectors = model.encode(texts, normalize_embeddings=True, show_progress_bar=False)
        result = vectors.tolist()
        if any(len(vector) != self._settings.dimension for vector in result):
            raise RuntimeError("Embedding model returned an unexpected dimension")
        return result

    def _load(self):
        if self._model is None:
            with self._lock:
                if self._model is None:
                    from sentence_transformers import SentenceTransformer
                    self._model = SentenceTransformer(
                        self._settings.model_name,
                        revision=self._settings.model_revision,
                    )
        return self._model
