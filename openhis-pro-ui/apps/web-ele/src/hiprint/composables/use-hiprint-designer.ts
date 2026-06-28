import type { HiprintTemplateInstance, PaperPreset } from '../core/types';

import { nextTick, onBeforeUnmount, ref, shallowRef } from 'vue';

import {
  applyPaperPresetToTemplate,
  DEFAULT_PAPER_PRESET,
  matchPaperPreset,
} from '../config/paper-presets';
import { $, ensureHiprintReady, hiprint } from '../core/bootstrap';
import { normalizePrintJson } from '../utils/print-json';

export { DEFAULT_PAPER_PRESET, PAPER_PRESETS } from '../config/paper-presets';

export function useHiprintDesigner() {
  const templateRef = shallowRef<HiprintTemplateInstance | null>(null);
  const scaleValue = ref(1);
  const currentPaper = ref<PaperPreset>(DEFAULT_PAPER_PRESET);
  const ready = ref(false);

  /** 在 DOM 就绪后创建设计器实例 */
  async function mountDesigner(
    templateJson: Record<string, unknown>,
    containers: {
      designSelector: string;
      paletteSelector: string;
      settingSelector: string;
    },
  ): Promise<HiprintTemplateInstance> {
    ensureHiprintReady();
    await nextTick();

    $(containers.designSelector).empty();
    $(containers.settingSelector).empty();

    hiprint.PrintElementTypeManager.buildByHtml(
      $(`${containers.paletteSelector} .ep-draggable-item`),
    );

    const normalized = normalizePrintJson(templateJson);
    const firstPanel = (normalized.panels as Record<string, unknown>[])?.[0];
    if (
      firstPanel &&
      typeof firstPanel.width === 'number' &&
      typeof firstPanel.height === 'number'
    ) {
      const matched = matchPaperPreset(firstPanel.width as number, firstPanel.height as number);
      currentPaper.value = matched ?? {
        label: '自定义',
        width: firstPanel.width as number,
        height: firstPanel.height as number,
      };
    }

    const instance = new hiprint.PrintTemplate({
      template: normalized,
      settingContainer: containers.settingSelector,
      history: true,
      dataMode: 1,
      onUpdateError: (error: unknown) => {
        console.error('hiprint update error', error);
      },
    }) as unknown as HiprintTemplateInstance;

    instance.design(containers.designSelector);
    templateRef.value = instance;
    scaleValue.value = instance.editingPanel?.scale ?? 1;
    ready.value = true;
    return instance;
  }

  /** 销毁设计器 DOM */
  function destroyDesigner(selectors: { designSelector: string; settingSelector: string }): void {
    $(selectors.designSelector).empty();
    $(selectors.settingSelector).empty();
    templateRef.value = null;
    ready.value = false;
  }

  /** 切换纸张尺寸 */
  function setPaper(preset: PaperPreset): void {
    const tpl = templateRef.value;
    if (!tpl) {
      currentPaper.value = preset;
      return;
    }
    const { width, height } = applyPaperPresetToTemplate(tpl, preset);
    const matched = matchPaperPreset(width, height);
    currentPaper.value = matched ?? { label: preset.label, width, height };
  }

  /** 属性面板修改纸张宽高后同步工具栏显示 */
  function updateCurrentPaperSize(width: number, height: number): void {
    const matched = matchPaperPreset(width, height);
    currentPaper.value = matched ?? { label: '自定义', width, height };
  }

  /** 缩放画布 */
  function changeScale(delta: number): void {
    let next = scaleValue.value + delta;
    if (next > 5) {
      next = 5;
    }
    if (next < 0.5) {
      next = 0.5;
    }
    templateRef.value?.zoom(next);
    scaleValue.value = next;
  }

  /** 撤销 */
  function undo(): void {
    (templateRef.value as HiprintTemplateInstance & { undo?: () => void })?.undo?.();
  }

  /** 重做 */
  function redo(): void {
    (templateRef.value as HiprintTemplateInstance & { redo?: () => void })?.redo?.();
  }

  /** 清空画布 */
  function clearTemplate(): void {
    templateRef.value?.clear();
  }

  /** 读取当前模板 JSON */
  function getTemplateJson(): Record<string, unknown> {
    return templateRef.value?.getJson() ?? normalizePrintJson(null);
  }

  /** 应用模板 JSON */
  function applyTemplateJson(json: Record<string, unknown>): void {
    templateRef.value?.update(normalizePrintJson(json));
  }

  /** 当前选中元素数量 */
  function getSelectedCount(): number {
    return templateRef.value?.editingPanel?.getSelectEls?.()?.length ?? 0;
  }

  /** 对齐选中元素（sv-print setElsAlign） */
  function setElsAlign(type: string): void {
    templateRef.value?.editingPanel?.setElsAlign?.(type);
  }

  onBeforeUnmount(() => {
    templateRef.value = null;
  });

  return {
    templateRef,
    scaleValue,
    currentPaper,
    ready,
    mountDesigner,
    destroyDesigner,
    setPaper,
    updateCurrentPaperSize,
    changeScale,
    undo,
    redo,
    clearTemplate,
    getTemplateJson,
    applyTemplateJson,
    getSelectedCount,
    setElsAlign,
  };
}
