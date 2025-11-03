package com.cookandroide.pikaboka.ui.model

data class WordUi(
    val id: Long,
    val jp: String,
    val kana: String,
    val mean: String,
    val isFavorite: Boolean
)
