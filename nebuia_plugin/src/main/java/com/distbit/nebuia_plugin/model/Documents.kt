package com.distbit.nebuia_plugin.model

import android.graphics.Bitmap

enum class Country {
    MX
}

enum class DocumentType {
    DRIVER_LICENSE, PASSPORT, ID
}

enum class Side {
    FRONT, BACK
}

object Documents {
    private var front: Bitmap? = null
    private var back: Bitmap? = null
    private var side: Side = Side.FRONT
    private var documentType: DocumentType = DocumentType.ID
    private var country: Country = Country.MX

    fun side(): Side {
        return this.side
    }

    fun type(): DocumentType {
        return this.documentType
    }

    fun country(): Country {
        return this.country
    }

    fun isComplete(): Boolean {
        return if (this.documentType == DocumentType.PASSPORT) {
            this.front != null
        } else {
            this.front != null && this.back != null
        }
    }

    fun getPreview(): Bitmap? {
        return if (this.documentType == DocumentType.PASSPORT) {
            this.front
        } else {
            if(this.side == Side.BACK) this.back
            else this.front
        }
    }

    fun setImage(image: Bitmap) {
        if(this.documentType == DocumentType.PASSPORT) {
            this.front = image
        } else {
            // other documents
            if(this.side == Side.FRONT)
                this.front = image
            else this.back = image
        }
    }

    fun setSide(side: Side) {
        this.side = side
    }

    fun frontImage(): Bitmap? = this.front
    fun backImage(): Bitmap? = this.back

    fun reset() {
        this.side = Side.FRONT
        this.front = null
        this.back = null
    }

    fun getLabel(): String {
        return if (this.documentType == DocumentType.PASSPORT) {
            "Parte frontal de tu pasaporte"
        } else {
            if(this.side == Side.BACK) "Parte trasera de tu documento de Identidad"
            else "Parte frontal de tu documento de Identidad"
        }
    }
}