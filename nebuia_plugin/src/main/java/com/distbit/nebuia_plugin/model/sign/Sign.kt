package com.distbit.nebuia_plugin.model.sign

data class DocumentToSign(
    val signatureLink: String,
    val documentId: String
)

fun HashMap<*, *>.mapToSignURL(): DocumentToSign? {
    val payloadMap = this["payload"]
    val status = this["status"] as? Boolean
    if (!status!!) {
        return null
    }

    val payloadData = payloadMap as? Map<*, *>
    val signatureLink = payloadData?.get("signatureLink") as? String
    val documentId = payloadData?.get("documentId") as? String

    return DocumentToSign(signatureLink!!, documentId!!)
}