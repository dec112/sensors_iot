package run.jkdev.dec4iot.jetpack.gms

import android.util.Log
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleAvailabilityResponse
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallClient
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallResponse
import run.jkdev.dec4iot.jetpack.TAG
import run.jkdev.dec4iot.jetpack.publicApplicationContext

class GMSModuleRequestClient {
    private fun getInstallClient(): ModuleInstallClient {
        return ModuleInstall.getClient(publicApplicationContext)
    }

    fun requestInstall(module: OptionalModuleApi) {
        this.getInstallClient()
            .deferredInstall(module)
    }

    fun urgentInstall(module: OptionalModuleApi, statusListener: InstallStatusListener, successListener: (ModuleInstallResponse) -> Unit) {
        val moduleInstallRequest = ModuleInstallRequest.newBuilder()
            .addApi(module)
            .setListener(statusListener)
            .build()

        this.getInstallClient()
            .installModules(moduleInstallRequest)
            .addOnSuccessListener { successListener(it) }
            .addOnFailureListener { Log.e(TAG, "Urgent Module Install Failure", it) }
    }

    fun urgentMultipleInstall(modules: Array<OptionalModuleApi>, statusListener: InstallStatusListener, successListener: (ModuleInstallResponse) -> Unit) {
        val moduleInstallRequestBuilder = ModuleInstallRequest.newBuilder()

        for(i in modules) {
            moduleInstallRequestBuilder.addApi(i)
        }

        moduleInstallRequestBuilder.setListener(statusListener)

        this.getInstallClient()
            .installModules(moduleInstallRequestBuilder.build())
            .addOnSuccessListener { successListener(it) }
            .addOnFailureListener { Log.e(TAG, "Urgent Module Install Failure", it) }
    }

    fun checkAvailability(module: OptionalModuleApi, successListener: (ModuleAvailabilityResponse) -> Unit) {
        this.getInstallClient()
            .areModulesAvailable(module)
            .addOnSuccessListener { successListener(it) }
            .addOnFailureListener { Log.e(TAG, "Module Availability Check Failure", it) }
    }
}