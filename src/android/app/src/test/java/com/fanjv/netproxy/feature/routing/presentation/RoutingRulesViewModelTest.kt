package com.fanjv.netproxy.feature.routing.presentation

import com.fanjv.netproxy.core.api.ApiTestRequest
import com.fanjv.netproxy.core.api.ModuleApiClient
import com.fanjv.netproxy.core.api.TestApiTransport
import com.fanjv.netproxy.core.api.apiSuccess
import com.fanjv.netproxy.feature.apps.data.AppPackageRepository
import com.fanjv.netproxy.feature.routing.model.RuleField
import com.fanjv.netproxy.feature.settings.data.ConfigRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingRulesViewModelTest {
    @Test
    fun appPickerFillsPackageNameIntoDraft() = runBlocking {
        val transport = TestApiTransport { request -> respond(request) }
        val model = RoutingRulesViewModel(
            ConfigRepository(ModuleApiClient(transport)),
            this,
            AppPackageRepository(ModuleApiClient(transport)),
        )

        model.refresh()
        withTimeout(5_000) { model.state.first { !it.isLoading && it.hasDocuments } }
        model.add()
        model.updateDraft(RuleField.PackageName, "")
        assertEquals(RuleField.PackageName, model.state.value.draft?.field)

        model.openAppPicker()
        assertTrue(model.state.value.showAppPicker)
        val listed = withTimeout(5_000) { model.state.first { it.pickerApps.size == 2 } }
        assertEquals(listOf("alpha.example", "beta.example"), listed.pickerApps.map { it.packageName })
        assertEquals("Alpha", listed.pickerApps.first().label)

        model.pickApp("alpha.example")
        assertFalse(model.state.value.showAppPicker)
        assertEquals("alpha.example", model.state.value.draft?.text)

        model.openAppPicker()
        model.pickApp("alpha.example")
        assertEquals("alpha.example", model.state.value.draft?.text)

        model.openAppPicker()
        model.pickApp("beta.example")
        assertEquals("alpha.example\nbeta.example", model.state.value.draft?.text)
    }

    @Test
    fun appPickerRequiresPackageNameDraft() = runBlocking {
        val transport = TestApiTransport { request -> respond(request) }
        val model = RoutingRulesViewModel(
            ConfigRepository(ModuleApiClient(transport)),
            this,
            AppPackageRepository(ModuleApiClient(transport)),
        )

        model.refresh()
        withTimeout(5_000) { model.state.first { !it.isLoading && it.hasDocuments } }
        model.add()
        model.openAppPicker()
        assertFalse(model.state.value.showAppPicker)
    }

    private fun respond(request: ApiTestRequest) = when (request.path) {
        "/config" -> {
            val tag = request.target.removePrefix("singbox/rules/local/").removeSuffix(".json")
            apiSuccess("""{"content":${jsonString("""{"version":1,"rules":[]}""")},"revision":"rev-$tag"}""")
        }
        "/android/apps" -> apiSuccess(APPS_JSON)
        else -> error("Unexpected request ${request.path}")
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                else -> append(char)
            }
        }
        append('"')
    }

    private companion object {
        const val APPS_JSON = """{"users":[{"id":0,"name":"Owner","revision":"apps-1","apps":[
            {"package_name":"alpha.example","label":"Alpha","uid":10001,"system":false,"version_code":1,"last_update_time":0,"icon_key":""},
            {"package_name":"beta.example","label":"Beta","uid":10002,"system":true,"version_code":2,"last_update_time":0,"icon_key":""}
        ]}]}"""
    }
}
