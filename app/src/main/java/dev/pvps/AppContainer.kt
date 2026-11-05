package dev.pvps

import android.content.Context
import dev.pvps.data.store.AuditStore
import dev.pvps.data.store.SecureStore
import dev.pvps.data.store.SettingsStore
import dev.pvps.data.store.VaultRepository
import dev.pvps.opencode.OpenCodeRepository
import dev.pvps.ssh.SshManager
import dev.pvps.system.SystemRepository
import dev.pvps.update.UpdateRepository

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val secureStore = SecureStore(context)
    val vault = VaultRepository(secureStore)
    val settings = SettingsStore(context)
    val audit = AuditStore(context)
    val ssh = SshManager(vault, audit, settings)
    val openCode = OpenCodeRepository(ssh, audit, settings)
    val system = SystemRepository(ssh, audit, vault)
    val updates = UpdateRepository(context)
}
