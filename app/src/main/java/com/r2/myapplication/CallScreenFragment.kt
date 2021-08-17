package com.r2.myapplication

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.r2.myapplication.databinding.FragmentCallScreenBinding
import java.lang.Exception

class CallScreenFragment(editPhoneNo: String?) : Fragment() {
    private var _binding: FragmentCallScreenBinding? = null

    private val binding get() = _binding!!

    private var phoneNo: String? = editPhoneNo

    private var callerID: TextView? = null
    private var bottomNavigationView: BottomNavigationView? = null
    private var muteColor: Int? = Color.parseColor("#595959")
    private var keypadColor: Int? = Color.parseColor("#595959")
    private var speakerColor: Int? = Color.parseColor("#595959")



    private fun toggleSelectedState(button: TextView?, buttonLabel: TextView? = null) {
        var mutePressColor: Int ?= null
        var keypadPressColor: Int ?= null
        var speakerPressColor: Int ?= null

        val pressColorTemp: Int = resources.getColor(resources.getIdentifier("callScreenButton", "color", activity?.packageName), activity?.theme)
        // TODO: rewrite the bonkers code
        try {
            when(button?.id){
                R.id.callScreenMute -> {
                    mutePressColor = if(muteColor == pressColorTemp) Color.RED else pressColorTemp
                    button.backgroundTintList = ColorStateList.valueOf(mutePressColor)
                    buttonLabel?.setTextColor(mutePressColor)
                    muteColor = mutePressColor
                }
                R.id.callScreenKeypad -> {
                    keypadPressColor = if(keypadColor == pressColorTemp) Color.parseColor("#0677DE") else pressColorTemp
                    button.backgroundTintList = ColorStateList.valueOf(keypadPressColor)
                    buttonLabel?.setTextColor(keypadPressColor)
                    keypadColor = keypadPressColor
                }
                R.id.callScreenSpeaker -> {
                    speakerPressColor = if(speakerColor == pressColorTemp) Color.parseColor("#00B528") else pressColorTemp
                    button.backgroundTintList = ColorStateList.valueOf(speakerPressColor)
                    buttonLabel?.setTextColor(speakerPressColor)
                    speakerColor = speakerPressColor
                }
            }
        } catch (ex: Exception) {
            println("Error toggling action button state: $ex")
            Toast.makeText(context, "Error toggling action button state: $ex", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        (activity as AppCompatActivity).supportActionBar?.hide()
        (activity as AppCompatActivity).window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onCreate(savedInstanceState)
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentCallScreenBinding.inflate(inflater, container, false)

        bottomNavigationView = activity?.findViewById(R.id.bottomNavigationView)
        bottomNavigationView?.visibility = INVISIBLE

        callerID = binding.callerID
        callerID?.text = phoneNo

        println("Phone No: $phoneNo")

        val muteButton = binding.callScreenMute
        val muteText = binding.callScreenMuteText
        detectButtonClick(muteButton, muteText)

        val keypadButton = binding.callScreenKeypad
        val keypadButtonText = binding.callScreenKeypadText
        detectButtonClick(keypadButton, keypadButtonText)

        val speakerButton = binding.callScreenSpeaker
        val speakerButtonText = binding.callScreenSpeakerText
        detectButtonClick(speakerButton, speakerButtonText)

        val endCallButton = binding.callScreenEndCall

        endCallButton.setOnClickListener{
            // TODO - either exit app or go back to last fragment and end call
            Toast.makeText(context, "End Call", Toast.LENGTH_SHORT).show()

        }

        return binding.root
    }

    private fun detectButtonClick(button: Button?, text: TextView?) {
        button?.setOnClickListener{
            toggleSelectedState(button, text)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}