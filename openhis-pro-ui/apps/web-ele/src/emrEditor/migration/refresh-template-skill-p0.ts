/**
 * Skill P0：控件升级 + §A4 系统可取纠错 + placeholder 规范化（§A2 / §A3 / §A4）
 * 用法：npx tsx src/emrEditor/migration/refresh-template-skill-p0.ts template.json [...]
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import type { JSONContent } from '@tiptap/core';

const DATE_FIELD_KEYS = new Set(['recordTime', 'admissionDateTime']);
const NUMBER_VITAL_LABELS = new Set(['体温', '脉搏', '呼吸', '血压']);

/** fieldKey 白名单（Skill §A） */
const METADATA_FIELD_KEYS = new Set([
  'admissionDateTime',
  'age',
  'attendingPhysician',
  'birthPlace',
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
  'patientName',
  'phone',
  'recordTime',
  'reliability',
  'solarTerm',
  'deptName',
  'bedNo',
  'orgName',
  'wardName',
  'visitTime',
  'doctorSignDate',
]);

/** 中文 label → fieldKey（Skill §A4） */
const LABEL_TO_FIELD_KEY: Record<string, string> = {
  姓名: 'patientName',
  患者姓名: 'patientName',
  性别: 'gender',
  年龄: 'age',
  电话: 'phone',
  联系电话: 'phone',
  身份证号: 'idCard',
  证件号: 'idCard',
  病历号: 'medicalRecordNo',
  住院号: 'inpatientNo',
  就诊号: 'medicalRecordNo',
  费别: 'insuranceName',
  医保: 'insuranceName',
  费用性质: 'insuranceName',
  职业: 'occupation',
  民族: 'ethnicity',
  婚姻: 'maritalStatus',
  婚姻状况: 'maritalStatus',
  出生地: 'birthPlace',
  籍贯: 'birthPlace',
  入院时间: 'admissionDateTime',
  入院日期时间: 'admissionDateTime',
  记录时间: 'recordTime',
  记录日期: 'recordTime',
  就诊日期: 'visitTime',
  医生签字日期: 'doctorSignDate',
  病史陈述者: 'historyProvider',
  可靠程度: 'reliability',
  节气: 'solarTerm',
  医师: 'doctorName',
  书写医师: 'doctorName',
  接诊医生: 'doctorName',
  医师签名: 'doctorName',
  主治医师: 'attendingPhysician',
  科室: 'deptName',
  病区: 'wardName',
  病房: 'wardName',
  床号: 'bedNo',
  床位: 'bedNo',
  机构名称: 'orgName',
  医院名称: 'orgName',
};

/** 只读身份项（套患者回填，通常不可手改） */
const READONLY_FIELD_KEYS = new Set([
  'patientName',
  'gender',
  'age',
  'phone',
  'idCard',
  'medicalRecordNo',
  'inpatientNo',
  'insuranceName',
  'doctorName',
  'attendingPhysician',
  'deptName',
  'bedNo',
  'orgName',
  'wardName',
  'visitTime',
]);

export interface P0Report {
  chipToDate: number;
  freeTextToNumber: number;
  freeTextToChip: number;
  dataElementToChip: number;
  placeholdersFixed: number;
}

function normalizeFillPlaceholder(label: string, placeholder: string): string {
  const ph = String(placeholder ?? '').trim();
  if (!ph || ph === '填写' || ph === label) {
    return `请在这里填写${label}`;
  }
  return ph;
}

function normalizeSelectPlaceholder(label: string, placeholder: string): string {
  const ph = String(placeholder ?? '').trim();
  if (!ph || ph === '填写' || ph === label || ph === '选择日期') {
    return `请在这里选择${label}`;
  }
  return ph;
}

function resolveFieldKeyFromLabel(label: string): string | null {
  const cleaned = label.replace(/[：:]\s*$/, '').trim();
  return LABEL_TO_FIELD_KEY[cleaned] ?? null;
}

function chipToDateElement(node: JSONContent): JSONContent {
  const attrs = node.attrs ?? {};
  const fieldKey = String(attrs.fieldKey ?? '').trim();
  const label = String(attrs.label ?? fieldKey);
  return {
    type: 'emrDataElement',
    attrs: {
      slotId: String(attrs.slotId ?? ''),
      elementId: fieldKey,
      labelSnapshot: label,
      value: String(attrs.value ?? ''),
      placeholder: normalizeSelectPlaceholder(label, ''),
      widgetOverride: 'date',
      optionsJson: '[]',
      editable: attrs.editable !== false,
      required: false,
      requiredMessage: '',
    },
  };
}

function freeTextToNumberElement(node: JSONContent): JSONContent {
  const attrs = node.attrs ?? {};
  const label = String(attrs.label ?? '数值');
  return {
    type: 'emrDataElement',
    attrs: {
      slotId: String(attrs.slotId ?? ''),
      elementId: '',
      labelSnapshot: label,
      value: String(attrs.value ?? ''),
      placeholder: normalizeFillPlaceholder(label, String(attrs.placeholder ?? '')),
      widgetOverride: 'number',
      optionsJson: '[]',
      editable: true,
      required: attrs.required === true,
      requiredMessage: String(attrs.requiredMessage ?? ''),
    },
  };
}

function toMetadataChip(opts: {
  slotId?: string;
  fieldKey: string;
  label: string;
  value?: string;
  editable?: boolean;
}): JSONContent {
  const editable =
    opts.editable !== undefined ? opts.editable : !READONLY_FIELD_KEYS.has(opts.fieldKey);
  return {
    type: 'emrMetadataChip',
    attrs: {
      slotId: String(opts.slotId ?? ''),
      fieldKey: opts.fieldKey,
      label: opts.label,
      value: String(opts.value ?? ''),
      editable,
    },
  };
}

function refreshNode(node: JSONContent, report: P0Report): JSONContent {
  if (node.type === 'emrMetadataChip') {
    const fieldKey = String(node.attrs?.fieldKey ?? '').trim();
    if (DATE_FIELD_KEYS.has(fieldKey)) {
      report.chipToDate += 1;
      return chipToDateElement(node);
    }
  }

  if (node.type === 'emrFreeText') {
    const label = String(node.attrs?.label ?? '').trim();
    const mapped = resolveFieldKeyFromLabel(label);
    if (mapped && DATE_FIELD_KEYS.has(mapped)) {
      report.chipToDate += 1;
      return chipToDateElement({
        type: 'emrMetadataChip',
        attrs: {
          slotId: node.attrs?.slotId,
          fieldKey: mapped,
          label,
          value: node.attrs?.value,
          editable: true,
        },
      });
    }
    if (mapped) {
      report.freeTextToChip += 1;
      return toMetadataChip({
        slotId: String(node.attrs?.slotId ?? ''),
        fieldKey: mapped,
        label,
        value: String(node.attrs?.value ?? ''),
      });
    }
    if (NUMBER_VITAL_LABELS.has(label)) {
      report.freeTextToNumber += 1;
      return freeTextToNumberElement(node);
    }
    const attrs = { ...(node.attrs ?? {}) };
    const nextPh = normalizeFillPlaceholder(label, String(attrs.placeholder ?? ''));
    if (nextPh !== attrs.placeholder) {
      report.placeholdersFixed += 1;
      attrs.placeholder = nextPh;
    }
    return { ...node, attrs };
  }

  if (node.type === 'emrDataElement') {
    const attrs = { ...(node.attrs ?? {}) };
    const widget = String(attrs.widgetOverride ?? 'text').trim() || 'text';
    const elementId = String(attrs.elementId ?? '').trim();
    const label = String(attrs.labelSnapshot ?? attrs.placeholder ?? elementId).trim();

    // 已是控件岛：只规范 placeholder，去掉空 content
    if (['select', 'radio', 'date', 'checkbox', 'number'].includes(widget)) {
      const prevPh = String(attrs.placeholder ?? '');
      let nextPh = prevPh;
      if (widget === 'date') {
        nextPh = normalizeSelectPlaceholder(label, prevPh);
      } else if (widget === 'select' || widget === 'radio' || widget === 'checkbox') {
        if (!prevPh || prevPh === '填写' || prevPh === label) {
          nextPh = `请在这里选择${label}`;
        }
      } else if (widget === 'number') {
        nextPh = normalizeFillPlaceholder(label, prevPh);
      }
      if (nextPh !== prevPh) {
        report.placeholdersFixed += 1;
        attrs.placeholder = nextPh;
      }
      const { content: _drop, ...rest } = node;
      return { ...rest, attrs };
    }

    // text/multiline 且系统可取 → chip（日期键留给 date 升级）
    const mapped =
      (METADATA_FIELD_KEYS.has(elementId) ? elementId : null) ?? resolveFieldKeyFromLabel(label);
    if (mapped && DATE_FIELD_KEYS.has(mapped)) {
      report.chipToDate += 1;
      return {
        type: 'emrDataElement',
        attrs: {
          slotId: String(attrs.slotId ?? ''),
          elementId: mapped,
          labelSnapshot: label || mapped,
          value: String(attrs.value ?? ''),
          placeholder: normalizeSelectPlaceholder(label || mapped, String(attrs.placeholder ?? '')),
          widgetOverride: 'date',
          optionsJson: '[]',
          editable: attrs.editable !== false,
          required: false,
          requiredMessage: '',
        },
      };
    }
    if (mapped) {
      report.dataElementToChip += 1;
      return toMetadataChip({
        slotId: String(attrs.slotId ?? ''),
        fieldKey: mapped,
        label: label || mapped,
        value: String(attrs.value ?? ''),
        editable: attrs.editable !== false,
      });
    }

    const prevPh = String(attrs.placeholder ?? '');
    const nextPh = normalizeFillPlaceholder(label, prevPh);
    if (nextPh !== prevPh) {
      report.placeholdersFixed += 1;
      attrs.placeholder = nextPh;
    }
    const { content: _drop, ...rest } = node;
    return { ...rest, attrs };
  }

  if (!Array.isArray(node.content)) return node;
  return {
    ...node,
    content: node.content.map((c) => refreshNode(c, report)),
  };
}

export function refreshTemplateSkillP0(raw: unknown): { doc: JSONContent; report: P0Report } {
  const doc = structuredClone(raw) as JSONContent;
  const report: P0Report = {
    chipToDate: 0,
    freeTextToNumber: 0,
    freeTextToChip: 0,
    dataElementToChip: 0,
    placeholdersFixed: 0,
  };
  if (Array.isArray(doc.content)) {
    doc.content = doc.content.map((n) => refreshNode(n, report));
  }
  return { doc, report };
}

function main() {
  const files = process.argv.slice(2);
  if (files.length === 0) {
    console.error('用法: npx tsx refresh-template-skill-p0.ts <file.json> [...]');
    process.exit(1);
  }
  for (const f of files) {
    const file = resolve(f);
    const raw = JSON.parse(readFileSync(file, 'utf8')) as unknown;
    const { doc, report } = refreshTemplateSkillP0(raw);
    writeFileSync(file, `${JSON.stringify(doc, null, 2)}\n`, 'utf8');
    console.log(`✓ ${file}`);
    console.log(`  chip→date: ${report.chipToDate}`);
    console.log(`  freeText→number: ${report.freeTextToNumber}`);
    console.log(`  freeText→chip: ${report.freeTextToChip}`);
    console.log(`  dataElement→chip: ${report.dataElementToChip}`);
    console.log(`  placeholder 修正: ${report.placeholdersFixed}`);
  }
}

const isDirectRun =
  typeof process.argv[1] === 'string' &&
  /refresh-template-skill-p0\.(ts|js|mjs|cjs)$/.test(process.argv[1].replace(/\\/g, '/'));

if (isDirectRun) {
  main();
}
