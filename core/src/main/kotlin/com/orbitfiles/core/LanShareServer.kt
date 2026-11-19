package com.orbitfiles.core

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tiny dependency-free HTTP server that lets any browser on the same Wi-Fi browse, download
 * (with Range/streaming support) and upload files. Every request requires a random access key,
 * and paths are confined to [root] (no traversal).
 */
class LanShareServer(
    private val root: File,
    val port: Int = 8686,
    val allowUpload: Boolean = true,
    val key: String = randomKey(),
    private val onEvent: (String) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private var pool: ExecutorService? = null

    val isRunning: Boolean get() = running.get()

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(port))
        server = ss
        val p = Executors.newFixedThreadPool(6)
        pool = p
        Thread({
            while (running.get()) {
                val socket = try { ss.accept() } catch (_: SocketException) { break } catch (_: IOException) { continue }
                p.execute { handle(socket) }
            }
        }, "orbit-lan-accept").apply { isDaemon = true }.start()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { server?.close() }
        pool?.shutdownNow()
        server = null; pool = null
    }

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: InputStream)

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 30_000
            val input = BufferedInputStream(s.getInputStream())
            val out = s.getOutputStream()
            try {
                val req = parse(input) ?: return
                val cookieKey = req.headers["cookie"]?.split(';')?.map { it.trim() }
                    ?.firstOrNull { it.startsWith("orbit_k=") }?.removePrefix("orbit_k=")
                val authorized = req.query["k"] == key || cookieKey == key
                if (!authorized) {
                    respond(out, 403, "text/html; charset=utf-8", page("<h1>Acesso negado</h1><p>Abra o endereço completo exibido no app Orbit Files (ele contém a chave de acesso).</p>").toByteArray())
                    return
                }
                val extra = if (req.query["k"] == key) mapOf("Set-Cookie" to "orbit_k=$key; Path=/; HttpOnly; SameSite=Strict") else emptyMap()
                when {
                    req.method == "GET" && req.path == "/" -> respond(out, 200, "text/html; charset=utf-8", appHtml().toByteArray(), extra)
                    req.method == "GET" && req.path == "/api/list" -> list(out, req)
                    req.method == "GET" && req.path == "/dl" -> download(out, req)
                    req.method == "PUT" && req.path == "/up" -> upload(out, req)
                    else -> respond(out, 404, "text/plain; charset=utf-8", "Não encontrado".toByteArray())
                }
            } catch (_: SocketException) {
            } catch (e: Exception) {
                runCatching { respond(out, 500, "text/plain; charset=utf-8", (e.message ?: "Erro").toByteArray()) }
            }
        }
    }

    private fun parse(input: InputStream): Request? {
        val headerBytes = java.io.ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            headerBytes.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
            if (headerBytes.size() > 16_384) throw IOException("Cabeçalho muito grande")
        }
        val lines = headerBytes.toString("ISO-8859-1").split("\r\n").filter { it.isNotEmpty() }
        val first = lines.firstOrNull()?.split(' ') ?: return null
        if (first.size < 2) return null
        val target = first[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val headers = lines.drop(1).mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() } }.toMap()
        return Request(first[0].uppercase(), path, query, headers, input)
    }

    /** Resolves a client supplied relative path, refusing anything outside [root]. */
    fun resolve(rel: String?): File {
        val base = root.canonicalFile
        val f = File(base, (rel ?: "").trimStart('/')).canonicalFile
        if (f.path != base.path && !f.path.startsWith(base.path + File.separator)) throw SecurityException("Caminho inválido")
        return f
    }

    private fun relative(f: File): String = f.canonicalFile.path.removePrefix(root.canonicalFile.path).trimStart('/')

    private fun list(out: OutputStream, req: Request) {
        val dir = resolve(req.query["p"])
        if (!dir.isDirectory) { respond(out, 404, "application/json", "{\"error\":\"not found\"}".toByteArray()); return }
        val items = dir.listFiles().orEmpty()
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .joinToString(",") { f ->
                "{\"name\":${json(f.name)},\"dir\":${f.isDirectory},\"size\":${if (f.isDirectory) 0 else f.length()},\"modified\":${f.lastModified()}}"
            }
        val body = "{\"path\":${json(relative(dir))},\"upload\":$allowUpload,\"items\":[$items]}"
        respond(out, 200, "application/json; charset=utf-8", body.toByteArray())
    }

    private fun download(out: OutputStream, req: Request) {
        val f = resolve(req.query["p"])
        if (!f.isFile) { respond(out, 404, "text/plain", "Arquivo não encontrado".toByteArray()); return }
        val total = f.length()
        var start = 0L
        var end = total - 1
        val range = req.headers["range"]
        val partial = range != null && range.startsWith("bytes=")
        if (partial) {
            val spec = range!!.removePrefix("bytes=").substringBefore(',')
            val a = spec.substringBefore('-'); val b = spec.substringAfter('-')
            if (a.isEmpty()) { start = (total - b.toLong()).coerceAtLeast(0) } else {
                start = a.toLong(); if (b.isNotEmpty()) end = minOf(b.toLong(), total - 1)
            }
            if (start > end || start >= total) {
                respond(out, 416, "text/plain", ByteArray(0), mapOf("Content-Range" to "bytes */$total")); return
            }
        }
        val len = end - start + 1
        val inline = req.query["inline"] == "1"
        val headers = mutableMapOf(
            "Accept-Ranges" to "bytes",
            "Content-Disposition" to (if (inline) "inline" else "attachment") + "; filename*=UTF-8''" + URLEncoder.encode(f.name, "UTF-8").replace("+", "%20"),
        )
        if (partial) headers["Content-Range"] = "bytes $start-$end/$total"
        writeHead(out, if (partial) 206 else 200, FileCategory.mimeOf(f.name), len, headers)
        RandomAccessFile(f, "r").use { raf ->
            raf.seek(start)
            val buf = ByteArray(128 * 1024)
            var remaining = len
            while (remaining > 0) {
                val n = raf.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n < 0) break
                out.write(buf, 0, n)
                remaining -= n
            }
        }
        out.flush()
        if (start == 0L) onEvent("Download: ${f.name}")
    }

    private fun upload(out: OutputStream, req: Request) {
        if (!allowUpload) { respond(out, 403, "text/plain", "Envio desativado".toByteArray()); return }
        val dir = resolve(req.query["p"])
        val name = req.query["name"]?.let { File(it).name } ?: throw IOException("Nome ausente")
        FileOps.validateName(name)?.let { throw IOException(it) }
        val length = req.headers["content-length"]?.toLongOrNull() ?: throw IOException("Content-Length obrigatório")
        dir.mkdirs()
        val target = File(dir, FileOps.uniqueName(dir, name))
        val part = File(dir, ".${target.name}.orbitpart")
        try {
            part.outputStream().buffered().use { fos ->
                val buf = ByteArray(128 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val n = req.body.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n < 0) throw IOException("Envio interrompido")
                    fos.write(buf, 0, n)
                    remaining -= n
                }
            }
            if (!part.renameTo(target)) throw IOException("Falha ao salvar")
        } catch (e: Exception) { part.delete(); throw e }
        onEvent("Recebido: ${target.name}")
        respond(out, 200, "application/json", "{\"ok\":true,\"name\":${json(target.name)}}".toByteArray())
    }

    private fun writeHead(out: OutputStream, code: Int, type: String, length: Long, extra: Map<String, String> = emptyMap()) {
        val reason = when (code) { 200 -> "OK"; 206 -> "Partial Content"; 403 -> "Forbidden"; 404 -> "Not Found"; 416 -> "Range Not Satisfiable"; else -> "Error" }
        val sb = StringBuilder("HTTP/1.1 $code $reason\r\n")
        sb.append("Content-Type: $type\r\nContent-Length: $length\r\nConnection: close\r\n")
        sb.append("Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n")
        extra.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray, extra: Map<String, String> = emptyMap()) {
        writeHead(out, code, type, body.size.toLong(), extra)
        out.write(body)
        out.flush()
    }

    companion object {
        fun randomKey(): String {
            val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
            val r = SecureRandom()
            return (1..10).map { alphabet[r.nextInt(alphabet.length)] }.joinToString("")
        }

        fun json(s: String): String {
            val sb = StringBuilder("\"")
            for (c in s) when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
            return sb.append('"').toString()
        }

        private fun page(content: String) = """<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Orbit Files</title><body style="font-family:system-ui;background:#0b0d14;color:#e7e9f5;display:grid;place-items:center;min-height:100vh;margin:0;text-align:center">$content</body></html>"""

        fun appHtml(): String = """<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Orbit Files · Wi-Fi</title>
<style>
:root{--bg:#0b0d14;--card:#151826;--line:#262a3d;--fg:#e7e9f5;--mut:#9aa0bd;--acc:#8b7bff;--acc2:#3ee6c1}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:radial-gradient(1200px 600px at 10% -10%,#2a2360 0,transparent 60%),var(--bg);color:var(--fg);min-height:100vh}
header{position:sticky;top:0;backdrop-filter:blur(14px);background:#0b0d14cc;border-bottom:1px solid var(--line);padding:16px 24px;display:flex;gap:16px;align-items:center;z-index:2}
.logo{width:36px;height:36px;border-radius:12px;background:conic-gradient(from 200deg,var(--acc),var(--acc2),var(--acc));box-shadow:0 0 24px #8b7bff66}
h1{font-size:18px;margin:0}.sub{color:var(--mut);font-size:13px}
main{max-width:1100px;margin:0 auto;padding:20px}
.crumbs{display:flex;flex-wrap:wrap;gap:6px;margin-bottom:16px}.crumbs a{color:var(--fg);text-decoration:none;background:var(--card);border:1px solid var(--line);padding:6px 12px;border-radius:999px;font-size:14px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px}
.item{background:var(--card);border:1px solid var(--line);border-radius:20px;padding:14px;display:flex;gap:12px;align-items:center;cursor:pointer;transition:.2s;text-decoration:none;color:inherit}
.item:hover{transform:translateY(-2px);border-color:var(--acc)}
.ic{width:44px;height:44px;border-radius:14px;display:grid;place-items:center;font-size:20px;flex:none;background:#8b7bff22}
.ic.dir{background:#3ee6c122}.nm{font-weight:600;font-size:14px;word-break:break-word}.meta{color:var(--mut);font-size:12px;margin-top:2px}
#drop{margin:0 0 16px;border:2px dashed var(--line);border-radius:20px;padding:22px;text-align:center;color:var(--mut);transition:.2s}
#drop.on{border-color:var(--acc2);color:var(--fg);background:#3ee6c111}
button{background:var(--acc);color:#fff;border:0;border-radius:999px;padding:10px 18px;font-weight:600;cursor:pointer}
.bar{height:6px;background:var(--line);border-radius:6px;overflow:hidden;margin-top:10px;display:none}.bar i{display:block;height:100%;width:0;background:linear-gradient(90deg,var(--acc),var(--acc2))}
</style></head><body>
<header><div class="logo"></div><div><h1>Orbit Files</h1><div class="sub">Compartilhamento por Wi-Fi · mantenha o app aberto</div></div></header>
<main><nav class="crumbs" id="crumbs"></nav>
<div id="drop">Arraste arquivos para cá para enviar ao celular, ou <button id="pick">escolher arquivos</button><input type="file" id="file" multiple hidden><div class="bar" id="bar"><i></i></div></div>
<section class="grid" id="list"></section></main>
<script>
let cur='';const fmt=b=>{const u=['B','KB','MB','GB','TB'];let i=0;while(b>=1024&&i<4){b/=1024;i++}return (i?b.toFixed(1):b)+' '+u[i]};
const icon=n=>{const e=n.split('.').pop().toLowerCase();if(['jpg','jpeg','png','gif','webp','heic'].includes(e))return'🖼️';if(['mp4','mkv','webm','mov'].includes(e))return'🎬';if(['mp3','m4a','flac','wav','ogg','opus'].includes(e))return'🎵';if(e==='pdf')return'📕';if(['zip','rar','7z','tar','gz'].includes(e))return'🗜️';if(e==='apk')return'🤖';return'📄'};
async function load(p){cur=p;const r=await fetch('/api/list?p='+encodeURIComponent(p));const d=await r.json();
document.getElementById('drop').style.display=d.upload?'block':'none';
const c=document.getElementById('crumbs');c.innerHTML='';const parts=d.path?d.path.split('/'):[];let acc='';
const mk=(t,p)=>{const a=document.createElement('a');a.href='#';a.textContent=t;a.onclick=e=>{e.preventDefault();load(p)};c.appendChild(a)};mk('Armazenamento','');
parts.forEach(x=>{acc+=(acc?'/':'')+x;mk(x,acc)});
const l=document.getElementById('list');l.innerHTML='';d.items.forEach(it=>{const rel=(d.path?d.path+'/':'')+it.name;const a=document.createElement('a');a.className='item';
a.innerHTML='<div class="ic '+(it.dir?'dir':'')+'">'+(it.dir?'📁':icon(it.name))+'</div><div><div class="nm"></div><div class="meta">'+(it.dir?'Pasta':fmt(it.size))+' · '+new Date(it.modified).toLocaleDateString('pt-BR')+'</div></div>';
a.querySelector('.nm').textContent=it.name;
if(it.dir){a.href='#';a.onclick=e=>{e.preventDefault();load(rel)}}else{a.href='/dl?p='+encodeURIComponent(rel)}l.appendChild(a)})}
async function send(files){const bar=document.getElementById('bar'),i=bar.firstElementChild;bar.style.display='block';
for(const f of files){await new Promise((ok,ko)=>{const x=new XMLHttpRequest();x.open('PUT','/up?p='+encodeURIComponent(cur)+'&name='+encodeURIComponent(f.name));
x.upload.onprogress=e=>{i.style.width=(e.loaded/e.total*100)+'%'};x.onload=ok;x.onerror=ko;x.send(f)})}bar.style.display='none';load(cur)}
const drop=document.getElementById('drop');['dragenter','dragover'].forEach(t=>drop.addEventListener(t,e=>{e.preventDefault();drop.classList.add('on')}));
['dragleave','drop'].forEach(t=>drop.addEventListener(t,e=>{e.preventDefault();drop.classList.remove('on')}));
drop.addEventListener('drop',e=>send(e.dataTransfer.files));document.getElementById('pick').onclick=()=>document.getElementById('file').click();
document.getElementById('file').onchange=e=>send(e.target.files);load('');
</script></body></html>"""
    }
}
