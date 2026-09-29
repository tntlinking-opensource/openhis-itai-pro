/**
 * 门诊病历定稿版式：以 apps/web-ele/src/template.sjon 为表格骨架，
 * 仅替换排版，保留原文档字段值（元数据 / 自由文本 / 日期控件）。
 */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import type { JSONContent } from '@tiptap/core';

import { extractFormTitleText } from './ensure-page-chrome-p0';
import { migrateEmrTemplate } from './migrate-emr-template';

export type FieldBag = Record<string, string>;

const GOLD_TEMPLATE_PATH = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../template.sjon',
);

/** 灌值时按骨架节点 fieldKey/label 回查 bag 的别名 */
const VALUE_ALIASES: Record<string, string[]> = {
  patientName: ['patientName', '患者姓名', '姓名'],
  gender: ['gender', '性别'],
  age: ['age', '年龄'],
  insuranceName: ['insuranceName', '医保', '费别', '费用性质'],
  phone: ['phone', '电话', '联系电话'],
  medicalRecordNo: ['medicalRecordNo', '病历号'],
  visitTime: ['visitTime', '就诊日期', '就诊时间'],
  deptName: ['deptName', '科室', '就诊科室'],
  doctorName: ['doctorName', '接诊医生', '医师', '书写医师'],
  doctorSignDate: ['doctorSignDate', '医生签字日期', '日期', 'visitTime', '就诊日期'],
  chiefComplaint: ['chiefComplaint', '主诉'],
  presentIllness: ['presentIllness', '现病史'],
  pastHistory: ['pastHistory', '既往史'],
  allergyHistory: ['allergyHistory', '过敏史'],
  familyHistory: ['familyHistory', '家族史'],
  personalHistory: ['personalHistory', '个人史'],
  temperature: ['temperature', '体温', 'T'],
  pulse: ['pulse', '脉搏', 'P'],
  respiration: ['respiration', '呼吸', 'R'],
  bloodPressure: ['bloodPressure', '血压', 'BP'],
  physicalExam: ['physicalExam', '体格检查', '体查描述', '查体'],
  auxiliaryExam: ['auxiliaryExam', '辅助检查'],
  tcmDiagnosis: ['tcmDiagnosis', '中医诊断'],
  westernDiagnosis: ['westernDiagnosis', '西医诊断', 'diagnosisText', '诊断', '印象诊断'],
  disposal: ['disposal', '处置'],
};

let cachedGold: (JSONContent & Record<string, unknown>) | null = null;

function loadGoldTemplate(): JSONContent & Record<string, unknown> {
  if (cachedGold) return structuredClone(cachedGold);
  const raw = readFileSync(GOLD_TEMPLATE_PATH, 'utf8');
  cachedGold = JSON.parse(raw) as JSONContent & Record<string, unknown>;
  return structuredClone(cachedGold);
}

function walkFindLogo(nodes: JSONContent[] | undefined): string | null {
  for (const n of nodes ?? []) {
    if (n.type === 'image' && n.attrs?.title === 'hospitalLogo') {
      return String(n.attrs.src ?? '');
    }
    const nested = walkFindLogo(n.content);
    if (nested) return nested;
  }
  return null;
}

function extractLogoFromChromeTemplate(tpl: string): string | null {
  const m = tpl.match(/\{\{emrImg[^:]*:([^}]+)\}\}/);
  return m?.[1]?.trim() || null;
}

function buildOutpatientHeaderTemplate(
  logoSrc: string | null,
  existingTpl: string,
  formTitleLiteral: string,
): string {
  const imgPart = logoSrc
    ? `{{emrImg w=39:${logoSrc}}}`
    : existingTpl.match(/\{\{emrImg[^}]+\}\}/)?.[0] ?? '';
  const nl = '\n';
  // 优先保留金标准 {documentTitle}；旧文若只有字面「门诊病历」也落到 documentTitle
  const titlePart = formTitleLiteral && formTitleLiteral !== '门诊病历'
    ? `<span style="font-size:16pt">${formTitleLiteral}</span>`
    : `<span style="font-size:16pt">{documentTitle}</span>`;
  return (
    `${imgPart}<span style="font-size:22pt;font-weight:700">{orgName}</span>${nl}` + titlePart
  );
}

/** 从旧 JSON 提取门诊侧车（院徽 + 页眉页脚） */
export function buildOutpatientSidecarFromRaw(raw: JSONContent & Record<string, unknown>): {
  emrPageChrome: Record<string, unknown>;
  emrPageMargins: Record<string, string>;
} {
  const content = Array.isArray(raw.content) ? raw.content : [];
  const chrome = (raw.emrPageChrome ?? {}) as Record<string, unknown>;
  const header = { ...(chrome.header as Record<string, unknown> | undefined) };
  const existingTpl = String(header.template ?? '');
  const logoSrc =
    walkFindLogo(content) || extractLogoFromChromeTemplate(existingTpl) || null;
  const formTitleLiteral = extractFormTitleText(content) || '门诊病历';

  header.enabled = true;
  header.align = header.align ?? 'center';
  header.heightMm = 0;
  header.bold = header.bold ?? false;
  header.italic = header.italic ?? false;
  header.underline = header.underline ?? false;
  header.template = buildOutpatientHeaderTemplate(logoSrc, existingTpl, formTitleLiteral);

  const footer = { ...(chrome.footer as Record<string, unknown> | undefined) };
  footer.enabled = footer.enabled ?? true;
  footer.align = footer.align ?? 'center';
  footer.heightMm = footer.heightMm ?? 16;
  footer.bold = footer.bold ?? false;
  footer.italic = footer.italic ?? false;
  footer.underline = footer.underline ?? false;
  footer.template =
    footer.template ||
    '第 {page} 页 (共 {total} 页)\n为了您和家人的健康，请勿吸烟！如需戒烟帮助，请咨询戒烟医生。';

  const margins = (raw.emrPageMargins as Record<string, string> | undefined) ?? {
    top: '25.4mm',
    right: '31.8mm',
    bottom: '25.4mm',
    left: '31.8mm',
  };

  return {
    emrPageChrome: { ...chrome, header, footer },
    emrPageMargins: margins,
  };
}

export function walkCollect(node: JSONContent, bag: FieldBag): void {
  const t = node.type ?? '';
  if (t === 'emrMetadataChip' || t === 'emrFreeText' || t === 'emrDataElement') {
    const a = node.attrs ?? {};
    const val = String(a.value ?? '').trim();
    if (val) {
      for (const key of [
        a.fieldKey,
        a.elementId,
        a.label,
        a.labelSnapshot,
      ]) {
        const k = String(key ?? '').trim();
        if (k) bag[k] = val;
      }
    }
  }
  for (const c of node.content ?? []) walkCollect(c, bag);
}

export function pick(bag: FieldBag, ...keys: string[]): string {
  for (const k of keys) {
    const v = bag[k];
    if (v) return v;
  }
  return '';
}

function resolveValue(bag: FieldBag, ...keys: string[]): string {
  for (const k of keys) {
    const aliases = VALUE_ALIASES[k];
    if (aliases) {
      const v = pick(bag, ...aliases);
      if (v) return v;
    }
    const direct = pick(bag, k);
    if (direct) return direct;
  }
  return '';
}

/** 把 bag 中的值灌进金标准骨架（按 fieldKey / elementId / label） */
export function applyBagToGoldDoc(doc: JSONContent, bag: FieldBag): void {
  const visit = (node: JSONContent) => {
    const t = node.type ?? '';
    if (t === 'emrMetadataChip' || t === 'emrFreeText' || t === 'emrDataElement') {
      const a = (node.attrs ??= {});
      const keys = [
        String(a.fieldKey ?? '').trim(),
        String(a.elementId ?? '').trim(),
        String(a.label ?? '').trim(),
        String(a.labelSnapshot ?? '').trim(),
      ].filter(Boolean);
      const val = resolveValue(bag, ...keys);
      if (val) a.value = val;
    }
    for (const c of node.content ?? []) visit(c);
  };
  visit(doc);
}

/**
 * 按 template.sjon 表格结构重建门诊病历（保留 bag 值 + 原侧车院徽）
 */
export function buildCleanOutpatientDoc(
  bag: FieldBag,
  sidecar: { emrPageChrome?: unknown; emrPageMargins?: unknown },
): JSONContent {
  const doc = loadGoldTemplate();
  applyBagToGoldDoc(doc, bag);
  doc.emrPageChrome = sidecar.emrPageChrome;
  doc.emrPageMargins = sidecar.emrPageMargins;
  return migrateEmrTemplate(doc).doc;
}

/** 从任意旧门诊 JSON 一键重建为 template.sjon 表格版式 */
export function rebuildOutpatientFromRaw(raw: unknown): JSONContent {
  const parsed = structuredClone(raw) as JSONContent & Record<string, unknown>;
  const bag: FieldBag = {};
  walkCollect(parsed, bag);
  const sidecar = buildOutpatientSidecarFromRaw(parsed);
  return buildCleanOutpatientDoc(bag, sidecar);
}
