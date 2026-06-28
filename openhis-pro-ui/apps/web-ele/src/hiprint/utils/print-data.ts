import type { HiprintTemplateInstance } from '../core/types';

import { normalizePrintJson } from './print-json';

/** 预览用 PrintData 是否视为空 */
function isEmptyPrintValue(value: unknown): boolean {
  if (value === undefined || value === null) {
    return true;
  }
  if (typeof value === 'string' && value.trim() === '') {
    return true;
  }
  if (Array.isArray(value) && value.length === 0) {
    return true;
  }
  return false;
}

/** 非空值写入目标（不覆盖已有有效值） */
function assignIfNonempty(target: Record<string, unknown>, source: Record<string, unknown>): void {
  for (const [key, value] of Object.entries(source)) {
    if (!isEmptyPrintValue(value)) {
      target[key] = value;
    }
  }
}

/** 解析 table 元素的 testData（hiprint 存为 JSON 字符串） */
function assignTableTestData(
  data: Record<string, unknown>,
  field: string,
  testData: unknown,
): void {
  if (typeof testData === 'string') {
    try {
      data[field] = JSON.parse(testData) as unknown;
    } catch {
      data[field] = [];
    }
    return;
  }
  if (Array.isArray(testData)) {
    data[field] = testData;
  }
}

/** 扫描 PrintJson 中所有 field（含 table 列） */
export function extractFieldNamesFromPrintJson(raw: Record<string, unknown>): string[] {
  const names = new Set<string>();
  const panels = normalizePrintJson(raw).panels;
  if (!Array.isArray(panels)) {
    return [];
  }

  for (const panel of panels) {
    if (!panel || typeof panel !== 'object') {
      continue;
    }
    const elements = (panel as Record<string, unknown>).printElements;
    if (!Array.isArray(elements)) {
      continue;
    }

    for (const item of elements) {
      if (!item || typeof item !== 'object') {
        continue;
      }
      const el = item as Record<string, unknown>;
      const pet = el.printElementType as Record<string, unknown> | undefined;
      const options = el.options as Record<string, unknown> | undefined;
      if (!options) {
        continue;
      }

      const rootField =
        (typeof options.field === 'string' && options.field.trim()) ||
        (typeof pet?.field === 'string' && pet.field.trim()) ||
        '';
      if (rootField) {
        names.add(rootField);
      }

      const columns = options.columns;
      if (!Array.isArray(columns)) {
        continue;
      }
      for (const row of columns) {
        if (!Array.isArray(row)) {
          continue;
        }
        for (const col of row) {
          if (!col || typeof col !== 'object') {
            continue;
          }
          const colField = (col as Record<string, unknown>).field;
          if (typeof colField === 'string' && colField.trim()) {
            names.add(colField.trim());
          }
        }
      }
    }
  }

  return [...names];
}

/**
 * 从 PrintJson 元素 options.testData 提取（存库 JSON 里可能有，也可能没有）。
 */
export function extractTestDataFromPrintJson(
  raw: Record<string, unknown>,
): Record<string, unknown> {
  const data: Record<string, unknown> = {};
  const panels = normalizePrintJson(raw).panels;
  if (!Array.isArray(panels)) {
    return data;
  }

  for (const panel of panels) {
    if (!panel || typeof panel !== 'object') {
      continue;
    }
    const elements = (panel as Record<string, unknown>).printElements;
    if (!Array.isArray(elements)) {
      continue;
    }

    for (const item of elements) {
      if (!item || typeof item !== 'object') {
        continue;
      }
      const el = item as Record<string, unknown>;
      const pet = el.printElementType as Record<string, unknown> | undefined;
      const options = el.options as Record<string, unknown> | undefined;
      if (!options) {
        continue;
      }

      const type = typeof pet?.type === 'string' ? pet.type : '';
      const field =
        (typeof options.field === 'string' && options.field.trim()) ||
        (typeof pet?.field === 'string' && pet.field.trim()) ||
        '';
      if (!field) {
        continue;
      }

      if (type === 'table') {
        assignTableTestData(data, field, options.testData);
        continue;
      }

      const testData =
        options.testData ??
        (pet?.data !== undefined && pet?.data !== null && pet?.data !== '' ? pet.data : undefined);
      if (!isEmptyPrintValue(testData)) {
        data[field] = testData;
      }
    }
  }

  return data;
}

export interface BuildPreviewPrintDataOptions {
  samplePrintData?: null | Record<string, unknown>;
  /** 设计器已挂载实例：读取属性面板里填的 testData（未写入存库 JSON 时也能预览） */
  liveTemplate?: HiprintTemplateInstance | null;
}

/**
 * 组装预览/打印用 PrintData：
 * 1. PrintJson.options.testData
 * 2. 设计器实例 getTestData()（属性面板测试值）
 * 3. samplePrintData（后端 prt_template.sample_print_data 或「编辑数据」）
 */
export function buildPreviewPrintData(
  templateJson: Record<string, unknown>,
  options?: BuildPreviewPrintDataOptions,
): Record<string, unknown> {
  const merged: Record<string, unknown> = { ...extractTestDataFromPrintJson(templateJson) };

  const liveTemplate = options?.liveTemplate as
    | (HiprintTemplateInstance & {
        getTestData?: () => Record<string, unknown>;
      })
    | null;
  if (liveTemplate?.getTestData) {
    assignIfNonempty(merged, liveTemplate.getTestData());
  }

  const sample =
    options?.samplePrintData && typeof options.samplePrintData === 'object'
      ? options.samplePrintData
      : {};
  assignIfNonempty(merged, sample);

  return merged;
}

/** @deprecated 使用 buildPreviewPrintData */
export function mergePreviewPrintData(
  templateJson: Record<string, unknown>,
  samplePrintData?: null | Record<string, unknown>,
): Record<string, unknown> {
  return buildPreviewPrintData(templateJson, { samplePrintData });
}
