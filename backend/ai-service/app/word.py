"""Bounded OOXML extraction; embedded raster images use the approved MinerU client."""
from io import BytesIO
from pathlib import PurePosixPath
import posixpath
import zipfile
from xml.etree import ElementTree as ET

from .pipeline import canonical_document_from_mineru
from .clients.mineru import MinerUClientError
from .unicode_offsets import SourceBlock, TextContractError, assemble_canonical_document

W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
R = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"
A = "{http://schemas.openxmlformats.org/drawingml/2006/main}"
V = "{urn:schemas-microsoft-com:vml}"
MC = "{http://schemas.openxmlformats.org/markup-compatibility/2006}"


class WordDocumentError(ValueError):
    pass


class SafeTreeBuilder(ET.TreeBuilder):
    def doctype(self, name, pubid, system):
        raise WordDocumentError("WORD_DOCUMENT_INVALID")


def parse_docx(content, mineru, *, warnings=None):
    """Return logical reading-order blocks, without fabricated page/bbox coordinates."""
    blocks, pending, image_cache = [], [], {}
    text_length = 0
    image_occurrence = 0

    def emit(text):
        nonlocal text_length
        if not text.strip():
            return
        text_length += len(text)
        if text_length > 2_000_000 or len(blocks) >= 20000:
            raise WordDocumentError("WORD_DOCUMENT_TOO_LARGE")
        blocks.append(SourceBlock(f"word-{len(blocks)}", text, len(blocks)))

    def flush():
        emit("".join(pending))
        pending.clear()

    try:
        with zipfile.ZipFile(BytesIO(content)) as package:
            entries = package.infolist()
            if len(entries) > 2048 or sum(e.file_size for e in entries) > 64 * 1024 * 1024:
                raise WordDocumentError("WORD_DOCUMENT_TOO_LARGE")
            if len({e.filename for e in entries}) != len(entries) or any(e.flag_bits & 1 for e in entries):
                raise WordDocumentError("WORD_DOCUMENT_INVALID")

            def read_xml(path):
                if package.getinfo(path).file_size > 4 * 1024 * 1024:
                    raise WordDocumentError("WORD_DOCUMENT_TOO_LARGE")
                return ET.fromstring(package.read(path), parser=ET.XMLParser(target=SafeTreeBuilder()))

            def relationships(part):
                p = PurePosixPath(part)
                rel_path = str(p.parent / "_rels" / (p.name + ".rels"))
                return {} if rel_path not in package.namelist() else {
                    rel.get("Id"): rel for rel in read_xml(rel_path)
                }

            def target_for(part, rel):
                if rel is None or rel.get("TargetMode") == "External":
                    raise WordDocumentError("WORD_EXTERNAL_CONTENT_UNSUPPORTED")
                target = rel.get("Target", "")
                if not target or "\\" in target or ":" in target or target.startswith("/"):
                    raise WordDocumentError("WORD_DOCUMENT_INVALID")
                path = posixpath.normpath(posixpath.join(posixpath.dirname(part), target))
                if not path.startswith("word/"):
                    raise WordDocumentError("WORD_DOCUMENT_INVALID")
                return path

            def image(part, rel):
                nonlocal image_occurrence
                image_occurrence += 1
                flush()
                path = target_for(part, rel)
                suffix = PurePosixPath(path).suffix.lower()
                media = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg"}.get(suffix)
                if not media:
                    raise WordDocumentError("WORD_IMAGE_FORMAT_UNSUPPORTED")
                if path not in image_cache:
                    if len(image_cache) >= 64:
                        raise WordDocumentError("WORD_DOCUMENT_TOO_LARGE")
                    payload = mineru.parse_bytes("word-image" + suffix, package.read(path), media)
                    try:
                        image_cache[path] = canonical_document_from_mineru(payload, allow_empty=True)
                    except TextContractError as exc:
                        raise MinerUClientError("MINERU_RESPONSE_INVALID") from exc
                if image_cache[path] is None:
                    if warnings is not None:
                        warnings.append({"code": "WORD_IMAGE_NO_TEXT", "imageIndex": image_occurrence})
                    return
                for block in image_cache[path].blocks:
                    emit(block.text)

            def walk(node, part, rels):
                if node.tag in {W + "del", W + "moveFrom", W + "instrText"}:
                    return
                if node.tag == MC + "AlternateContent":
                    branch = node.find(MC + "Choice")
                    if branch is None:
                        branch = node.find(MC + "Fallback")
                    if branch is not None:
                        walk(branch, part, rels)
                    return
                if node.tag in {W + "altChunk", W + "object"}:
                    raise WordDocumentError("WORD_EMBEDDED_CONTENT_UNSUPPORTED")
                if node.tag == W + "t":
                    pending.append(node.text or "")
                elif node.tag == W + "tab":
                    pending.append("\t")
                elif node.tag in {W + "br", W + "cr"}:
                    pending.append("\n")
                elif node.tag == W + "noBreakHyphen":
                    pending.append("\u2011")
                elif node.tag == A + "blip":
                    image(part, rels.get(node.get(R + "embed") or node.get(R + "link")))
                elif node.tag == V + "imagedata":
                    image(part, rels.get(node.get(R + "id")))
                else:
                    for child in node:
                        walk(child, part, rels)
                if node.tag == W + "p":
                    flush()

            root = read_xml("word/document.xml")
            body = root.find(W + "body")
            if root.tag != W + "document" or body is None:
                raise WordDocumentError("WORD_DOCUMENT_INVALID")
            rels = relationships("word/document.xml")
            walk(body, "word/document.xml", rels)
            flush()
            # Auxiliary text follows the body once; pagination is deliberately unspecified.
            visited = set()
            for rel in rels.values():
                if rel.get("Type", "").rsplit("/", 1)[-1] not in {"header", "footer", "footnotes", "endnotes"}:
                    continue
                part = target_for("word/document.xml", rel)
                if part not in visited:
                    visited.add(part)
                    walk(read_xml(part), part, relationships(part))
                    flush()
    except WordDocumentError:
        raise
    except (zipfile.BadZipFile, KeyError, ET.ParseError, RuntimeError, OSError, ValueError, RecursionError) as exc:
        # MinerU errors must retain service-unavailable semantics, not look like corrupt Word.
        if isinstance(exc, MinerUClientError):
            raise
        raise WordDocumentError("WORD_DOCUMENT_INVALID") from exc
    if not blocks:
        raise WordDocumentError("WORD_DOCUMENT_EMPTY")
    return assemble_canonical_document(blocks)
