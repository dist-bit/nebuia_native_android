package com.distbit.nebuia_plugin.model.sign

import org.json.JSONArray
import org.json.JSONObject

data class KeyToFill(
    val description: String,
    val place: Place,
    val label: String,
    val key: String
)

data class Place(
    val w: Double,
    val x: Double,
    val h: Double,
    val y: Double,
    val page: Int
)

data class Template(
    val keysToFill: List<KeyToFill>,
    val name: String,
    val signsTypes: List<String>,
    val description: String,
    val id: String,
    val requiresKYC: Boolean
)

fun JSONObject.mapToTemplates(): Template {
    val keysToFillArray = getJSONArray("keysToFill")
    val keysToFillList = mutableListOf<KeyToFill>()

    for (i in 0 until keysToFillArray.length()) {
        val keyToFillObject = keysToFillArray.getJSONObject(i)
        val placeObject = keyToFillObject.getJSONObject("place")
        val place = Place(
            placeObject.getDouble("w"),
            placeObject.getDouble("x"),
            placeObject.getDouble("h"),
            placeObject.getDouble("y"),
            placeObject.getInt("page")
        )
        val keyToFill = KeyToFill(
            keyToFillObject.getString("description"),
            place,
            keyToFillObject.getString("label"),
            keyToFillObject.getString("key")
        )
        keysToFillList.add(keyToFill)
    }

    val signsTypesArray = getJSONArray("signsTypes")
    val signsTypesList = (0 until signsTypesArray.length()).map { signsTypesArray.getString(it) }

    return Template(
        keysToFillList,
        getString("name"),
        signsTypesList,
        getString("description"),
        getString("id"),
        getBoolean("requiresKYC")
    )
}

fun JSONArray.mapToTemplateList(): List<Template> {
    val templates = mutableListOf<Template>()
    for (i in 0 until length()) {
        val templateObject = getJSONObject(i)
        val template = templateObject.mapToTemplates()
        templates.add(template)
    }

    return templates
}