package com.fanjv.netproxy.feature.settings.presentation

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.ui.component.AdaptiveTopAppBar
import com.fanjv.netproxy.core.ui.component.BackIconButton
import com.fanjv.netproxy.core.ui.component.BlurredBar
import com.fanjv.netproxy.core.ui.component.CardItem
import com.fanjv.netproxy.core.ui.component.groupedCardSection
import com.fanjv.netproxy.core.ui.component.rememberBlurBackdrop
import com.fanjv.netproxy.navigation.LocalNavigator
import com.fanjv.netproxy.navigation.Route
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 专家设置：高级入口的二级页，日常设置页只留一个入口。 */
@Composable
internal fun ExpertSettingsScreen(
    onBack: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val navigator = LocalNavigator.current

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_expert),
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
                groupedCardSection(
                    keyPrefix = "expert_entries",
                    title = { stringResource(R.string.settings_section_proxy) },
                    items = listOf(
                        CardItem("inbound") {
                            ArrowPreference(
                                title = stringResource(R.string.inbound_settings),
                                summary = stringResource(R.string.inbound_settings_summary),
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Rounded.Router,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = { navigator.push(Route.InboundSettings) }
                            )
                        },
                        CardItem("network") {
                            ArrowPreference(
                                title = stringResource(R.string.network_matching),
                                summary = stringResource(R.string.network_matching_summary),
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Rounded.Wifi,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = { navigator.push(Route.NetworkMatching) }
                            )
                        },
                        CardItem("routing") {
                            ArrowPreference(
                                title = stringResource(R.string.routing_rules),
                                summary = stringResource(R.string.routing_rules_summary),
                                startAction = {
                                    Icon(Icons.AutoMirrored.Rounded.AltRoute, contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp), tint = colorScheme.onBackground)
                                },
                                onClick = { navigator.push(Route.RoutingRules) }
                            )
                        },
                        CardItem("kernel") {
                            ArrowPreference(
                                title = stringResource(R.string.kernel_settings),
                                summary = stringResource(R.string.kernel_settings_summary),
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Rounded.Memory,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = colorScheme.onBackground
                                    )
                                },
                                onClick = { navigator.push(Route.KernelSettings) }
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
