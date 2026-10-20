package com.britishcoach.app.ai

import android.app.ActivityManager
import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ModelState {
    data object NotInstalled : ModelState
    data object Unloaded : ModelState
    data class Loading(val message: String) : ModelState
    data class Ready(val backend: String) : ModelState
    data class Failed(val message: String) : ModelState
}

/** A multi-turn chat with the model, keeping its own history. */
interface CoachChat : AutoCloseable {
    /** Streams the reply as text chunks. Cancel the collecting coroutine to stop generation. */
    fun send(text: String): Flow<String>
}

interface CoachModel {
    val state: StateFlow<ModelState>
    suspend fun load(file: File, preferGpu: Boolean)
    fun unload()
    fun markNotInstalled()
    /** One-off generation with a fresh context. Throws if the model isn't ready. */
    suspend fun generate(systemPrompt: String, prompt: String, temperature: Double = 0.4): String
    suspend fun startChat(systemPrompt: String, temperature: Double = 0.8): CoachChat
}

/**
 * Gemma 4 E2B through LiteRT-LM. All engine calls run on one dedicated thread,
 * so the UI never blocks and only one large model is ever loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiteRtCoachModel(private val context: Context) : CoachModel {

    private val engineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedPath: String? = null

    private val _state = MutableStateFlow<ModelState>(ModelState.Unloaded)
    override val state: StateFlow<ModelState> = _state.asStateFlow()

    override fun markNotInstalled() {
        unload()
        _state.value = ModelState.NotInstalled
    }

    override suspend fun load(file: File, preferGpu: Boolean) = withContext(engineDispatcher) {
        mutex.withLock {
            if (engine != null && loadedPath == file.absolutePath) return@withLock
            closeEngine()
            if (!file.exists()) {
                _state.value = ModelState.NotInstalled
                return@withLock
            }
            val avail = availableMemoryBytes()
            if (avail in 1 until MIN_FREE_MEMORY) {
                _state.value = ModelState.Failed(
                    "There isn't enough free memory to load the AI coach right now (about ${avail / 1_000_000} MB free). " +
                        "Close some other apps and try again. Lessons still work without it.",
                )
                return@withLock
            }
            val backends = if (preferGpu) listOf("GPU", "CPU") else listOf("CPU")
            var lastError: Throwable? = null
            for (name in backends) {
                _state.value = ModelState.Loading("Loading the AI coach ($name)…")
                try {
                    val e = Engine(
                        EngineConfig(
                            modelPath = file.absolutePath,
                            backend = if (name == "GPU") Backend.GPU() else Backend.CPU(),
                            cacheDir = context.cacheDir.path,
                        ),
                    )
                    e.initialize()
                    engine = e
                    loadedPath = file.absolutePath
                    _state.value = ModelState.Ready(name)
                    return@withLock
                } catch (t: Throwable) {
                    // OutOfMemoryError and native failures included: the app must not crash.
                    lastError = t
                }
            }
            _state.value = ModelState.Failed(
                "The AI coach couldn't start on this phone (${lastError?.message ?: lastError?.javaClass?.simpleName}). " +
                    "Lessons, recording and transcript comparison still work.",
            )
        }
    }

    override fun unload() {
        val e = engine
        engine = null
        loadedPath = null
        runCatching { e?.close() }
        if (_state.value !is ModelState.NotInstalled) _state.value = ModelState.Unloaded
    }

    private fun closeEngine() {
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
    }

    private fun requireEngine(): Engine = engine ?: throw IllegalStateException("The AI coach isn't loaded.")

    override suspend fun generate(systemPrompt: String, prompt: String, temperature: Double): String =
        withContext(engineDispatcher) {
            val conv = requireEngine().createConversation(config(systemPrompt, temperature))
            try {
                val sb = StringBuilder()
                conv.sendMessageAsync(prompt).collect { sb.append(textOf(it)) }
                sb.toString()
            } finally {
                runCatching { conv.close() }
            }
        }

    override suspend fun startChat(systemPrompt: String, temperature: Double): CoachChat =
        withContext(engineDispatcher) {
            val conv = requireEngine().createConversation(config(systemPrompt, temperature))
            LiteRtChat(conv)
        }

    private inner class LiteRtChat(private val conv: Conversation) : CoachChat {
        override fun send(text: String): Flow<String> =
            conv.sendMessageAsync(text).map { textOf(it) }.flowOn(engineDispatcher)

        override fun close() {
            runCatching { conv.close() }
        }
    }

    private fun config(systemPrompt: String, temperature: Double) = ConversationConfig(
        systemInstruction = Contents.of(systemPrompt),
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature),
    )

    /** LiteRT-LM streams Message chunks; their string form is the generated text. */
    private fun textOf(m: Message): String = m.toString()

    private fun availableMemoryBytes(): Long = runCatching {
        val am = context.getSystemService(ActivityManager::class.java)
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
    }.getOrDefault(0L)

    companion object {
        /** Gemma 4 E2B needs roughly 1.5–2 GB of working memory; below this, don't try. */
        private const val MIN_FREE_MEMORY = 1_500_000_000L
    }
}
