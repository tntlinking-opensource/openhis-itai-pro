import '../core/jquery';

import { hiprint } from 'vue-plugin-hiprint';

import { functionCodeOptionItems, paperSizeOptionItems } from './option-items';

/** OpenHIS 设计器 hiprint.setConfig：保留函数项、隐藏 html 面板配置 */
export function applyOpenHisDesignConfig(): void {
  hiprint.setConfig({
    movingDistance: 2.5,
    showPosition: true,
    positionLineMode: true,
    positionUnit: true,
    showAdsorbLine: true,
    optionItems: [...functionCodeOptionItems, ...paperSizeOptionItems],
    panel: {
      supportOptions: [
        { name: 'paperPreset', hidden: false },
        { name: 'paperWidth', hidden: false },
        { name: 'paperHeight', hidden: false },
        { name: 'panelPaperRule', hidden: false },
        { name: 'panelPageRule', hidden: false },
        { name: 'firstPaperFooter', hidden: false },
        { name: 'evenPaperFooter', hidden: false },
        { name: 'oddPaperFooter', hidden: false },
        { name: 'lastPaperFooter', hidden: false },
        { name: 'leftOffset', hidden: false },
        { name: 'topOffset', hidden: false },
        { name: 'panelLayoutOptions', hidden: false },
        { name: 'fontFamily', hidden: false },
        { name: 'orient', hidden: false },
        { name: 'paperNumberDisabled', hidden: false },
        { name: 'paperNumberContinue', hidden: false },
        { name: 'paperNumberFormat', hidden: false },
        { name: 'watermarkOptions', hidden: false },
      ],
    },
    html: {
      supportOptions: [{ name: 'formatter', hidden: true }],
    },
    tableColumn: {
      supportOptions: [{ name: 'field', hidden: false, after: 'title' }],
    },
  });
}
