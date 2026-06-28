import type { FunctionEditorContext } from '../core/types';

type OpenFn = (ctx: FunctionEditorContext) => void;

let openFunctionEditor: null | OpenFn = null;

/** 注册函数代码编辑器打开回调（由 HiprintDesigner 挂载） */
export function bindFunctionEditorBridge(open: OpenFn): void {
  openFunctionEditor = open;
}

export function requestOpenFunctionEditor(ctx: FunctionEditorContext): void {
  openFunctionEditor?.(ctx);
}
