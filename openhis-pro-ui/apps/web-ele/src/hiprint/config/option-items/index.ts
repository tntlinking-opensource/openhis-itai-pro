import $ from 'jquery';

import { requestOpenFunctionEditor } from '../function-bridge';
import { paperPresetOptionItem } from './paper-preset';
import { paperHeightOptionItem, paperWidthOptionItem } from './paper-size';

/** 函数类 option 名称 */
export const FUNCTION_OPTION_NAMES = new Set([
  'footerFormatter',
  'formatter',
  'formatter2',
  'gridColumnsFooterFormatter',
  'groupFieldsFormatter',
  'groupFooterFormatter',
  'groupFormatter',
  'renderFormatter',
  'rowsColumnsMerge',
  'rowsColumnsMergeClean',
  'rowStyler',
  'styler',
  'styler2',
  'stylerHeader',
  'tableSummaryFormatter',
]);

const FUNCTION_OPTION_LABELS: Record<string, string> = {
  formatter: '格式化函数',
  styler: '样式函数',
  rowStyler: '行样式函数',
  footerFormatter: '页脚格式化',
  rowsColumnsMerge: '行列合并',
  rowsColumnsMergeClean: '合并清理',
  groupFieldsFormatter: '分组字段',
  groupFormatter: '分组格式化',
  groupFooterFormatter: '分组页脚',
  gridColumnsFooterFormatter: '列脚格式化',
  renderFormatter: '列渲染格式化',
  formatter2: '格式化函数2',
  styler2: '样式函数2',
  stylerHeader: '表头样式',
  tableSummaryFormatter: '合计格式化',
};

function createFunctionCodeOption(optionName: string) {
  const label = FUNCTION_OPTION_LABELS[optionName] ?? optionName;

  return function FunctionCodeOptionFactory() {
    class Item {
      name = optionName;
      target: JQuery | null = null;

      createTarget() {
        this.target = $(`
          <div class="hiprint-option-item hiprint-option-item-row oh-fn-option">
            <div class="hiprint-option-item-label">${label}</div>
            <div class="hiprint-option-item-field">
              <button type="button" class="oh-fn-edit-btn">编辑函数</button>
              <textarea class="auto-submit oh-fn-preview" rows="4" placeholder="function(...) { ... }"></textarea>
            </div>
          </div>
        `);
        this.target.find('.oh-fn-edit-btn').on('click', () => {
          requestOpenFunctionEditor({
            optionName,
            label,
            value: this.getValue() ?? '',
            apply: (code) => this.setValue(code),
          });
        });
        this.target.find('textarea').on('dblclick', () => {
          requestOpenFunctionEditor({
            optionName,
            label,
            value: this.getValue() ?? '',
            apply: (code) => this.setValue(code),
          });
        });
        return this.target;
      }

      destroy(): void {
        this.target?.remove();
        this.target = null;
      }

      getValue(): string | undefined {
        const val = this.target?.find('textarea').val();
        if (val === undefined || val === null) {
          return undefined;
        }
        return String(val);
      }

      setValue(value: string | undefined): void {
        this.target?.find('textarea').val(value ?? '');
        this.target?.find('textarea').trigger('change');
      }
    }
    return new Item();
  };
}

/** 重写函数类参数 UI，支持弹窗编辑 */
export const functionCodeOptionItems = [...FUNCTION_OPTION_NAMES].map((name) =>
  createFunctionCodeOption(name),
);

/** 面板纸张宽高（mm）与规格预设 */
export const paperSizeOptionItems = [
  paperPresetOptionItem,
  paperWidthOptionItem,
  paperHeightOptionItem,
];
