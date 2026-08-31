import { $, installHiprintJquery } from './jquery';
import { isHiprintEngineInited, markHiprintEngineInited } from './engine-state';

import { defaultElementTypeProvider, disAutoConnect, hiprint } from 'vue-plugin-hiprint';

import { applyOpenHisDesignConfig } from '../config/design-config';

let designConfigApplied = false;

/**
 * 关闭默认表格的「表头 title#field」编辑，并去掉 provider 自带 field，
 * 避免拖入/带 tid 加载时把业务列表字段（如 orderRows）覆盖成 table 等默认值。
 */
function patchDefaultTableElementTypes(): void {
  // Vite 对 UMD 的 named export 抽不齐，updateElementType 在 hiprint 对象上
  const patchType = hiprint.updateElementType;
  if (typeof patchType !== 'function') {
    console.warn('[hiprint] updateElementType 不可用，跳过默认表格类型修补');
    return;
  }

  const disableHeaderFieldEdit = (type: Record<string, unknown> | null | undefined) => {
    if (!type || typeof type !== 'object') {
      return type;
    }
    type.isEnableEditField = false;
    delete type.field;
    return type;
  };

  try {
    patchType('defaultModule.table', disableHeaderFieldEdit);
    patchType('defaultModule.emptyTable', disableHeaderFieldEdit);
  } catch (error) {
    console.warn('[hiprint] 修补默认表格类型失败', error);
  }
}

/** 打印运行时已 init 时标记，避免设计器再 init 一次把 hidraggable/provider 搞乱 */
export { markHiprintEngineInited } from './engine-state';

/** 只初始化 hiprint 引擎（预览/getHtml 用，不套设计器 setConfig） */
export function ensureHiprintPrintEngine(): typeof hiprint {
  installHiprintJquery();
  if (!isHiprintEngineInited()) {
    try {
      disAutoConnect();
    } catch (error) {
      console.warn('[hiprint] disAutoConnect 失败', error);
    }
    hiprint.init({
      providers: [new defaultElementTypeProvider()], // eslint-disable-line new-cap
      lang: 'cn',
    });
    markHiprintEngineInited();
  }
  return hiprint;
}

/** 挂载 jQuery 并初始化 hiprint 设计器配置（画布编辑用） */
export function ensureHiprintReady(): typeof hiprint {
  ensureHiprintPrintEngine();
  if (!designConfigApplied) {
    patchDefaultTableElementTypes();
    applyOpenHisDesignConfig();
    designConfigApplied = true;
  }
  return hiprint;
}

export { $, disAutoConnect, hiprint };
