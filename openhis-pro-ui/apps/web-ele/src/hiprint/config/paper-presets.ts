import type { HiprintTemplateInstance, PaperPreset } from '../core/types';

/** 默认纸张：A4 竖向 */
export const DEFAULT_PAPER_PRESET: PaperPreset = {
  label: 'A4',
  width: 210,
  height: 297,
};

/** hiprint 内置纸张名（setPaper('A4')），与自定义 numeric 区分 */
export const HIPRINT_NAMED_PAPER: Record<string, string> = {
  A3: 'A3',
  A4: 'A4',
  B4: 'B4',
};

/** 常用纸张规格（mm；高度 297 等为标准值，与 hiprint 296.6 裁剪可匹配） */
export const PAPER_PRESETS: PaperPreset[] = [
  DEFAULT_PAPER_PRESET,
  { label: 'A3', width: 420, height: 297 },
  { label: 'A5', width: 148, height: 210 },
  { label: 'B4', width: 250, height: 353 },
  { label: 'B5', width: 176, height: 250 },
  { label: '小票58mm', width: 58, height: 200 },
  { label: '小票80mm', width: 80, height: 200 },
];

/** 按宽高匹配预设（允许 hiprint 高度裁剪误差） */
export function matchPaperPreset(width: number, height: number): PaperPreset | undefined {
  return PAPER_PRESETS.find(
    (p) => Math.abs(p.width - width) < 1 && Math.abs(p.height - height) < 1.5,
  );
}

/** 将预设应用到 hiprint 模板，返回实际面板 mm 尺寸 */
export function applyPaperPresetToTemplate(
  tpl: HiprintTemplateInstance,
  preset: PaperPreset,
): { height: number; width: number } {
  const named = HIPRINT_NAMED_PAPER[preset.label];
  if (named) {
    tpl.setPaper(named);
  } else {
    tpl.setPaper(preset.width, preset.height);
  }
  const panel = (tpl.getJson() as { panels?: { height?: number; width?: number }[] })?.panels?.[0];
  return {
    width: panel?.width ?? preset.width,
    height: panel?.height ?? preset.height,
  };
}
