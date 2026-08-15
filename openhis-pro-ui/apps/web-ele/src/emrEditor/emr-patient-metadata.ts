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

/** 患者身份绑定域：存模板须清空；套模板/复用须按当前患者强制覆盖（含可改芯片） */
export const PATIENT_BOUND_FIELD_KEYS: ReadonlySet<string> = new Set(
  FIELD_MAP.map((item) => item.fieldKey),
);

/**
 * 会话/时间上下文域：存模板须清空；套模板/复用须强制覆盖为当前就诊解析值。
 * 避免病历底部「就诊日期 / 签字日期 / 记录时间」等沿用模板中的旧日期。
 */
export const CONTEXT_REFRESH_FIELD_KEYS: ReadonlySet<string> = new Set([
  'visitTime',
  'signDate',
  'patientSignDate',
  'doctorSignDate',
  'printTime',
  'staffSignAt',
  'consultAt',
  'recordTime',
  'talkTime',
]);

/** 套模板/复用时须强制覆盖（患者身份 + 日期时间上下文） */
function shouldForceOverwriteMetadataKey(key: string): boolean {
  return PATIENT_BOUND_FIELD_KEYS.has(key) || CONTEXT_REFRESH_FIELD_KEYS.has(key);
}

function resolveMetadataNodeKey(type: string, attrs: Record<string, unknown>): string {
  if (type === 'emrMetadataChip') {
    return String(attrs.fieldKey ?? '');
  }
  if (type === 'emrDataElement') {
    return String(attrs.elementId ?? '');
  }
  return '';
}

/**
 * TipTap JSON 深拷贝。
 * 须兼容 Vue reactive Proxy：structuredClone 对 Proxy 会抛 DataCloneError，
 * 医生站模板维护弹窗 v-model 正文即响应式对象，会导致「点保存没反应」。
 */
function cloneEmrJsonContent(doc: unknown): JSONContent {
  if (!doc || typeof doc !== 'object') {
    return { type: 'doc', content: [] };
  }
  try {
    return JSON.parse(JSON.stringify(doc)) as JSONContent;
  } catch {
    return { type: 'doc', content: [] };
  }
}

/**
 * 存为模板前清空患者绑定与日期时间上下文元数据 value，
 * 避免把原患者信息或旧就诊/签字日期写入模板库。
 */
export function stripPatientBoundMetadataFromContentJson(doc: unknown): JSONContent {
  const root = cloneEmrJsonContent(doc);

  const visit = (node: JSONContent) => {
    const type = node.type ?? '';
    const attrs = node.attrs;
    if (attrs && (type === 'emrMetadataChip' || type === 'emrDataElement')) {
      const key = resolveMetadataNodeKey(type, attrs);
      if (key && shouldForceOverwriteMetadataKey(key)) {
        attrs.value = '';
      }
    }
    const children = node.content;
    if (Array.isArray(children)) {
      for (const child of children) {
        visit(child);
      }
    }
  };

  visit(root);
  return root;
}

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
 * （患者绑定域与日期时间上下文域始终按 vals 强制覆盖）
 */
export function applyMetadataValuesToContentJson(
  doc: JSONContent,
  vals: Record<string, string>,
  readonlyOnly = true,
): JSONContent {
  const cloned = cloneEmrJsonContent(doc);

  const visit = (node: JSONContent) => {
    const type = node.type;
    const attrs = node.attrs;
    if (attrs && (type === 'emrMetadataChip' || type === 'emrDataElement')) {
      const key = resolveMetadataNodeKey(type ?? '', attrs);
      const forceOverwrite = Boolean(key && shouldForceOverwriteMetadataKey(key));
      const editable = attrs.editable !== false;
      const cur = String(attrs.value ?? '').trim();
      if (forceOverwrite || !(readonlyOnly && editable && cur)) {
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
