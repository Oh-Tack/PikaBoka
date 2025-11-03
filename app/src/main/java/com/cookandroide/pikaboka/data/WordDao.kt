package com.cookandroide.pikaboka.data

import androidx.room.*

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY id ASC")
    fun getAllFlow(): kotlinx.coroutines.flow.Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE isFavorite = 1 ORDER BY id ASC")
    fun getFavoritesFlow(): kotlinx.coroutines.flow.Flow<List<WordEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<WordEntity>)

    @Query("UPDATE words SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)
}

