/**
 * 门诊病历：仅将 signDate 段落右下角日期改为可编辑日期控件，保留原 value。
 */
import type { JSONContent } from '@tiptap/core';

const SIGN_DATE_KEYS = new Set([
  'doctorSignDate',
  'signDate',
  'visitTime',
  'patientSignDate',
  '日期',
  '医生签字日期',
]);

export function buildDoctorSignDateElement(value: string): JSONContent {
  return {
    type: 'emrDataElement',
    attrs: {
      elementId: 'doctorSignDate',
      labelSnapshot: '医生签字日期',
      value: String(value ?? '').trim(),
      placeholder: '请在这里选择医生签字日期',
      widgetOverride: 'date',
      optionsJson: '[]',
      editable: true,
      required: false,
      requiredMessage: '',
    },
  };
}

function isSignDateValueNode(node: JSONContent): boolean {
  const t = node.type ?? '';
  if (t === 'emrFreeText') {
    const label = String(node.attrs?.label ?? '').trim();
    return label === '日期' || label === '医生签字日期' || SIGN_DATE_KEYS.has(label);
  }
  if (t === 'emrMetadataChip') {
    const key = String(node.attrs?.fieldKey ?? '').trim();
    return SIGN_DATE_KEYS.has(key);
  }
  if (t === 'emrDataElement') {
    const id = String(node.attrs?.elementId ?? '').trim();
    return SIGN_DATE_KEYS.has(id);
  }
  return false;
}

function isAlreadyDateWidget(node: JSONContent): boolean {
  return (
    node.type === 'emrDataElement' &&
    String(node.attrs?.elementId ?? '') === 'doctorSignDate' &&
    String(node.attrs?.widgetOverride ?? '') === 'date'
  );
}

/** 从 signDate 段落提取已有日期文本（不 fallback 到 visitTime 等其他段落） */
export function extractSignDateValueFromParagraph(para: JSONContent): string {
  for (const c of para.content ?? []) {
    if (isSignDateValueNode(c)) {
      const v = String(c.attrs?.value ?? '').trim();
      if (v) return v;
    }
  }
  for (const c of para.content ?? []) {
    if (c.type === 'text') {
      const text = String(c.text ?? '').trim();
      if (text && text !== '日期' && text !== '日期：') return text;
    }
  }
  return '';
}

export function patchOutpatientSignDateToDateWidget(doc: JSONContent): {
  changed: boolean;
  doc: JSONContent;
  skipped: boolean;
  value: string;
} {
  const cloned = structuredClone(doc) as JSONContent;
  let changed = false;
  let skipped = false;
  let preservedValue = '';

  function walk(node: JSONContent): void {
    if (node.attrs?.name !== 'signDate' || !Array.isArray(node.content)) {
      for (const c of node.content ?? []) walk(c);
      return;
    }

    if (node.content.some((c) => isAlreadyDateWidget(c))) {
      skipped = true;
      preservedValue = extractSignDateValueFromParagraph(node);
      return;
    }

    preservedValue = extractSignDateValueFromParagraph(node);
    const nextContent: JSONContent[] = [];
    let dateInserted = false;

    for (const c of node.content) {
      if (c.type === 'text' && String(c.text ?? '').includes('日期')) {
        nextContent.push(c);
        continue;
      }
      if (isSignDateValueNode(c)) {
        if (!dateInserted) {
          nextContent.push(buildDoctorSignDateElement(preservedValue));
          dateInserted = true;
        }
        continue;
      }
      if (c.type === 'text') {
        const text = String(c.text ?? '').trim();
        if (text && !dateInserted) {
          nextContent.push(buildDoctorSignDateElement(text));
          preservedValue = preservedValue || text;
          dateInserted = true;
          continue;
        }
      }
      nextContent.push(c);
    }

    if (!dateInserted) {
      nextContent.push(buildDoctorSignDateElement(preservedValue));
    }

    node.content = nextContent;
    changed = true;
  }

  walk(cloned);
  return { changed, doc: cloned, skipped, value: preservedValue };
}
