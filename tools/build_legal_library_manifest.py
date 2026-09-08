#!/usr/bin/env python3
"""Build a reviewed legal-library JSON manifest from local DOCX source files.

The generated JSON is meant for the admin "法规阅读" import action. It contains
only extracted text and metadata; original Word files remain local until they
are separately uploaded as attachments after review.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

from docx import Document


PILOT_ITEMS = (
    {
        "path": "中华人民共和国劳动法_20181229.docx",
        "title": "中华人民共和国劳动法",
        "version": "2018-12-29",
    },
    {
        "path": "中华人民共和国劳动合同法_20121228.docx",
        "title": "中华人民共和国劳动合同法",
        "version": "2012-12-28",
    },
    {
        "path": "中华人民共和国民事诉讼法_20230901.docx",
        "title": "中华人民共和国民事诉讼法",
        "version": "2023-09-01",
    },
)


def clean_text(document_path: Path) -> str:
    paragraphs = []
    for paragraph in Document(document_path).paragraphs:
        text = re.sub(r"[ \t]+", " ", paragraph.text).strip()
        if text:
            paragraphs.append(text)
    return "\n".join(paragraphs)


def build_item(root: Path, config: dict[str, str]) -> dict[str, str]:
    source_path = root / config["path"]
    if not source_path.is_file():
        raise FileNotFoundError(f"资料不存在: {source_path}")

    content = clean_text(source_path)
    if len(content) < 200:
        raise ValueError(f"资料正文过短，未生成清单: {source_path}")

    relative_path = source_path.relative_to(root).as_posix()
    fingerprint = hashlib.sha256(relative_path.encode("utf-8")).hexdigest()[:20]
    version = config["version"]
    return {
        "importKey": f"legal-library:{fingerprint}",
        "contentType": "book",
        "title": config["title"],
        "summary": (
            f"资料版本：{version}。根据用户提供的 Word 原件整理为可读正文；"
            "阅读和使用前请以国家法律法规数据库等官方发布的现行有效文本为准。"
        ),
        "content": content,
        "sourceName": f"用户提供的法规资料（文件标注版本：{version}）",
        "sourceUrl": "",
        "sourceRelativePath": relative_path,
        "reviewStatus": "pending",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="生成法规阅读首批审核清单")
    parser.add_argument("source_dir", type=Path, help="法律法规文件夹")
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("data/legal-library-pilot.json"),
        help="输出 JSON 文件路径",
    )
    args = parser.parse_args()

    root = args.source_dir.expanduser().resolve()
    if not root.is_dir():
        raise SystemExit(f"找不到法规目录: {root}")

    items = [build_item(root, config) for config in PILOT_ITEMS]
    payload = {
        "name": "法视界法规阅读首批审核清单",
        "generatedFrom": str(root),
        "itemCount": len(items),
        "items": items,
    }
    output = args.output.expanduser().resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"已生成 {len(items)} 条法规审核清单: {output}")


if __name__ == "__main__":
    main()
