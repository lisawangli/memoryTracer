package com.source.hmileak

import java.io.File

interface OOMReportUploader {

    fun upload(file: File,content:String)
}