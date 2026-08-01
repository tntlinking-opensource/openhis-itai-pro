import type { JSONContent } from '@tiptap/core';

/** 医生站患者条等传入的片段信息，用于在接口不可用时的本地映射 */
export interface PatientInfoForEmrMetadata {
  name?: string;
  gender?: string;
  age?: string;
  phone?: string;
  /** 医保/费用性质展示（与侧栏 contractDisplay 同口径） */
  insurance?: string;
  medicalRecordNo?: string;
  idCard?: string;
}

const FIELD_MAP: {
  fieldKey: string;
  from: (info: PatientInfoForEmrMetadata) => string | undefined;
}[] = [
  { fieldKey: 'patientName', from: (i) => String(i.name ?? '').trim() || undefined },
  { fieldKey: 'gender', from: (i) => i.gender },
  { fieldKey: 'age', from: (i) => i.age },
  { fieldKey: 'phone', from: (i) => String(i.phone ?? '').trim() || undefined },
  {
    fieldKey: 'insuranceName',
    from: (i) => String(i.insurance ?? '').trim() || undefined,
  },
  { fieldKey: 'idCard', from: (i) => i.idCard },
  {
    fieldKey: 'medicalRecordNo',
    from: (i) => (i.medicalRecordNo ? String(i.medicalRecordNo).trim() : undefined),
  },
];

/**
 * 将患者条信息映射为 fieldKey → 值（仅作接口失败或未接 EMR 解析时的补充，不含演示假数据）。
 */
export function buildMetadataValuesFromPatientInfo(
  info: null | PatientInfoForEmrMetadata | undefined,
): Record<string, string> {
  if (!info) return {};
  const out: Record<string, string> = {};
  for (const { fieldKey, from } of FIELD_MAP) {
    const v = from(info);
    if (v !== undefined && v !== '') {
      out[fieldKey] = v;
    }
  }
  return out;
}

/**
 * 合并服务端解析值与患者条本地映射：服务端非空优先，空串不覆盖本地已有值。
 */
export function mergeEmrMetadataValues(
  local: Record<string, string>,
  server: Record<string, string>,
): Record<string, string> {
  const out: Record<string, string> = { ...local };
  for (const [key, raw] of Object.entries(server)) {
    const v = String(raw ?? '').trim();
    if (v) {
      out[key] = String(raw).trim();
    } else if (!(key in out)) {
      out[key] = '';
    }
  }
  return out;
}

/**
 * 将元数据写入模板/文书 JSON（套模板时预填，避免编辑器 watch 竞态导致首屏空白）。
 * @param readonlyOnly 为 true 时：只读域始终写入；可改域仅在当前值为空时预填
 */
export function applyMetadataValuesToContentJson(
  doc: JSONContent,
  vals: Record<string, string>,
  readonlyOnly = true,
): JSONContent {
  const cloned = structuredClone(doc) as JSONContent;

  const visit = (node: JSONContent) => {
    const type = node.type;
    const attrs = node.attrs;
    if (attrs && (type === 'emrMetadataChip' || type === 'emrDataElement')) {
      const editable = attrs.editable !== false;
      const cur = String(attrs.value ?? '').trim();
      if (!(readonlyOnly && editable && cur)) {
        const key =
          type === 'emrMetadataChip' ? String(attrs.fieldKey ?? '') : String(attrs.elementId ?? '');
        if (key) {
          const nextVal = vals[key] ?? '';
          if (String(attrs.value ?? '') !== String(nextVal)) {
            attrs.value = nextVal;
          }
        }
      }
    }
    const children = node.content;
    if (Array.isArray(children)) {
      for (const child of children) {
        visit(child);
      }
    }
  };

  visit(cloned);
  return cloned;
}

/** 应用模版到编辑器时由业务传入（供父组件 / 右侧列表回调使用） */
export interface ApplyEmrTemplatePayload {
  /** 模版 Tiptap JSON；null/undefined 表示仅切换模式或更新元数据 */
  content?: JSONContent | null;
  /** 覆盖当前就诊元数据 */
  metadataValues?: Record<string, string>;
}
