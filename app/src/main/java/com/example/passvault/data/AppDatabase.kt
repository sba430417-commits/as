package com.example.passvault.data

import android.content.ContentValues
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.passvault.util.CryptoManager

@Database(entities = [Account::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "passvault.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE accounts ADD COLUMN category TEXT NOT NULL DEFAULT 'عام'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cursor = database.query("SELECT id, siteName, displayName, username, encryptedPassword, email, phone, category FROM accounts")
                cursor.use {
                    val id = it.getColumnIndexOrThrow("id")
                    val columns = listOf("siteName", "displayName", "username", "encryptedPassword", "email", "phone", "category")
                    val indexes = columns.map(it::getColumnIndexOrThrow)
                    while (it.moveToNext()) {
                        val values = ContentValues()
                        columns.zip(indexes).forEach { (column, index) ->
                            if (it.isNull(index)) values.putNull(column)
                            else values.put(column, CryptoManager.encrypt(it.getString(index)))
                        }
                        database.update("accounts", 0, values, "id = ?", arrayOf(it.getLong(id).toString()))
                    }
                }
            }
        }
    }
}
