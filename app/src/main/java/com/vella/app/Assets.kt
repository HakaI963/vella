package com.vella.app

import android.content.Context
import android.content.res.AssetManager
import java.io.File

object Assets {
    private const val VERSION = "ghidra-12.2-v1"

    fun ensure(context: Context): File {
        val root = File(context.filesDir, "ghidra")
        val marker = File(root, ".version")
        if (marker.isFile && marker.readText() == VERSION) return root

        root.deleteRecursively()
        root.mkdirs()
        copyDir(context.assets, "ghidra", root)
        marker.writeText(VERSION)
        return root
    }

    private fun copyDir(assets: AssetManager, path: String, target: File) {
        val children = assets.list(path)
        if (children == null) {
            target.parentFile?.mkdirs()
            assets.open(path).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        target.mkdirs()
        for (child in children) {
            copyDir(assets, "$path/$child", File(target, child))
        }
    }
}
