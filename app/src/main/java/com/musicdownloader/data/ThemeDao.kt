package com.musicdownloader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ThemeDao {
    @Query("SELECT * FROM user_themes ORDER BY isPreset DESC, createdAt DESC")
    suspend fun getAll(): List<UserTheme>

    @Query("SELECT * FROM user_themes WHERE id = :id")
    suspend fun getById(id: Long): UserTheme?

    @Query("SELECT * FROM user_themes WHERE isPreset = 1")
    suspend fun getPresets(): List<UserTheme>

    @Query("SELECT * FROM user_themes WHERE isPreset = 0")
    suspend fun getCustom(): List<UserTheme>

    @Insert
    suspend fun insert(theme: UserTheme): Long

    @Insert
    suspend fun insertAll(themes: List<UserTheme>)

    @Update
    suspend fun update(theme: UserTheme)

    @Delete
    suspend fun delete(theme: UserTheme)

    @Query("DELETE FROM user_themes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM user_themes")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM user_themes WHERE name = :name")
    suspend fun countByName(name: String): Int

    @Query("SELECT * FROM user_themes WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): UserTheme?
}
