import android.os.Bundle
import android.support.v7.preference.PreferenceFragmentCompat
import net.chilltec.tempo.R

class SettingsFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(p0: Bundle?, p1: String?) {
        //Load the XML
        addPreferencesFromResource(R.xml.settings)
    }

}