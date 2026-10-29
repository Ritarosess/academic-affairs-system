<template>
  <el-container class="layout-root">
    <el-header class="layout-header">
      <div class="header-left">
        <el-icon class="logo-icon"><School /></el-icon>
        <span class="system-name">高校教务系统</span>
      </div>
      <div class="header-right">
        <el-tag v-if="auth.roleLabel" type="primary" effect="dark" size="small">
          {{ auth.roleLabel }}
        </el-tag>
        <span class="user-name">{{ auth.realName }}</span>
        <el-dropdown @command="handleCommand">
          <span class="dropdown-trigger">
            <el-avatar :size="30" class="user-avatar">
              {{ avatarText }}
            </el-avatar>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><User /></el-icon>个人中心
              </el-dropdown-item>
              <el-dropdown-item command="dashboard">
                <el-icon><HomeFilled /></el-icon>工作台
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container class="layout-body">
      <el-aside width="210px" class="layout-aside">
        <el-menu
          :default-active="activeMenu"
          router
          class="side-menu"
          background-color="#ffffff"
          text-color="#303133"
          active-text-color="#409eff"
        >
          <el-menu-item
            v-for="item in menus"
            :key="item.path"
            :index="item.path"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

/** 按角色过滤的菜单 */
const ALL_MENUS = [
  {
    path: '/dashboard',
    title: '工作台',
    icon: 'HomeFilled',
    roles: ['ADMIN', 'TEACHER', 'STUDENT']
  },
  { path: '/admin/users', title: '用户管理', icon: 'UserFilled', roles: ['ADMIN'] },
  { path: '/admin/courses', title: '课程审核', icon: 'Notebook', roles: ['ADMIN'] },
  { path: '/admin/warnings', title: '学业预警', icon: 'WarnTriangleFilled', roles: ['ADMIN'] },
  { path: '/admin/registrations', title: '注册审核', icon: 'Stamp', roles: ['ADMIN'] },
  { path: '/admin/org', title: '院系管理', icon: 'OfficeBuilding', roles: ['ADMIN'] },
  { path: '/admin/semesters', title: '学期管理', icon: 'Calendar', roles: ['ADMIN'] },
  { path: '/admin/mail-settings', title: '邮件配置', icon: 'Message', roles: ['ADMIN'] },
  { path: '/teacher/course-applies', title: '课程申报', icon: 'EditPen', roles: ['TEACHER'] },
  { path: '/teacher/courses', title: '我的课程', icon: 'Notebook', roles: ['TEACHER'] },
  { path: '/student/courses', title: '选课大厅', icon: 'ShoppingCart', roles: ['STUDENT'] },
  { path: '/student/my-courses', title: '我的课表', icon: 'Calendar', roles: ['STUDENT'] },
  { path: '/student/scores', title: '我的成绩', icon: 'DataLine', roles: ['STUDENT'] },
  { path: '/profile', title: '个人中心', icon: 'User', roles: ['ADMIN', 'TEACHER', 'STUDENT'] }
]

const menus = computed(() =>
  ALL_MENUS.filter((item) => item.roles.includes(auth.role))
)

const activeMenu = computed(() => route.meta?.activeMenu || route.path)

const avatarText = computed(() => {
  const name = auth.realName
  return name ? name.slice(0, 1) : '?'
})

async function handleCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'dashboard') {
    router.push('/dashboard')
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch (e) {
      return
    }
    auth.logout()
    ElMessage.success('已退出登录')
    router.replace('/login')
  }
}
</script>

<style scoped>
.layout-root {
  height: 100vh;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 20px;
  background: linear-gradient(90deg, #1f4e9c 0%, #2f6fd0 100%);
  color: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  font-size: 24px;
}

.system-name {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 1px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  font-size: 14px;
}

.dropdown-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #fff;
  outline: none;
}

.user-avatar {
  background: #ffffff33;
  color: #fff;
  font-weight: 600;
}

.layout-body {
  height: calc(100vh - 60px);
}

.layout-aside {
  background: #fff;
  border-right: 1px solid #e6e8eb;
  overflow-y: auto;
}

.side-menu {
  border-right: none;
}

.layout-main {
  padding: 0;
  background: #f0f2f5;
  overflow-y: auto;
}
</style>
