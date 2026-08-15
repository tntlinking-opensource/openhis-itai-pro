<script setup lang="ts">
import type { HomepageFormModel } from '../homepage-form.types';

import { computed, nextTick, ref } from 'vue';

import { ElAnchor, ElAnchorLink, ElButton, ElForm, ElTag } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

import {
  CASE_RECORD_STATUS_LABEL,
  CASE_RECORD_STATUS_TAG,
  HOMEPAGE_ANCHOR_SECTIONS,
} from '../../mrms.constants';
import HomepageDiagnosisGrid from './homepage-diagnosis-grid.vue';
import HomepageFeeForm from './homepage-fee-form.vue';
import HomepagePatientForm from './homepage-patient-form.vue';
import HomepageProcedureGrid from './homepage-procedure-grid.vue';

defineOptions({ name: 'HomepageEditPanel' });

interface Props {
  formModel: HomepageFormModel;
  statusCode?: string;
  medcasno?: string;
  returnReason?: string;
  readonly?: boolean;
  canSave?: boolean;
  canImport?: boolean;
  canSubmit?: boolean;
  canReceive?: boolean;
  canReturn?: boolean;
  saving?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  statusCode: 'draft',
  medcasno: '',
  returnReason: '',
  readonly: false,
  canSave: true,
  canImport: true,
  canSubmit: true,
  canReceive: false,
  canReturn: false,
  saving: false,
});

const emit = defineEmits<{
  back: [];
  dirty: [];
  save: [];
  submit: [];
  receive: [];
  return: [];
  'import-all': [];
  'import-section': [section: string];
}>();

const scrollRef = ref<HTMLElement | null>(null);

const statusText = computed(
  () => CASE_RECORD_STATUS_LABEL[props.statusCode ?? ''] ?? props.statusCode ?? '',
);
const statusTag = computed(() => CASE_RECORD_STATUS_TAG[props.statusCode ?? ''] ?? 'info');

const displayMedcasno = computed(() => props.formModel.medcasno || props.medcasno || '—');

/** 标脏 */
function markDirty() {
  emit('dirty');
}

/** 滚动到章节 */
async function scrollToSection(id: string) {
  await nextTick();
  const root = scrollRef.value;
  if (!root) {
    return;
  }
  const el = root.querySelector(`#${id}`);
  if (el instanceof HTMLElement) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}

/** 锚点默认跳转拦截 */
function handleAnchorClick(e: MouseEvent) {
  e.preventDefault();
}
</script>

<template>
  <div class="hp-edit">
    <div class="hp-edit__header">
      <div class="hp-edit__meta">
        <ElButton link type="primary" @click="emit('back')">← 返回列表</ElButton>
        <span class="hp-edit__title">病案首页</span>
        <span class="hp-edit__no">病案号 {{ displayMedcasno }}</span>
        <span class="hp-edit__name">{{ formModel.psnName || '—' }}</span>
        <ElTag size="small" effect="light" :type="statusTag">{{ statusText }}</ElTag>
        <span v-if="returnReason" class="hp-edit__return">退回原因：{{ returnReason }}</span>
      </div>
      <div class="hp-edit__actions">
        <ElButton
          v-if="canImport && !readonly"
          plain
          :disabled="saving"
          @click="emit('import-all')"
        >
          全部导入
        </ElButton>
        <ElButton
          v-if="canSave && !readonly"
          type="primary"
          :loading="saving"
          @click="emit('save')"
        >
          保存
        </ElButton>
        <ElButton
          v-if="canSubmit && !readonly"
          type="primary"
          plain
          :disabled="saving"
          @click="emit('submit')"
        >
          提交
        </ElButton>
        <ElButton
          v-if="canReceive"
          type="success"
          plain
          :disabled="saving"
          @click="emit('receive')"
        >
          接收
        </ElButton>
        <ElButton v-if="canReturn" type="warning" plain :disabled="saving" @click="emit('return')">
          退回
        </ElButton>
      </div>
    </div>

    <div class="hp-edit__body">
      <aside class="hp-edit__anchor">
        <ElAnchor :offset="12" @click="handleAnchorClick">
          <ElAnchorLink
            v-for="sec in HOMEPAGE_ANCHOR_SECTIONS"
            :key="sec.id"
            :href="`#${sec.id}`"
            :title="sec.label"
            @click="scrollToSection(sec.id)"
          />
        </ElAnchor>
      </aside>

      <div ref="scrollRef" class="hp-edit__scroll">
        <ElForm :model="formModel" label-width="80px" label-position="right" size="small">
          <section id="sec-inpatient" class="hp-edit__section">
            <div class="hp-edit__section-head">
              <SectionTitle title="住院信息" />
              <ElButton
                v-if="canImport && !readonly"
                link
                type="primary"
                size="small"
                @click="emit('import-section', 'basic')"
              >
                导入本章
              </ElButton>
            </div>
            <HomepagePatientForm
              part="inpatient"
              :model-value="formModel"
              :readonly="readonly"
              @dirty="markDirty"
            />
          </section>

          <section id="sec-diagnosis" class="hp-edit__section">
            <div class="hp-edit__section-head">
              <SectionTitle title="诊断记录" />
              <ElButton
                v-if="canImport && !readonly"
                link
                type="primary"
                size="small"
                @click="emit('import-section', 'diagnosis')"
              >
                导入本章
              </ElButton>
            </div>
            <div class="hp-edit__panel">
              <HomepageDiagnosisGrid
                v-model="formModel.diagnoses"
                :readonly="readonly"
                @dirty="markDirty"
              />
            </div>
          </section>

          <section id="sec-fee" class="hp-edit__section">
            <div class="hp-edit__section-head">
              <SectionTitle title="费用结算" />
              <ElButton
                v-if="canImport && !readonly"
                link
                type="primary"
                size="small"
                @click="emit('import-section', 'fee')"
              >
                导入本章
              </ElButton>
            </div>
            <HomepageFeeForm :model-value="formModel" :readonly="readonly" @dirty="markDirty" />
          </section>

          <section id="sec-procedure" class="hp-edit__section">
            <div class="hp-edit__section-head">
              <SectionTitle title="手术/操作记录" />
              <ElButton
                v-if="canImport && !readonly"
                link
                type="primary"
                size="small"
                @click="emit('import-section', 'procedure')"
              >
                导入本章
              </ElButton>
            </div>
            <div class="hp-edit__panel">
              <HomepageProcedureGrid
                v-model="formModel.procedures"
                :readonly="readonly"
                @dirty="markDirty"
              />
            </div>
          </section>

          <section id="sec-appendix" class="hp-edit__section">
            <div class="hp-edit__section-head">
              <SectionTitle title="病案附页" />
              <ElButton
                v-if="canImport && !readonly"
                link
                type="primary"
                size="small"
                @click="emit('import-section', 'staff')"
              >
                导入本章
              </ElButton>
            </div>
            <HomepagePatientForm
              part="appendix"
              :model-value="formModel"
              :readonly="readonly"
              @dirty="markDirty"
            />
          </section>
        </ElForm>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hp-edit {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--el-fill-color-light);
}

.hp-edit__header {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.hp-edit__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.hp-edit__title {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.hp-edit__no,
.hp-edit__name {
  color: var(--el-text-color-primary);
}

.hp-edit__return {
  color: var(--el-color-danger);
  font-size: 12px;
}

.hp-edit__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.hp-edit__body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.hp-edit__anchor {
  flex-shrink: 0;
  width: 120px;
  padding: 8px 4px;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-extra-light);
}

.hp-edit__scroll {
  flex: 1;
  min-width: 0;
  padding: 8px 10px 24px;
  overflow: auto;
  background: var(--el-fill-color-light);
}

.hp-edit__section {
  margin-bottom: 8px;
}

.hp-edit__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.hp-edit__panel {
  padding: 6px 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 6px;
}
</style>
