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
import com.gitmergehelper.model.BranchPrefix
import com.gitmergehelper.services.GitMergeHelperSettings
import com.gitmergehelper.services.GitMergeService
import com.gitmergehelper.utils.NotificationUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 创建Feature分支操作
 * 提供快捷创建feature分支的功能，支持自定义前缀和描述信息
 * 
 * @author Git Merge Helper Team
 * @since 1.1.0
 */
class CreateFeatureBranchAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        
        // 获取配置和服务
        val settings = GitMergeHelperSettings.getInstance()
        val gitService = GitMergeService.getInstance()
        
        // 验证配置
        val configValidation = settings.validateConfig()
        if (!configValidation.isValid) {
            NotificationUtils.showError(
                project,
                "配置验证失败，正在尝试重置配置...",
                "Git合并助手"
            )
            
            // 尝试重置为默认配置
            try {
                settings.resetToDefault()
                NotificationUtils.showInfo(
                    project,
                    "已重置为默认配置，请重试操作",
                    "Git合并助手"
                )
            } catch (ex: Exception) {
                Messages.showErrorDialog(
                    project,
                    "配置无效且无法重置：\n${configValidation.errors.joinToString("\n")}\n\n错误详情: ${ex.message}",
                    "Git合并助手"
                )
                return
            }
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
        
        // 显示分支前缀选择弹窗
        showBranchPrefixSelectionPopup(project)
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
        
        if (project != null) {
            val gitService = GitMergeService.getInstance()
            val gitStatus = gitService.getGitStatus(project)
            
            // 根据Git状态更新显示文本
            if (gitStatus != null) {
                e.presentation.text = "创建Feature分支"
                e.presentation.description = "快捷创建feature分支"
            }
        }
    }
    
    /**
     * 显示分支前缀选择弹窗
     * 
     * @param project 项目实例
     */
    private fun showBranchPrefixSelectionPopup(project: Project) {
        val settings = GitMergeHelperSettings.getInstance()
        var config = settings.getConfig()
        var branchPrefixes = config.branchPrefixes
        
        // 如果分支前缀为空，自动初始化默认配置
        if (branchPrefixes.isEmpty()) {
            NotificationUtils.showInfo(
                project,
                "检测到配置为空，正在初始化默认配置...",
                "Git合并助手"
            )
            
            settings.resetToDefault()
            config = settings.getConfig()
            branchPrefixes = config.branchPrefixes
            
            // 再次检查，如果仍然为空则显示错误
            if (branchPrefixes.isEmpty()) {
                Messages.showErrorDialog(
                    project,
                    "无法初始化配置。\n请手动在设置中添加至少一个分支前缀。",
                    "Git合并助手"
                )
                return
            }
        }
        
        // 如果只有一个前缀，直接使用
        if (branchPrefixes.size == 1) {
            showBaseBranchSelectionDialog(project, branchPrefixes.first())
            return
        }
        
        val popupStep = object : BaseListPopupStep<BranchPrefix>("选择分支前缀", branchPrefixes) {
            override fun getTextFor(value: BranchPrefix): String = value.toString()
            
            override fun onChosen(selectedValue: BranchPrefix, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    showBaseBranchSelectionDialog(project, selectedValue)
                }
                return PopupStep.FINAL_CHOICE
            }
        }
        
        val popup: ListPopup = JBPopupFactory.getInstance().createListPopup(popupStep)
        popup.showCenteredInCurrentWindow(project)
    }
    
    /**
     * 显示基分支选择对话框
     * 
     * @param project 项目实例
     * @param branchPrefix 选择的分支前缀
     */
    private fun showBaseBranchSelectionDialog(project: Project, branchPrefix: BranchPrefix) {
        val gitService = GitMergeService.getInstance()
        val availableBranches = gitService.getAllBranches(project)
        
        if (availableBranches.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "无法获取分支列表，请检查Git仓库状态",
                "Git合并助手"
            )
            return
        }
        
        // 获取当前分支作为默认选项
        val currentBranch = gitService.getCurrentBranch(project)
        val defaultBranch = currentBranch ?: availableBranches.first()
        
        val selectedBranch = Messages.showEditableChooseDialog(
            "请选择基分支：",
            "选择基分支",
            Messages.getQuestionIcon(),
            availableBranches.toTypedArray(),
            defaultBranch,
            null
        )
        
        if (selectedBranch != null) {
            showBranchDescriptionDialog(project, branchPrefix, selectedBranch)
        }
    }
    
    /**
     * 显示分支描述输入对话框
     * 
     * @param project 项目实例
     * @param branchPrefix 选择的分支前缀
     * @param baseBranch 基分支
     */
    private fun showBranchDescriptionDialog(project: Project, branchPrefix: BranchPrefix, baseBranch: String) {
        // 使用更简单的输入对话框
        val description = Messages.showInputDialog(
            project,
            "请输入分支描述信息：\n" +
            "• 只允许字母、数字、中文、下划线和短横线\n" +
            "• 不允许包含空格\n" +
            "• 基分支：$baseBranch\n" +
            "• 生成的分支名格式：${branchPrefix.prefix}/日期/描述信息_用户名",
            "创建${branchPrefix.description}",
            Messages.getQuestionIcon(),
            "",
            null
        )
        
        // 检查用户是否取消或输入为空
        if (description.isNullOrEmpty()) {
            return
        }
        
        val trimmedDescription = description.trim()
        if (trimmedDescription.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "描述信息不能为空",
                "Git合并助手"
            )
            // 递归调用，让用户重新输入
            showBranchDescriptionDialog(project, branchPrefix, baseBranch)
            return
        }
        
        // 验证描述信息
        if (!isValidDescription(trimmedDescription)) {
            Messages.showErrorDialog(
                project,
                "描述信息格式无效：$trimmedDescription\n\n" +
                "要求：\n" +
                "• 只允许字母、数字、中文、下划线(_)和短横线(-)\n" +
                "• 不允许包含空格或其他特殊字符",
                "Git合并助手"
            )
            // 递归调用，让用户重新输入
            showBranchDescriptionDialog(project, branchPrefix, baseBranch)
            return
        }
        
        // 生成分支名称（确保不包含空格）
        val branchName = generateBranchName(branchPrefix.prefix, trimmedDescription, project)
        
        // 验证生成的分支名称
        if (!isValidBranchName(branchName)) {
            Messages.showErrorDialog(
                project,
                "生成的分支名称包含无效字符：$branchName\n\n" +
                "请检查描述信息是否包含空格或特殊字符",
                "Git合并助手"
            )
            showBranchDescriptionDialog(project, branchPrefix, baseBranch)
            return
        }
        
        // 显示确认对话框
        val confirmResult = Messages.showYesNoDialog(
            project,
            "分支创建信息：\n\n" +
            "• 基分支：$baseBranch\n" +
            "• 新分支：$branchName\n" +
            "• 描述：$trimmedDescription\n\n" +
            "确认创建吗？",
            "确认创建分支",
            Messages.getQuestionIcon()
        )
        
        if (confirmResult == Messages.YES) {
            createFeatureBranch(project, branchPrefix, trimmedDescription, baseBranch, branchName)
        }
    }
    
    /**
     * 生成分支名称
     * 
     * @param prefix 分支前缀
     * @param description 描述信息
     * @param project 项目实例
     * @return 生成的分支名称
     */
    private fun generateBranchName(prefix: String, description: String, project: Project): String {
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val gitName = getGitName(project).replace(" ", "_") // 将空格替换为下划线
        
        // 确保描述信息不包含空格（额外保护）
        val safeDescription = description.replace(" ", "_")
        
        return "$prefix/$dateStr/${safeDescription}_$gitName"
    }
    
    /**
     * 获取Git用户名
     * 
     * @param project 项目实例
     * @return Git用户名
     */
    private fun getGitName(project: Project): String {
        val config = GitMergeHelperSettings.getInstance().getConfig()
        
        // 如果配置了自定义Git用户名，优先使用
        if (config.customGitName.isNotEmpty()) {
            return config.customGitName
        }
        
        // 否则尝试获取全局Git用户名
        return GitMergeService.getInstance().getGitUserName(project) ?: "user"
    }
    
    /**
     * 验证描述信息是否有效
     * 
     * @param description 描述信息
     * @return 是否有效
     */
    private fun isValidDescription(description: String): Boolean {
        // 只允许字母、数字、中文、下划线和短横线，不允许空格
        return description.matches(Regex("[a-zA-Z0-9\\u4e00-\\u9fa5_-]+"))
    }
    
    /**
     * 验证分支名称是否有效
     * 
     * @param branchName 分支名称
     * @return 是否有效
     */
    private fun isValidBranchName(branchName: String): Boolean {
        // Git分支名称规则：不能包含空格、特殊字符等
        return branchName.matches(Regex("[a-zA-Z0-9\\u4e00-\\u9fa5_/-]+")) && 
               !branchName.contains("//") && 
               !branchName.startsWith("/") && 
               !branchName.endsWith("/") &&
               !branchName.contains(" ") // 明确禁止空格
    }
    
    /**
     * 创建Feature分支
     * 
     * @param project 项目实例
     * @param branchPrefix 分支前缀
     * @param description 描述信息
     * @param baseBranch 基分支
     * @param branchName 完整分支名称
     */
    private fun createFeatureBranch(
        project: Project, 
        branchPrefix: BranchPrefix, 
        description: String, 
        baseBranch: String,
        branchName: String
    ) {
        val gitService = GitMergeService.getInstance()
        
        NotificationUtils.showInfo(
            project,
            "正在基于分支 '$baseBranch' 创建分支 '$branchName'...",
            "Git合并助手"
        )
        
        gitService.createAndCheckoutBranchFromBase(project, branchName, baseBranch) { result ->
            if (result.success) {
                NotificationUtils.showSuccess(
                    project,
                    "成功创建并切换到分支 '$branchName'",
                    "Git合并助手"
                )
                
                Messages.showInfoMessage(
                    project,
                    "分支创建成功！\n\n" +
                    "• 基分支：$baseBranch\n" +
                    "• 新分支：$branchName\n" +
                    "• 描述：$description",
                    "分支创建成功"
                )
            } else {
                NotificationUtils.showError(
                    project,
                    "分支创建失败：${result.message}",
                    "Git合并助手"
                )
                
                Messages.showErrorDialog(
                    project,
                    "分支创建失败：\n${result.message}\n\n" +
                    "可能的原因：\n" +
                    "• 分支名称包含无效字符（如空格）\n" +
                    "• 分支已存在\n" +
                    "• Git仓库状态异常",
                    "分支创建失败"
                )
            }
        }
    }
} 