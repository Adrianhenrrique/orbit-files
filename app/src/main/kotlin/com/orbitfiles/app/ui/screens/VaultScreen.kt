package com.orbitfiles.app.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.orbitfiles.app.AppContainer
import com.orbitfiles.app.data.FileEntry
import com.orbitfiles.app.ops.OpKind
import com.orbitfiles.app.ui.FileOpener
import com.orbitfiles.app.ui.Navigator
import com.orbitfiles.app.ui.components.ConfirmDialog
import com.orbitfiles.app.ui.components.EmptyState
import com.orbitfiles.app.ui.components.FileRow
import com.orbitfiles.app.ui.components.OrbitScreen
import com.orbitfiles.app.util.Biometrics
import com.orbitfiles.core.FileCategory
import com.orbitfiles.core.Formatters
import com.orbitfiles.core.JournalEntry
import com.orbitfiles.core.Progress
import com.orbitfiles.core.VaultItem
import com.orbitfiles.core.WrongPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun Context.findActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun VaultScreen(c: AppContainer, nav: Navigator) {
    val unlocked by c.vault.unlocked.collectAsState()
    var setUp by remember { mutableStateOf(c.vault.isSetUp) }
    when {
        !setUp -> VaultSetup(c, nav) { setUp = true }
        !unlocked -> VaultLocked(c, nav) { setUp = c.vault.isSetUp }
        else -> VaultContent(c, nav) { setUp = c.vault.isSetUp }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, error: String? = null, onDone: (() -> Unit)? = null) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        isError = error != null, supportingText = { if (error != null) Text(error) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Mostrar senha") } },
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun VaultHero(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(36.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VaultSetup(c: AppContainer, nav: Navigator, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val tooShort = p1.isNotEmpty() && p1.length < 6
    val mismatch = p2.isNotEmpty() && p1 != p2
    OrbitScreen("Cofre", onBack = { nav.pop() }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()).imePadding()) {
            VaultHero(Icons.Rounded.Shield, "Crie seu cofre", "Arquivos no cofre são criptografados com AES-256-GCM. Sem a senha ninguém consegue abri-los — nem mesmo outros apps ou um computador.")
            Spacer(Modifier.height(24.dp))
            PasswordField(p1, { p1 = it }, "Senha (mín. 6 caracteres)", if (tooShort) "Senha muito curta" else null)
            PasswordField(p2, { p2 = it }, "Confirmar senha", if (mismatch) "As senhas não coincidem" else null)
            Text(
                "⚠ Não existe recuperação de senha. Desinstalar o app também apaga o cofre — exporte seus arquivos antes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !busy && p1.length >= 6 && p1 == p2,
                onClick = {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.Default) { c.vault.setup(p1.toCharArray()) }
                        busy = false; onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = MaterialTheme.shapes.large,
            ) { Text(if (busy) "Criando…" else "Criar cofre") }
        }
    }
}

@Composable
private fun VaultLocked(c: AppContainer, nav: Navigator, onReset: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsState()
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var resetAsk by remember { mutableStateOf(false) }

    fun tryBiometric() {
        val activity = context.findActivity() ?: return
        val cipher = c.vault.decryptCipher() ?: run { error = "Biometria indisponível. Use a senha."; return }
        Biometrics.authenticateCipher(activity, "Desbloquear Cofre", cipher, onSuccess = { authorized ->
            runCatching { c.vault.unlockWithBiometric(authorized) }.onFailure { error = "Falha na biometria. Use a senha." }
        })
    }
    LaunchedEffect(Unit) { if (settings.vaultBiometric && c.vault.hasBiometricKey) tryBiometric() }

    fun unlock() {
        if (pw.isEmpty() || busy) return
        busy = true; error = null
        scope.launch {
            try {
                withContext(Dispatchers.Default) { c.vault.unlock(pw.toCharArray()) }
            } catch (_: WrongPasswordException) {
                error = "Senha incorreta"
            } catch (e: Exception) {
                error = e.message
            } finally { busy = false; pw = "" }
        }
    }

    OrbitScreen("Cofre", onBack = { nav.pop() }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()).imePadding()) {
            VaultHero(Icons.Rounded.Lock, "Cofre bloqueado", "Digite sua senha para ver os arquivos protegidos.")
            Spacer(Modifier.height(24.dp))
            PasswordField(pw, { pw = it; error = null }, "Senha", error, onDone = ::unlock)
            Spacer(Modifier.height(12.dp))
            Button(onClick = ::unlock, enabled = pw.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = MaterialTheme.shapes.large) {
                Icon(Icons.Rounded.LockOpen, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Verificando…" else "Desbloquear")
            }
            if (c.vault.hasBiometricKey) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = ::tryBiometric, modifier = Modifier.fillMaxWidth().height(54.dp), shape = MaterialTheme.shapes.large) {
                    Icon(Icons.Rounded.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Usar biometria")
                }
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { resetAsk = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Esqueci a senha") }
        }
    }
    if (resetAsk) ConfirmDialog(
        "Redefinir o cofre?",
        "Sem a senha não há como recuperar os arquivos. Redefinir APAGA todo o conteúdo do cofre permanentemente.",
        "Apagar e redefinir", destructive = true, icon = Icons.Rounded.RestartAlt,
        onDismiss = { resetAsk = false },
    ) { resetAsk = false; c.vault.reset(); onReset() }
}

private data class PendingImport(val uris: List<Uri>)

@Composable
private fun VaultContent(c: AppContainer, nav: Navigator, onReset: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by c.ops.version.collectAsState()
    var items by remember { mutableStateOf<List<VaultItem>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingImport?>(null) }
    var deleteItem by remember { mutableStateOf<VaultItem?>(null) }
    var changePw by remember { mutableStateOf(false) }
    var resetAsk by remember { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(version) { items = withContext(Dispatchers.IO) { runCatching { c.vault.list() }.getOrDefault(emptyList()) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) pending = PendingImport(uris)
    }
    val exportDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Orbit Cofre")

    fun importUris(uris: List<Uri>, deleteOriginals: Boolean) {
        c.ops.run(OpKind.VAULT, "Protegendo ${uris.size} ${if (uris.size == 1) "arquivo" else "arquivos"}") { task, progress ->
            val cr = context.contentResolver
            var removed = 0
            uris.forEachIndexed { i, uri ->
                var name = "arquivo_${i + 1}"
                var modified = System.currentTimeMillis()
                cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { cur ->
                    if (cur.moveToFirst()) {
                        cur.getString(0)?.let { name = it }
                        val lm = cur.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        if (lm >= 0 && !cur.isNull(lm)) modified = cur.getLong(lm)
                    }
                }
                var done = 0L
                cr.openInputStream(uri)?.use { input ->
                    c.vault.addStream(name, modified, input, task.token) { n -> done += n; progress(Progress(done, 0, i, uris.size, name)) }
                } ?: throw java.io.IOException("Não foi possível ler $name")
                if (deleteOriginals && runCatching { DocumentsContract.deleteDocument(cr, uri) }.getOrDefault(false)) removed++
            }
            val msg = "${uris.size} ${if (uris.size == 1) "arquivo protegido" else "arquivos protegidos"}" +
                if (deleteOriginals) " · $removed ${if (removed == 1) "original removido" else "originais removidos"}" else ""
            msg to JournalEntry.Info("Adicionado ao Cofre: ${uris.size} itens")
        }
    }

    fun export(item: VaultItem) {
        c.ops.run(OpKind.VAULT, "Exportando ${item.meta.name}") { task, progress ->
            var done = 0L
            val out = c.vault.export(item, exportDir, task.token) { n -> done += n; progress(Progress(done, item.meta.size, 0, 1, item.meta.name)) }
            c.ops.scan(listOf(out))
            "Exportado para Download/Orbit Cofre/${out.name}" to JournalEntry.Created(out)
        }
    }

    fun open(item: VaultItem) {
        scope.launch {
            val f = withContext(Dispatchers.IO) { runCatching { c.vault.decryptForPreview(item) }.getOrNull() }
            if (f != null) FileOpener.open(context, nav, f) else c.ops.emit("Não foi possível abrir", error = true)
        }
    }

    OrbitScreen(
        title = "Cofre",
        subtitle = items?.let { list -> "${list.size} ${if (list.size == 1) "arquivo" else "arquivos"} · ${Formatters.size(list.sumOf { it.meta.size })}" },
        onBack = { nav.pop() },
        actions = {
            IconButton(onClick = { c.vault.lock() }) { Icon(Icons.Rounded.Lock, "Bloquear agora") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais") }
                DropdownMenu(menu, { menu = false }, shape = MaterialTheme.shapes.large) {
                    DropdownMenuItem(text = { Text("Alterar senha") }, leadingIcon = { Icon(Icons.Rounded.Key, null) }, onClick = { menu = false; changePw = true })
                    val activity = context.findActivity()
                    if (activity != null && Biometrics.canUseStrong(activity)) {
                        DropdownMenuItem(
                            text = { Text(if (c.vault.hasBiometricKey) "Desativar biometria" else "Ativar biometria") },
                            leadingIcon = { Icon(Icons.Rounded.Fingerprint, null) },
                            onClick = {
                                menu = false
                                if (c.vault.hasBiometricKey) {
                                    c.vault.disableBiometric(); scope.launch { c.settings.setVaultBiometric(false) }
                                } else {
                                    runCatching { c.vault.encryptCipher() }.onSuccess { cipher ->
                                        Biometrics.authenticateCipher(activity, "Ativar biometria no Cofre", cipher, onSuccess = { ok ->
                                            runCatching { c.vault.enableBiometric(ok) }
                                                .onSuccess { scope.launch { c.settings.setVaultBiometric(true) }; c.ops.emit("Biometria ativada para o Cofre") }
                                                .onFailure { c.ops.emit("Não foi possível ativar a biometria", error = true) }
                                        })
                                    }.onFailure { c.ops.emit("Biometria indisponível neste aparelho", error = true) }
                                }
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Apagar cofre", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) }, onClick = { menu = false; resetAsk = true })
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Adicionar") },
                shape = MaterialTheme.shapes.large,
            )
        },
    ) { padding ->
        val list = items
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.padding(padding)) {
                EmptyState(Icons.Rounded.Shield, "Cofre vazio", "Toque em Adicionar, ou no navegador use ⋮ › Mover para o Cofre.")
            }
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 120.dp)) {
                items(list, key = { it.id }) { item ->
                    val entry = FileEntry(File(item.meta.name), item.meta.name, false, item.meta.size, item.meta.modified, false, FileCategory.ofName(item.meta.name), null)
                    FileRow(
                        entry, false, thumbnails = false,
                        onClick = { open(item) }, onLongClick = { itemMenu = item.id },
                        subtitle = "${Formatters.size(item.meta.size)} · protegido ${Formatters.relative(item.meta.addedAt)}",
                        trailing = {
                            Box {
                                IconButton(onClick = { itemMenu = item.id }) { Icon(Icons.Rounded.MoreVert, "Ações") }
                                DropdownMenu(itemMenu == item.id, { itemMenu = null }, shape = MaterialTheme.shapes.large) {
                                    DropdownMenuItem(text = { Text("Abrir") }, leadingIcon = { Icon(Icons.Rounded.LockOpen, null) }, onClick = { itemMenu = null; open(item) })
                                    DropdownMenuItem(text = { Text("Exportar (descriptografar)") }, leadingIcon = { Icon(Icons.Rounded.Output, null) }, onClick = { itemMenu = null; export(item) })
                                    DropdownMenuItem(text = { Text("Excluir") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { itemMenu = null; deleteItem = item })
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = { Icon(Icons.Rounded.Shield, null) },
            title = { Text("Proteger ${p.uris.size} ${if (p.uris.size == 1) "arquivo" else "arquivos"}") },
            text = { Text("Mover apaga os originais depois de criptografá-los. Copiar mantém os originais onde estão.") },
            confirmButton = { TextButton(onClick = { pending = null; importUris(p.uris, true) }) { Text("Mover") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { pending = null }) { Text("Cancelar") }
                    TextButton(onClick = { pending = null; importUris(p.uris, false) }) { Text("Copiar") }
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
    deleteItem?.let { item ->
        ConfirmDialog("Excluir do cofre?", "\"${item.meta.name}\" será apagado permanentemente.", "Excluir", destructive = true, icon = Icons.Rounded.Delete, onDismiss = { deleteItem = null }) {
            deleteItem = null
            scope.launch { withContext(Dispatchers.IO) { c.vault.delete(item) }; c.ops.notifyChanged() }
        }
    }
    if (resetAsk) ConfirmDialog(
        "Apagar o cofre?", "Todos os arquivos protegidos serão apagados permanentemente. Exporte o que quiser manter antes.",
        "Apagar tudo", destructive = true, icon = Icons.Rounded.RestartAlt, onDismiss = { resetAsk = false },
    ) { resetAsk = false; c.vault.reset(); scope.launch { c.settings.setVaultBiometric(false) }; onReset() }
    if (changePw) ChangePasswordDialog(c) { changePw = false }
}

@Composable
private fun ChangePasswordDialog(c: AppContainer, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alterar senha") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PasswordField(old, { old = it }, "Senha atual", error)
                PasswordField(new1, { new1 = it }, "Nova senha", if (new1.isNotEmpty() && new1.length < 6) "Mínimo 6 caracteres" else null)
                PasswordField(new2, { new2 = it }, "Confirmar nova senha", if (new2.isNotEmpty() && new1 != new2) "Não coincide" else null)
            }
        },
        confirmButton = {
            TextButton(enabled = old.isNotEmpty() && new1.length >= 6 && new1 == new2, onClick = {
                scope.launch {
                    try {
                        withContext(Dispatchers.Default) { c.vault.changePassword(old.toCharArray(), new1.toCharArray()) }
                        c.ops.emit("Senha do cofre alterada"); onDismiss()
                    } catch (_: WrongPasswordException) { error = "Senha atual incorreta" }
                    catch (e: Exception) { error = e.message }
                }
            }) { Text("Alterar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}
