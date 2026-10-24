package com.vella.app

object Native {
    init {
        System.loadLibrary("vella")
    }

    external fun decompile(
        binaryPath: String,
        languageId: String,
        base: Long,
        entryOffset: Long,
        size: Long,
        functionName: String,
        specDir: String
    ): String
}
