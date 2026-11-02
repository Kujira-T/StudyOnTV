package com.studyontv.tv

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException





class ApkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException("no name")
        val dir = File(requireNotNull(context).cacheDir, "updates")
        val file = File(dir, name)
        if (!file.exists()) throw FileNotFoundException(file.absolutePath)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = 0

    companion object {
        fun uriFor(context: Context, file: File): Uri =
            Uri.parse("content://" + context.packageName + ".apk/" + file.name)
    }
}
