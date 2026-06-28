import type { HiprintTemplateInstance } from '../core/types';

import { applyPaperPresetToTemplate, matchPaperPreset } from './paper-presets';

type HinnnEvent = {
  __ohPaperPatched?: boolean;
  trigger?: (name: string, payload?: unknown, ...rest: unknown[]) => unknown;
};

let getTemplate: (() => HiprintTemplateInstance | null | undefined) | null = null;
let onPaperSizeChanged: ((width: number, height: number) => void) | null = null;

/** 面板属性项不含 width/height 键，注入 paperWidth/paperHeight 供自定义 option 渲染 */
function patchPanelOptionsTrigger(): void {
  const w = window as Window & { hinnn?: { event?: HinnnEvent } };
  const eventApi = w.hinnn?.event;
  if (!eventApi?.trigger || eventApi.__ohPaperPatched) {
    return;
  }
  const originalTrigger = eventApi.trigger.bind(eventApi);
  eventApi.trigger = (name: string, payload?: unknown, ...rest: unknown[]) => {
    if (typeof name === 'string' && name.startsWith('BuildCustomOptionSettingEventKey_')) {
      const panel = getTemplate?.()?.editingPanel as
        | undefined
        | { height?: number; width?: number };
      const data = payload as undefined | { options?: Record<string, unknown> };
      if (panel && Number.isFinite(panel.width) && Number.isFinite(panel.height) && data?.options) {
        const matched = matchPaperPreset(panel.width, panel.height);
        data.options = {
          paperPreset: matched?.label ?? '',
          paperWidth: panel.width,
          paperHeight: panel.height,
          ...data.options,
        };
      }
    }
    return originalTrigger(name, payload, ...rest);
  };
  eventApi.__ohPaperPatched = true;
}

/** 绑定模板引用，供属性面板纸张尺寸项调用 setPaper */
export function bindPaperSizeBridge(
  getter: () => HiprintTemplateInstance | null | undefined,
  onChanged?: (width: number, height: number) => void,
): void {
  getTemplate = getter;
  onPaperSizeChanged = onChanged ?? null;
  patchPanelOptionsTrigger();
}

export function unbindPaperSizeBridge(): void {
  getTemplate = null;
  onPaperSizeChanged = null;
}

function readPanelSize(): null | { height: number; width: number } {
  const tpl = getTemplate?.();
  if (!tpl) {
    return null;
  }
  const jsonPanel = (tpl.getJson() as { panels?: { height?: number; width?: number }[] })
    ?.panels?.[0];
  if (jsonPanel && Number.isFinite(jsonPanel.width) && Number.isFinite(jsonPanel.height)) {
    const { width, height } = jsonPanel;
    return { width, height };
  }
  const panel = tpl.editingPanel as undefined | { height?: number; width?: number };
  if (!panel || !Number.isFinite(panel.width) || !Number.isFinite(panel.height)) {
    return null;
  }
  const { width, height } = panel;
  return { width, height };
}

/** 应用纸张预设或自定义宽高（mm） */
export function applyPaperPreset(preset: import('../core/types').PaperPreset): void {
  const tpl = getTemplate?.();
  if (!tpl) {
    return;
  }
  const { width, height } = applyPaperPresetToTemplate(tpl, preset);
  syncPaperSizeOptionInputs(width, height);
  syncPaperPresetSelect(width, height);
  onPaperSizeChanged?.(width, height);
}

/** 应用纸张宽高（mm）并触发回调 */
export function applyPaperSize(width: number, height: number): void {
  const tpl = getTemplate?.();
  if (!tpl || !Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    return;
  }
  tpl.setPaper(width, height);
  syncPaperSizeOptionInputs(width, height);
  syncPaperPresetSelect(width, height);
  onPaperSizeChanged?.(width, height);
}

/** 同步属性面板宽/高输入框 */
export function syncPaperSizeOptionInputs(width: number, height: number): void {
  document
    .querySelectorAll(
      '#PrintElementOptionSetting .oh-paper-size-option[data-paper-dim="width"] input',
    )
    .forEach((el) => {
      if (el instanceof HTMLInputElement) {
        el.value = String(width);
      }
    });
  document
    .querySelectorAll(
      '#PrintElementOptionSetting .oh-paper-size-option[data-paper-dim="height"] input',
    )
    .forEach((el) => {
      if (el instanceof HTMLInputElement) {
        el.value = String(height);
      }
    });
}

/** 同步属性面板规格下拉 */
export function syncPaperPresetSelect(width: number, height: number): void {
  const matched = matchPaperPreset(width, height);
  document
    .querySelectorAll('#PrintElementOptionSetting .oh-paper-preset-option select')
    .forEach((el) => {
      if (el instanceof HTMLSelectElement) {
        el.value = matched?.label ?? '';
      }
    });
}

/** 读取当前面板纸张宽度（mm） */
export function getPanelPaperWidth(): number | undefined {
  return readPanelSize()?.width;
}

/** 读取当前面板纸张高度（mm） */
export function getPanelPaperHeight(): number | undefined {
  return readPanelSize()?.height;
}
