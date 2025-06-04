package com.gitmergehelper.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.gitmergehelper.model.GitMergeConfig
import com.intellij.openapi.project.Project

/**
 * Git合并助手配置管理服务
 * 使用IntelliJ平台的PersistentStateComponent来持久化配置
 */
@Service(Service.Level.APP)
@State(
    name = "GitMergeHelperSettings",
    storages = [Storage("gitMergeHelper.xml")]
)
class GitMergeHelperSettings : PersistentStateComponent<GitMergeHelperSettings.State> {
    
    /**
     * 配置状态类
     */
    class State {
        var mainBranch: String = "main"
        var targetBranches: MutableList<TargetBranchState> = mutableListOf()
        var featureBranchPatterns: MutableList<String> = mutableListOf()
        
        /**
         * 目标分支状态
         */
        class TargetBranchState {
            var name: String = ""
            var description: String = ""
            
            constructor()
            constructor(name: String, description: String) {
                this.name = name
                this.description = description
            }
        }
    }
    
    private var state = State()
    
    companion object {
        /**
         * 获取服务实例
         * 
         * @return GitMergeHelperSettings实例
         */
        fun getInstance(): GitMergeHelperSettings {
            return ApplicationManager.getApplication().getService(GitMergeHelperSettings::class.java)
        }
    }
    
    override fun getState(): State {
        return state
    }
    
    override fun loadState(state: State) {
        this.state = state
        
        // 如果配置为空，加载默认配置
        if (state.targetBranches.isEmpty() && state.featureBranchPatterns.isEmpty()) {
            loadDefaultConfig()
        }
    }
    
    /**
     * 获取当前配置
     * 
     * @return GitMergeConfig实例
     */
    fun getConfig(): GitMergeConfig {
        val config = GitMergeConfig()
        config.mainBranch = state.mainBranch
        
        // 转换目标分支
        config.targetBranches.clear()
        config.targetBranches.addAll(
            state.targetBranches.map { 
                com.gitmergehelper.model.TargetBranch(it.name, it.description)
            }
        )
        
        // 转换功能分支模式
        config.featureBranchPatterns.clear()
        config.featureBranchPatterns.addAll(state.featureBranchPatterns)
        
        return config
    }
    
    /**
     * 保存配置
     * 
     * @param config 要保存的配置
     */
    fun saveConfig(config: GitMergeConfig) {
        state.mainBranch = config.mainBranch
        
        // 转换目标分支
        state.targetBranches.clear()
        state.targetBranches.addAll(
            config.targetBranches.map { 
                State.TargetBranchState(it.name, it.description)
            }
        )
        
        // 转换功能分支模式
        state.featureBranchPatterns.clear()
        state.featureBranchPatterns.addAll(config.featureBranchPatterns)
    }
    
    /**
     * 重置为默认配置
     */
    fun resetToDefault() {
        loadDefaultConfig()
    }
    
    /**
     * 加载默认配置
     */
    private fun loadDefaultConfig() {
        val defaultConfig = GitMergeConfig.getDefault()
        saveConfig(defaultConfig)
    }
    
    /**
     * 验证当前配置
     * 
     * @return 验证结果
     */
    fun validateConfig(): com.gitmergehelper.model.ConfigValidationResult {
        return getConfig().validate()
    }
    
    /**
     * 重置全局配置为默认
     */
    fun resetGlobalConfigToDefault() {
        GitMergeHelperSettings.getInstance().resetToDefault()
    }
    
    /**
     * 重置项目级配置为全局默认
     */
    fun resetProjectConfigToDefault(project: Project) {
        ProjectConfigService.getInstance(project).initFromGlobalConfig()
    }
} 