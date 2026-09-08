#!/usr/bin/env python3
"""Build a reviewed legal-document template manifest from user-provided DOCX files."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

from docx import Document


PILOT_ITEMS = (
    ("起诉状.docx", "起诉状", "civil", "民事诉讼起诉文书参考模板。"),
    ("民事答辩状.docx", "民事答辩状", "civil", "民事诉讼答辩文书参考模板。"),
    ("公民授权委托书.docx", "公民授权委托书", "civil", "诉讼代理授权文书参考模板。"),
    ("上诉状.docx", "上诉状", "civil", "民事上诉文书参考模板。"),
    ("申请执行书.docx", "申请执行书", "civil", "申请人民法院执行的参考模板。"),
)


def clean_text(document_path: Path) -> str:
    paragraphs = []
    for paragraph in Document(document_path).paragraphs:
        text = re.sub(r"[ \t]+", " ", paragraph.text).strip()
        if text:
            paragraphs.append(text)
    return "\n".join(paragraphs)


def build_item(root: Path, relative_path: str, title: str, category: str, description: str) -> dict[str, str]:
    source_path = root / relative_path
    if not source_path.is_file():
        raise FileNotFoundError(f"模板不存在: {source_path}")

    content = clean_text(source_path)
    if len(content) < 100:
        raise ValueError(f"模板正文过短，未生成清单: {source_path}")

    fingerprint = hashlib.sha256(relative_path.encode("utf-8")).hexdigest()[:20]
    return {
        "importKey": f"template-library:{fingerprint}",
        "title": title,
        "category": category,
        "description": (
            f"{description}根据用户提供的 Word 原件整理；请结合具体案件事实、"
            "现行法律规则和法院要求修改后使用。"
        ),
        "content": content,
        "sourceRelativePath": relative_path,
        "reviewStatus": "pending",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="生成文书模板首批审核清单")
    parser.add_argument("source_dir", type=Path, help="法律文书模板文件夹")
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("data/document-template-pilot.json"),
        help="输出 JSON 文件路径",
    )
    args = parser.parse_args()

    root = args.source_dir.expanduser().resolve()
    if not root.is_dir():
        raise SystemExit(f"找不到模板目录: {root}")

    items = [build_item(root, *config) for config in PILOT_ITEMS]
    payload = {
        "name": "法视界文书模板首批审核清单",
        "generatedFrom": str(root),
        "itemCount": len(items),
        "items": items,
    }
    output = args.output.expanduser().resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"已生成 {len(items)} 条模板审核清单: {output}")


if __name__ == "__main__":
    main()
