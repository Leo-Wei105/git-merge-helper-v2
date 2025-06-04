package com.gitmergehelper.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

/**
 * 打开Git合并助手设置的操作
 */
class OpenSettingsAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        
        // 打开设置对话框，直接定位到Git合并助手配置页面
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            "Git合并助手"
        )
    }
    
    override fun update(e: AnActionEvent) {
        // 设置操作始终可用
        e.presentation.isEnabled = true
    }
} 