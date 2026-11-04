package com.exemplo.tvlauncher

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val pacote: String,
    val atividade: String,
    val nome: String,
    val imagem: ImageBitmap,   // já pré-renderizada: a grade só desenha, não decodifica
    val ehBanner: Boolean
)

object AppRepository {

    /** Rode SEMPRE fora da thread principal (Dispatchers.IO). */
    fun carregar(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val meuPacote = ctx.packageName

        fun consultar(categoria: String) =
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(categoria), 0)

        // Leanback primeiro: apps de TV têm banner 16:9 próprio, que é preferido ao ícone
        val candidatos = consultar(Intent.CATEGORY_LEANBACK_LAUNCHER) + consultar(Intent.CATEGORY_LAUNCHER)

        val vistos = HashSet<String>()
        val resultado = ArrayList<AppEntry>(candidatos.size)

        for (ri in candidatos) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == meuPacote) continue          // não lista o próprio launcher
            if (!vistos.add(ai.packageName)) continue          // 1 entrada por app

            val banner = runCatching { ai.loadBanner(pm) }.getOrNull()
            val drawable = banner ?: ri.loadIcon(pm)
            // Tamanhos pequenos de propósito: economiza RAM em boxes de 1–2 GB
            val bmp = if (banner != null) drawable.toBitmap(256, 144) else drawable.toBitmap(128, 128)

            resultado += AppEntry(
                pacote = ai.packageName,
                atividade = ai.name,
                nome = ri.loadLabel(pm).toString(),
                imagem = bmp.asImageBitmap(),
                ehBanner = banner != null
            )
        }
        return resultado.sortedBy { it.nome.lowercase() }
    }
}
