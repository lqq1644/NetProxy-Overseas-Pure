package com.fanjv.netproxy.feature.scenes.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.module.ServiceRepository
import com.fanjv.netproxy.core.ui.UiText
import com.fanjv.netproxy.core.ui.toUiText
import com.fanjv.netproxy.core.ui.userMessage
import com.fanjv.netproxy.feature.apps.data.AppPolicyRepository
import com.fanjv.netproxy.feature.catalog.data.NodeRepository
import com.fanjv.netproxy.feature.scenes.data.ScenePreferenceStore
import com.fanjv.netproxy.feature.scenes.model.SceneSnapshot
import com.fanjv.netproxy.feature.scenes.model.sanitizedSceneBindings
import com.fanjv.netproxy.feature.scenes.model.selectionNodeTag
import com.fanjv.netproxy.feature.scenes.model.validSceneBindings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ScenesUiState(
    val scenes: List<SceneSnapshot> = emptyList(),
    val nameInput: String = "",
    val busy: Boolean = false,
    val notice: UiText = UiText.Empty,
    val noticeId: Long = 0,
)

/** 自定义场景：保存当前分流快照，点场景名恢复，删除只动本地偏好。 */
internal class ScenesViewModel(
    private val serviceRepository: ServiceRepository,
    private val appPolicyRepository: AppPolicyRepository,
    private val nodeRepository: NodeRepository,
    private val sceneStore: ScenePreferenceStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ScenesUiState())
    val state: StateFlow<ScenesUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(scenes = sceneStore.scenes()) }
    }

    fun setNameInput(value: String) {
        _state.update { it.copy(nameInput = value) }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = UiText.Empty) }
    }

    fun saveCurrent() {
        val name = _state.value.nameInput.trim()
        if (name.isEmpty()) {
            postNotice(UiText.Resource(R.string.scenes_name_empty))
            return
        }
        runSceneOp {
            val status = serviceRepository.status()
            val appConfig = appPolicyRepository.config()
            val selection = nodeRepository.snapshot().selection
            val mode = status.outboundMode.takeIf { it != "unknown" && it.isNotBlank() }
                ?: status.configuredOutboundMode
            val scene = SceneSnapshot(
                name = name,
                mode = mode,
                appConfig = appConfig.copy(bindings = appConfig.bindings.sanitizedSceneBindings()),
                selectionGroupId = selection.activeGroupId,
                selectionNodeTag = selectionNodeTag(
                    selection.selectorMode,
                    selection.activeGroupId,
                    selection.selected
                ),
            )
            sceneStore.save(scene)
            _state.update { it.copy(scenes = sceneStore.scenes(), nameInput = "") }
            UiText.Resource(R.string.scenes_saved_notice, listOf(name))
        }
    }

    fun applyScene(scene: SceneSnapshot) {
        runSceneOp {
            val status = serviceRepository.status()
            val snapshot = nodeRepository.snapshot()
            val validTags = snapshot.groups.flatMap { group -> group.nodes.map { it.tag } }.toSet()
            var modeSkipped = false
            var selectionSkipped = false
            if (scene.mode in status.availableOutboundModes) {
                serviceRepository.setMode(scene.mode)
            } else {
                modeSkipped = true
            }
            val (keptBindings, skippedBindings) = scene.appConfig.bindings.validSceneBindings(validTags)
            val current = appPolicyRepository.config()
            appPolicyRepository.apply(
                current.copy(
                    enabled = scene.appConfig.enabled,
                    mode = scene.appConfig.mode,
                    proxyApps = scene.appConfig.proxyApps,
                    bypassApps = scene.appConfig.bypassApps,
                    bindings = keptBindings,
                )
            )
            if (scene.selectionGroupId.isNotBlank()) {
                val group = snapshot.groups.firstOrNull { it.group.id == scene.selectionGroupId }
                if (group == null) {
                    selectionSkipped = true
                } else if (scene.selectionNodeTag.isBlank()) {
                    nodeRepository.selectAuto(scene.selectionGroupId)
                } else if (group.nodes.any { it.tag == scene.selectionNodeTag }) {
                    nodeRepository.select("${scene.selectionGroupId}/${scene.selectionNodeTag}")
                } else {
                    selectionSkipped = true
                }
            }
            when {
                skippedBindings > 0 -> UiText.Resource(
                    R.string.scenes_applied_partial_notice,
                    listOf(scene.name, skippedBindings)
                )
                modeSkipped || selectionSkipped -> UiText.Resource(
                    R.string.scenes_applied_kept_notice,
                    listOf(scene.name)
                )
                else -> UiText.Resource(R.string.scenes_applied_notice, listOf(scene.name))
            }
        }
    }

    fun deleteScene(name: String) {
        sceneStore.delete(name)
        _state.update { it.copy(scenes = sceneStore.scenes()) }
        postNotice(UiText.Resource(R.string.scenes_deleted_notice, listOf(name)))
    }

    private fun runSceneOp(block: suspend () -> UiText) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = runCatching { block() }
            _state.update {
                it.copy(
                    busy = false,
                    notice = result.fold({ message -> message }, { error -> error.userMessage().toUiText() }),
                    noticeId = it.noticeId + 1,
                )
            }
        }
    }

    private fun postNotice(notice: UiText) {
        _state.update { it.copy(notice = notice, noticeId = it.noticeId + 1) }
    }
}
