import { createRouter, createWebHashHistory } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import ReviewReport from '../views/ReviewReport.vue'
import TodoCases from '../views/TodoCases.vue'
import LegalElements from '../views/LegalElements.vue'
import DocumentEntities from '../views/DocumentEntities.vue'
import Summary from '../views/Summary.vue'
import CaseRecommend from '../views/CaseRecommend.vue'
import Organization from '../views/Organization.vue'
import ContentManagement from '../views/ContentManagement.vue'
import PendingTasks from '../views/PendingTasks.vue'
import { hasPermission, hasValidSession } from '../auth/session'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: Login, meta: { title: '登录', public: true } },
  {
    path: '/',
    component: AppLayout,
    children: [
      { path: 'dashboard', component: Dashboard, meta: { title: '首页工作台' } },
      { path: 'review-report', component: ReviewReport, meta: { title: '审查报告生成' } },
      { path: 'todo-cases', component: TodoCases, meta: { title: '待办案件' } },
      { path: 'legal-elements', component: LegalElements, meta: { title: '法律要素识别' } },
      { path: 'document-entities', component: DocumentEntities, meta: { title: '文书实体识别' } },
      { path: 'summary', component: Summary, meta: { title: '案例摘要生成' } },
      { path: 'case-recommend', component: CaseRecommend, meta: { title: '典型案例推送' } },
      {
        path: 'organization',
        component: Organization,
        meta: { title: '组织管理', permission: 'USER_MANAGE' }
      },
      {
        path: 'content-management',
        component: ContentManagement,
        meta: { title: '内容管理', permission: 'CONTENT_MANAGE' }
      },
      { path: 'pending-tasks', component: PendingTasks, meta: { title: '待办任务' } }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const authenticated = hasValidSession()
  if (to.meta.public) {
    return authenticated ? '/dashboard' : true
  }
  if (!authenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.permission && !hasPermission(to.meta.permission)) {
    return '/dashboard'
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - LexPro` : 'LexPro'
})

export default router
