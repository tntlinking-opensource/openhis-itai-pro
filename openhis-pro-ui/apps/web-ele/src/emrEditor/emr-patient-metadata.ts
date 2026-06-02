import type { JSONContent } from '@tiptap/core';

/** 医生站患者条等传入的片段信息，用于在接口不可用时的本地映射 */
export interface PatientInfoForEmrMetadata {
  name?: string;
  gender?: string;
  age?: string;
  phone?: string;
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
  { fieldKey: 'phone', from: (i) => i.phone },
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

/** 应用模版到编辑器时由业务传入（供父组件 / 右侧列表回调使用） */
export interface ApplyEmrTemplatePayload {
  /** 模版 Tiptap JSON；null/undefined 表示仅切换模式或更新元数据 */
  content?: JSONContent | null;
  /** 覆盖当前就诊元数据 */
  metadataValues?: Record<string, string>;
}
