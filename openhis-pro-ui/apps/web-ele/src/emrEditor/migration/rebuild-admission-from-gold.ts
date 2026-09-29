/**
 * 按「董复刻」入院记录骨架重建其它入院记录：只换版式（去硬分页），按 label/fieldKey 灌回原值。
 * 不要用 slotId 对值（路径随顶层块增减会错位，例如缺「现病史」时主诉会被灌错）。
 */
import type { JSONContent } from '@tiptap/core';

const FILLABLE_TYPES = new Set(['emrMetadataChip', 'emrFreeText', 'emrDataElement']);

export type AdmissionFieldBag = Record<string, string>;

function walkNodes(nodes: JSONContent[] | undefined, fn: (node: JSONContent) => void): void {
  for (const node of nodes ?? []) {
    fn(node);
    walkNodes(node.content, fn);
  }
}

/** 统计顶层+嵌套 emrPageBreak 个数 */
export function countEmrPageBreaks(doc: JSONContent): number {
  let n = 0;
  walkNodes(doc.content, (node) => {
    if (node.type === 'emrPageBreak') n += 1;
  });
  return n;
}

function fillableKeys(attrs: Record<string, unknown>): string[] {
  return [attrs.label, attrs.labelSnapshot, attrs.fieldKey, attrs.elementId]
    .map((k) => String(k ?? '').trim())
    .filter(Boolean);
}

function flattenCellPlainText(cell: JSONContent): string {
  const parts: string[] = [];
  walkNodes([cell], (node) => {
    if (node.type === 'text') parts.push(String(node.text ?? ''));
  });
  return parts.join('').trim();
}

/** 从旧入院记录抽出可填域值；表格格内纯文本用 __cell:表名:行:列 兜底 */
export function collectAdmissionBag(doc: JSONContent): AdmissionFieldBag {
  const bag: AdmissionFieldBag = {};
  walkNodes(doc.content, (node) => {
    if (!FILLABLE_TYPES.has(node.type ?? '')) return;
    const attrs = (node.attrs ?? {}) as Record<string, unknown>;
    const val = String(attrs.value ?? '').trim();
    if (!val) return;
    for (const key of fillableKeys(attrs)) bag[key] = val;
  });
  walkNodes(doc.content, (node) => {
    if (node.type !== 'table') return;
    const tableName = String(node.attrs?.name ?? '').trim() || 'table';
    const rows = node.content ?? [];
    rows.forEach((row, r) => {
      (row.content ?? []).forEach((cell, c) => {
        const plain = flattenCellPlainText(cell);
        if (plain) bag[`__cell:${tableName}:${r}:${c}`] = plain;
      });
    });
  });
  return bag;
}

function pickBag(bag: AdmissionFieldBag, ...keys: string[]): string {
  for (const key of keys) {
    const val = bag[key];
    if (val) return val;
  }
  return '';
}

/** 清空骨架上的填写值，避免把金标准患者的正文抄到别人病历里 */
export function clearAdmissionFillableValues(doc: JSONContent): void {
  walkNodes(doc.content, (node) => {
    if (!FILLABLE_TYPES.has(node.type ?? '')) return;
    const attrs = node.attrs;
    if (!attrs) return;
    attrs.value = '';
  });
}

type TableWalkCtx = { col: number; row: number; tableName: string };

function walkWithTableCtx(
  nodes: JSONContent[] | undefined,
  ctx: TableWalkCtx | null,
  fn: (node: JSONContent, ctx: TableWalkCtx | null) => void,
): void {
  for (const node of nodes ?? []) {
    fn(node, ctx);
    if (node.type === 'table') {
      const tableName = String(node.attrs?.name ?? '').trim() || 'table';
      (node.content ?? []).forEach((row, r) => {
        (row.content ?? []).forEach((cell, c) => {
          walkWithTableCtx(cell.content, { tableName, row: r, col: c }, fn);
        });
      });
      continue;
    }
    walkWithTableCtx(node.content, ctx, fn);
  }
}

/** 按 label / fieldKey 把旧值灌进已清空的金标准骨架 */
export function applyAdmissionBag(doc: JSONContent, bag: AdmissionFieldBag): void {
  walkWithTableCtx(doc.content, null, (node, ctx) => {
    if (!FILLABLE_TYPES.has(node.type ?? '')) return;
    const attrs = (node.attrs ??= {}) as Record<string, unknown>;
    const keys = fillableKeys(attrs);
    if (ctx != null) keys.push(`__cell:${ctx.tableName}:${ctx.row}:${ctx.col}`);
    const val = pickBag(bag, ...keys);
    if (val) attrs.value = val;
  });
}

export interface RebuildAdmissionReport {
  bagKeys: number;
  goldFillable: number;
  pageBreaksBefore: number;
  unmatchedLabels: string[];
}

function listGoldFillableLabels(doc: JSONContent): string[] {
  const labels: string[] = [];
  walkNodes(doc.content, (node) => {
    if (!FILLABLE_TYPES.has(node.type ?? '')) return;
    const attrs = (node.attrs ?? {}) as Record<string, unknown>;
    const label = String(attrs.label ?? attrs.labelSnapshot ?? attrs.fieldKey ?? '').trim();
    if (label) labels.push(label);
  });
  return labels;
}

/**
 * 克隆金标准骨架 → 清空值 → 灌入旧文书 bag。
 * 页眉侧车沿用金标准（病房/床位 float），运行时按就诊元数据回填姓名住院号。
 */
export function rebuildAdmissionFromGold(
  raw: JSONContent,
  gold: JSONContent,
): { doc: JSONContent; report: RebuildAdmissionReport } {
  const bag = collectAdmissionBag(raw);
  const doc = structuredClone(gold);
  const pageBreaksBefore = countEmrPageBreaks(raw);
  clearAdmissionFillableValues(doc);
  applyAdmissionBag(doc, bag);
  const goldLabels = listGoldFillableLabels(gold);
  const unmatchedLabels = goldLabels.filter((label) => {
    const val = pickBag(bag, label);
    return !val;
  });
  return {
    doc,
    report: {
      bagKeys: Object.keys(bag).length,
      goldFillable: goldLabels.length,
      pageBreaksBefore,
      unmatchedLabels,
    },
  };
}
