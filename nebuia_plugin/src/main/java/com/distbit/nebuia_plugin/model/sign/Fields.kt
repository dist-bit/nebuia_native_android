package com.distbit.nebuia_plugin.model.sign

data class DocumentField(
    val key: String,
    val value: String
)

fun MutableMap<String, String>.toDocumentFields(): Array<HashMap<String, String>> {
    return this.map { (key, value) ->
        hashMapOf("key" to key, "value" to value)
    }.toTypedArray()
}