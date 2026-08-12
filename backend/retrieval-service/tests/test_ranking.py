import unittest

from app.ranking import Candidate, fuse_and_rerank, lexical_score


class RankingTests(unittest.TestCase):
    def test_hybrid_fusion_preserves_rank_and_reasons(self):
        candidates = {
            1: Candidate(1, "买卖合同纠纷", "买卖合同纠纷", ["货款支付"], "被告未支付货款"),
            2: Candidate(2, "借款合同纠纷", "借款合同纠纷", ["利息"], "借款利息争议"),
        }
        lexical = [(1, 0.9), (2, 0.2)]
        vector = [(2, 0.8), (1, 0.7)]

        result = fuse_and_rerank("买卖合同货款支付", ["货款支付"], candidates, lexical, vector, 2)

        self.assertEqual([1, 2], [item["rank"] for item in result])
        self.assertEqual(1, result[0]["typicalCaseId"])
        self.assertTrue(any(reason["type"] == "DISPUTE_FOCUS_MATCH" for reason in result[0]["reasons"]))

    def test_chinese_lexical_score_prefers_related_case(self):
        related = Candidate(1, "劳动合同纠纷", None, [], "违法解除劳动合同并请求经济补偿")
        unrelated = Candidate(2, "建设工程纠纷", None, [], "工程价款结算")

        self.assertGreater(
            lexical_score("解除劳动合同经济补偿", related),
            lexical_score("解除劳动合同经济补偿", unrelated),
        )


if __name__ == "__main__":
    unittest.main()
