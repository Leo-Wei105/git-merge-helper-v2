package com.gitmergehelper.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.gitmergehelper.model.TargetBranch
import com.gitmergehelper.services.GitMergeHelperSettings
import com.gitmergehelper.services.GitMergeService
import com.gitmergehelper.utils.NotificationUtils

/**
 * 自动合并分支操作
 * 提供用户界面让用户选择目标分支并执行自动合并流程
 */
class AutoMergeAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        
        // 获取配置和服务
        val settings = GitMergeHelperSettings.getInstance()
        val gitService = GitMergeService.getInstance()
        
        // 验证配置
        val configValidation = settings.validateConfig()
        if (!configValidation.isValid) {
            Messages.showErrorDialog(
                project,
                "配置无效：\n${configValidation.errors.joinToString("\n")}",
                "Git合并助手"
            )
            return
        }
        
        // 检查Git状态
        val gitStatus = gitService.getGitStatus(project)
        if (gitStatus == null) {
            Messages.showErrorDialog(
                project,
                "未找到Git仓库或无法获取Git状态",
                "Git合并助手"
            )
            return
        }
        
        // 检查是否为功能分支
        if (!gitStatus.isFeatureBranch) {
            Messages.showErrorDialog(
                project,
                "当前分支 '${gitStatus.currentBranch}' 不是功能分支。\n" +
                        "请切换到功能分支后再执行合并操作。",
                "Git合并助手"
            )
            return
        }
        
        // 检查是否有未提交的更改
        if (gitStatus.hasUncommittedChanges) {
            val choice = Messages.showYesNoDialog(
                project,
                "检测到未提交的更改。\n" +
                        "您可以选择：\n" +
                        "• 是 - 使用快速提交功能\n" +
                        "• 否 - 手动提交后再执行合并",
                "Git合并助手",
                "快速提交",
                "取消",
                Messages.getQuestionIcon()
            )
            
            if (choice == Messages.YES) {
                // 执行快速提交
                QuickCommitAction().actionPerformed(e)
                return
            } else {
                return
            }
        }
        
        // 显示目标分支选择弹窗
        showTargetBranchSelectionPopup(project)
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
        
        if (project != null) {
            val gitService = GitMergeService.getInstance()
            val gitStatus = gitService.getGitStatus(project)
            
            // 根据Git状态更新显示文本
            if (gitStatus != null) {
                e.presentation.text = if (gitStatus.isFeatureBranch) {
                    "自动合并分支 (${gitStatus.currentBranch})"
                } else {
                    "自动合并分支 (非功能分支)"
                }
            }
        }
    }
    
    /**
     * 显示目标分支选择弹窗
     * 
     * @param project 项目实例
     */
    private fun showTargetBranchSelectionPopup(project: Project) {
        val config = GitMergeHelperSettings.getInstance().getConfig()
        val targetBranches = config.targetBranches
        
        if (targetBranches.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "未配置目标分支。\n请在设置中添加至少一个目标分支。",
                "Git合并助手"
            )
            return
        }
        
        val popupStep = object : BaseListPopupStep<TargetBranch>("选择目标分支", targetBranches) {
            override fun getTextFor(value: TargetBranch): String = value.toString()
            
            override fun onChosen(selectedValue: TargetBranch, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    executeAutoMerge(project, selectedValue.name)
                }
                return PopupStep.FINAL_CHOICE
            }
        }
        
        val popup: ListPopup = JBPopupFactory.getInstance().createListPopup(popupStep)
        popup.showCenteredInCurrentWindow(project)
    }
    
    /**
     * 执行自动合并
     * 
     * @param project 项目实例
     * @param targetBranch 目标分支名称
     */
    private fun executeAutoMerge(project: Project, targetBranch: String) {
        val gitService = GitMergeService.getInstance()
        
        NotificationUtils.showInfo(
            project,
            "开始自动合并到分支 '$targetBranch'...",
            "Git合并助手"
        )
        
        gitService.executeAutoMerge(project, targetBranch) { result ->
            if (result.success) {
                NotificationUtils.showSuccess(
                    project,
                    result.message,
                    "Git合并助手"
                )
                
                // 显示合并结果详情
                if (result.mergedBranches.isNotEmpty()) {
                    val details = result.mergedBranches.joinToString("\n") { 
                        "• ${it.fromBranch} → ${it.toBranch}"
                    }
                    
                    Messages.showInfoMessage(
                        project,
                        "${result.message}\n\n合并详情：\n$details",
                        "合并完成"
                    )
                }
            } else {
                NotificationUtils.showError(
                    project,
                    result.message,
                    "Git合并助手"
                )
                
                // 如果有冲突，提供解决选项
                if (result.conflictFiles.isNotEmpty()) {
                    showConflictResolutionDialog(project, result.conflictFiles)
                }
            }
        }
    }
    
    /**
     * 显示冲突解决对话框
     * 
     * @param project 项目实例
     * @param conflictFiles 冲突文件列表
     */
    private fun showConflictResolutionDialog(project: Project, conflictFiles: List<String>) {
        val conflictList = conflictFiles.joinToString("\n• ", "• ")
        
        val choice = Messages.showYesNoDialog(
            project,
            "检测到合并冲突：\n$conflictList\n\n" +
                    "请手动解决冲突后，选择继续合并操作。",
            "合并冲突",
            "打开冲突文件",
            "取消",
            Messages.getWarningIcon()
        )
        
        if (choice == Messages.YES) {
            // TODO: 实现打开冲突文件的功能
            NotificationUtils.showInfo(
                project,
                "请在编辑器中手动解决冲突，然后提交更改。",
                "Git合并助手"
            )
        }
    }
} 