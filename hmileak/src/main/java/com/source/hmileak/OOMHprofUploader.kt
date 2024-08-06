package com.source.hmileak

import java.io.File

interface OOMHprofUploader {

    enum class HprofType {
        ORIGIN,STRIPPED
    }

    fun upload(file: File, type:HprofType)
}