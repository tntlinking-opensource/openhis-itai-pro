import type { JSONContent } from '@tiptap/core';

import { isCanvasPayload } from '#/emr-canvas/utils/emr-content';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

import { syncFillableJsonNodeContent } from './components/simple-editor/emr-data-element-text';

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
  /** 科室名称（住院就诊科室） */
  deptName?: string;
  /** 病房/病室展示名（页眉 {wardName}；优先 houseName） */
  wardName?: string;
  /** 床位/床号展示（页眉 {bedNo}） */
  bedNo?: string;
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
  {
    fieldKey: 'deptName',
    from: (i) => String(i.deptName ?? '').trim() || undefined,
  },
  {
    fieldKey: 'wardName',
    from: (i) => String(i.wardName ?? '').trim() || undefined,
  },
  {
    fieldKey: 'bedNo',
    from: (i) => String(i.bedNo ?? '').trim() || undefined,
  },
];

/** 患者身份绑定域：存模板须清空；套模板/复用须按当前患者强制覆盖（含可改芯片） */
export const PATIENT_BOUND_FIELD_KEYS: ReadonlySet<string> = new Set([
  'patientName',
  'gender',
  'age',
  'phone',
  'insuranceName',
  'idCard',
  'medicalRecordNo',
  'inpatientNo',
  'deptName',
  'wardName',
  'bedNo',
]);

/**
 * 病历落款时间（时间控件）：保存时写入正文。
 * 查看已有文书不得用当天覆盖；仅空槽（套模板/复用清空后）才预填当天。
 */
export const DOCUMENT_STAMP_FIELD_KEYS: ReadonlySet<string> = new Set([
  'recordTime',
  'talkTime',
  'signDate',
  'patientSignDate',
  'doctorSignDate',
  'printTime',
  'staffSignAt',
  'consultAt',
]);

/**
 * 会话/时间上下文域：存模板须清空。
 * visitTime 随就诊刷新；落款时间见 DOCUMENT_STAMP_FIELD_KEYS。
 */
export const CONTEXT_REFRESH_FIELD_KEYS: ReadonlySet<string> = new Set([
  'visitTime',
  ...DOCUMENT_STAMP_FIELD_KEYS,
]);

/** 门诊病历中西医诊断元数据 fieldKey（与 metadata_field_def 种子对齐） */
export const EMR_DIAGNOSIS_METADATA_FIELD_KEYS = ['tcmDiagnosis', 'westernDiagnosis'] as const;

export type EmrDiagnosisMetadataFieldKey = (typeof EMR_DIAGNOSIS_METADATA_FIELD_KEYS)[number];

/** 存为模板时清空：患者身份 + 就诊/落款时间，避免把原患者与旧日期写入模板库 */
export function shouldStripMetadataKeyOnSaveAsTemplate(key: string): boolean {
  return PATIENT_BOUND_FIELD_KEYS.has(key) || CONTEXT_REFRESH_FIELD_KEYS.has(key);
}

/**
 * 套模板/打开文书时强制覆盖：仅患者身份 + 就诊时间。
 * 落款时间（记录时间等）已有值则保留，避免查看时带入当天。
 */
export function shouldForceOverwriteMetadataKey(key: string): boolean {
  return PATIENT_BOUND_FIELD_KEYS.has(key) || key === 'visitTime';
}

/** 是否为病历落款时间控件 fieldKey */
export function isDocumentStampMetadataKey(key: string): boolean {
  return DOCUMENT_STAMP_FIELD_KEYS.has(key);
}

export interface ApplyMetadataValuesOptions {
  /** 关闭后不对患者绑定/日期上下文域强制覆盖（诊断空槽回填用） */
  forceOverwriteBoundKeys?: boolean;
  /** 仅写入这些 fieldKey；默认全部命中槽 */
  onlyKeys?: ReadonlySet<string>;
}

/** V4 门诊病历段落 attrs.name → 诊断 fieldKey（机器名，非展示文案） */
const DIAGNOSIS_BLOCK_NAME_TO_FIELD_KEY: Record<string, EmrDiagnosisMetadataFieldKey> = {
  impressionDiagnosis: 'tcmDiagnosis',
  westernDiagnosis: 'westernDiagnosis',
};

/**
 * 存量自由文本槽仅保留元数据目录中文名时的 fieldKey 对照。
 * 对照的是 metadata_field_def.label（tcmDiagnosis/westernDiagnosis），不是字典枚举文案。
 */
const DIAGNOSIS_SLOT_TITLE_TO_FIELD_KEY: Record<string, EmrDiagnosisMetadataFieldKey> = {
  中医诊断: 'tcmDiagnosis',
  西医诊断: 'westernDiagnosis',
};

function isFillableMetadataNodeType(type: string): boolean {
  return type === 'emrMetadataChip' || type === 'emrDataElement' || type === 'emrFreeText';
}

/** 从可填节点 attrs / 所属命名块解析元数据 fieldKey */
export function resolveFillableMetadataKey(
  type: string,
  attrs: Record<string, unknown>,
  parentBlockName?: string,
): string {
  const fieldKey = String(attrs.fieldKey ?? '').trim();
  if (fieldKey) return fieldKey;
  const elementId = String(attrs.elementId ?? '').trim();
  if (elementId) return elementId;

  const blockName = String(parentBlockName ?? '').trim();
  const fromBlock = DIAGNOSIS_BLOCK_NAME_TO_FIELD_KEY[blockName];
  if (fromBlock) return fromBlock;

  const title = String(attrs.label ?? attrs.labelSnapshot ?? '').trim();
  const fromTitle = DIAGNOSIS_SLOT_TITLE_TO_FIELD_KEY[title];
  if (fromTitle) return fromTitle;

  return '';
}

function resolveMetadataNodeKey(type: string, attrs: Record<string, unknown>): string {
  return resolveFillableMetadataKey(type, attrs);
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
  return stripMetadataKeysFromContentJson(doc, (key) =>
    shouldStripMetadataKeyOnSaveAsTemplate(key),
  );
}

/** 复用病历前清空落款时间，使新文书按当天预填，不沿用源文书记录时间 */
export function stripDocumentStampMetadataFromContentJson(doc: unknown): JSONContent {
  return stripMetadataKeysFromContentJson(doc, (key) => isDocumentStampMetadataKey(key));
}

/**
 * 门诊病历套模板/复用前清空右下角落款（接诊医生 + 签字/记录时间）。
 * 清空后由当前登录人与当天日期重新写入，不沿用模板或历史文书。
 */
export function stripOutpatientSignoffMetadataFromContentJson(doc: unknown): JSONContent {
  return stripMetadataKeysFromContentJson(
    doc,
    (key) => key === 'doctorName' || isDocumentStampMetadataKey(key),
  );
}

/** 按 fieldKey 谓词清空可填元数据 value */
function stripMetadataKeysFromContentJson(
  doc: unknown,
  shouldStrip: (key: string) => boolean,
): JSONContent {
  const root = cloneEmrJsonContent(doc);

  const visit = (node: JSONContent) => {
    const type = node.type ?? '';
    const attrs = node.attrs;
    if (attrs && (type === 'emrMetadataChip' || type === 'emrDataElement')) {
      const key = resolveMetadataNodeKey(type, attrs);
      if (key && shouldStrip(key)) {
        attrs.value = '';
        syncFillableJsonNodeContent(node);
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
  if (!info) return ensureContextDateMetadataDefaults({});
  const out: Record<string, string> = {};
  for (const { fieldKey, from } of FIELD_MAP) {
    const v = from(info);
    if (v !== undefined && v !== '') {
      out[fieldKey] = v;
    }
  }
  // 页眉模板常用 {inpatientNo}；患者条本地映射是 medicalRecordNo（就诊流水号）
  if (out.medicalRecordNo && !out.inpatientNo) {
    out.inpatientNo = out.medicalRecordNo;
  }
  return ensureContextDateMetadataDefaults(out);
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
  if (out.medicalRecordNo && !out.inpatientNo) {
    out.inpatientNo = out.medicalRecordNo;
  }
  return ensureContextDateMetadataDefaults(out);
}

/** 补齐日期时间上下文默认值（接口未返回时用当天，避免界面空 `[]`） */
export function ensureContextDateMetadataDefaults(
  vals: Record<string, string>,
  now: Date = new Date(),
): Record<string, string> {
  const out = { ...vals };
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  // date 控件只认 YYYY-MM-DD；带时刻的字符串会导致 type=date 空白仍显 []
  const dateOnly = `${y}-${m}-${d}`;

  for (const key of CONTEXT_REFRESH_FIELD_KEYS) {
    if (String(out[key] ?? '').trim()) continue;
    out[key] = dateOnly;
  }
  fillHospitalMetadataAliases(out);
  return out;
}

/** Canvas 页眉用 hospitalName，Tiptap 页眉用 orgName；空则登录选院 */
function fillHospitalMetadataAliases(out: Record<string, string>) {
  const hospital =
    String(out.hospitalName ?? '').trim() ||
    String(out.orgName ?? '').trim() ||
    resolvePrintHospitalName().trim();
  if (!hospital) return;
  if (!String(out.hospitalName ?? '').trim()) {
    out.hospitalName = hospital;
  }
  if (!String(out.orgName ?? '').trim()) {
    out.orgName = hospital;
  }
}

function canvasControlText(control: Record<string, unknown>): string {
  const raw = control.value;
  if (Array.isArray(raw)) {
    return raw
      .map((item) => {
        if (typeof item === 'string') return item;
        if (item && typeof item === 'object' && 'value' in item) {
          return String((item as { value?: unknown }).value ?? '');
        }
        return '';
      })
      .join('')
      .trim();
  }
  if (typeof raw === 'string') return raw.trim();
  return '';
}

/** Canvas 控件：按 conceptId 写入，页眉和正文同名字段都会带上 */
function applyCanvasControlMetadata(
  root: unknown,
  vals: Record<string, string>,
  readonlyOnly: boolean,
  options?: ApplyMetadataValuesOptions,
) {
  const onlyKeys = options?.onlyKeys;
  const allowForceOverwrite = options?.forceOverwriteBoundKeys !== false;

  const visit = (node: unknown) => {
    if (!node || typeof node !== 'object') return;
    if (Array.isArray(node)) {
      for (const child of node) visit(child);
      return;
    }
    const rec = node as Record<string, unknown>;
    const control = rec.control;
    if (control && typeof control === 'object' && !Array.isArray(control)) {
      const ctrl = control as Record<string, unknown>;
      const key = String(ctrl.conceptId ?? '').trim();
      if (key && (!onlyKeys || onlyKeys.has(key))) {
        const nextVal = vals[key] ?? '';
        const skipEmptyOnlyKey = Boolean(onlyKeys) && !String(nextVal).trim();
        if (!skipEmptyOnlyKey && String(nextVal).trim()) {
          const cur = canvasControlText(ctrl);
          const forceOverwrite =
            allowForceOverwrite && Boolean(key && shouldForceOverwriteMetadataKey(key));
          const keepSavedStamp = isDocumentStampMetadataKey(key) && Boolean(cur);
          if (!keepSavedStamp && (forceOverwrite || !readonlyOnly || !cur)) {
            if (cur !== String(nextVal)) {
              ctrl.value = [
                {
                  value: String(nextVal),
                  type: 'text',
                  size: typeof ctrl.size === 'number' ? ctrl.size : 14,
                  highlight: typeof ctrl.highlight === 'string' ? ctrl.highlight : '#eff6ff',
                },
              ];
            }
          }
        }
      }
    }
    for (const child of Object.values(rec)) {
      if (child && typeof child === 'object') visit(child);
    }
  };

  visit(root);
}

/**
 * 将元数据写入模板/文书 JSON（套模板时预填，避免编辑器 watch 竞态导致首屏空白）。
 * @param readonlyOnly 为 true 时：只读域始终写入；可改域仅在当前值为空时预填
 * （患者绑定域与就诊时间默认按 vals 强制覆盖；落款时间已有值不覆盖）
 */
export function applyMetadataValuesToContentJson(
  doc: JSONContent,
  vals: Record<string, string>,
  readonlyOnly = true,
  options?: ApplyMetadataValuesOptions,
): JSONContent {
  const cloned = cloneEmrJsonContent(doc);
  const onlyKeys = options?.onlyKeys;
  const allowForceOverwrite = options?.forceOverwriteBoundKeys !== false;

  if (isCanvasPayload(cloned)) {
    applyCanvasControlMetadata(cloned, vals, readonlyOnly, options);
    return cloned;
  }

  const visit = (node: JSONContent, parentBlockName = '') => {
    const type = node.type ?? '';
    const attrs = node.attrs;
    const blockName = String(attrs?.name ?? '').trim() || parentBlockName;
    if (attrs && isFillableMetadataNodeType(type)) {
      const key = resolveFillableMetadataKey(type, attrs, blockName);
      if (key && (!onlyKeys || onlyKeys.has(key))) {
        const nextVal = vals[key] ?? '';
        const skipEmptyOnlyKey = Boolean(onlyKeys) && !String(nextVal).trim();
        if (!skipEmptyOnlyKey) {
          const forceOverwrite =
            allowForceOverwrite && Boolean(key && shouldForceOverwriteMetadataKey(key));
          const editable = attrs.editable !== false;
          const cur = String(attrs.value ?? '').trim();
          // 落款时间已写入正文则保留（查看/打印不得换成当天）
          const keepSavedStamp = isDocumentStampMetadataKey(key) && Boolean(cur);
          if (!keepSavedStamp && (forceOverwrite || !(readonlyOnly && editable && cur))) {
            if (String(attrs.value ?? '') !== String(nextVal)) {
              attrs.value = nextVal;
              syncFillableJsonNodeContent(node);
            }
          }
        }
      }
    }
    const children = node.content;
    if (Array.isArray(children)) {
      for (const child of children) {
        visit(child, blockName);
      }
    }
  };

  visit(cloned);
  return cloned;
}

/** 仅把已开立中/西医诊断写入对应空槽，不覆盖医生已填写内容、不改日期/患者域 */
export function applyDiagnosisMetadataToContentJson(
  doc: JSONContent,
  vals: Record<string, string>,
): JSONContent {
  return applyMetadataValuesToContentJson(doc, vals, true, {
    forceOverwriteBoundKeys: false,
    onlyKeys: new Set(EMR_DIAGNOSIS_METADATA_FIELD_KEYS),
  });
}

/** 应用模版到编辑器时由业务传入（供父组件 / 右侧列表回调使用） */
export interface ApplyEmrTemplatePayload {
  /** 模版 Tiptap JSON；null/undefined 表示仅切换模式或更新元数据 */
  content?: JSONContent | null;
  /** 覆盖当前就诊元数据 */
  metadataValues?: Record<string, string>;
}
