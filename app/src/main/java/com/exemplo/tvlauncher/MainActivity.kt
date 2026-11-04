package com.exemplo.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val favoritos = mutableStateOf<Set<String>>(emptySet())
    private val homeSignal = mutableIntStateOf(0)
    private val prefs by lazy { getSharedPreferences("launcher", Context.MODE_PRIVATE) }

    // Atualiza a lista quando um app é instalado/removido
    private val receptorPacotes = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = carregarApps()
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        favoritos.value = prefs.getStringSet("favs", emptySet())!!.toSet()
        carregarApps()

        val filtro = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(receptorPacotes, filtro)

        setContent {
            MaterialTheme {
                TelaLauncher(
                    apps = apps.value,
                    favoritos = favoritos.value,
                    homeSignal = homeSignal.intValue,
                    onAbrir = { abrirApp(it) },
                    onAlternarFavorito = { alternarFavorito(it) },
                    onAbrirConfiguracoes = { abrirConfiguracoes() }
                )
            }
        }
    }

    // HOME pressionado com o launcher já na pilha (singleTask) -> volta o foco ao menu
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal.intValue++
    }

    override fun onDestroy() {
        unregisterReceiver(receptorPacotes)
        super.onDestroy()
    }

    private fun carregarApps() {
        lifecycleScope.launch {
            apps.value = withContext(Dispatchers.IO) { AppRepository.carregar(applicationContext) }
        }
    }

    private fun abrirApp(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(app.pacote, app.atividade)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir ${app.nome}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirConfiguracoes() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Configurações indisponíveis", Toast.LENGTH_SHORT).show()
        }
    }

    private fun alternarFavorito(app: AppEntry) {
        val novo = favoritos.value.toMutableSet().apply {
            if (!add(app.pacote)) remove(app.pacote)
        }
        favoritos.value = novo
        prefs.edit().putStringSet("favs", novo).apply()
    }
}
