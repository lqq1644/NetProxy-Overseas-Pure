package com.fanjv.netproxy.feature.scenes.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.ui.component.AdaptiveTopAppBar
import com.fanjv.netproxy.core.ui.component.AppSnackbarHost
import com.fanjv.netproxy.core.ui.component.BackIconButton
import com.fanjv.netproxy.core.ui.component.BlurredBar
import com.fanjv.netproxy.core.ui.component.EmptyCatalog
import com.fanjv.netproxy.core.ui.component.SnackbarNoticeEffect
import com.fanjv.netproxy.core.ui.component.rememberAppSnackbarHostState
import com.fanjv.netproxy.core.ui.component.rememberBlurBackdrop
import com.fanjv.netproxy.feature.scenes.model.sceneModeLabel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 自定义场景：保存当前分流快照，点场景恢复，删除只动本地偏好。 */
@Composable
internal fun ScenesScreen(
    onBack: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: ScenesViewModel = netProxyViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = rememberAppSnackbarHostState()
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface

    BackHandler { onBack() }
    LaunchedEffect(Unit) { viewModel.load() }

    SnackbarNoticeEffect(
        eventId = state.noticeId,
        message = state.notice.resolve(),
        isError = false,
        hostState = snackbarHostState,
        onConsumed = viewModel::clearNotice
    )

    Scaffold(
        snackbarHost = {
            AppSnackbarHost(snackbarHostState, Modifier.padding(bottom = bottomPadding))
        },
        topBar = {
            BlurredBar(backdrop) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.scenes_title),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackIconButton(onClick = onBack) },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    SmallTitle(
                        text = stringResource(R.string.scenes_save_section),
                        insideMargin = PaddingValues(
                            start = 14.dp,
                            top = 20.dp,
                            end = 14.dp,
                            bottom = 8.dp,
                        ),
                    )
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = state.nameInput,
                            onValueChange = viewModel::setNameInput,
                            label = stringResource(R.string.scenes_name_label),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(top = 12.dp),
                            keyboardActions = KeyboardActions(onDone = { viewModel.saveCurrent() }),
                        )
                        Text(
                            text = stringResource(R.string.scenes_same_name_hint),
                            color = colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                text = stringResource(R.string.scenes_save),
                                onClick = viewModel::saveCurrent,
                                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
                item {
                    SmallTitle(
                        text = stringResource(R.string.scenes_saved_section),
                        insideMargin = PaddingValues(
                            start = 14.dp,
                            top = 20.dp,
                            end = 14.dp,
                            bottom = 8.dp,
                        ),
                    )
                }
                if (state.scenes.isEmpty()) {
                    item {
                        EmptyCatalog(
                            text = stringResource(R.string.scenes_empty),
                            onRefresh = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(state.scenes, key = { it.name }) { scene ->
                        val modeLabel = sceneModeLabel(
                            scene.mode,
                            stringResource(R.string.dashboard_mode_rule),
                            stringResource(R.string.dashboard_mode_proxy),
                            stringResource(R.string.dashboard_mode_direct),
                        )
                        val selectionText = scene.selectionNodeTag.ifBlank {
                            stringResource(R.string.scenes_selection_auto)
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BasicComponent(
                                title = scene.name,
                                summary = stringResource(
                                    R.string.scenes_summary,
                                    modeLabel,
                                    scene.appConfig.bindings.size,
                                    selectionText,
                                ),
                                onClick = { viewModel.applyScene(scene) },
                                endActions = {
                                    TextButton(
                                        text = stringResource(R.string.scenes_apply),
                                        onClick = { viewModel.applyScene(scene) },
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteScene(scene.name) },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = stringResource(R.string.scenes_delete),
                                        )
                                    }
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item { Spacer(Modifier.height(80.dp + bottomPadding)) }
            }
        }
    }
}
