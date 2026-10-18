#!/usr/bin/env node
/**
 * 将 参考/ 目录下 PDF、XLS 转为 Markdown（供 AI 阅读）
 *
 * 用法（在 参考/_convert_tmp 已 npm install 后）：
 *   cd whale-doc/whale-module-elep-jilin/参考/_convert_tmp
 *   node ../convert-to-md.js
 *
 * 依赖：pdf-parse@1.1.1、xlsx（见 _convert_tmp/package.json）
 */
const fs = require("fs");
const path = require("path");

const refDir = path.resolve(__dirname);
const tmpDir = path.join(refDir, "_convert_tmp");
const pdf = require(path.join(tmpDir, "node_modules/pdf-parse"));
const XLSX = require(path.join(tmpDir, "node_modules/xlsx"));

function sanitize(name) {
  return name
    .replace(/\.(pdf|xls|xlsx)$/i, "")
    .replace(/[\\/:*?"<>|()（）\s]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}

function sheetToMd(rows) {
  const clean = rows.map((r) =>
    (r || []).map((c) =>
      (c == null ? "" : String(c)).replace(/\|/g, "\\|").replace(/\n/g, " "),
    ),
  );
  if (!clean.length) return "_（空表）_\n";
  const width = Math.max(...clean.map((r) => r.length));
  const norm = clean.map((r) => {
    while (r.length < width) r.push("");
    return r;
  });
  const header = norm[0];
  const sep = header.map(() => "---");
  const body = norm
    .slice(1)
    .map((r) => "| " + r.join(" | ") + " |")
    .join("\n");
  return (
    "| " +
    header.join(" | ") +
    " |\n| " +
    sep.join(" | ") +
    " |\n" +
    body +
    (body ? "\n" : "")
  );
}

async function convertPdf(filePath) {
  const buf = fs.readFileSync(filePath);
  const data = await pdf(buf);
  const base = path.basename(filePath);
  const title = base.replace(/\.pdf$/i, "");
  let text = (data.text || "").replace(/\r\n/g, "\n");
  const trimmed = text.replace(/\s/g, "");
  if (trimmed.length < 50) {
    const outName = sanitize(base) + ".md";
    return {
      outName,
      md: `# ${title}

> **来源**：\`${base}\`
> **页数**：${data.numpages}
> **转换状态**：⚠️ 扫描件或无文字层，文本提取为空；请 OCR 或人工补全（见同目录 OCR 说明）

---

_（自动提取无正文，请阅读 PDF 或运行 ocr-pdf.mjs）_
`,
      scanned: true,
    };
  }
  text = text
    .split("\n")
    .map((l) => l.trimEnd())
    .join("\n");
  text = text.replace(/\n{3,}/g, "\n\n");
  return {
    outName: sanitize(base) + ".md",
    md: `# ${title}

> **来源**：\`${base}\`
> **页数**：${data.numpages}
> **转换**：自动提取（供 AI 阅读，细则以 PDF 为准）

---

${text.trim()}
`,
    scanned: false,
  };
}

function convertXls(filePath) {
  const wb = XLSX.readFile(filePath, { cellDates: true });
  const base = path.basename(filePath);
  const title = base.replace(/\.xls(x)?$/i, "");
  let md = `# ${title}

> **来源**：\`${base}\`
> **转换**：自动提取（**含密钥等敏感信息，勿提交公开仓库**）

---

`;
  for (const name of wb.SheetNames) {
    const sheet = wb.Sheets[name];
    const rows = XLSX.utils.sheet_to_json(sheet, {
      header: 1,
      defval: "",
      raw: false,
    });
    md += `## Sheet：${name}\n\n`;
    md += sheetToMd(rows) + "\n\n";
  }
  return { outName: sanitize(base) + ".md", md, scanned: false };
}

(async () => {
  if (!fs.existsSync(tmpDir)) {
    console.error("请先: cd 参考/_convert_tmp && npm install pdf-parse@1.1.1 xlsx");
    process.exit(1);
  }
  const files = fs
    .readdirSync(refDir)
    .filter((f) => /\.(pdf|xls|xlsx)$/i.test(f));
  const index = [];
  for (const f of files) {
    const fp = path.join(refDir, f);
    console.log("Converting:", f);
    let result;
    if (/\.pdf$/i.test(f)) result = await convertPdf(fp);
    else result = convertXls(fp);
    const outPath = path.join(refDir, result.outName);
    fs.writeFileSync(outPath, result.md, "utf8");
    index.push({
      source: f,
      md: result.outName,
      bytes: fs.statSync(outPath).size,
      scanned: result.scanned || false,
    });
  }
  const idxMd = `# ELEP 参考文档索引

> 由 PDF/XLS 自动转换，便于 AI 阅读。原始文件保留在同目录。

## 文件

| 原始文件 | Markdown | 说明 |
|----------|----------|------|
${index
  .map(
    (i) =>
      `| ${i.source} | [${i.md}](./${i.md}) | ${i.scanned ? "⚠️ 扫描件待 OCR" : "✅ 已提取"} |`,
  )
  .join("\n")}

## 安全

- \`电子处方正式环境秘钥.md\` 含 **生产密钥**，勿 push 到公开 git
- 配置写入 \`elep-contract.properties\` 时使用院点实际机构名（xls 中为「朝阳区第二中医院」，请与 \`H22010402403\` 核对）

## 重新转换

\`\`\`powershell
cd whale-doc/whale-module-elep-jilin/参考/_convert_tmp
npm install pdf-parse@1.1.1 xlsx
node ../convert-to-md.js
\`\`\`

扫描件 OCR（可选）：\`node ocr-pdf.mjs\`（需 canvas、pdfjs-dist、tesseract.js）
`;
  fs.writeFileSync(path.join(refDir, "README.md"), idxMd, "utf8");
  console.log("Done:", index.length, "files");
})();
