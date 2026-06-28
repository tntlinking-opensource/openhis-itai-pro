import $ from 'jquery';

import { applyPaperSize, getPanelPaperHeight, getPanelPaperWidth } from '../paper-size-bridge';

function createPaperSizeOption(name: 'paperHeight' | 'paperWidth', label: string) {
  return function PaperSizeOptionFactory() {
    class Item {
      name = name;
      target: JQuery | null = null;

      createTarget() {
        this.target = $(`
          <div class="hiprint-option-item hiprint-option-item-row oh-paper-size-option" data-paper-dim="${name === 'paperWidth' ? 'width' : 'height'}">
            <div class="hiprint-option-item-label">${label}</div>
            <div class="hiprint-option-item-field">
              <input type="number" min="1" step="0.1" class="auto-submit" placeholder="mm" />
            </div>
          </div>
        `);
        this.target.find('input').on('change', () => {
          const width = getPanelPaperWidth();
          const height = getPanelPaperHeight();
          const value = this.getValue();
          if (value === undefined) {
            return;
          }
          if (name === 'paperWidth' && height !== undefined) {
            applyPaperSize(value, height);
          }
          if (name === 'paperHeight' && width !== undefined) {
            applyPaperSize(width, value);
          }
        });
        return this.target;
      }

      destroy(): void {
        this.target?.remove();
        this.target = null;
      }

      getValue(): number | undefined {
        const raw = this.target?.find('input').val();
        if (raw === undefined || raw === null || raw === '') {
          return undefined;
        }
        const num = Number.parseFloat(String(raw));
        return Number.isFinite(num) ? num : undefined;
      }

      setValue(
        value: number | undefined,
        options?: { height?: number; paperHeight?: number; paperWidth?: number; width?: number },
      ): void {
        const resolved =
          name === 'paperWidth'
            ? (value ?? options?.paperWidth ?? options?.width ?? getPanelPaperWidth())
            : (value ?? options?.paperHeight ?? options?.height ?? getPanelPaperHeight());
        if (resolved === undefined || resolved === null) {
          this.target?.find('input').val('');
          return;
        }
        this.target?.find('input').val(String(resolved));
      }
    }
    return new Item();
  };
}

export const paperWidthOptionItem = createPaperSizeOption('paperWidth', '纸张宽度');
export const paperHeightOptionItem = createPaperSizeOption('paperHeight', '纸张高度');
