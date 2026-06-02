<script setup lang="ts">
import type { KnowledgeBaseItem } from '#/api/emg/knowledge';

import { Search } from '@element-plus/icons-vue';
import { ElButton, ElTabPane, ElTabs } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

defineProps<{
  activeTab: string;
  chiefComplaintTags: string[];
  chiefComplaintTop30: KnowledgeBaseItem[];
  onOpenSymptomSearch: () => void;
  onToggleChiefComplaint: (name: string) => void;
  onToggleCondition: (name: string) => void;
  selectedConditions: string[];
  symptomTop30: KnowledgeBaseItem[];
}>();

const emit = defineEmits<{
  (e: 'update:activeTab', value: string): void;
}>();

const assessmentTabs = [
  { label: 'AIS 评估', name: 'assessment' },
  { label: 'Barden 压疮风险评估', name: 'assessment-barden' },
  { label: 'Barthel 日常生活能力评估', name: 'assessment-barthel' },
  { label: 'Caprini 血栓风险评估', name: 'assessment-caprini' },
  { label: 'ESI 急诊分诊评估', name: 'assessment-esi' },
  { label: '跌倒风险评估（Morse 类）', name: 'assessment-fall' },
  { label: 'GCS 昏迷评分', name: 'assessment-gcs' },
  { label: '创伤/伤情相关评估', name: 'assessment-hurt' },
  { label: 'MEWS 评估', name: 'assessment-mews' },
  { label: 'Morse 跌倒风险分级', name: 'assessment-morse' },
  { label: '新冠流调/筛查', name: 'assessment-covid' },
  { label: '疼痛评估（FLACC）', name: 'assessment-pain' },
  { label: 'REMS 评估', name: 'assessment-rems' },
  { label: 'Norton 压疮风险评估', name: 'assessment-norton' },
];

function handleTabChange(name: number | string) {
  if (typeof name === 'string') {
    emit('update:activeTab', name);
  }
}
</script>

<template>
  <div class="workbench-right-tabs">
    <SectionTitle title="评估与知识库" />
    <ElTabs
      :model-value="activeTab"
      type="border-card"
      tab-position="right"
      @update:model-value="handleTabChange"
    >
      <ElTabPane label="主诉/症状知识库" name="knowledge">
        <div class="knowledge-tabs">
          <div class="knowledge-block">
            <div class="knowledge-block__head">
              <span class="knowledge-block__title">主诉知识库（多选）</span>
              <span class="knowledge-block__sub">支持多选回填，左侧仍可继续手动输入</span>
            </div>
            <div class="symptom-pill-row">
              <ElButton
                v-for="s in chiefComplaintTop30"
                :key="`chief-${s.id}`"
                :type="chiefComplaintTags.includes(s.name) ? 'primary' : 'default'"
                plain
                size="small"
                round
                class="symptom-pill"
                @click="onToggleChiefComplaint(s.name)"
              >
                {{ s.name }}
              </ElButton>
            </div>
          </div>
          <div class="knowledge-block">
            <div class="knowledge-block__head">
              <span class="knowledge-block__title">症状知识库（多选）</span>
              <span class="knowledge-block__sub">多选后自动同步到左侧症状行</span>
            </div>
            <div class="symptom-pill-row">
              <ElButton
                v-for="s in symptomTop30"
                :key="`symptom-${s.id}`"
                :type="selectedConditions.includes(s.name) ? 'primary' : 'default'"
                plain
                size="small"
                round
                class="symptom-pill"
                @click="onToggleCondition(s.name)"
              >
                {{ s.name }}
              </ElButton>
            </div>
            <div class="symptom-more">
              <ElButton type="primary" link :icon="Search" @click="onOpenSymptomSearch">
                搜索更多症状
              </ElButton>
            </div>
          </div>
        </div>
      </ElTabPane>
      <ElTabPane v-for="tab in assessmentTabs" :key="tab.name" :label="tab.label" :name="tab.name">
        <div class="assessment-tab-empty"></div>
      </ElTabPane>
    </ElTabs>
  </div>
</template>

<style scoped>
.workbench-right-tabs {
  min-height: 0;
  padding: 8px;
}

.triage-right-tabs {
  display: flex;
  flex: 1;
  flex-direction: row-reverse;
  min-height: 0;
  margin-top: 4px;
}

.triage-right-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.triage-right-tabs :deep(.el-tabs__nav-wrap) {
  padding: 6px 0;
}

.triage-right-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.assessment-tab-empty {
  min-height: 1px;
}

.knowledge-tabs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.knowledge-block {
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.knowledge-block__head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.knowledge-block__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.knowledge-block__sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.symptom-pill-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, 72px);
  gap: 8px;
  justify-content: start;
}

.symptom-pill-row :deep(.el-button + .el-button) {
  margin-left: 0;
}

.symptom-pill {
  width: 72px;
  height: 28px;
  padding: 0;
  box-sizing: border-box;
  font-size: 13px;
  justify-content: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.symptom-more {
  margin-top: 4px;
}
</style>
