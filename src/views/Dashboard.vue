<template>
  <div class="page dashboard-page" v-loading="loading">
    <section class="welcome-band">
      <div><span>今日工作台</span><strong>欢迎回来，{{ currentUser?.realName || currentUser?.username || '用户' }}</strong></div>
      <el-button :icon="Refresh" circle title="刷新仪表盘" @click="load" />
    </section>

    <SectionCard title="应用中心">
      <div class="app-grid">
        <AppIconCard
          v-for="app in visibleApps" :key="app.title" :title="app.title" :color="app.color"
          :icon="iconMap[app.icon]" @select="router.push(app.path)"
        />
      </div>
    </SectionCard>

    <div class="metrics-grid">
      <SectionCard title="案件概况">
        <div class="stats-row">
          <StatCard v-for="item in caseCards" :key="item.label" v-bind="item" />
        </div>
      </SectionCard>
      <SectionCard title="我的任务">
        <div class="stats-row">
          <StatCard v-for="item in taskCards" :key="item.label" v-bind="item" />
        </div>
      </SectionCard>
    </div>

    <div class="dashboard-grid">
      <SectionCard title="最近案件">
        <el-table :data="dashboard.recentCases" stripe>
          <el-table-column prop="caseName" label="案件名称" min-width="220" />
          <el-table-column prop="caseType" label="案件类型" width="120" />
          <el-table-column prop="handlerName" label="承办人" width="100" />
          <el-table-column label="更新时间" width="160">
            <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default><el-button type="primary" link @click="router.push('/todo-cases')">查看</el-button></template>
          </el-table-column>
        </el-table>
      </SectionCard>

      <SectionCard title="案件分类">
        <div class="category-summary"><strong>{{ categoryTotal }}</strong><span>可见案件</span></div>
        <div class="category-list">
          <div v-for="(item, index) in dashboard.caseCategories" :key="item.name" class="category-row">
            <span class="category-swatch" :style="{ background: colors[index % colors.length] }"></span>
            <label>{{ item.name }}</label><strong>{{ item.count }}</strong>
            <em>{{ percent(item.count, categoryTotal) }}%</em>
          </div>
          <el-empty v-if="!dashboard.caseCategories.length" :image-size="60" description="暂无案件分类" />
        </div>
      </SectionCard>
    </div>

    <SectionCard v-if="hasPermission('TASK_MANAGE')" title="紧急任务">
      <el-table :data="dashboard.urgentTasks" stripe>
        <el-table-column prop="title" label="任务名称" min-width="220" />
        <el-table-column prop="subjectTitle" label="关联对象" min-width="180" />
        <el-table-column prop="assigneeName" label="负责人" width="110" />
        <el-table-column label="截止时间" width="180">
          <template #default="{ row }"><span :class="{ overdue: row.overdue }">{{ formatDate(row.dueAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default><el-button type="primary" link @click="router.push('/pending-tasks')">进入任务</el-button></template>
        </el-table-column>
      </el-table>
    </SectionCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { Aim, CollectionTag, DocumentChecked, FolderOpened, Reading, Refresh, Search, Tickets } from '@element-plus/icons-vue'
import AppIconCard from '../components/common/AppIconCard.vue'
import SectionCard from '../components/common/SectionCard.vue'
import StatCard from '../components/common/StatCard.vue'
import { currentUser, hasPermission } from '../auth/session'
import { getDashboard } from '../api/workspace'

const router = useRouter()
const loading = ref(false)
const dashboard = ref({
  cases: { total: 0, active: 0, pending: 0, overdue: 0 },
  tasks: { active: 0, dueToday: 0, overdue: 0, waitingConfirmation: 0 },
  caseCategories: [], recentCases: [], urgentTasks: []
})
const apps = [
  { title: '待办案件', icon: 'FolderOpened', color: '#2f72f6', path: '/todo-cases' },
  { title: '法律要素', icon: 'Aim', color: '#18a058', path: '/legal-elements' },
  { title: '实体识别', icon: 'Tickets', color: '#7c5ce7', path: '/document-entities' },
  { title: '报告生成', icon: 'DocumentChecked', color: '#e67e22', path: '/review-report' },
  { title: '典型案例', icon: 'CollectionTag', color: '#d84a7f', path: '/case-recommend' },
  { title: '知识内容', icon: 'Reading', color: '#008d95', path: '/content-management' },
  { title: '待办任务', icon: 'Search', color: '#c0392b', path: '/pending-tasks', permission: 'TASK_MANAGE' }
]
const iconMap = { Aim, CollectionTag, DocumentChecked, FolderOpened, Reading, Search, Tickets }
const colors = ['#2f72f6', '#14b8a6', '#49b735', '#ff8a00', '#e83e8c']
const visibleApps = computed(() => apps.filter((app) => !app.permission || hasPermission(app.permission)))
const categoryTotal = computed(() => dashboard.value.caseCategories.reduce((sum, item) => sum + item.count, 0))
const caseCards = computed(() => metricCards(dashboard.value.cases, [
  ['total', '可见案件', '#2f72f6'], ['active', '在办案件', '#46a834'],
  ['pending', '待处理案件', '#ff8a00'], ['overdue', '超期案件', '#d9363e']
]))
const taskCards = computed(() => metricCards(dashboard.value.tasks, [
  ['active', '进行中', '#2f72f6'], ['dueToday', '今日到期', '#ff8a00'],
  ['overdue', '已超期', '#d9363e'], ['waitingConfirmation', '待确认', '#008d95']
]))

onMounted(load)

async function load() {
  loading.value = true
  try { dashboard.value = await getDashboard() }
  catch (error) { ElMessage.error(error.message) }
  finally { loading.value = false }
}

function metricCards(values, definitions) {
  const total = Math.max(values.total || values.active || 1, 1)
  return definitions.map(([key, label, color]) => ({ label, display: `${values[key] || 0}项`, value: Math.min(100, Math.round(((values[key] || 0) / total) * 100)), color }))
}

function percent(count, total) { return total ? ((count / total) * 100).toFixed(1) : '0.0' }
function formatDate(value) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '-'
}
</script>

<style scoped>
.dashboard-page { display: grid; gap: 16px; }
.welcome-band { display: flex; align-items: center; justify-content: space-between; min-height: 86px; padding: 0 26px; color: #fff; background: #2f72f6; border-radius: 6px; box-shadow: 0 8px 24px rgba(47, 114, 246, .2); }
.welcome-band div { display: grid; gap: 6px; }
.welcome-band span { font-size: 13px; opacity: .85; }
.welcome-band strong { font-size: 18px; }
.app-grid, .stats-row { display: flex; gap: 12px; }
.metrics-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.dashboard-grid { display: grid; grid-template-columns: minmax(0, 1.6fr) minmax(300px, .8fr); gap: 16px; align-items: start; }
.category-summary { display: flex; align-items: baseline; gap: 8px; margin-bottom: 18px; }
.category-summary strong { font-size: 30px; color: var(--primary-deep); }
.category-summary span { color: #64748b; }
.category-list { display: grid; gap: 12px; }
.category-row { display: grid; grid-template-columns: 10px minmax(0, 1fr) 38px 56px; align-items: center; gap: 10px; font-size: 13px; }
.category-swatch { width: 10px; height: 10px; border-radius: 2px; }
.category-row em { color: #64748b; font-style: normal; text-align: right; }
.overdue { color: #d9363e; font-weight: 700; }
@media (max-width: 1100px) {
  .metrics-grid, .dashboard-grid { grid-template-columns: 1fr; }
  .app-grid, .stats-row { flex-wrap: wrap; }
}
</style>
