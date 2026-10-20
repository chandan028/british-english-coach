package com.britishcoach.core.model

/** A downloadable on-device model file. */
data class ModelOption(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
    /** Approximate size, used for the storage check and progress display. */
    val approxBytes: Long,
    /** Below this size a finished download is treated as broken (for example an HTML error page). */
    val minValidBytes: Long,
    val description: String,
)

/**
 * Gemma 4 E2B Instruct in LiteRT-LM format, published by Google's
 * litert-community on Hugging Face under the Apache 2.0 licence. The files are
 * downloaded once and then used fully offline.
 */
object ModelCatalog {
    private const val REPO = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/"

    val GEMMA_4_E2B = ModelOption(
        id = "gemma-4-e2b-it",
        displayName = "Gemma 4 E2B Instruct",
        fileName = "gemma-4-E2B-it.litertlm",
        url = REPO + "gemma-4-E2B-it.litertlm",
        approxBytes = 2_590_000_000L,
        minValidBytes = 1_000_000_000L,
        description = "Recommended. Works on CPU and GPU on most recent phones.",
    )

    val GEMMA_4_E2B_GPU = ModelOption(
        id = "gemma-4-e2b-it-gpu",
        displayName = "Gemma 4 E2B Instruct (GPU build)",
        fileName = "gemma-4-E2B-it-gpu.litertlm",
        url = REPO + "gemma-4-E2B-it-gpu.litertlm",
        approxBytes = 2_010_000_000L,
        minValidBytes = 800_000_000L,
        description = "Smaller download tuned for phone GPUs. Try this if the recommended file is slow.",
    )

    val options = listOf(GEMMA_4_E2B, GEMMA_4_E2B_GPU)
    val default = GEMMA_4_E2B

    fun byId(id: String?): ModelOption = options.firstOrNull { it.id == id } ?: default

    /** Free space needed before starting: the file plus a margin so the phone isn't left full. */
    fun requiredFreeBytes(option: ModelOption): Long = option.approxBytes + 500_000_000L
}
