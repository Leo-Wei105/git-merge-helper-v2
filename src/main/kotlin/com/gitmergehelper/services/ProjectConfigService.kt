package com.gitmergehelper.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.gitmergehelper.model.GitMergeConfig

/**
 * 项目级别的Git合并助手配置服务
 * 允许不同项目使用不同的配置设置
 */
@Service(Service.Level.PROJECT)
@State(
    name = "ProjectGitMergeConfig",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class ProjectConfigService : PersistentStateComponent<ProjectConfigService.State> {
    
    /**
     * 配置状态类
     */
    class State {
        var useProjectConfig: Boolean = false
        var mainBranch: String = ""
        var targetBranches: MutableList<GitMergeHelperSettings.State.TargetBranchState> = mutableListOf()
        var featureBranchPatterns: MutableList<String> = mutableListOf()
    }
    
    private var state = State()
    
    companion object {
        /**
         * 获取项目服务实例
         * 
         * @param project 项目实例
         * @return ProjectConfigService实例
         */
        fun getInstance(project: Project): ProjectConfigService {
            return project.getService(ProjectConfigService::class.java)
        }
    }
    
    override fun getState(): State {
        return state
    }
    
    override fun loadState(state: State) {
        this.state = state
    }
    
    /**
     * 是否使用项目级配置
     * 
     * @return 是否使用项目级配置
     */
    fun isUsingProjectConfig(): Boolean {
        return state.useProjectConfig
    }
    
    /**
     * 设置是否使用项目级配置
     * 
     * @param useProjectConfig 是否使用项目级配置
     */
    fun setUseProjectConfig(useProjectConfig: Boolean) {
        state.useProjectConfig = useProjectConfig
    }
    
    /**
     * 获取有效配置（项目级或全局级）
     * 
     * @return GitMergeConfig实例
     */
    fun getEffectiveConfig(): GitMergeConfig {
        return if (state.useProjectConfig) {
            getProjectConfig()
        } else {
            GitMergeHelperSettings.getInstance().getConfig()
        }
    }
    
    /**
     * 获取项目级配置
     * 
     * @return GitMergeConfig实例
     */
    fun getProjectConfig(): GitMergeConfig {
        val config = GitMergeConfig()
        
        if (state.mainBranch.isNotEmpty()) {
            config.mainBranch = state.mainBranch
        }
        
        config.targetBranches.clear()
        config.targetBranches.addAll(
            state.targetBranches.map { 
                com.gitmergehelper.model.TargetBranch(it.name, it.description)
            }
        )
        
        config.featureBranchPatterns.clear()
        config.featureBranchPatterns.addAll(state.featureBranchPatterns)
        
        return config
    }
    
    /**
     * 保存项目级配置
     * 
     * @param config 要保存的配置
     */
    fun saveProjectConfig(config: GitMergeConfig) {
        state.mainBranch = config.mainBranch
        
        state.targetBranches.clear()
        state.targetBranches.addAll(
            config.targetBranches.map { 
                GitMergeHelperSettings.State.TargetBranchState(it.name, it.description)
            }
        )
        
        state.featureBranchPatterns.clear()
        state.featureBranchPatterns.addAll(config.featureBranchPatterns)
    }
    
    /**
     * 从全局配置初始化项目配置
     */
    fun initFromGlobalConfig() {
        val globalConfig = GitMergeHelperSettings.getInstance().getConfig()
        saveProjectConfig(globalConfig)
        state.useProjectConfig = true
    }
    
    /**
     * 清除项目配置
     */
    fun clearProjectConfig() {
        state.useProjectConfig = false
        state.mainBranch = ""
        state.targetBranches.clear()
        state.featureBranchPatterns.clear()
    }
} 