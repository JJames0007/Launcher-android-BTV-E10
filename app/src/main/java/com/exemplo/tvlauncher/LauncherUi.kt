package com.exemplo.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import java.util.Date

// ---------- Design tokens (10-foot UI) ----------
private val Fundo = Color(0xFF0E1116)
private val Superficie = Color(0xFF1B2029)
private val AreaImagem = Color(0xFF252B36)
private val Destaque = Color.White
private val Acento = Color(0xFF3D8BFF)
private val TextoSuave = Color(0xFFB8C0CC)
private val Titulo = 40.sp
private val Subtitulo = 28.sp
private val Corpo = 22.sp
private val MargemH = 48.dp   // área segura contra overscan
private val MargemV = 27.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TelaLauncher(
    apps: List<AppEntry>,
    favoritos: Set<String>,
    homeSignal: Int,
    onAbrir: (AppEntry) -> Unit,
    onAlternarFavorito: (AppEntry) -> Unit,
    onAbrirConfiguracoes: () -> Unit
) {
    // 0 = Favoritos, 1 = Todos os Apps (Configurações é ação, não página)
    var selecionado by remember { mutableIntStateOf(if (favoritos.isNotEmpty()) 0 else 1) }
    var conteudoTemFoco by remember { mutableStateOf(false) }
    val focoMenu = remember { List(3) { FocusRequester() } }
    val focusManager = LocalFocusManager.current

    // Foco inicial e também ao apertar HOME com o launcher já aberto
    LaunchedEffect(homeSignal) { runCatching { focoMenu[selecionado].requestFocus() } }

    // Launcher nunca "fecha": VOLTAR só sobe do conteúdo para o menu
    BackHandler {
        if (conteudoTemFoco) runCatching { focoMenu[selecionado].requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Fundo)
            .padding(horizontal = MargemH, vertical = MargemV)
    ) {
        Cabecalho()

        Row(Modifier.weight(1f)) {
            // ---------------- Menu lateral ----------------
            Column(
                Modifier.width(280.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ItemMenu(
                    "★  Favoritos", ativo = selecionado == 0,
                    modifier = Modifier.focusRequester(focoMenu[0]),
                    aoFocar = { selecionado = 0 },
                    onClick = { focusManager.moveFocus(FocusDirection.Right) }
                )
                ItemMenu(
                    "Todos os Apps", ativo = selecionado == 1,
                    modifier = Modifier.focusRequester(focoMenu[1]),
                    aoFocar = { selecionado = 1 },
                    onClick = { focusManager.moveFocus(FocusDirection.Right) }
                )
                Spacer(Modifier.weight(1f))
                ItemMenu(
                    "Configurações", ativo = false,
                    modifier = Modifier.focusRequester(focoMenu[2]),
                    aoFocar = null,
                    onClick = onAbrirConfiguracoes
                )
            }

            Spacer(Modifier.width(32.dp))

            // ---------------- Conteúdo ----------------
            Column(
                Modifier.weight(1f).onFocusChanged { conteudoTemFoco = it.hasFocus }
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (selecionado == 0) "Favoritos" else "Todos os Apps",
                        fontSize = Titulo, fontWeight = FontWeight.Bold, color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    Text("Segure OK para fixar ★", fontSize = Corpo, color = TextoSuave)
                }
                Spacer(Modifier.height(16.dp))

                val lista = remember(apps, favoritos, selecionado) {
                    if (selecionado == 0) apps.filter { it.pacote in favoritos } else apps
                }

                if (lista.isEmpty()) {
                    Text(
                        if (selecionado == 0)
                            "Nenhum favorito ainda.\nEm “Todos os Apps”, segure OK sobre um app para fixá-lo."
                        else "Carregando aplicativos…",
                        fontSize = Subtitulo, color = TextoSuave
                    )
                } else {
                    // key() reinicia a rolagem ao trocar de categoria
                    key(selecionado) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 220.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            contentPadding = PaddingValues(8.dp), // evita cortar borda/escala do foco
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(lista, key = { it.pacote }) { app ->
                                CartaoApp(
                                    app = app,
                                    favorito = app.pacote in favoritos,
                                    onClick = { onAbrir(app) },
                                    onLongClick = { onAlternarFavorito(app) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================= Componentes =======================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ItemMenu(
    texto: String,
    ativo: Boolean,
    modifier: Modifier = Modifier,
    aoFocar: (() -> Unit)?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            // Trocar de categoria já ao FOCAR (sem exigir OK) deixa a navegação mais rápida
            .onFocusChanged { if (it.isFocused) aoFocar?.invoke() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (ativo) Superficie else Color.Transparent,
            contentColor = TextoSuave,
            focusedContainerColor = Acento,
            focusedContentColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Destaque), shape = RoundedCornerShape(12.dp))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
            Text(texto, fontSize = Subtitulo, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CartaoApp(
    app: AppEntry,
    favorito: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth().height(176.dp),
        shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
        colors = CardDefaults.colors(
            containerColor = Superficie,
            contentColor = Color.White,
            focusedContainerColor = Superficie,
            focusedContentColor = Color.White
        ),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(5.dp, Destaque), shape = RoundedCornerShape(14.dp))
        ),
        scale = CardDefaults.scale(focusedScale = 1.08f)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(112.dp).background(AreaImagem)) {
                Image(
                    bitmap = app.imagem,
                    contentDescription = null,
                    contentScale = if (app.ehBanner) ContentScale.Crop else ContentScale.Fit,
                    modifier = if (app.ehBanner) Modifier.fillMaxSize()
                               else Modifier.fillMaxSize().padding(16.dp)
                )
                if (favorito) {
                    Text(
                        "★", fontSize = Corpo, color = Color(0xFFFFD54F),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    )
                }
            }
            Box(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    app.nome, fontSize = Corpo, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ======================= Cabeçalho: rede + relógio =======================

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Cabecalho() {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IndicadorRede()
        Spacer(Modifier.width(28.dp))
        Relogio()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Relogio() {
    val ctx = LocalContext.current
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // ACTION_TIME_TICK dispara a cada minuto e só funciona registrado dinamicamente.
    // Sem loop/polling: custo zero entre um tick e outro.
    DisposableEffect(Unit) {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { agora = System.currentTimeMillis() }
        }
        val filtro = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ctx.registerReceiver(receptor, filtro)
        agora = System.currentTimeMillis()
        onDispose { ctx.unregisterReceiver(receptor) }
    }

    val formato = remember { DateFormat.getTimeFormat(ctx) } // respeita 12h/24h do sistema
    Text(formato.format(Date(agora)), fontSize = Subtitulo, fontWeight = FontWeight.Medium, color = TextoSuave)
}

private enum class Rede(val rotulo: String) {
    WIFI("Wi-Fi"), ETHERNET("Ethernet"), OUTRA("Conectado"), NENHUMA("Sem rede")
}

private fun NetworkCapabilities?.paraRede(): Rede = when {
    this == null -> Rede.NENHUMA
    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Rede.ETHERNET
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Rede.WIFI
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> Rede.OUTRA
    else -> Rede.NENHUMA
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IndicadorRede() {
    val ctx = LocalContext.current
    var rede by remember { mutableStateOf(Rede.NENHUMA) }

    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        rede = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }.paraRede()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                rede = caps.paraRede()
            }
            override fun onLost(network: Network) { rede = Rede.NENHUMA }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(14.dp).background(
                if (rede == Rede.NENHUMA) Color(0xFFFF5252) else Color(0xFF4CAF50), CircleShape
            )
        )
        Spacer(Modifier.width(10.dp))
        Text(rede.rotulo, fontSize = Corpo, color = TextoSuave)
    }
}
