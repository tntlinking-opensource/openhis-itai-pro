import { defaultElementTypeProvider, disAutoConnect, hiprint } from 'vue-plugin-hiprint';

import $ from 'jquery';

import { applyOpenHisDesignConfig } from '../config/design-config';

let inited = false;

/** 挂载 jQuery 并初始化 hiprint（设计器不连打印客户端） */
export function ensureHiprintReady(): typeof hiprint {
  if (!inited) {
    const w = window as Window & { $?: typeof $; jQuery?: typeof $ };
    w.$ = $;
    w.jQuery = $;
    disAutoConnect();
    hiprint.init({
      providers: [new defaultElementTypeProvider()], // eslint-disable-line new-cap
      lang: 'cn',
    });
    applyOpenHisDesignConfig();
    inited = true;
  }
  return hiprint;
}

export { $, disAutoConnect, hiprint };
