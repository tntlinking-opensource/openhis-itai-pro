/**
 * 入院记录模版：emrDataElement → emrMetadataChip / emrFreeText（方案 B 组件）
 * 用法：npx tsx src/emrEditor/migration/adapt-template-to-plan-b-nodes.ts template.json
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import type { JSONContent } from '@tiptap/core';

/** 只读患者/文书元数据：用 emrMetadataChip（与 emr-template-migration Skill §A4 白名单对齐） */
const METADATA_FIELD_KEYS = new Set([
  'admissionDateTime',
  'age',
  'attendingPhysician',
  'bedNo',
  'birthPlace',
  'deptName',
  'doctorName',
  'ethnicity',
  'gender',
  'historyProvider',
  'idCard',
  'inpatientNo',
  'insuranceName',
  'maritalStatus',
  'medicalRecordNo',
  'occupation',
  'orgName',
  'patientName',
  'phone',
  'recordTime',
  'reliability',
  'solarTerm',
  'wardName',
]);

/**
 * 控件岛：编辑器已支持，迁移时必须保留为 emrDataElement，禁止转 chip/freeText。
 * 与 data-element-types.ts EMR_DATA_ELEMENT_WIDGETS 中非 text-like 对齐。
 */
const CONTROL_WIDGETS = new Set(['select', 'radio', 'date', 'checkbox', 'number']);

interface AdaptReport {
  metadataChip: number;
  freeText: number;
  keptDataElement: number;
}

function isInPatientHeader(ctx: { inPatientHeader: boolean }): boolean {
  return ctx.inPatientHeader;
}

function toMetadataChip(attrs: Record<string, unknown>): JSONContent {
  const elementId = String(attrs.elementId ?? '').trim();
  return {
    type: 'emrMetadataChip',
    attrs: {
      slotId: String(attrs.slotId ?? ''),
      fieldKey: elementId,
      label: String(attrs.labelSnapshot ?? attrs.placeholder ?? elementId),
      value: String(attrs.value ?? ''),
      editable: attrs.editable !== false,
    },
  };
}

function toFreeText(attrs: Record<string, unknown>): JSONContent {
  const label = String(attrs.labelSnapshot ?? attrs.placeholder ?? '填写');
  return {
    type: 'emrFreeText',
    attrs: {
      slotId: String(attrs.slotId ?? ''),
      label,
      placeholder: String(attrs.placeholder ?? label),
      value: String(attrs.value ?? ''),
      required: attrs.required === true,
      requiredMessage: String(attrs.requiredMessage ?? ''),
    },
  };
}

function adaptNode(node: JSONContent, ctx: { inPatientHeader: boolean }, report: AdaptReport): JSONContent {
  if (node.type === 'table' && node.attrs?.name === 'patientHeader') {
    const nextCtx = { inPatientHeader: true };
    const content = Array.isArray(node.content)
      ? node.content.map((c) => adaptNode(c, nextCtx, report))
      : node.content;
    return { ...node, content };
  }

  if (node.type === 'emrDataElement') {
    const attrs = (node.attrs ?? {}) as Record<string, unknown>;
    const widget = String(attrs.widgetOverride ?? 'text').trim() || 'text';
    const elementId = String(attrs.elementId ?? '').trim();

    // 日期/下拉/单选/复选/数字：保留控件岛（病历中应继续使用这些元素）
    if (CONTROL_WIDGETS.has(widget)) {
      report.keptDataElement += 1;
      return node;
    }

    if (widget === 'multiline') {
      report.freeText += 1;
      return toFreeText(attrs);
    }

    if (isInPatientHeader(ctx) || METADATA_FIELD_KEYS.has(elementId)) {
      report.metadataChip += 1;
      return toMetadataChip(attrs);
    }

    report.freeText += 1;
    return toFreeText(attrs);
  }

  if (!Array.isArray(node.content)) return node;
  return {
    ...node,
    content: node.content.map((c) => adaptNode(c, ctx, report)),
  };
}

function normalizePageChrome(doc: Record<string, unknown>): void {
  const chrome = (doc.emrPageChrome ?? {}) as Record<string, unknown>;
  const header = (chrome.header ?? {}) as Record<string, unknown>;
  header.enabled = true;
  header.align = header.align ?? 'center';
  header.bold = header.bold ?? false;
  header.italic = header.italic ?? false;
  header.underline = header.underline ?? false;
  // 0 = 运行时 DOM 实测；勿默认写死 28/40（会造成页眉与正文空隙不均）
  header.heightMm = header.heightMm ?? 0;
  chrome.header = header;

  const footer = (chrome.footer ?? {}) as Record<string, unknown>;
  footer.enabled = footer.enabled ?? true;
  footer.align = footer.align ?? 'center';
  footer.bold = footer.bold ?? false;
  footer.italic = footer.italic ?? false;
  footer.underline = footer.underline ?? false;
  footer.heightMm = footer.heightMm ?? 12;
  chrome.footer = footer;

  doc.emrPageChrome = chrome;
}

export function adaptTemplateToPlanBNodes(raw: unknown): { doc: JSONContent; report: AdaptReport } {
  const doc = structuredClone(raw) as JSONContent & Record<string, unknown>;
  const report: AdaptReport = { metadataChip: 0, freeText: 0, keptDataElement: 0 };

  if (Array.isArray(doc.content)) {
    doc.content = doc.content.map((n) => adaptNode(n, { inPatientHeader: false }, report));
  }

  normalizePageChrome(doc);

  return { doc, report };
}

function main() {
  const file = resolve(process.argv[2] ?? 'template.json');
  const raw = JSON.parse(readFileSync(file, 'utf8')) as unknown;
  const { doc, report } = adaptTemplateToPlanBNodes(raw);
  writeFileSync(file, `${JSON.stringify(doc, null, 2)}\n`, 'utf8');
  console.log(`✓ ${file}`);
  console.log(`  emrMetadataChip: ${report.metadataChip}`);
  console.log(`  emrFreeText: ${report.freeText}`);
  console.log(`  kept emrDataElement: ${report.keptDataElement}`);
}

const isDirectRun =
  typeof process.argv[1] === 'string' &&
  /adapt-template-to-plan-b-nodes\.(ts|js|mjs|cjs)$/.test(process.argv[1].replace(/\\/g, '/'));

if (isDirectRun) {
  main();
}