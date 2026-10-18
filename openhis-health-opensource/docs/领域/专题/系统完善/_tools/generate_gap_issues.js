/**
 * 从 SYS-IMP 父文档拆分生成独立 GAP 问题单。
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const OUT = path.join(ROOT, "问题清单");

const PREFIX_META = {
  EXE: ["01-并发双写与条件更新", "并发双写与条件更新", "SYS-IMP-001"],
  FIN: ["01-并发双写与条件更新", "并发双写与条件更新", "SYS-IMP-002"],
  BKG: ["01-并发双写与条件更新", "并发双写与条件更新", "SYS-IMP-003"],
  ATD: ["01-并发双写与条件更新", "并发双写与条件更新", "SYS-IMP-004"],
  INV: ["01-并发双写与条件更新", "并发双写与条件更新", "SYS-IMP-005"],
  CHS: ["06-外部集成与分布式顺序", "外部集成与分布式顺序", "SYS-IMP-006"],
  STALE: ["02-Stale页面与跨角色", "Stale页面与跨角色", "SYS-IMP-007"],
  ACL: ["03-权限越权与数据域", "权限越权与数据域", "SYS-IMP-008"],
  FLW: ["04-流程状态机与硬门禁", "流程状态机与硬门禁", "SYS-IMP-009"],
  BAT: ["05-批处理与报表一致性", "批处理与报表一致性", "SYS-IMP-010"],
  INT: ["06-外部集成与分布式顺序", "外部集成与分布式顺序", "SYS-IMP-011"],
  CLN: ["07-临床规则与主数据", "临床规则与主数据", "SYS-IMP-012"],
  REG: ["08-监管合规与追溯", "监管合规与追溯", "SYS-IMP-013"],
  ARCH: ["09-读模型渠道与全局架构", "读模型渠道与全局架构", "SYS-IMP-014"],
};

function walk(dir, acc = []) {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name);
    if (fs.statSync(p).isDirectory() && name !== "问题清单" && name !== "_tools") {
      walk(p, acc);
    } else if (name.startsWith("SYS-IMP-") && name.endsWith(".md")) {
      acc.push(p);
    }
  }
  return acc;
}

function gapPrefix(gapId) {
  if (gapId.startsWith("GAP-CLN-PRICE")) return "CLN";
  const m = gapId.match(/^GAP-([A-Z]+)-/);
  return m ? m[1] : "OTHER";
}

function parseOverview(text) {
  const meta = {};
  const lines = text.split(/\r?\n/);
  let inTable = false;
  for (const line of lines) {
    if (/^## 2\. 问题清单总览/.test(line)) inTable = true;
    else if (inTable && /^## /.test(line)) break;
    else if (inTable && line.startsWith("| GAP-")) {
      const parts = line.split("|").map((s) => s.trim());
      if (parts.length >= 7) {
        const gapId = parts[1].split(/\s+/)[0];
        meta[gapId] = {
          title_short: parts[2],
          severity: parts[3],
          scope: parts[4],
          priority: parts[5],
          status: parts[6],
        };
      }
    }
  }
  return meta;
}

function parseGapSections(text) {
  const re = /^### (GAP-[A-Z]+(?:-PRICE)?-\d+)\s+(.+)$/gm;
  const sections = [];
  let m;
  const matches = [];
  while ((m = re.exec(text)) !== null) matches.push(m);
  for (let i = 0; i < matches.length; i++) {
    const gapId = matches[i][1];
    const title = matches[i][2].trim();
    const start = matches[i].index + matches[i][0].length;
    const end = i + 1 < matches.length ? matches[i + 1].index : text.length;
    sections.push([gapId, title, text.slice(start, end).trim()]);
  }
  return sections;
}

function parseDimensionTable(body) {
  const fields = {};
  for (const line of body.split(/\r?\n/)) {
    if (!line.startsWith("|")) continue;
    const parts = line.split("|").map((s) => s.trim());
    if (parts.length >= 4 && parts[1] && parts[1] !== "维度" && !parts[1].startsWith("---")) {
      fields[parts[1].replace(/\*/g, "")] = parts[2];
    }
  }
  return fields;
}

function extractBlock(body, header) {
  const re = new RegExp(
    `\\*\\*${header.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}(?:（[^）]*）)?\\*\\*\\s*\\n([\\s\\S]*?)(?=\\n\\*\\*涉及代码\\*\\*|\\n\\*\\*验收用例\\*\\*|\\n\\*\\*工作量\\*\\*|\\n### |\\n## |$)`,
  );
  const m = body.match(re);
  return m ? m[1].trim() : "";
}

function extractSolution(body, tableSolution) {
  if (tableSolution && !/^见/.test(tableSolution) && tableSolution.length > 20) {
    return tableSolution;
  }
  for (const h of ["修改方案（推荐组合）", "修改方案（推荐）", "修改方案"]) {
    const block = extractBlock(body, h);
    if (block && block.length > 10) return block;
  }
  if (tableSolution) return tableSolution;
  return "";
}

function linkifyGaps(text) {
  return text.replace(/GAP-([A-Z]+(?:-PRICE)?-\d+)/g, (match) => {
    const prefix = gapPrefix(match);
    const [folderName] = PREFIX_META[prefix] || ["其他"];
    return `[${match}](../${folderName}/${match}.md)`;
  });
}

function buildIssueMd(gapId, title, body, overview, parentRel, problemType, parentImp) {
  const f = parseDimensionTable(body);
  const severity = f["严重度"] || overview.severity || "";
  const scope = f["门/住"] || f["门急诊"] || overview.scope || "";
  const priority = overview.priority || "";
  const status = f["状态"] || overview.status || "待实施";
  const ifLink = f["关联 IF/BR"] || f["关联"] || "";
  const solution = extractSolution(body, f["修改方案"]);
  const code = f["涉及代码"] || extractBlock(body, "涉及代码");
  const tests = f["验收用例"] || extractBlock(body, "验收用例");
  const effort = f["工作量"] || "";

  const lines = [
    `# ${gapId} ${title}`,
    "",
    `> **问题类型**：${problemType}`,
    `> **严重度**：${severity}`,
    `> **影响范围**：${scope}`,
    `> **优先级**：${priority}`,
    `> **状态**：${status}`,
    `> **父文档**：[${parentImp}](${parentRel})`,
  ];
  if (ifLink) lines.push(`> **关联 IF/BR**：${ifLink}`);
  lines.push("", "---", "");

  lines.push("## 1. 问题描述", "", overview.title_short || title, "");
  lines.push("## 2. 发生场景", "");
  if (f["典型时序"]) lines.push(f["典型时序"], "");
  else if (f["现象"]) lines.push(f["现象"], "");
  else lines.push(`在 ${scope || "业务"} 场景下，${title}。`, "");

  lines.push("## 3. 根因分析", "");
  lines.push(f["根因"] || "见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。", "");
  lines.push("## 4. 业务影响", "");
  lines.push(f["业务影响"] || f["现有兜底"] || "账实/账价不一致，或合规与安全风险。", "");
  lines.push("## 5. 修改方案", "");
  const sol = solution || `见 [父文档](${parentRel}) 修改方案章节。`;
  lines.push(linkifyGaps(sol), "");

  let section = 6;
  if (code) {
    lines.push("", `## ${section}. 涉及代码`, "", code, "");
    section++;
  }
  if (tests) {
    lines.push(`## ${section}. 验收用例`, "", tests, "");
    section++;
  }
  if (effort) {
    lines.push(`## ${section}. 工作量`, "", effort, "");
    section++;
  }

  lines.push(
    `## ${section}. 修订记录`,
    "",
    "| 版本 | 日期 | 说明 |",
    "| --- | --- | --- |",
    "| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |",
    "",
  );
  return lines.join("\n");
}

function main() {
  const impFiles = walk(ROOT);
  const allGaps = {};

  for (const file of impFiles) {
    const text = fs.readFileSync(file, "utf8");
    const overview = parseOverview(text);
    const sections = parseGapSections(text);
    const relParts = path.relative(ROOT, file).split(path.sep);
    const parentRel = "../" + relParts.join("/");
    const impMatch = path.basename(file).match(/SYS-IMP-(\d+)/);
    const parentImp = impMatch ? `SYS-IMP-${impMatch[1]}` : path.basename(file, ".md");

    for (const [gapId, title, body] of sections) {
      const ov = overview[gapId] || {};
      if (!allGaps[gapId] || body.length > allGaps[gapId].body.length) {
        allGaps[gapId] = { title, body, ov, parentRel, parentImp };
      }
    }
  }

  const indexRows = [];
  const gapIds = Object.keys(allGaps).sort();

  for (const gapId of gapIds) {
    const { title, body, ov, parentRel, parentImp } = allGaps[gapId];
    const prefix = gapPrefix(gapId);
    const [folderName, problemType] = PREFIX_META[prefix] || ["其他", "其他"];
    const outDir = path.join(OUT, folderName);
    fs.mkdirSync(outDir, { recursive: true });
    const outFile = path.join(outDir, `${gapId}.md`);
    fs.writeFileSync(
      outFile,
      buildIssueMd(gapId, title, body, ov, parentRel, problemType, parentImp),
      "utf8",
    );
    indexRows.push({
      id: gapId,
      title: ov.title_short || title,
      type: problemType,
      severity: ov.severity || "",
      priority: ov.priority || "",
      status: ov.status || "",
      path: `${folderName}/${gapId}.md`,
    });
  }

  const typeOrder = [
    "并发双写与条件更新",
    "Stale页面与跨角色",
    "权限越权与数据域",
    "流程状态机与硬门禁",
    "批处理与报表一致性",
    "外部集成与分布式顺序",
    "临床规则与主数据",
    "监管合规与追溯",
    "读模型渠道与全局架构",
  ];

  const byType = {};
  for (const r of indexRows) {
    (byType[r.type] = byType[r.type] || []).push(r);
  }

  const indexLines = [
    "# 问题清单总索引",
    "",
    "> 每条 GAP 对应独立问题单；**编号稳定**，成码后以 PR 关闭。",
    "> 生成：`_tools/generate_gap_issues.js`（从 SYS-IMP 父文档同步）。",
    "",
    `**合计**：${indexRows.length} 条`,
    "",
    "[← 返回系统完善总索引](../README.md)",
    "",
    "---",
    "",
    "## 按问题类型",
    "",
  ];

  for (const t of typeOrder) {
    const rows = byType[t];
    if (!rows) continue;
    const folder = rows[0].path.split("/")[0];
    indexLines.push(`### ${t}（${rows.length}）`, "", `目录：[${folder}/](./${folder}/)`, "");
    indexLines.push("| 编号 | 标题 | 严重度 | 优先级 | 状态 |");
    indexLines.push("| --- | --- | --- | --- | --- |");
    for (const r of rows) {
      indexLines.push(
        `| [${r.id}](./${r.path}) | ${r.title} | ${r.severity} | ${r.priority} | ${r.status} |`,
      );
    }
    indexLines.push("");
  }

  indexLines.push("---", "", "## 按编号（全表）", "");
  indexLines.push("| 编号 | 类型 | 标题 | 严重度 | 优先级 |");
  indexLines.push("| --- | --- | --- | --- | --- |");
  for (const r of indexRows) {
    indexLines.push(
      `| [${r.id}](./${r.path}) | ${r.type} | ${r.title} | ${r.severity} | ${r.priority} |`,
    );
  }
  indexLines.push("");

  fs.mkdirSync(OUT, { recursive: true });
  fs.writeFileSync(path.join(OUT, "README.md"), indexLines.join("\n"), "utf8");

  for (const t of typeOrder) {
    const rows = byType[t];
    if (!rows) continue;
    const folder = rows[0].path.split("/")[0];
    const catLines = [
      `# ${t}`,
      "",
      `共 **${rows.length}** 条问题。`,
      "",
      "| 编号 | 标题 | 严重度 | 优先级 | 状态 |",
      "| --- | --- | --- | --- | --- |",
    ];
    for (const r of rows) {
      catLines.push(
        `| [${r.id}](./${r.id}.md) | ${r.title} | ${r.severity} | ${r.priority} | ${r.status} |`,
      );
    }
    catLines.push("", "[← 问题清单总索引](../README.md)", "");
    fs.writeFileSync(path.join(OUT, folder, "README.md"), catLines.join("\n"), "utf8");
  }

  console.log(`Created ${indexRows.length} issue files in ${OUT}`);
}

main();
