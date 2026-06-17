/** 医生站病历表单注册的「放弃未保存」确认；切换 Tab / 换患者前由宿主调用 */
type ConfirmDiscardFn = () => Promise<boolean>;

let activeConfirm: ConfirmDiscardFn | null = null;
/** 退出登录中：跳过 onBeforeRouteLeave 未保存确认，避免路由导航挂起 */
let forceLogoutSession = false;

/** 病历组件挂载时注册，卸载时传 null 注销 */
export function registerEmrUnsavedGuard(fn: ConfirmDiscardFn | null) {
  activeConfirm = fn;
}

/** 退出登录前调用，避免病历离开守卫阻塞 router 导航 */
export function beginForceLogoutSession() {
  forceLogoutSession = true;
}

/** 退出登录流程结束（含跳转失败可重试）后重置 */
export function endForceLogoutSession() {
  forceLogoutSession = false;
}

export function isForceLogoutSession() {
  return forceLogoutSession;
}

/**
 * 若当前有未保存修改则弹窗确认；无注册或无脏数据时返回 true。
 */
export async function confirmEmrDiscardUnsaved(): Promise<boolean> {
  if (!activeConfirm) return true;
  return activeConfirm();
}
