package com.cookandroide.pikaboka.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "words",
    indices = [Index(value = ["jp", "kana"], unique = true)]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val jp: String,
    val kana: String,
    val mean: String,
    val romaji: String,
    val isFavorite: Boolean = false
)
