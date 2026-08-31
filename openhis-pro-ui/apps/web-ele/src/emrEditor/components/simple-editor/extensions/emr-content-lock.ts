/**
 * 文书锁（EmrContentLock）已下线：医生站书写不再拦截结构字。
 * 仅保留 bypass meta 常量，避免历史 setMeta 调用报错。
 */
export const EMR_CONTENT_LOCK_BYPASS_META = 'emrContentLockBypass';
