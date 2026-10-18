import type { SetupVxeTable } from './types';

import { defineComponent, watch } from 'vue';

import { usePreferences } from '@zxplat/preferences';

import { useVbenForm } from '@zxplat-core/form-ui';

import PluginRenderElement from '@vxe-ui/plugin-render-element';
import {
  VxeButton,
  VxeCheckbox,

  // VxeFormGather,
  // VxeForm,
  // VxeFormItem,
  VxeIcon,
  VxeInput,
  VxeLoading,
  VxeModal,
  VxeNumberInput,
  VxePager,
  // VxeList,
  // VxeModal,
  // VxeOptgroup,
  // VxeOption,
  VxePulldown,
  // VxeRadio,
  // VxeRadioButton,
  VxeRadioGroup,
  VxeSelect,
  VxeTooltip,
  VxeUI,
  VxeUpload,
  // VxeSwitch,
  // VxeTextarea,
} from 'vxe-pc-ui';
import enUS from 'vxe-pc-ui/lib/language/en-US';
// 导入默认的语言
import zhCN from 'vxe-pc-ui/lib/language/zh-CN';
import { VxeColgroup, VxeColumn, VxeGrid, VxeTable, VxeToolbar } from 'vxe-table';

import 'vxe-pc-ui/styles/cssvar.scss';
import 'vxe-table/styles/cssvar.scss';
// TableLayout 等直接使用 VxeGrid 的路径不会加载 use-vxe-grid 分包，主题样式须在 init 全局引入
import './style.css';

import { extendsDefaultFormatter } from './extends';

// 是否加载过
let isInit = false;

// eslint-disable-next-line import/no-mutable-exports
export let useTableForm: typeof useVbenForm;

// 部分组件，如果没注册，vxe-table 会报错，这里实际没用组件，只是为了不报错，同时可以减少打包体积
const createVirtualComponent = (name = '') => {
  return defineComponent({
    name,
  });
};

export function initVxeTable() {
  if (isInit) {
    return;
  }

  // 挂载 Element Plus 渲染插件，提供 ElInput、ElSelect 等渲染器
  VxeUI.use(PluginRenderElement);

  VxeUI.component(VxeTable);
  VxeUI.component(VxeColumn);
  VxeUI.component(VxeColgroup);
  VxeUI.component(VxeGrid);
  VxeUI.component(VxeToolbar);

  VxeUI.component(VxeButton);
  // VxeUI.component(VxeButtonGroup);
  VxeUI.component(VxeCheckbox);
  // VxeUI.component(VxeCheckboxGroup);
  VxeUI.component(createVirtualComponent('VxeForm'));
  // VxeUI.component(VxeFormGather);
  // VxeUI.component(VxeFormItem);
  VxeUI.component(VxeIcon);
  VxeUI.component(VxeInput);
  // VxeUI.component(VxeList);
  VxeUI.component(VxeLoading);
  VxeUI.component(VxeModal);
  VxeUI.component(VxeNumberInput);
  // VxeUI.component(VxeOptgroup);
  // VxeUI.component(VxeOption);
  VxeUI.component(VxePager);
  VxeUI.component(VxePulldown);
  // VxeUI.component(VxeRadio);
  // VxeUI.component(VxeRadioButton);
  VxeUI.component(VxeRadioGroup);
  VxeUI.component(VxeSelect);
  // VxeUI.component(VxeSwitch);
  // VxeUI.component(VxeTextarea);
  VxeUI.component(VxeTooltip);
  VxeUI.component(VxeUpload);

  isInit = true;
}

export function setupVbenVxeTable(setupOptions: SetupVxeTable) {
  const { configVxeTable, useVbenForm } = setupOptions;

  initVxeTable();
  useTableForm = useVbenForm;

  const { isDark, locale } = usePreferences();

  const localMap = {
    'zh-CN': zhCN,
    'en-US': enUS,
  };

  watch(
    [() => isDark.value, () => locale.value],
    ([isDarkValue, localeValue]) => {
      VxeUI.setTheme(isDarkValue ? 'dark' : 'light');
      VxeUI.setI18n(localeValue, localMap[localeValue]);
      VxeUI.setLanguage(localeValue);
    },
    {
      immediate: true,
    },
  );

  extendsDefaultFormatter(VxeUI);

  configVxeTable(VxeUI);
}
