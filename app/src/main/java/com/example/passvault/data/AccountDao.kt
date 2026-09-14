package com.example.passvault.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY siteName COLLATE NOCASE ASC")
    fun getAll(): LiveData<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY siteName COLLATE NOCASE ASC")
    suspend fun getAllNow(): List<Account>

    @Insert
    suspend fun insert(account: Account): Long

    @Insert
    suspend fun insertAll(accounts: List<Account>)

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("DELETE FROM accounts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): Account?
}
