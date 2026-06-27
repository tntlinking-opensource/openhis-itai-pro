import type { Ref } from 'vue';

import type {
  CaseRecordDetailDto,
  HomepageDto,
  RefreshSectionCode,
  SaveHomepageParams,
} from '#/api/mrms/types';

import { ref } from 'vue';

import { ElMessage } from 'element-plus';

import {
  loadHomepagePageApi,
  refreshAutoFillApi,
  saveHomepageApi,
  submitHomepageApi,
} from '#/api/mrms';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { homepageToFormModel, mergeCaseRecordIntoFormModel } from '../homepage-form.util';

function normalizeCaseRecordDetail(
  raw: CaseRecordDetailDto | undefined | null,
): CaseRecordDetailDto | null {
  if (!raw) {
    return null;
  }
  return {
    ...raw,
    caseRecordId: preserveApiIdForRequest(raw.caseRecordId) ?? raw.caseRecordId,
    patientId: preserveApiIdForRequest(raw.patientId),
    encounterId: preserveApiIdForRequest(raw.encounterId),
  };
}

export function useCaseRecordContext(caseRecordId: Ref<string>) {
  const caseRecord = ref<CaseRecordDetailDto | null>(null);
  const homepage = ref<HomepageDto | null>(null);
  const formModel = ref<SaveHomepageParams | null>(null);
  const fieldSources = ref<Record<string, string>>({});
  const formDirty = ref(false);
  const loadError = ref<string | null>(null);
  let loadSeq = 0;

  const { run: runLoadPage, loading: pageLoading } = useRequest(loadHomepagePageApi, {
    manual: true,
  });
  const { run: runSave, loading: saving } = useRequest(saveHomepageApi, {
    manual: true,
    onSuccess: (data) => {
      applyHomepage(data);
      formDirty.value = false;
      ElMessage.success('保存成功');
    },
  });
  const { run: runRefresh, loading: refreshing } = useRequest(refreshAutoFillApi, {
    manual: true,
    onSuccess: (data) => {
      applyHomepage(data);
      formDirty.value = false;
      ElMessage.success('已从 HIS 导入');
    },
  });
  const { run: runSubmit, loading: submitting } = useRequest(submitHomepageApi, {
    manual: true,
    onSuccess: (detail) => {
      caseRecord.value = normalizeCaseRecordDetail(detail);
      formDirty.value = false;
      ElMessage.success('提交成功');
    },
  });

  const loading = ref(false);

  /** 将接口首页数据写入本地表单模型 */
  function applyHomepage(data: HomepageDto) {
    homepage.value = data;
    fieldSources.value = { ...(data.fieldSources ?? {}) };
    formModel.value = homepageToFormModel(data);
  }

  /** 按病案主键加载主档与首页（单次 loadPage） */
  async function loadAll() {
    const id = caseRecordId.value?.trim();
    if (!id) {
      loadError.value = '缺少病案 ID，请从病案列表进入';
      formModel.value = null;
      return;
    }
    const seq = ++loadSeq;
    loading.value = true;
    loadError.value = null;
    try {
      const page = await runLoadPage(id);
      if (seq !== loadSeq) {
        return;
      }
      const detail = normalizeCaseRecordDetail(page?.caseRecord);
      caseRecord.value = detail;
      if (page?.homepage) {
        applyHomepage(page.homepage);
        formModel.value = mergeCaseRecordIntoFormModel(formModel.value!, detail);
      } else {
        formModel.value = null;
        loadError.value = '未加载到病案首页数据';
      }
      formDirty.value = false;
    } catch (e) {
      if (seq !== loadSeq) {
        return;
      }
      loadError.value = (e as Error)?.message || '加载病案首页失败';
      formModel.value = null;
    } finally {
      if (seq === loadSeq) {
        loading.value = false;
      }
    }
  }

  /** 整页保存 */
  async function save() {
    if (!formModel.value?.caseRecordId) {
      return;
    }
    await runSave({ ...formModel.value });
  }

  /** HIS 自动取值 */
  async function refreshSections(sections: RefreshSectionCode[], forceOverwrite = false) {
    if (!formModel.value?.caseRecordId) {
      return;
    }
    await runRefresh({
      caseRecordId: formModel.value.caseRecordId,
      sections,
      forceOverwrite,
    });
  }

  /** 医生提交 */
  async function submit() {
    const id = formModel.value?.caseRecordId;
    if (!id) {
      return;
    }
    await runSubmit(id);
  }

  function markDirty() {
    formDirty.value = true;
  }

  return {
    caseRecord,
    homepage,
    formModel,
    fieldSources,
    formDirty,
    loadError,
    loading,
    pageLoading,
    saving,
    refreshing,
    submitting,
    loadAll,
    save,
    refreshSections,
    submit,
    markDirty,
    applyHomepage,
  };
}
