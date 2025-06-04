package com.gitmergehelper.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.gitmergehelper.services.GitMergeHelperSettings
import com.gitmergehelper.services.GitMergeService

/**
 * 显示Git状态和配置信息的操作
 */
class ShowStatusAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        
        val settings = GitMergeHelperSettings.getInstance()
        val gitService = GitMergeService.getInstance()
        
        // 获取配置信息
        val config = settings.getConfig()
        val configValidation = config.validate()
        
        // 获取Git状态
        val gitStatus = gitService.getGitStatus(project)
        
        // 构建状态信息
        val statusBuilder = StringBuilder()
        
        // Git状态部分
        statusBuilder.append("=== Git状态信息 ===\n")
        if (gitStatus != null) {
            statusBuilder.append("当前分支: ${gitStatus.currentBranch}\n")
            statusBuilder.append("是否为功能分支: ${if (gitStatus.isFeatureBranch) "是" else "否"}\n")
            statusBuilder.append("未提交更改: ${if (gitStatus.hasUncommittedChanges) "有" else "无"}\n")
            statusBuilder.append("远程状态: ${gitStatus.remoteStatus}\n")
        } else {
            statusBuilder.append("无法获取Git状态（可能不在Git仓库中）\n")
        }
        
        statusBuilder.append("\n")
        
        // 配置信息部分
        statusBuilder.append("=== 配置信息 ===\n")
        statusBuilder.append("主分支: ${config.mainBranch}\n")
        statusBuilder.append("目标分支数量: ${config.targetBranches.size}\n")
        statusBuilder.append("功能分支模式数量: ${config.featureBranchPatterns.size}\n")
        
        statusBuilder.append("\n目标分支列表:\n")
        if (config.targetBranches.isEmpty()) {
            statusBuilder.append("  (无配置)\n")
        } else {
            config.targetBranches.forEach { branch ->
                statusBuilder.append("  • ${branch.name} - ${branch.description}\n")
            }
        }
        
        statusBuilder.append("\n功能分支模式:\n")
        if (config.featureBranchPatterns.isEmpty()) {
            statusBuilder.append("  (无配置)\n")
        } else {
            config.featureBranchPatterns.forEach { pattern ->
                statusBuilder.append("  • $pattern\n")
            }
        }
        
        statusBuilder.append("\n")
        
        // 配置验证结果
        statusBuilder.append("=== 配置验证 ===\n")
        if (configValidation.isValid) {
            statusBuilder.append("配置有效 ✓\n")
        } else {
            statusBuilder.append("配置无效 ✗\n")
            statusBuilder.append("错误信息:\n")
            configValidation.errors.forEach { error ->
                statusBuilder.append("  • $error\n")
            }
        }
        
        // 显示状态对话框
        Messages.showInfoMessage(
            project,
            statusBuilder.toString(),
            "Git合并助手 - 状态信息"
        )
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
} 