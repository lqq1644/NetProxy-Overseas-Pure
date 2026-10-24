package com.fanjv.netproxy.feature.routing.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanjv.netproxy.core.api.ModuleApiException
import com.fanjv.netproxy.core.ui.userMessage
import com.fanjv.netproxy.feature.apps.data.AppIconCache
import com.fanjv.netproxy.feature.apps.data.AppPackageRepository
import com.fanjv.netproxy.feature.apps.presentation.AppInfoModel
import com.fanjv.netproxy.feature.routing.model.LocalRuleDocument
import com.fanjv.netproxy.feature.routing.model.LocalRuleSet
import com.fanjv.netproxy.feature.routing.model.RuleField
import com.fanjv.netproxy.feature.routing.model.RuleInputError
import com.fanjv.netproxy.feature.routing.model.simpleRule
import com.fanjv.netproxy.feature.settings.data.ConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal data class RuleIdentity(val target: LocalRuleSet, val index: Int)

internal data class RuleDraft(
    val target: LocalRuleSet,
    val index: Int? = null,
    val field: RuleField? = RuleField.DomainSuffix,
    val text: String = "",
)

internal data class RuleFileState(
    val document: LocalRuleDocument? = null,
    val revision: String = "",
    val requiresReload: Boolean = false,
    val invalidDocument: Boolean = false,
    val error: String = "",
)

internal data class RoutingRulesState(
    val files: Map<LocalRuleSet, RuleFileState> = LocalRuleSet.entries.associateWith { RuleFileState() },
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val draft: RuleDraft? = null,
    val deleting: RuleIdentity? = null,
    val inputError: RuleInputError? = null,
    val pickerApps: List<AppInfoModel> = emptyList(),
    val isLoadingPickerApps: Boolean = false,
    val showAppPicker: Boolean = false,
) {
    val rules: List<RuleIdentity> get() = LocalRuleSet.entries.flatMap { target ->
        files.getValue(target).document?.rules?.indices?.map { RuleIdentity(target, it) }.orEmpty()
    }
    val hasDocuments: Boolean get() = files.values.any { it.document != null }
    val isEmpty: Boolean get() = files.values.all { it.document?.rules?.isEmpty() == true && !it.requiresReload && it.error.isEmpty() }
    val canAdd: Boolean get() = LocalRuleSet.entries.any(::editable)

    fun showPageError(target: LocalRuleSet): Boolean = target != draft?.target && target != deleting?.target &&
        files.getValue(target).let { it.error.isNotEmpty() || it.requiresReload }

    fun editable(target: LocalRuleSet): Boolean = files.getValue(target).let {
        it.document != null && !isLoading && !isSaving && !it.requiresReload
    }

    fun withFile(target: LocalRuleSet, file: RuleFileState): RoutingRulesState = copy(files = files + (target to file))
}

internal class RoutingRulesViewModel(
    private val repository: ConfigRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    // 只为包名规则的应用选择器提供已安装应用清单；缺省时不展示选择入口。
    private val packageCatalog: AppPackageRepository? = null,
) : ViewModel(scope) {
    private val mutableState = MutableStateFlow(RoutingRulesState())
    val state = mutableState.asStateFlow()

    fun selectTarget(target: LocalRuleSet) {
        val current = state.value
        val draft = current.draft ?: return
        if (draft.index != null || current.isLoading || current.isSaving) return
        mutableState.update { it.copy(draft = draft.copy(target = target), inputError = null) }
    }

    fun refresh(discardDraft: Boolean = false) {
        val current = state.value
        if (current.isLoading || current.isSaving || (!discardDraft && (current.draft != null || current.deleting != null))) return
        mutableState.update { it.copy(isLoading = true, draft = null, deleting = null, inputError = null) }
        viewModelScope.launch {
            try {
                val files = LocalRuleSet.entries.associateWith { target ->
                    try {
                        val snapshot = repository.readSnapshot(target.target)
                        val document = try {
                            withContext(Dispatchers.Default) { LocalRuleDocument.parse(snapshot.content) }
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            null
                        }
                        RuleFileState(document, snapshot.revision, requiresReload = document == null, invalidDocument = document == null)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        current.files.getValue(target).copy(requiresReload = true, error = error.userMessage())
                    }
                }
                mutableState.update { it.copy(files = files) }
            } finally {
                mutableState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun add() {
        val current = state.value
        if (current.draft != null || current.deleting != null) return
        val target = LocalRuleSet.entries.firstOrNull { current.editable(it) } ?: return
        mutableState.update { it.copy(draft = RuleDraft(target), inputError = null)
            .withFile(target, it.files.getValue(target).copy(error = "")) }
    }

    fun edit(identity: RuleIdentity) {
        val current = state.value
        if (!current.editable(identity.target) || current.draft != null || current.deleting != null) return
        val rule = current.files.getValue(identity.target).document!!.rules.getOrNull(identity.index) ?: return
        val simple = rule.simpleRule()
        mutableState.update { it.copy(draft = RuleDraft(identity.target, identity.index, simple?.field,
            simple?.values?.joinToString("\n").orEmpty()), inputError = null)
            .withFile(identity.target, it.files.getValue(identity.target).copy(error = "")) }
    }

    fun updateDraft(field: RuleField, text: String) {
        val current = state.value
        val draft = current.draft ?: return
        if (current.isSaving || draft.field == null) return
        mutableState.update {
            val file = it.files.getValue(draft.target)
            it.copy(draft = draft.copy(field = field, text = text), inputError = null)
                .withFile(draft.target, file.copy(error = if (file.requiresReload) file.error else ""))
        }
    }

    fun requestDelete(identity: RuleIdentity) {
        val current = state.value
        if (!current.editable(identity.target) || current.files.getValue(identity.target).document!!.rules.getOrNull(identity.index) == null) return
        mutableState.update { it.copy(draft = null, deleting = identity, inputError = null)
            .withFile(identity.target, it.files.getValue(identity.target).copy(error = "")) }
    }

    fun dismiss() {
        if (!state.value.isSaving) mutableState.update {
            it.copy(draft = null, deleting = null, inputError = null, showAppPicker = false)
        }
    }

    fun openAppPicker() {
        val catalog = packageCatalog ?: return
        val current = state.value
        if (current.draft?.field != RuleField.PackageName || current.isSaving) return
        mutableState.update { it.copy(showAppPicker = true) }
        if (current.pickerApps.isNotEmpty() || current.isLoadingPickerApps) return
        mutableState.update { it.copy(isLoadingPickerApps = true) }
        viewModelScope.launch {
            try {
                val snapshot = catalog.snapshot()
                val apps = withContext(Dispatchers.Default) {
                    snapshot.users.flatMap { user ->
                        user.apps.map { app ->
                            AppInfoModel(app.packageName, app.label, user.id.toString(), app.system, app.iconKey)
                        }
                    }
                }
                mutableState.update { it.copy(pickerApps = apps, isLoadingPickerApps = false) }
                loadPickerIcons(apps)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(isLoadingPickerApps = false) }
            }
        }
    }

    fun closeAppPicker() {
        mutableState.update { it.copy(showAppPicker = false) }
    }

    /** 选中应用只把包名追加进草稿文本，已存在的包名不重复添加，实际写入仍走草稿保存路径。 */
    fun pickApp(packageName: String) {
        val draft = state.value.draft ?: return
        if (draft.field != RuleField.PackageName || state.value.isSaving) return
        val values = RuleField.PackageName.values(draft.text)
        if (packageName !in values) {
            updateDraft(RuleField.PackageName, (values + packageName).joinToString("\n"))
        }
        closeAppPicker()
    }

    private fun loadPickerIcons(apps: List<AppInfoModel>) {
        val catalog = packageCatalog ?: return
        viewModelScope.launch {
            val generation = AppIconCache.generation
            val slots = Semaphore(4)
            coroutineScope {
                apps.map { it.iconKey }.distinct()
                    .filter { it.isNotEmpty() && AppIconCache.cachedIcon(it) == null }
                    .map { key ->
                        async {
                            slots.withPermit {
                                try {
                                    AppIconCache.putIcon(key, catalog.icon(key), generation)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }.awaitAll()
            }
        }
    }

    fun save() {
        val current = state.value
        val draft = current.draft ?: return
        val field = draft.field ?: return
        if (!current.editable(draft.target)) return
        val error = field.inputError(draft.text)
        if (error != null) {
            mutableState.update { it.copy(inputError = error) }
            return
        }
        apply(draft.target, current.files.getValue(draft.target).document!!.replace(draft.index, field.rule(draft.text)))
    }

    fun delete() {
        val current = state.value
        val identity = current.deleting ?: return
        if (!current.editable(identity.target)) return
        apply(identity.target, current.files.getValue(identity.target).document!!.remove(identity.index))
    }

    private fun apply(target: LocalRuleSet, document: LocalRuleDocument) {
        val current = state.value
        val file = current.files.getValue(target)
        mutableState.update { it.copy(isSaving = true).withFile(target, file.copy(error = "")) }
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.Default) { document.content() }
                val revision = repository.apply(target.target, content, file.revision)
                mutableState.update { it.copy(isSaving = false, draft = null, deleting = null, inputError = null)
                    .withFile(target, RuleFileState(document, revision)) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val unchanged = if ((error as? ModuleApiException)?.resultCode == "config.conflict") false else try {
                    repository.readSnapshot(target.target).revision == file.revision
                } catch (readError: Exception) {
                    if (readError is CancellationException) throw readError
                    false
                }
                // 仅确认原 revision 未变化时允许修正草稿重试，不拿新 revision 覆盖旧快照。
                mutableState.update { it.copy(isSaving = false).withFile(target, file.copy(requiresReload = !unchanged, error = error.userMessage())) }
            }
        }
    }
}
