package com.example.officepdf

interface SettingsStorage {
    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)
}
