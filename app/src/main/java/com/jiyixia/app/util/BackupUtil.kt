package com.jiyixia.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object BackupUtil {
    private const val TAG = "BackupUtil"
    private const val DB_NAME = "jiyixia.db"
    private const val BACKUP_DIR = "记一下"

    /**
     * 备份数据库到 Downloads/记一下/ 目录（加密）
     * @param password 加密密码，为空则不加密
     * @return 备份文件路径或错误信息
     */
    fun backup(context: Context, password: String? = null): Result<String> {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                return Result.failure(Exception("数据库文件不存在"))
            }

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val isEncrypted = !password.isNullOrEmpty()
            val fileName = if (isEncrypted) {
                "jiyixia_backup_${dateFormat.format(Date())}.db.enc"
            } else {
                "jiyixia_backup_${dateFormat.format(Date())}.db"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore
                backupWithMediaStore(context, dbFile, fileName, password)
            } else {
                // Android 9 及以下使用文件系统
                backupWithFileSystem(dbFile, fileName, password)
            }
        } catch (e: Exception) {
            Log.e(TAG, "备份失败", e)
            Result.failure(e)
        }
    }

    /**
     * 使用 MediaStore 备份（Android 10+）
     */
    private fun backupWithMediaStore(context: Context, dbFile: File, fileName: String, password: String?): Result<String> {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_DIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return Result.failure(Exception("无法创建备份文件"))

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            FileInputStream(dbFile).use { inputStream ->
                if (!password.isNullOrEmpty()) {
                    // 加密备份
                    CryptoUtil.encrypt(inputStream, outputStream, password)
                } else {
                    // 普通备份
                    inputStream.copyTo(outputStream)
                }
            }
        }

        // 标记为非待处理
        contentValues.clear()
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
        context.contentResolver.update(uri, contentValues, null, null)

        val path = "Downloads/$BACKUP_DIR/$fileName"
        return Result.success(path)
    }

    /**
     * 使用文件系统备份（Android 9 及以下）
     */
    private fun backupWithFileSystem(dbFile: File, fileName: String, password: String?): Result<String> {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            BACKUP_DIR
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val backupFile = File(dir, fileName)
        if (!password.isNullOrEmpty()) {
            // 加密备份
            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    CryptoUtil.encrypt(input, output, password)
                }
            }
        } else {
            // 普通备份
            dbFile.copyTo(backupFile, overwrite = true)
        }

        return Result.success(backupFile.absolutePath)
    }

    /**
     * 从备份文件恢复数据库（安全版本：先校验再替换）
     * @param uri 备份文件的 URI
     * @param password 解密密码（加密备份时需要）
     * @return 恢复结果
     */
    fun restore(context: Context, uri: Uri, password: String? = null): Result<Unit> {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            val tempFile = File("${dbFile.absolutePath}.temp")

            // 判断是否是加密文件
            val fileName = getFileName(context, uri)
            val isEncrypted = fileName?.endsWith(".enc") == true

            // 1. 先写入临时文件
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    if (isEncrypted) {
                        // 解密恢复
                        if (password.isNullOrEmpty()) {
                            tempFile.delete()
                            return Result.failure(Exception("加密备份需要输入密码"))
                        }
                        val decryptResult = CryptoUtil.decrypt(inputStream, outputStream, password)
                        if (!decryptResult) {
                            tempFile.delete()
                            return Result.failure(Exception("密码错误或备份文件损坏"))
                        }
                        Unit
                    } else {
                        // 普通恢复
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            // 2. 校验临时文件大小
            if (tempFile.length() == 0L) {
                tempFile.delete()
                return Result.failure(Exception("备份文件为空或损坏"))
            }

            // 3. 检查 SQLite 文件头（magic number: "SQLite format 3\000"，共 16 字节）
            val isValidSqlite = tempFile.inputStream().use { stream ->
                val header = ByteArray(16)
                val bytesRead = stream.read(header)
                // 完整检查 "SQLite format 3" (15字节) + null 字节
                bytesRead >= 16 &&
                    header[0] == 'S'.code.toByte() &&
                    header[1] == 'Q'.code.toByte() &&
                    header[2] == 'L'.code.toByte() &&
                    header[3] == 'i'.code.toByte() &&
                    header[4] == 't'.code.toByte() &&
                    header[5] == 'e'.code.toByte() &&
                    header[6] == ' '.code.toByte() &&
                    header[7] == 'f'.code.toByte() &&
                    header[8] == 'o'.code.toByte() &&
                    header[9] == 'r'.code.toByte() &&
                    header[10] == 'm'.code.toByte() &&
                    header[11] == 'a'.code.toByte() &&
                    header[12] == 't'.code.toByte() &&
                    header[13] == ' '.code.toByte() &&
                    header[14] == '3'.code.toByte() &&
                    header[15] == 0.toByte()
            }

            if (!isValidSqlite) {
                tempFile.delete()
                return Result.failure(Exception("备份文件不是有效的数据库文件"))
            }

            // 4. 校验通过，关闭数据库连接
            context.deleteDatabase(DB_NAME)

            // 5. 原子替换：重命名临时文件为正式数据库文件
            val renamed = tempFile.renameTo(dbFile)
            if (!renamed) {
                // 如果重命名失败（跨文件系统），尝试直接复制
                tempFile.copyTo(dbFile, overwrite = true)
                tempFile.delete()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "恢复失败", e)
            // 清理临时文件
            val tempFile = File("${context.getDatabasePath(DB_NAME).absolutePath}.temp")
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    /**
     * 获取文件名
     */
    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name
    }

    /**
     * 获取备份文件列表
     */
    fun getBackupFiles(context: Context): List<BackupFile> {
        val files = mutableListOf<BackupFile>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.DATE_MODIFIED,
                MediaStore.Downloads.SIZE
            )

            val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_DIR/")

            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val date = cursor.getLong(dateColumn)
                    val size = cursor.getLong(sizeColumn)

                    if (name.endsWith(".db")) {
                        val uri = Uri.withAppendedPath(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        files.add(BackupFile(uri, name, date, size))
                    }
                }
            }
        } else {
            // Android 9 及以下使用文件系统
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                BACKUP_DIR
            )
            if (dir.exists()) {
                dir.listFiles()?.filter { it.name.endsWith(".db") }?.forEach { file ->
                    files.add(
                        BackupFile(
                            Uri.fromFile(file),
                            file.name,
                            file.lastModified(),
                            file.length()
                        )
                    )
                }
            }
        }

        return files.sortedByDescending { it.date }
    }
}

data class BackupFile(
    val uri: Uri,
    val name: String,
    val date: Long,
    val size: Long
)
