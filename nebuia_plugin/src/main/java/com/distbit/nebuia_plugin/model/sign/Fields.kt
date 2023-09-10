package com.distbit.nebuia_plugin.model.sign

data class DocumentField(
    val key: String,
    val value: String
)

fun MutableMap<String, String>.toDocumentFields(): Map<String, String> {
    val documentFieldsJson = mutableMapOf<String, String>()

    for ((key, value) in this) {
        documentFieldsJson["key"] = key
        documentFieldsJson["value"] = value
    }

    return documentFieldsJson
}