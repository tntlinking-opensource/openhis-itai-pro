/** 将编辑器 getHTML() 片段包装为可供 electron-hiprint 渲染的完整 HTML 文档 */

export interface EmrEditorPrintMargins {
  top?: string;
  right?: string;
  bottom?: string;
  left?: string;
}

const DEFAULT_MARGINS: Required<EmrEditorPrintMargins> = {
  top: '25.4mm',
  right: '31.8mm',
  bottom: '25.4mm',
  left: '31.8mm',
};

function escapeHtml(text: string): string {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

/** 内联打印样式（与 SimpleEditor 屏显/打印规则对齐的子集） */
function buildPrintStyles(margins: Required<EmrEditorPrintMargins>): string {
  return `
@page { size: A4 portrait; margin: 0; }
* { box-sizing: border-box; }
body {
  margin: 0;
  padding: 0;
  font-family: SimSun, "Songti SC", "Noto Serif CJK SC", serif;
  font-size: 12pt;
  line-height: 1.5;
  color: #1f2937;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}
.emr-print-page {
  width: 210mm;
  min-height: 297mm;
  margin: 0 auto;
  padding: ${margins.top} ${margins.right} ${margins.bottom} ${margins.left};
  background: #fff;
}
.emr-print-page p { margin: 0.5em 0; }
.emr-print-page h1, .emr-print-page h2, .emr-print-page h3 {
  margin: 0.75em 0 0.35em;
  font-weight: 700;
}
.emr-print-page table {
  border-collapse: collapse;
  table-layout: fixed;
  width: 100%;
  margin: 0.75em 0;
}
.emr-print-page th, .emr-print-page td {
  border: 1px solid #374151;
  padding: 6px 8px;
  vertical-align: top;
}
.emr-print-page th {
  background: #f3f4f6;
  font-weight: 600;
}
.emr-print-page .emr-page-break {
  break-after: page;
  page-break-after: always;
  height: 0;
  margin: 0;
  border: none;
}
.emr-print-page img { max-width: 100%; height: auto; }
`;
}

export function buildEmrEditorPrintDocument(options: {
  bodyHtml: string;
  title?: string;
  margins?: EmrEditorPrintMargins;
}): string {
  const body = (options.bodyHtml ?? '').trim();
  const margins = {
    ...DEFAULT_MARGINS,
    ...options.margins,
  };
  const title = escapeHtml((options.title ?? '病历').trim() || '病历');
  const styles = buildPrintStyles(margins);

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${title}</title>
  <style>${styles}</style>
</head>
<body>
  <div class="emr-print-page ProseMirror">${body}</div>
</body>
</html>`;
}
