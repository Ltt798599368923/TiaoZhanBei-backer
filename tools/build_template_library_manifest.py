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
    ("起诉状.docx", "起诉状", "complaint", "civil_commercial", "template", "民事诉讼起诉文书参考模板。"),
    ("民事答辩状.docx", "民事答辩状", "defense", "civil_commercial", "template", "民事诉讼答辩文书参考模板。"),
    ("公民授权委托书.docx", "公民授权委托书", "authorization", "civil_commercial", "template", "诉讼代理授权文书参考模板。"),
    ("上诉状.docx", "上诉状", "appeal", "civil_commercial", "template", "民事上诉文书参考模板。"),
    ("申请执行书.docx", "申请执行书", "execution", "enforcement", "template", "申请人民法院执行的参考模板。"),
)


def clean_text(document_path: Path) -> str:
    paragraphs = []
    for paragraph in Document(document_path).paragraphs:
        text = re.sub(r"[ \t]+", " ", paragraph.text).strip()
        if text:
            paragraphs.append(text)
    return "\n".join(paragraphs)


def infer_category(text: str) -> str:
    if "答辩" in text:
        return "defense"
    if "上诉" in text:
        return "appeal"
    if "委托" in text:
        return "authorization"
    if "保全" in text:
        return "preservation"
    if "执行" in text:
        return "execution"
    if "起诉" in text or "自诉" in text or "反诉" in text:
        return "complaint"
    if "申请" in text or "申诉" in text or "复议" in text:
        return "application"
    if "意见" in text or "陈述" in text:
        return "statement"
    return "other"


def infer_practice_area(text: str) -> str:
    if "知识产权" in text:
        return "intellectual_property"
    if "国家赔偿" in text or "赔偿" in text:
        return "state_compensation"
    if "刑事" in text or "刑事自诉" in text:
        return "criminal"
    if "普通行政" in text or "行政" in text:
        return "administrative"
    if "执行" in text:
        return "enforcement"
    if "海事" in text:
        return "maritime"
    if "环境资源" in text or "环境" in text:
        return "environmental"
    return "civil_commercial"


def infer_material_type(text: str) -> str:
    if "实例" in text:
        return "example"
    if "说明" in text or "指引" in text:
        return "guide"
    return "template"


def build_item(
    root: Path,
    relative_path: str,
    title: str,
    category: str,
    practice_area: str,
    material_type: str,
    description: str,
) -> dict[str, str]:
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
        "practiceArea": practice_area,
        "materialType": material_type,
        "description": description,
        "content": content,
        "sourceRelativePath": relative_path,
        "reviewStatus": "pending",
    }


def build_item_from_path(root: Path, source_path: Path) -> dict[str, str]:
    relative_path = str(source_path.relative_to(root))
    title = source_path.stem
    context = relative_path.replace("\\", "/")
    material_type = infer_material_type(context)
    description = (
        f"来源目录：{context.rsplit('/', 1)[0] if '/' in context else '通用文书'}。"
        + ("填写实例，仅供参考，发布前请确认全部当事人信息已脱敏。" if material_type == "example"
           else "请结合具体案件事实、现行法律规则和法院要求修改后使用。")
    )
    return build_item(
        root,
        relative_path,
        title,
        infer_category(context),
        infer_practice_area(context),
        material_type,
        description,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="生成文书模板首批审核清单")
    parser.add_argument("source_dir", type=Path, help="法律文书模板文件夹")
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("data/document-template-pilot.json"),
        help="输出 JSON 文件路径",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="扫描目录内的 DOCX 文书；默认仅生成首批 5 条审核清单",
    )
    parser.add_argument(
        "--include-examples",
        action="store_true",
        help="扫描时包含文件名含“实例”的资料；导入前务必核验脱敏情况",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=100,
        help="扫描模式下单份清单的最大条数（后台单次最多导入 100 条）",
    )
    args = parser.parse_args()

    root = args.source_dir.expanduser().resolve()
    if not root.is_dir():
        raise SystemExit(f"找不到模板目录: {root}")

    if args.all:
        source_paths = sorted(root.rglob("*.docx"))
        if not args.include_examples:
            source_paths = [path for path in source_paths if "实例" not in path.stem]
        items = [build_item_from_path(root, path) for path in source_paths[:args.limit]]
    else:
        items = [build_item(root, *config) for config in PILOT_ITEMS]
    payload = {
        "name": "法视界文书模板审核清单",
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
