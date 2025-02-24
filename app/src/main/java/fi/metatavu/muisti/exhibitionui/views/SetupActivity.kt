package fi.metatavu.muisti.exhibitionui.views

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import fi.metatavu.muisti.exhibitionui.R

/**
 * Setup activity
 */
class SetupActivity : MuistiActivity() {

    private lateinit var mViewModel: SetupViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setup_activity)

        mViewModel = ViewModelProvider(this).get(SetupViewModel::class.java)

        val deviceNameInput = findViewById<EditText>(R.id.deviceNameInput)
        val deviceDescriptionInput = findViewById<EditText>(R.id.deviceDescriptionInput)
        val saveSetupButton = findViewById<Button>(R.id.saveSetup)

        saveSetupButton.setOnClickListener {
            val deviceName = deviceNameInput.text.toString()
            val deviceDescription = deviceDescriptionInput.text.toString()

        }

    }
}