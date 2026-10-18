import type { IEditorData } from '@hufe921/canvas-editor';
import type { JSONContent } from '@tiptap/core';

import { getClinicDocPreset } from '#/emr-canvas/templates/clinic-presets';
import {
  emptyCanvasData,
  isCanvasPayload,
  isTiptapDoc,
  parseTemplateContent,
  toCanvasEditorData,
  wrapCanvasPayload,
  type CanvasEditorPayload,
} from '#/emr-canvas/utils/emr-content';

export { wrapCanvasPayload };

/** 空白信封，给新建 Tab */
export function emptyStationRecordDoc(): CanvasEditorPayload {
  return wrapCanvasPayload(emptyCanvasData());
}

/** 门诊病历调试预设（旧 Tiptap 模版打不开时的纸面） */
export function outpatientCanvasPreset(): CanvasEditorPayload {
  const preset = getClinicDocPreset('OP_OUTPATIENT_RECORD');
  return wrapCanvasPayload(preset?.data ?? emptyCanvasData());
}

/**
 * 接口 content → 医生站 Canvas 信封。
 * 旧 Tiptap 门诊模版落到门诊预设；其它旧稿给空白纸，避免把 AST 塞进画布。
 */
export function toStationRecordDoc(raw: unknown, documentTypeCode = ''): CanvasEditorPayload {
  const parsed = parseTemplateContent(raw);
  if (isCanvasPayload(parsed)) {
    return parsed;
  }
  const converted = toCanvasEditorData(parsed);
  if (converted.legacyTiptap || isTiptapDoc(parsed)) {
    if (documentTypeCode.trim() === 'OUTPATIENT_RECORD') {
      return outpatientCanvasPreset();
    }
    return emptyStationRecordDoc();
  }
  return wrapCanvasPayload(converted.data);
}

/** 从 Tab.recordDoc 取出画布 data */
export function stationDocToEditorData(doc: unknown): IEditorData {
  if (isCanvasPayload(doc)) return doc.data;
  return toStationRecordDoc(doc).data;
}

/** 存盘：已是信封则原样；否则包装。不再写 Tiptap 侧车。 */
export function packCanvasTabContent(doc: unknown): JSONContent {
  const payload = isCanvasPayload(doc) ? doc : toStationRecordDoc(doc);
  return payload as unknown as JSONContent;
}
