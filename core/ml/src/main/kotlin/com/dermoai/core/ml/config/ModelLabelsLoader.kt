package com.dermoai.core.ml.config

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelLabelsLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun load(): List<String> {
        return context.assets.open(LABELS_PATH).bufferedReader().use { reader ->
            reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    companion object {
        const val LABELS_PATH = "ml/labels.txt"
    }
}