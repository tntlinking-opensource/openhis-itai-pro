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
  getCaseRecordByIdApi,
  getHomepageApi,
  refreshAutoFillApi,
  saveHomepageApi,
  submitHomepageApi,
} from '#/api/mrms';
import { useRequest } from '#/hooks/use-request';

import { homepageToFormModel, mergeCaseRecordIntoFormModel } from '../homepage-form.util';

export function useCaseRecordContext(caseRecordId: Ref<string>) {
  const caseRecord = ref<CaseRecordDetailDto | null>(null);
  const homepage = ref<HomepageDto | null>(null);
  const formModel = ref<SaveHomepageParams | null>(null);
  const fieldSources = ref<Record<string, string>>({});
  const formDirty = ref(false);
  const loadError = ref<string | null>(null);
  let loadSeq = 0;

  const { run: runLoadCase, loading: caseLoading } = useRequest(getCaseRecordByIdApi, {
    manual: true,
  });
  const { run: runLoadHomepage, loading: homepageLoading } = useRequest(getHomepageApi, {
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
      caseRecord.value = detail;
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

  /** 按病案主键加载主档与首页 */
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
      const [detail, hp] = await Promise.all([runLoadCase(id), runLoadHomepage(id)]);
      if (seq !== loadSeq) {
        return;
      }
      caseRecord.value = detail;
      applyHomepage(hp);
      formModel.value = mergeCaseRecordIntoFormModel(formModel.value!, detail);
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
    caseLoading,
    homepageLoading,
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
