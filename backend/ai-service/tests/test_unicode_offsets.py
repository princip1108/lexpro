import unittest

from app.unicode_offsets import (
    SourceBlock,
    TextContractError,
    assemble_canonical_document,
    canonicalize_text,
    codepoint_span_to_utf16,
    locate_entities,
    slice_utf16,
    utf16_length,
    verify_located_entity,
)


class UnicodeOffsetTests(unittest.TestCase):
    def test_expected_utf16_vectors(self):
        vectors = (
            ("张某在北京", "北京", (3, 5)),
            ("张𠮷在北京", "北京", (4, 6)),
            ("张😀在北京", "北京", (4, 6)),
            ("A👍🏽B北京", "北京", (6, 8)),
            ("e\u0301在北京", "北京", (3, 5)),
            ("👨‍👩‍👧‍👦北京", "北京", (11, 13)),
            ("A\t北京", "北京", (2, 4)),
            ("A\u00a0北京", "北京", (2, 4)),
        )
        for text, entity, expected in vectors:
            with self.subTest(text=text):
                start_codepoint = text.index(entity)
                actual = codepoint_span_to_utf16(text, start_codepoint, start_codepoint + len(entity))
                self.assertEqual(expected, actual)
                self.assertEqual(entity, slice_utf16(text, *actual))

    def test_canonicalization_precedes_offset_calculation(self):
        canonical = canonicalize_text("\ufeffA\r\n北\u2028京\u2029", strip_initial_bom=True)
        self.assertEqual("A\n北\n京\n", canonical)
        self.assertEqual((2, 3), codepoint_span_to_utf16(canonical, 2, 3))

    def test_rejects_characters_that_cannot_enter_postgresql_contract(self):
        for text in ("A\x00B", "A\ufffdB", "A\ud800B"):
            with self.subTest(text=repr(text)), self.assertRaises(TextContractError):
                canonicalize_text(text)

    def test_slice_rejects_surrogate_pair_split(self):
        self.assertEqual(4, utf16_length("A😀B"))
        with self.assertRaisesRegex(TextContractError, "UTF16_SPAN_SPLITS_SURROGATE_PAIR"):
            slice_utf16("A😀B", 1, 2)

    def test_document_assembly_tracks_block_and_global_offsets(self):
        document = assemble_canonical_document(
            (
                SourceBlock("b1", "\ufeff张😀", order=1, page_no=1),
                SourceBlock("b2", "北京", order=2, page_no=2),
            )
        )
        self.assertEqual("张😀\n\n北京", document.text)
        self.assertEqual((0, 3), (
            document.blocks[0].global_start_utf16,
            document.blocks[0].global_end_utf16,
        ))
        self.assertEqual((5, 7), (
            document.blocks[1].global_start_utf16,
            document.blocks[1].global_end_utf16,
        ))

    def test_repeated_entities_are_located_in_source_order(self):
        document = assemble_canonical_document((SourceBlock("b1", "张某、张某在北京", order=0),))
        entities = locate_entities(
            document,
            "b1",
            (("犯罪嫌疑人", "张某"), ("犯罪嫌疑人", "张某"), ("地名", "北京")),
        )
        self.assertEqual(((0, 2), (3, 5), (6, 8)), tuple(
            (item.global_start_utf16, item.global_end_utf16) for item in entities
        ))
        self.assertEqual((0, 1), tuple(item.occurrence_index for item in entities[:2]))
        for entity in entities:
            verify_located_entity(document, entity)

    def test_entity_after_astral_character_round_trips(self):
        document = assemble_canonical_document((SourceBlock("b1", "张𠮷在北京", order=0),))
        entity = locate_entities(document, "b1", (("地名", "北京"),))[0]
        self.assertEqual((4, 6), (entity.global_start_utf16, entity.global_end_utf16))
        verify_located_entity(document, entity)

    def test_same_text_with_different_categories_keeps_exact_span(self):
        document = assemble_canonical_document((SourceBlock("b1", "惠法公安局", order=0),))
        entities = locate_entities(
            document,
            "b1",
            (("地名", "惠法"), ("组织机构名", "惠法")),
        )
        self.assertEqual(((0, 2), (0, 2)), tuple(
            (item.global_start_utf16, item.global_end_utf16) for item in entities
        ))

    def test_missing_entity_fails_instead_of_fabricating_position(self):
        document = assemble_canonical_document((SourceBlock("b1", "张某在北京", order=0),))
        with self.assertRaisesRegex(TextContractError, "ENTITY_TEXT_NOT_FOUND_IN_BLOCK"):
            locate_entities(document, "b1", (("罪名", "诈骗罪"),))

    def test_document_rejects_duplicate_block_identity_and_order(self):
        with self.assertRaisesRegex(TextContractError, "DUPLICATE_BLOCK_ID"):
            assemble_canonical_document((SourceBlock("b", "一", 0), SourceBlock("b", "二", 1)))
        with self.assertRaisesRegex(TextContractError, "DUPLICATE_BLOCK_ORDER"):
            assemble_canonical_document((SourceBlock("b1", "一", 0), SourceBlock("b2", "二", 0)))


if __name__ == "__main__":
    unittest.main()
