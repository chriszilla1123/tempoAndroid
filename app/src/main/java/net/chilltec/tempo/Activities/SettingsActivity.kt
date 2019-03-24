package net.chilltec.tempo.Activities

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import net.chilltec.tempo.Utils.SettingsFragment

class SettingsActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment()).commit()

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val preferenceTag = "PreferenceChange"
        preferences.registerOnSharedPreferenceChangeListener { sharedPreferences, key ->
            if(key == "server_base_url"){
                var newUrl = sharedPreferences.getString(key, "") ?: "http://www.example.com/api"
                newUrl = validateURL(newUrl)
                sharedPreferences.edit().putString(key, newUrl).apply()
                this.recreate()
            }
            Log.i(preferenceTag,
                "Preference key {$key} has been changed to {${sharedPreferences.getString(key, "")}}")
        }
    }

    fun validateURL(url: String): String {
        var newUrl = url
        if(newUrl.contains("http://") || newUrl.contains("https://")){ }
        else{
            newUrl = "http://$url"
        }

        if(newUrl.endsWith("/")){
            while(newUrl.endsWith("/")){
                newUrl = newUrl.substring(0, newUrl.lastIndex - 1)
            }
        }

        newUrl = newUrl.replace("\\s".toRegex(), "")

        return newUrl
    }
}