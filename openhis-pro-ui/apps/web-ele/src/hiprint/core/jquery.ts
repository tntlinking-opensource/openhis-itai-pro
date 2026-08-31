import $ from 'jquery';

type JqueryWindow = Window & { $?: typeof $; jQuery?: typeof $ };

/**
 * 把同一份 jQuery 挂到 window。
 * vue-plugin-hiprint 的 hidraggable 注册在全局 jQuery 上，必须在 import 该库之前完成。
 */
export function installHiprintJquery(): typeof $ {
  const w = window as JqueryWindow;
  w.$ = $;
  w.jQuery = $;
  return $;
}

installHiprintJquery();

export { $ };
