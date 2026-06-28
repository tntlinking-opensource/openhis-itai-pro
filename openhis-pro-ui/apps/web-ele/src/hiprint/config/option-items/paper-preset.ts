import $ from 'jquery';

import { matchPaperPreset, PAPER_PRESETS } from '../paper-presets';
import { applyPaperPreset, getPanelPaperHeight, getPanelPaperWidth } from '../paper-size-bridge';

const PRESET_OPTIONS_HTML = PAPER_PRESETS.map(
  (p) => `<option value="${p.label}">${p.label}（${p.width}×${p.height}mm）</option>`,
).join('');

/** 面板属性：纸张规格下拉（与 paper-size 一致：单层 factory，供 hiprint optionItems 注册） */
export function paperPresetOptionItem() {
  class Item {
    name = 'paperPreset';
    target: JQuery | null = null;

    createTarget() {
      this.target = $(`
          <div class="hiprint-option-item hiprint-option-item-row oh-paper-preset-option">
            <div class="hiprint-option-item-label">纸张规格</div>
            <div class="hiprint-option-item-field">
              <select class="auto-submit">
                <option value="">自定义</option>
                ${PRESET_OPTIONS_HTML}
              </select>
            </div>
          </div>
        `);
      this.target.find('select').on('change', () => {
        const label = String(this.target?.find('select').val() ?? '');
        if (!label) {
          return;
        }
        const preset = PAPER_PRESETS.find((p) => p.label === label);
        if (!preset) {
          return;
        }
        applyPaperPreset(preset);
      });
      return this.target;
    }

    destroy(): void {
      this.target?.remove();
      this.target = null;
    }

    getValue(): string | undefined {
      const val = this.target?.find('select').val();
      if (val === undefined || val === null || val === '') {
        return undefined;
      }
      return String(val);
    }

    setValue(
      value: string | undefined,
      options?: { height?: number; paperHeight?: number; paperWidth?: number; width?: number },
    ): void {
      const width = options?.paperWidth ?? options?.width ?? getPanelPaperWidth();
      const height = options?.paperHeight ?? options?.height ?? getPanelPaperHeight();
      const matched =
        value && PAPER_PRESETS.some((p) => p.label === value)
          ? value
          : width !== undefined && height !== undefined
            ? matchPaperPreset(width, height)?.label
            : undefined;
      this.target?.find('select').val(matched ?? '');
    }
  }
  return new Item();
}
