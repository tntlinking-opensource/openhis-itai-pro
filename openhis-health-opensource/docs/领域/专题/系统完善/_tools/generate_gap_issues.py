# -*- coding: utf-8 -*-
"""从 SYS-IMP 父文档拆分生成独立 GAP 问题单。"""
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "问题清单"

PREFIX_META = {
    "EXE": ("01-并发双写与条件更新", "并发双写与条件更新", "SYS-IMP-001"),
    "FIN": ("01-并发双写与条件更新", "并发双写与条件更新", "SYS-IMP-002"),
    "BKG": ("01-并发双写与条件更新", "并发双写与条件更新", "SYS-IMP-003"),
    "ATD": ("01-并发双写与条件更新", "并发双写与条件更新", "SYS-IMP-004"),
    "INV": ("01-并发双写与条件更新", "并发双写与条件更新", "SYS-IMP-005"),
    "CHS": ("06-外部集成与分布式顺序", "外部集成与分布式顺序", "SYS-IMP-006"),
    "STALE": ("02-Stale页面与跨角色", "Stale页面与跨角色", "SYS-IMP-007"),
    "ACL": ("03-权限越权与数据域", "权限越权与数据域", "SYS-IMP-008"),
    "FLW": ("04-流程状态机与硬门禁", "流程状态机与硬门禁", "SYS-IMP-009"),
    "BAT": ("05-批处理与报表一致性", "批处理与报表一致性", "SYS-IMP-010"),
    "INT": ("06-外部集成与分布式顺序", "外部集成与分布式顺序", "SYS-IMP-011"),
    "CLN": ("07-临床规则与主数据", "临床规则与主数据", "SYS-IMP-012"),
    "REG": ("08-监管合规与追溯", "监管合规与追溯", "SYS-IMP-013"),
    "ARCH": ("09-读模型渠道与全局架构", "读模型渠道与全局架构", "SYS-IMP-014"),
}


def gap_prefix(gap_id: str) -> str:
    if gap_id.startswith("GAP-CLN-PRICE"):
        return "CLN"
    m = re.match(r"GAP-([A-Z]+)-", gap_id)
    return m.group(1) if m else "OTHER"


def parse_overview_table(text: str) -> dict:
    meta = {}
    in_table = False
    for line in text.splitlines():
        if re.match(r"^## 2\. 问题清单总览", line):
            in_table = True
            continue
        if in_table and line.startswith("## "):
            break
        if not in_table or not line.startswith("| GAP-"):
            continue
        parts = [p.strip() for p in line.split("|")]
        if len(parts) < 7:
            continue
        gap_id = parts[1].split()[0]
        meta[gap_id] = {
            "title_short": parts[2],
            "severity": parts[3],
            "scope": parts[4],
            "priority": parts[5],
            "status": parts[6],
        }
    return meta


def parse_gap_sections(text: str) -> list:
    sections = []
    pattern = re.compile(r"^### (GAP-[A-Z]+(?:-PRICE)?-\d+)\s+(.+)$", re.MULTILINE)
    matches = list(pattern.finditer(text))
    for i, m in enumerate(matches):
        gap_id = m.group(1)
        title = m.group(2).strip()
        start = m.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        body = text[start:end].strip()
        sections.append((gap_id, title, body))
    return sections


def parse_dimension_table(body: str) -> dict:
    fields = {}
    for line in body.splitlines():
        if not line.startswith("|"):
            continue
        parts = [p.strip() for p in line.split("|")]
        if len(parts) >= 4 and parts[1] and parts[1] not in ("维度", "---"):
            fields[parts[1].strip("*")] = parts[2]
    return fields


def extract_block(body: str, header: str) -> str:
    pat = rf"\*\*{re.escape(header)}\*\*\s*\n([\s\S]*?)(?=\n\*\*|\n### |\n## |\Z)"
    m = re.search(pat, body)
    return m.group(1).strip() if m else ""


def build_issue_md(gap_id, title, body, overview, parent_rel, problem_type, parent_imp):
    fields = parse_dimension_table(body)
    severity = fields.get("严重度") or overview.get("severity", "")
    scope = fields.get("门/住") or fields.get("门急诊") or overview.get("scope", "")
    priority = overview.get("priority", "")
    status = fields.get("状态") or overview.get("status", "待实施")
    if_link = fields.get("关联 IF/BR") or fields.get("关联") or ""
    root_cause = fields.get("根因") or ""
    impact = fields.get("业务影响") or fields.get("现有兜底") or ""
    solution = fields.get("修改方案") or extract_block(body, "修改方案（推荐）") or extract_block(body, "修改方案")
    code = fields.get("涉及代码") or extract_block(body, "涉及代码")
    tests = fields.get("验收用例") or extract_block(body, "验收用例")
    effort = fields.get("工作量") or ""

    lines = [
        f"# {gap_id} {title}",
        "",
        f"> **问题类型**：{problem_type}",
        f"> **严重度**：{severity}",
        f"> **影响范围**：{scope}",
        f"> **优先级**：{priority}",
        f"> **状态**：{status}",
        f"> **父文档**：[{parent_imp}]({parent_rel})",
    ]
    if if_link:
        lines.append(f"> **关联 IF/BR**：{if_link}")
    lines.extend(["", "---", ""])

    lines.extend(["## 1. 问题描述", "", overview.get("title_short") or title, ""])

    lines.extend(["## 2. 发生场景", ""])
    if fields.get("典型时序"):
        lines.append(fields["典型时序"])
        lines.append("")
    elif fields.get("现象"):
        lines.append(fields["现象"])
        lines.append("")
    else:
        lines.append(f"在 {scope or '业务'} 场景下，{title}。")
        lines.append("")

    lines.extend(["## 3. 根因分析", ""])
    lines.append(root_cause or "见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。")
    lines.append("")

    lines.extend(["## 4. 业务影响", ""])
    lines.append(impact or "账实/账价不一致，或合规与安全风险。")
    lines.append("")

    lines.extend(["## 5. 修改方案", ""])
    if solution:
        lines.append(solution)
    else:
        rest = re.sub(r"^\|.*\$\n?", "", body, flags=re.MULTILINE).strip()
        lines.append(rest or "见父文档。")
    lines.append("")

    if code:
        lines.extend(["## 6. 涉及代码", "", code, ""])
    if tests:
        lines.extend(["## 7. 验收用例", "", tests, ""])
    if effort:
        lines.extend(["## 8. 工作量", "", effort, ""])

    lines.extend([
        "## 9. 修订记录",
        "",
        "| 版本 | 日期 | 说明 |",
        "| --- | --- | --- |",
        "| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |",
        "",
    ])
    return "\n".join(lines)


def main():
    imp_files = list(ROOT.rglob("SYS-IMP-*.md"))
    all_gaps = {}

    for f in imp_files:
        text = f.read_text(encoding="utf-8")
        overview = parse_overview_table(text)
        sections = parse_gap_sections(text)
        parent_rel = "../" + "/".join(f.relative_to(ROOT).parts)
        imp_match = re.search(r"SYS-IMP-(\d+)", f.name)
        parent_imp = f"SYS-IMP-{imp_match.group(1)}" if imp_match else f.stem

        for gap_id, title, body in sections:
            ov = overview.get(gap_id, {})
            if gap_id not in all_gaps or len(body) > len(all_gaps[gap_id][1]):
                all_gaps[gap_id] = (title, body, ov, parent_rel, parent_imp)

    created = []
    index_rows = []

    for gap_id in sorted(all_gaps.keys()):
        title, body, ov, parent_rel, parent_imp = all_gaps[gap_id]
        prefix = gap_prefix(gap_id)
        folder_name, problem_type, _ = PREFIX_META.get(prefix, ("其他", "其他", parent_imp))
        out_dir = OUT / folder_name
        out_dir.mkdir(parents=True, exist_ok=True)
        out_file = out_dir / f"{gap_id}.md"
        md = build_issue_md(gap_id, title, body, ov, parent_rel, problem_type, parent_imp)
        out_file.write_text(md, encoding="utf-8")
        created.append(out_file)
        index_rows.append({
            "id": gap_id,
            "title": ov.get("title_short") or title,
            "type": problem_type,
            "severity": ov.get("severity", ""),
            "priority": ov.get("priority", ""),
            "status": ov.get("status", ""),
            "path": f"{folder_name}/{gap_id}.md",
        })

    # master index
    index_lines = [
        "# 问题清单总索引",
        "",
        "> 每条 GAP 对应独立问题单；**编号稳定**，成码后以 PR 关闭。",
        "> 生成方式：`_tools/generate_gap_issues.py`（从 SYS-IMP 父文档同步）。",
        "",
        f"**合计**：{len(index_rows)} 条",
        "",
        "---",
        "",
        "## 按问题类型",
        "",
    ]

    by_type = {}
    for row in index_rows:
        by_type.setdefault(row["type"], []).append(row)

    type_order = [
        "并发双写与条件更新",
        "Stale页面与跨角色",
        "权限越权与数据域",
        "流程状态机与硬门禁",
        "批处理与报表一致性",
        "外部集成与分布式顺序",
        "临床规则与主数据",
        "监管合规与追溯",
        "读模型渠道与全局架构",
    ]
    for t in type_order:
        rows = by_type.get(t, [])
        if not rows:
            continue
        folder = rows[0]["path"].split("/")[0]
        index_lines.append(f"### {t}（{len(rows)}）")
        index_lines.append("")
        index_lines.append(f"目录：[{folder}/](./{folder}/)")
        index_lines.append("")
        index_lines.append("| 编号 | 标题 | 严重度 | 优先级 | 状态 |")
        index_lines.append("| --- | --- | --- | --- | --- |")
        for r in rows:
            index_lines.append(
                f"| [{r['id']}](./{r['path']}) | {r['title']} | {r['severity']} | {r['priority']} | {r['status']} |"
            )
        index_lines.append("")

    index_lines.extend([
        "---",
        "",
        "## 按编号（全表）",
        "",
        "| 编号 | 类型 | 标题 | 严重度 | 优先级 |",
        "| --- | --- | --- | --- | --- |",
    ])
    for r in index_rows:
        index_lines.append(
            f"| [{r['id']}](./{r['path']}) | {r['type']} | {r['title']} | {r['severity']} | {r['priority']} |"
        )
    index_lines.append("")

    (OUT / "README.md").write_text("\n".join(index_lines), encoding="utf-8")

    # category READMEs
    for t in type_order:
        rows = by_type.get(t, [])
        if not rows:
            continue
        folder = rows[0]["path"].split("/")[0]
        cat_readme = OUT / folder / "README.md"
        lines = [
            f"# {t}",
            "",
            f"共 **{len(rows)}** 条问题。",
            "",
            "| 编号 | 标题 | 严重度 | 优先级 | 状态 |",
            "| --- | --- | --- | --- | --- |",
        ]
        for r in rows:
            lines.append(
                f"| [{r['id']}](./{r['id']}.md) | {r['title']} | {r['severity']} | {r['priority']} | {r['status']} |"
            )
        lines.extend(["", "[← 问题清单总索引](../README.md)", ""])
        cat_readme.write_text("\n".join(lines), encoding="utf-8")

    print(f"Created {len(created)} issue files")
    print(f"Index: {OUT / 'README.md'}")


if __name__ == "__main__":
    main()
