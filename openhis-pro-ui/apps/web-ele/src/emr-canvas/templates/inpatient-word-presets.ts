import type { IEditorData } from '@hufe921/canvas-editor';

import { applyControlChromeInElements } from '#/emr-canvas/utils/canvas-controls';

import type { OpSurgeryPreset } from './op-surgery-presets';

type WordPresetJson = {
  code: string;
  name: string;
  category: string;
  sourceTemplateId: string;
  typeCode: string;
  summary: string;
  estimatedPages?: number;
  data: IEditorData;
};

const modules = import.meta.glob<{ default: WordPresetJson }>(
  './generated/inpatient-word/*.json',
  { eager: true },
);

function normalizePreset(raw: WordPresetJson): OpSurgeryPreset {
  const data: IEditorData = {
    header: raw.data?.header ?? [],
    main: raw.data?.main ?? [],
    footer: raw.data?.footer ?? [],
  };
  applyControlChromeInElements(data.header);
  applyControlChromeInElements(data.main);
  applyControlChromeInElements(data.footer);
  return {
    code: raw.code,
    name: `[${raw.category}] ${raw.name}`,
    typeCode: raw.typeCode || 'INPATIENT_WORD',
    summary: raw.summary || `Word正文迁移 ${raw.category}`,
    data,
  };
}

function asPresetJson(mod: unknown): WordPresetJson | null {
  if (!mod || typeof mod !== 'object') return null;
  const withDefault = mod as { default?: WordPresetJson };
  const raw = withDefault.default ?? (mod as WordPresetJson);
  if (!raw?.code || !raw?.data) return null;
  return raw;
}

/** 国基住院病历：由导出 Word 正文批量刷入的 Canvas 预设 */
export function getInpatientWordPresets(): OpSurgeryPreset[] {
  const list: OpSurgeryPreset[] = [];
  for (const [key, mod] of Object.entries(modules)) {
    if (key.endsWith('/index.json')) continue;
    const raw = asPresetJson(mod);
    if (!raw) continue;
    list.push(normalizePreset(raw));
  }
  return list.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'));
}

export function getInpatientWordPreset(code: string): OpSurgeryPreset | undefined {
  return getInpatientWordPresets().find((item) => item.code === code);
}
