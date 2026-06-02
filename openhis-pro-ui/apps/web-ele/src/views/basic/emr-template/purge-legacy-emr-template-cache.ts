/** 旧版本地模板列表缓存键（已废弃，进入页面时清除） */
const LEGACY_TEMPLATE_LIST_STORAGE_KEY = 'openhis-emr-templates-v1';

const EDITOR_DRAFT_DB_NAME = 'emrEditorDrafts';
const EDITOR_DRAFT_STORE_NAME = 'drafts';
const EDITOR_DRAFT_DB_VERSION = 1;
/** 模板编辑弹窗曾用该前缀写入 IndexedDB 草稿 */
const LEGACY_TEMPLATE_DRAFT_DOC_ID_PREFIX = 'emr-tpl-';

/** 清除旧版 localStorage 模板列表 */
function purgeLegacyTemplateListStorage(): void {
  try {
    localStorage.removeItem(LEGACY_TEMPLATE_LIST_STORAGE_KEY);
  } catch {
    // ignore
  }
}

/** 清除模板编辑器遗留的 IndexedDB 草稿（docId 以 emr-tpl- 开头） */
function purgeLegacyTemplateEditorDrafts(): Promise<void> {
  if (typeof indexedDB === 'undefined') {
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    const req = indexedDB.open(EDITOR_DRAFT_DB_NAME, EDITOR_DRAFT_DB_VERSION);
    req.onerror = () => resolve();
    req.onsuccess = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(EDITOR_DRAFT_STORE_NAME)) {
        resolve();
        return;
      }
      try {
        const tx = db.transaction(EDITOR_DRAFT_STORE_NAME, 'readwrite');
        const store = tx.objectStore(EDITOR_DRAFT_STORE_NAME);
        const cursorReq = store.openCursor();
        cursorReq.onsuccess = () => {
          const cursor = cursorReq.result;
          if (!cursor) return;
          const record = cursor.value as { docId?: string };
          if (record.docId?.startsWith(LEGACY_TEMPLATE_DRAFT_DOC_ID_PREFIX)) {
            cursor.delete();
          }
          cursor.continue();
        };
        tx.oncomplete = () => resolve();
        tx.onerror = () => resolve();
      } catch {
        resolve();
      }
    };
  });
}

/** 进入病历模板页时调用：清理全部已废弃的前端持久化缓存 */
export function purgeLegacyEmrTemplateCaches(): void {
  purgeLegacyTemplateListStorage();
  purgeLegacyTemplateEditorDrafts().catch(() => {});
}
