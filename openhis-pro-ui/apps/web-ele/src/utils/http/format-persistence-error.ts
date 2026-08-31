/** Hibernate / JPA 乐观锁、并发更新类错误原文特征。 */
export function isPersistenceConcurrencyMessage(msg: string): boolean {
  const text = String(msg ?? '').trim();
  if (!text) {
    return false;
  }
  return (
    text.includes('Row was updated or deleted by another transaction') ||
    text.includes('OptimisticLockingFailureException') ||
    text.includes('StaleObjectStateException') ||
    text.includes('ObjectOptimisticLockingFailureException') ||
    text.includes('unsaved-value mapping was incorrect')
  );
}

/** 将并发保存冲突转为用户可读文案；非此类错误返回 null。 */
export function formatPersistenceConflictError(rawMessage: string): null | string {
  if (!isPersistenceConcurrencyMessage(rawMessage)) {
    return null;
  }
  return '数据保存发生冲突（可能同时有多条记录在写入），请刷新后重试。';
}
