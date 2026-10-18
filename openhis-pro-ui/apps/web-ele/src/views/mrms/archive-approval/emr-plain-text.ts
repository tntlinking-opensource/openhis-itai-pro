/**
 * 文书 JSON → 纯文本（审批对比用，不依赖编辑器）。
 * 同时识别 canvas-editor 信封和历史 TipTap doc。
 */

type JsonNode = {
  attrs?: Record<string, unknown>;
  content?: JsonNode[];
  text?: string;
  type?: string;
};

const BLOCK_BREAK_TYPES = new Set([
  'paragraph',
  'heading',
  'blockquote',
  'listItem',
  'tableRow',
  'horizontalRule',
  'emrPageBreak',
]);

const ATOM_VALUE_TYPES = new Set([
  'emrFreeText',
  'emrDataElement',
  'emrMetadataChip',
  'emrTextField',
  'emrSelectField',
  'emrFillableField',
]);

/** 解析 content 字符串/对象为 JSON 根节点 */
export function parseEmrContentJson(raw: unknown): JsonNode | null {
  if (raw == null) return null;
  if (typeof raw === 'object') return raw as JsonNode;
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim();
  if (!trimmed) return null;
  try {
    return JSON.parse(trimmed) as JsonNode;
  } catch {
    // 非 JSON 当纯文本
    return { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: trimmed }] }] };
  }
}

/** 画布控件里不参与对比的装饰片段：括号、占位、前后缀 */
const CANVAS_SKIP_COMPONENTS = new Set([
  'prefix',
  'postfix',
  'placeholder',
  'preText',
  'postText',
]);

type AnyRecord = Record<string, unknown>;

function isRecord(value: unknown): value is AnyRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function normalizePlainText(text: string): string {
  return text
    .replace(/\u200B/g, '')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

/** canvas-editor 信封，或直接存的 { header, main, footer } */
function isCanvasDocument(root: JsonNode): boolean {
  const rec = root as AnyRecord;
  if (rec.engine === 'canvas-editor' && isRecord(rec.data) && Array.isArray(rec.data.main)) {
    return true;
  }
  if (root.type === 'doc') return false;
  return Array.isArray(rec.main);
}

function canvasDataOf(root: JsonNode): AnyRecord {
  const rec = root as AnyRecord;
  if (rec.engine === 'canvas-editor' && isRecord(rec.data)) return rec.data;
  return rec;
}

/** 未展开的控件：已填值优先，否则用选项 code，空控件保留占位 */
function compactControlText(control: AnyRecord): string {
  if (Array.isArray(control.value) && control.value.length > 0) {
    const inner: string[] = [];
    appendCanvasElements(control.value, inner);
    return inner.join('');
  }
  const code = control.code;
  const sets = Array.isArray(control.valueSets) ? control.valueSets : [];
  if (code != null && String(code).trim()) {
    const labels = String(code)
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
      .map((item) => {
        const hit = sets.find((set) => isRecord(set) && String(set.code) === item);
        return hit && isRecord(hit) ? String(hit.value ?? item) : item;
      });
    if (labels.length) return labels.join('、');
  }
  const placeholder = String(control.placeholder ?? '').trim();
  return placeholder ? `【${placeholder}】` : '';
}

function appendCanvasElements(elements: unknown, out: string[]) {
  if (!Array.isArray(elements)) return;
  for (const el of elements) {
    if (!isRecord(el)) continue;
    const component = String(el.controlComponent ?? '');
    if (CANVAS_SKIP_COMPONENTS.has(component)) continue;

    const type = String(el.type ?? '');
    if (type === 'table' && Array.isArray(el.trList)) {
      for (const tr of el.trList) {
        if (!isRecord(tr) || !Array.isArray(tr.tdList)) continue;
        const cells: string[] = [];
        for (const td of tr.tdList) {
          if (!isRecord(td)) continue;
          const cellOut: string[] = [];
          appendCanvasElements(td.value, cellOut);
          const cellText = cellOut.join('').replace(/\s+/g, ' ').trim();
          if (cellText) cells.push(cellText);
        }
        if (cells.length) {
          out.push(cells.join('  '));
          out.push('\n');
        }
      }
      continue;
    }
    if (type === 'pageBreak' || type === 'separator') {
      out.push('\n');
      continue;
    }
    if (type === 'image' || type === 'latex') continue;
    if (Array.isArray(el.valueList) && el.valueList.length > 0) {
      appendCanvasElements(el.valueList, out);
      continue;
    }
    if (type === 'control' && !component && isRecord(el.control)) {
      const text = compactControlText(el.control);
      if (text) out.push(text);
      continue;
    }
    if (type === 'checkbox' && isRecord(el.checkbox)) {
      out.push(el.checkbox.value === true ? '☑' : '☐');
      continue;
    }
    const text = String(el.value ?? '');
    if (!text) continue;
    out.push(text);
  }
}

function renderCanvasDocument(root: JsonNode): string {
  const data = canvasDataOf(root);
  const out: string[] = [];
  appendCanvasElements(data.header, out);
  if (out.length > 0 && !out[out.length - 1]?.endsWith('\n')) {
    out.push('\n');
  }
  appendCanvasElements(data.main, out);
  const footer: string[] = [];
  appendCanvasElements(data.footer, footer);
  const footerText = footer.join('').trim();
  if (footerText) {
    if (out.length > 0 && !out[out.length - 1]?.endsWith('\n')) out.push('\n');
    out.push(footerText);
  }
  return out.join('');
}

/**
 * 从文书/模板 JSON 提取可读纯文本。
 * Canvas：页眉、正文、表格单元格、填写值；历史 TipTap：填写岛 attrs.value，块级换行。
 */
export function extractEmrPlainText(raw: unknown): string {
  const root = parseEmrContentJson(raw);
  if (!root) return '';
  if (isCanvasDocument(root)) {
    return normalizePlainText(renderCanvasDocument(root));
  }
  const lines: string[] = [];
  walk(root, lines);
  return normalizePlainText(lines.join(''));
}

function walk(node: JsonNode, out: string[]) {
  const type = node.type ?? '';
  if (type === 'text') {
    out.push(String(node.text ?? ''));
    return;
  }
  if (type === 'hardBreak') {
    out.push('\n');
    return;
  }
  if (ATOM_VALUE_TYPES.has(type)) {
    const label = String(node.attrs?.label ?? node.attrs?.fieldKey ?? '').trim();
    const value = String(node.attrs?.value ?? '').trim();
    if (label && value) {
      out.push(`${label}：${value}`);
    } else if (value) {
      out.push(value);
    } else if (label) {
      out.push(`【${label}】`);
    }
    return;
  }
  if (Array.isArray(node.content)) {
    for (const child of node.content) {
      walk(child, out);
    }
  }
  if (BLOCK_BREAK_TYPES.has(type)) {
    out.push('\n');
  }
}

