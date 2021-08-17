package com.r2.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.PhoneNumberUtils
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.r2.myapplication.databinding.FragmentKeypadBinding
import kotlin.properties.Delegates

class KeypadFragment : Fragment() {
    private var _binding: FragmentKeypadBinding? = null
    private var callScreenFragment: CallScreenFragment? = null

    private val binding get() = _binding!!

    private var btnDel: TextView? = null
    private var bottomNavigationView: BottomNavigationView? = null

    private var editPhoneNo by Delegates.observable("") { _, _, newValue ->
        if(newValue.isEmpty()) {
            btnDel?.visibility = INVISIBLE
        } else {
            btnDel?.visibility = VISIBLE
        }

        val phone = view?.findViewById<TextView>(R.id.editPhoneNumber)
        phone?.text = newValue
    }

    private fun buttonClickEvent(v: View) {
        var phoneNo: String = editPhoneNo

        vibratePhone()

        try {
            when (v.id) {
                R.id.buttonAsterisk -> {
                    phoneNo += "*"
                    editPhoneNo = phoneNo

                    btnDel?.visibility = VISIBLE
                }
                R.id.buttonHash -> {
                    phoneNo += "#"
                    editPhoneNo = phoneNo
                }
                R.id.buttonZero -> {
                    phoneNo += "0"
                    editPhoneNo = phoneNo

                }
                R.id.buttonOne -> {
                    phoneNo += "1"
                    editPhoneNo = phoneNo
                }
                R.id.buttonTwo -> {
                    phoneNo += "2"
                    editPhoneNo = phoneNo

                }
                R.id.buttonThree -> {
                    phoneNo += "3"
                    editPhoneNo = phoneNo
                }
                R.id.buttonFour -> {
                    phoneNo += "4"
                    editPhoneNo = phoneNo
                }
                R.id.buttonFive -> {
                    phoneNo += "5"
                    editPhoneNo = phoneNo
                }
                R.id.buttonSix -> {
                    phoneNo += "6"
                    editPhoneNo = phoneNo
                }
                R.id.buttonSeven -> {
                    phoneNo += "7"
                    editPhoneNo = phoneNo
                }
                R.id.buttonEight -> {
                    phoneNo += "8"
                    editPhoneNo = phoneNo
                }
                R.id.buttonNine -> {
                    phoneNo += "9"
                    editPhoneNo = phoneNo
                }
                R.id.buttonDel -> {
                    if (phoneNo.isNotEmpty()) {
                        phoneNo = phoneNo.substring(0, phoneNo.length - 1)
                    }
                    editPhoneNo = phoneNo

                    if(phoneNo.isEmpty()) {
                        btnDel?.visibility = INVISIBLE
                    }
                }
                else -> {
                    if (phoneNo.subSequence(phoneNo.length - 1, phoneNo.length) == "#") {
                        phoneNo = phoneNo.substring(0, phoneNo.length - 1)
                        val callInfo = "tel:" + phoneNo + Uri.encode("#")
                        val callIntent = Intent(Intent.ACTION_CALL)
                        callIntent.data = Uri.parse(callInfo)
                        startActivity(callIntent)
                    } else {
                        val callInfo = "tel:$phoneNo"
                        val callIntent = Intent(Intent.ACTION_CALL)
                        callIntent.data = Uri.parse(callInfo)
                        startActivity(callIntent)
                    }
                }
            }

            if (phoneNo.length >= 3) {
                val formattedPhoneNum = PhoneNumberUtils.formatNumber(phoneNo, "US")
                editPhoneNo = formattedPhoneNum
            }
        } catch (ex: Exception) {
        }
    }

    private fun startCall() {
        callScreenFragment = CallScreenFragment(editPhoneNo)
        val fragmentManager = activity?.supportFragmentManager

        if(editPhoneNo.isNotEmpty()) {

            fragmentManager?.beginTransaction()?.replace(R.id.keypad, callScreenFragment!!, "callScreen")?.commitNow()
        } else {
            Toast.makeText(context, "Please input a number", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibratePhone() {
        val v = (activity?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(25, 175))
        } else {
            v.vibrate(500)
        }
    }

    private fun detectButtonClick(button: TextView?) {
        button?.setOnClickListener {
            buttonClickEvent(button)
        }
    }

    private fun detectLongButtonClick(button: TextView?) {
        button?.setOnLongClickListener{
            editPhoneNo = ""
            btnDel!!.visibility = INVISIBLE
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (activity as AppCompatActivity).supportActionBar?.hide()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentKeypadBinding.inflate(inflater, container, false)
        val root: View = binding.root

        bottomNavigationView = activity?.findViewById(R.id.bottomNavigationView)
        bottomNavigationView?.visibility = VISIBLE

        btnDel = binding.buttonDel
        btnDel?.visibility = INVISIBLE
        detectButtonClick(btnDel)
        detectLongButtonClick(btnDel)

        val btn1 = binding.buttonOne
        detectButtonClick(btn1)

        val btn2 = binding.buttonTwo
        detectButtonClick(btn2)

        val btn3 = binding.buttonThree
        detectButtonClick(btn3)

        val btn4 = binding.buttonFour
        detectButtonClick(btn4)

        val btn5 = binding.buttonFive
        detectButtonClick(btn5)

        val btn6 = binding.buttonSix
        detectButtonClick(btn6)

        val btn7 = binding.buttonSeven
        detectButtonClick(btn7)

        val btn8 = binding.buttonEight
        detectButtonClick(btn8)

        val btn9 = binding.buttonNine
        detectButtonClick(btn9)

        val btn0 = binding.buttonZero
        detectButtonClick(btn0)

        val btnAsterisk = binding.buttonAsterisk
        detectButtonClick(btnAsterisk)

        val btnHash = binding.buttonHash
        detectButtonClick(btnHash)

        val startCall = binding.startCall

        startCall.setOnClickListener{
            startCall()
            // TODO - validate number before sending to call screen fragment
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}