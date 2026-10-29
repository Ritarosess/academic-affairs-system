import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore, homeOfRole } from '@/stores/auth'
import MainLayout from '@/layouts/MainLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/RegisterView.vue'),
    meta: { title: '注册账号', public: true }
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/common/DashboardView.vue'),
        meta: { title: '工作台', roles: ['ADMIN', 'TEACHER', 'STUDENT'] }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/common/ProfileView.vue'),
        meta: { title: '个人中心', roles: ['ADMIN', 'TEACHER', 'STUDENT'] }
      },

      /* ---------------- 管理员 ---------------- */
      {
        path: 'admin/users',
        name: 'AdminUserManage',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理', roles: ['ADMIN'] }
      },
      {
        path: 'admin/courses',
        name: 'AdminCourseManage',
        component: () => import('@/views/admin/CourseManage.vue'),
        meta: { title: '课程审核', roles: ['ADMIN'] }
      },
      {
        path: 'admin/org',
        name: 'AdminOrgManage',
        component: () => import('@/views/admin/OrgManage.vue'),
        meta: { title: '院系管理', roles: ['ADMIN'] }
      },
      {
        path: 'admin/semesters',
        name: 'AdminSemesterManage',
        component: () => import('@/views/admin/SemesterManage.vue'),
        meta: { title: '学期管理', roles: ['ADMIN'] }
      },
      {
        path: 'admin/warnings',
        name: 'AdminWarningList',
        component: () => import('@/views/admin/WarningList.vue'),
        meta: { title: '学业预警', roles: ['ADMIN'] }
      },
      {
        path: 'admin/registrations',
        name: 'AdminRegistrationManage',
        component: () => import('@/views/admin/RegistrationManage.vue'),
        meta: { title: '注册审核', roles: ['ADMIN'] }
      },
      {
        path: 'admin/mail-settings',
        name: 'AdminMailConfig',
        component: () => import('@/views/admin/MailConfig.vue'),
        meta: { title: '邮件配置', roles: ['ADMIN'] }
      },

      /* ---------------- 教师 ---------------- */
      {
        path: 'teacher/courses',
        name: 'TeacherMyCourses',
        component: () => import('@/views/teacher/MyCourses.vue'),
        meta: { title: '我的课程', roles: ['TEACHER'] }
      },
      {
        path: 'teacher/courses/:courseId/students',
        name: 'TeacherCourseStudents',
        component: () => import('@/views/teacher/CourseStudents.vue'),
        meta: { title: '学生名单与成绩', roles: ['TEACHER'], activeMenu: '/teacher/courses' }
      },
      {
        path: 'teacher/course-applies',
        name: 'TeacherCourseApply',
        component: () => import('@/views/teacher/CourseApply.vue'),
        meta: { title: '课程申报', roles: ['TEACHER'] }
      },

      /* ---------------- 学生 ---------------- */
      {
        path: 'student/courses',
        name: 'StudentCourseSelect',
        component: () => import('@/views/student/CourseSelect.vue'),
        meta: { title: '选课大厅', roles: ['STUDENT'] }
      },
      {
        path: 'student/my-courses',
        name: 'StudentMyCourses',
        component: () => import('@/views/student/MyCourses.vue'),
        meta: { title: '我的课表', roles: ['STUDENT'] }
      },
      {
        path: 'student/scores',
        name: 'StudentMyScores',
        component: () => import('@/views/student/MyScores.vue'),
        meta: { title: '我的成绩', roles: ['STUDENT'] }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/common/NotFound.vue'),
    meta: { title: '页面不存在', public: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const title = to.meta?.title
  document.title = title ? `${title} - 高校教务系统` : '高校教务系统'

  const isPublic = !!to.meta?.public

  // 无令牌：放行公开页，其余跳登录
  if (!auth.token) {
    if (isPublic) return true
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 有令牌但缺少用户信息（例如本地缓存被清理）：补全一次，否则无法判定角色
  if (!auth.user && !auth.profileLoaded) {
    try {
      await auth.fetchProfile()
    } catch (e) {
      // 令牌失效已由拦截器处理
      auth.logout()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  // 已登录再访问登录页：回到角色首页
  if (to.path === '/login') {
    return { path: homeOfRole(auth.role) }
  }

  // 根路径按角色分发
  if (to.path === '/') {
    return { path: homeOfRole(auth.role) }
  }

  // 角色校验
  const roles = to.meta?.roles
  if (Array.isArray(roles) && roles.length > 0 && !roles.includes(auth.role)) {
    ElMessage.error('无权访问该资源')
    return { path: homeOfRole(auth.role) }
  }

  return true
})

export default router
