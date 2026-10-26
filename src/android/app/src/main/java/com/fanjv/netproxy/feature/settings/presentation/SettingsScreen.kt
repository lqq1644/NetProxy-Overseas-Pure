package com.fanjv.netproxy.feature.settings.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.ui.component.BlurredBar
import com.fanjv.netproxy.core.ui.component.LocalModuleAvailability
import com.fanjv.netproxy.core.ui.component.CardItem
import com.fanjv.netproxy.core.ui.component.groupedCardSection
import com.fanjv.netproxy.core.ui.component.rememberBlurBackdrop
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.Route
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 设置页：模块开关与各功能入口。 */
@Composable
internal fun SettingsScreen(
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    isActive: Boolean = true,
    viewModel: SettingsViewModel = netProxyViewModel()
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val available = LocalModuleAvailability.current?.available == true
    val navigator = LocalNavigator.current

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    LifecycleResumeEffect(isActive, available) {
        viewModel.setVisible(isActive && available)
        onPauseOrDispose { if (isActive && available) viewModel.setVisible(false) }
    }


    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings),
                    scrollBehavior = scrollBehavior
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
                groupedCardSection(
                    keyPrefix = "settings_general",
                    title = { stringResource(R.string.settings_section_general) },
                    items = buildList {
                        if (available) add(CardItem("autoStart") {
                            SwitchPreference(
                                title = stringResource(R.string.auto_start),
                                summary = stringResource(R.string.auto_start_summary),
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Rounded.PowerSettingsNew,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                checked = settings.autoStartEnabled,
                                enabled = settings.hasLoaded,
                                onCheckedChange = { viewModel.setAutoStartEnabled(it) }
                            )
                        })
                        if (available) add(CardItem("blockAds") {
                            SwitchPreference(
                                title = stringResource(R.string.settings_block_ads),
                                summary = stringResource(R.string.settings_block_ads_summary),
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Rounded.Block,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                checked = settings.blockAdsInSplitMode,
                                enabled = settings.hasLoaded,
                                onCheckedChange = { viewModel.setBlockAdsInSplitMode(it) }
                            )
                        })
                        add(CardItem("theme") {
                            ArrowPreference(
                                title = stringResource(R.string.settings_theme),
                                summary = stringResource(R.string.settings_theme_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Palette,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = { navigator.push(Route.ThemeSettings) }
                            )
                        })
                        add(CardItem("expert") {
                            ArrowPreference(
                                title = stringResource(R.string.settings_expert),
                                summary = stringResource(R.string.settings_expert_summary),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Tune,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = { navigator.push(Route.ExpertSettings) }
                            )
                        })
                    },
                )

                groupedCardSection(
                    keyPrefix = "settings_support",
                    title = { stringResource(R.string.settings_section_support) },
                    outerBottomPadding = 12.dp,
                    items = listOf(
                        CardItem("logs") {
                            ArrowPreference(
                                title = stringResource(R.string.logs),
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Rounded.BugReport,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = { navigator.push(Route.Logs) }
                            )
                        },
                        CardItem("about") {
                            ArrowPreference(
                                title = stringResource(R.string.about),
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Rounded.ContactPage,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = { navigator.push(Route.About) }
                            )
                        },
                    ),
                )
                item {
                    Spacer(Modifier.height(80.dp + bottomPadding))
                }
            }
        }
    }
}
