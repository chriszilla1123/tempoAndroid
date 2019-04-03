package net.chilltec.tempo.utils

import android.os.Bundle
import android.support.v7.preference.PreferenceFragmentCompat
import net.chilltec.tempo.R

class SettingsFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }
}