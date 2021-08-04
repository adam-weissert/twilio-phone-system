package com.r2.myapplication

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.*
import android.widget.Chronometer
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Task
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.messaging.FirebaseMessaging
import com.r2.myapplication.IncomingCallNotificationService
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioSwitch
import com.twilio.voice.*
import com.twilio.voice.Call.CallQualityWarning
import java.util.*

class VoiceActivity : AppCompatActivity() {
    private val accessToken =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCIsImN0eSI6InR3aWxpby1mcGE7dj0xIn0.eyJqdGkiOiJTSzM5ZGYwZTAwZmE3ZjI2Y2YyNmJmYjlkMGFkMzgxOWU2LTE2MjgxMDE0NDEiLCJncmFudHMiOnsiaWRlbnRpdHkiOiJxdWlja19zdGFydCIsInZvaWNlIjp7Im91dGdvaW5nIjp7ImFwcGxpY2F0aW9uX3NpZCI6IkFQMzUyYTg1YWEyMzc1NDY1ZGFjOWEyMjEyY2IwZjRiYTAifSwicHVzaF9jcmVkZW50aWFsX3NpZCI6IkNSZjlkYTlmODM4MjE4OThkYTJkZDE0MDY4ZmE2ZmZhNjMifX0sImlhdCI6MTYyODEwMTQ0MSwiZXhwIjoxNjI4MTA1MDQxLCJpc3MiOiJTSzM5ZGYwZTAwZmE3ZjI2Y2YyNmJmYjlkMGFkMzgxOWU2Iiwic3ViIjoiQUNlN2ZmMzAwYTQzMDBmZWM1MDgzMTkxZWFlOTYyMzU3MyJ9.YPeTku3_3qOd4S3aWdXdSBUDcIAkYZpEgcxV5ATCNIU"

    /*
     * Audio device management
     */
    private var audioSwitch: AudioSwitch? = null
    private var savedVolumeControlStream = 0
    private var audioDeviceMenuItem: MenuItem? = null
    private var isReceiverRegistered = false
    private var voiceBroadcastReceiver: VoiceBroadcastReceiver? = null

    // Empty HashMap, never populated for the Quickstart
    private var params = HashMap<String, String>()
    private var coordinatorLayout: CoordinatorLayout? = null
    private var callActionFab: FloatingActionButton? = null
    private var hangupActionFab: FloatingActionButton? = null
    private var holdActionFab: FloatingActionButton? = null
    private var muteActionFab: FloatingActionButton? = null
    private var chronometer: Chronometer? = null
    private var notificationManager: NotificationManager? = null
    private var alertDialog: AlertDialog? = null
    private var activeCallInvite: CallInvite? = null
    private var activeCall: Call? = null
    private var activeCallNotificationId = 0
    private var registrationListener = registrationListener()
    private var callListener = callListener()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice)

        // These flags ensure that the activity can be launched when the screen is locked.
        val window = window
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        coordinatorLayout = findViewById(R.id.coordinator_layout)
        callActionFab = findViewById(R.id.call_action_fab)
        hangupActionFab = findViewById(R.id.hangup_action_fab)
        holdActionFab = findViewById(R.id.hold_action_fab)
        muteActionFab = findViewById(R.id.mute_action_fab)
        chronometer = findViewById(R.id.chronometer)
        callActionFab?.setOnClickListener(callActionFabClickListener())
        hangupActionFab?.setOnClickListener(hangupActionFabClickListener())
        holdActionFab?.setOnClickListener(holdActionFabClickListener())
        muteActionFab?.setOnClickListener(muteActionFabClickListener())
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        /*
         * Setup the broadcast receiver to be notified of FCM Token updates
         * or incoming call invite in this Activity.
         */voiceBroadcastReceiver = VoiceBroadcastReceiver()
        registerReceiver()

        /*
         * Setup audio device management and set the volume control stream
         */audioSwitch = AudioSwitch(applicationContext)
        savedVolumeControlStream = volumeControlStream
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        /*
         * Setup the UI
         */resetUI()

        /*
         * Displays a call dialog if the intent contains a call invite
         */handleIncomingCallIntent(intent)

        /*
         * Ensure the microphone permission is enabled
         */if (!checkPermissionForMicrophone()) {
            requestPermissionForMicrophone()
        } else {
            registerForCallInvites()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingCallIntent(intent)
    }

    private fun registrationListener(): RegistrationListener {
        return object : RegistrationListener {
            override fun onRegistered(accessToken: String, fcmToken: String) {
                Log.d(TAG, "Successfully registered FCM $fcmToken")
            }

            override fun onError(
                error: RegistrationException,
                accessToken: String,
                fcmToken: String
            ) {
                val message = String.format(
                    Locale.US,
                    "Registration Error: %d, %s",
                    error.errorCode,
                    error.message
                )
                Log.e(TAG, message)
                Snackbar.make(coordinatorLayout!!, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun callListener(): Call.Listener {
        return object : Call.Listener {
            /*
             * This callback is emitted once before the Call.Listener.onConnected() callback when
             * the callee is being alerted of a Call. The behavior of this callback is determined by
             * the answerOnBridge flag provided in the Dial verb of your TwiML application
             * associated with this client. If the answerOnBridge flag is false, which is the
             * default, the Call.Listener.onConnected() callback will be emitted immediately after
             * Call.Listener.onRinging(). If the answerOnBridge flag is true, this will cause the
             * call to emit the onConnected callback only after the call is answered.
             * See answeronbridge for more details on how to use it with the Dial TwiML verb. If the
             * twiML response contains a Say verb, then the call will emit the
             * Call.Listener.onConnected callback immediately after Call.Listener.onRinging() is
             * raised, irrespective of the value of answerOnBridge being set to true or false
             */
            override fun onRinging(call: Call) {
                Log.d(TAG, "Ringing")
                /*
                 * When [answerOnBridge](https://www.twilio.com/docs/voice/twiml/dial#answeronbridge)
                 * is enabled in the <Dial> TwiML verb, the caller will not hear the ringback while
                 * the call is ringing and awaiting to be accepted on the callee's side. The application
                 * can use the `SoundPoolManager` to play custom audio files between the
                 * `Call.Listener.onRinging()` and the `Call.Listener.onConnected()` callbacks.
                 */if (BuildConfig.playCustomRingback) {
                    SoundPoolManager.getInstance(this@VoiceActivity)?.playRinging()
                }
            }

            override fun onConnectFailure(call: Call, error: CallException) {
                audioSwitch!!.deactivate()
                if (BuildConfig.playCustomRingback) {
                    SoundPoolManager.getInstance(this@VoiceActivity)?.stopRinging()
                }
                Log.d(TAG, "Connect failure")
                val message = String.format(
                    Locale.US,
                    "Call Error: %d, %s",
                    error.errorCode,
                    error.message
                )
                Log.e(TAG, message)
                Snackbar.make(coordinatorLayout!!, message, Snackbar.LENGTH_LONG).show()
                resetUI()
            }

            override fun onConnected(call: Call) {
                audioSwitch!!.activate()
                if (BuildConfig.playCustomRingback) {
                    SoundPoolManager.getInstance(this@VoiceActivity)?.stopRinging()
                }
                Log.d(TAG, "Connected")
                activeCall = call
            }

            override fun onReconnecting(call: Call, callException: CallException) {
                Log.d(TAG, "onReconnecting")
            }

            override fun onReconnected(call: Call) {
                Log.d(TAG, "onReconnected")
            }

            override fun onDisconnected(call: Call, error: CallException?) {
                audioSwitch!!.deactivate()
                if (BuildConfig.playCustomRingback) {
                    SoundPoolManager.getInstance(this@VoiceActivity)?.stopRinging()
                }
                Log.d(TAG, "Disconnected")
                if (error != null) {
                    val message = String.format(
                        Locale.US,
                        "Call Error: %d, %s",
                        error.errorCode,
                        error.message
                    )
                    Log.e(TAG, message)
                    Snackbar.make(coordinatorLayout!!, message, Snackbar.LENGTH_LONG).show()
                }
                resetUI()
            }

            /*
             * currentWarnings: existing quality warnings that have not been cleared yet
             * previousWarnings: last set of warnings prior to receiving this callback
             *
             * Example:
             *   - currentWarnings: { A, B }
             *   - previousWarnings: { B, C }
             *
             * Newly raised warnings = currentWarnings - intersection = { A }
             * Newly cleared warnings = previousWarnings - intersection = { C }
             */
            override fun onCallQualityWarningsChanged(
                call: Call,
                currentWarnings: MutableSet<CallQualityWarning>,
                previousWarnings: MutableSet<CallQualityWarning>
            ) {
                if (previousWarnings.size > 1) {
                    val intersection: MutableSet<CallQualityWarning> = HashSet(currentWarnings)
                    currentWarnings.removeAll(previousWarnings)
                    intersection.retainAll(previousWarnings)
                    previousWarnings.removeAll(intersection)
                }
                val message = String.format(
                    Locale.US,
                    "Newly raised warnings: $currentWarnings Clear warnings $previousWarnings"
                )
                Log.e(TAG, message)
                Snackbar.make(coordinatorLayout!!, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    /*
     * The UI state when there is an active call
     */
    private fun setCallUI() {
        callActionFab!!.hide()
        hangupActionFab!!.show()
        holdActionFab!!.show()
        muteActionFab!!.show()
        chronometer!!.visibility = View.VISIBLE
        chronometer!!.base = SystemClock.elapsedRealtime()
        chronometer!!.start()
    }

    /*
     * Reset UI elements
     */
    private fun resetUI() {
        callActionFab!!.show()
        muteActionFab!!.setImageDrawable(
            ContextCompat.getDrawable(
                this@VoiceActivity,
                R.drawable.ic_mic_white_24dp
            )
        )
        holdActionFab!!.hide()
        holdActionFab!!.backgroundTintList = ColorStateList
            .valueOf(ContextCompat.getColor(this, R.color.colorAccent))
        muteActionFab!!.hide()
        hangupActionFab!!.hide()
        chronometer!!.visibility = View.INVISIBLE
        chronometer!!.stop()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver()
    }

    public override fun onDestroy() {
        /*
         * Tear down audio device management and restore previous volume stream
         */
        audioSwitch!!.stop()
        volumeControlStream = savedVolumeControlStream
        SoundPoolManager.getInstance(this)?.release()
        super.onDestroy()
    }

    private fun handleIncomingCallIntent(intent: Intent?) {
        if (intent != null && intent.action != null) {
            val action = intent.action
            activeCallInvite = intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE)
            activeCallNotificationId =
                intent.getIntExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, 0)
            when (action) {
                Constants.ACTION_INCOMING_CALL -> handleIncomingCall()
                Constants.ACTION_INCOMING_CALL_NOTIFICATION -> showIncomingCallDialog()
                Constants.ACTION_CANCEL_CALL -> handleCancel()
                Constants.ACTION_FCM_TOKEN -> registerForCallInvites()
                Constants.ACTION_ACCEPT -> answer()
                else -> {
                }
            }
        }
    }

    private fun handleIncomingCall() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            showIncomingCallDialog()
        } else {
            if (isAppVisible) {
                showIncomingCallDialog()
            }
        }
    }

    private fun handleCancel() {
        if (alertDialog != null && alertDialog!!.isShowing) {
            SoundPoolManager.getInstance(this)?.stopRinging()
            alertDialog!!.cancel()
        }
    }

    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(Constants.ACTION_INCOMING_CALL)
            intentFilter.addAction(Constants.ACTION_CANCEL_CALL)
            intentFilter.addAction(Constants.ACTION_FCM_TOKEN)
            LocalBroadcastManager.getInstance(this).registerReceiver(
                voiceBroadcastReceiver!!, intentFilter
            )
            isReceiverRegistered = true
        }
    }

    private fun unregisterReceiver() {
        if (isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceBroadcastReceiver!!)
            isReceiverRegistered = false
        }
    }

    private inner class VoiceBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null && (action == Constants.ACTION_INCOMING_CALL || action == Constants.ACTION_CANCEL_CALL)) {
                /*
                 * Handle the incoming or cancelled call invite
                 */
                handleIncomingCallIntent(intent)
            }
        }
    }

    private fun answerCallClickListener(): DialogInterface.OnClickListener {
        return DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
            Log.d(TAG, "Clicked accept")
            val acceptIntent =
                Intent(applicationContext, IncomingCallNotificationService::class.java)
            acceptIntent.action = Constants.ACTION_ACCEPT
            acceptIntent.putExtra(Constants.INCOMING_CALL_INVITE, activeCallInvite)
            acceptIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, activeCallNotificationId)
            Log.d(TAG, "Clicked accept startService")
            startService(acceptIntent)
        }
    }

    private fun callClickListener(): DialogInterface.OnClickListener {
        return DialogInterface.OnClickListener { dialog: DialogInterface, _: Int ->
            // Place a call
            val contact = (dialog as AlertDialog).findViewById<EditText>(R.id.contact)
            params["to"] = contact.text.toString()
            val connectOptions = ConnectOptions.Builder(accessToken)
                .params(params)
                .build()
            activeCall = Voice.connect(this@VoiceActivity, connectOptions, callListener)
            setCallUI()
            alertDialog!!.dismiss()
        }
    }

    private fun cancelCallClickListener(): DialogInterface.OnClickListener {
        return DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
            SoundPoolManager.getInstance(this@VoiceActivity)?.stopRinging()
            if (activeCallInvite != null) {
                val intent = Intent(this@VoiceActivity, IncomingCallNotificationService::class.java)
                intent.action = Constants.ACTION_REJECT
                intent.putExtra(Constants.INCOMING_CALL_INVITE, activeCallInvite)
                startService(intent)
            }
            if (alertDialog != null && alertDialog!!.isShowing) {
                alertDialog!!.dismiss()
            }
        }
    }

    /*
     * Register your FCM token with Twilio to receive incoming call invites
     */
    private fun registerForCallInvites() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(this) { instanceIdResult: Task<String?> ->
            val fcmToken = instanceIdResult.result
            Log.i(TAG, "Registering with FCM: $fcmToken")
            Voice.register(
                accessToken,
                Voice.RegistrationChannel.FCM,
                fcmToken!!,
                registrationListener
            )
        }
    }

    private fun callActionFabClickListener(): View.OnClickListener {
        return View.OnClickListener {
            alertDialog =
                createCallDialog(callClickListener(), cancelCallClickListener(), this@VoiceActivity)
            alertDialog!!.show()
        }
    }

    private fun hangupActionFabClickListener(): View.OnClickListener {
        return View.OnClickListener {
            SoundPoolManager.getInstance(this@VoiceActivity)?.playDisconnect()
            resetUI()
            disconnect()
        }
    }

    private fun holdActionFabClickListener(): View.OnClickListener {
        return View.OnClickListener { hold() }
    }

    private fun muteActionFabClickListener(): View.OnClickListener {
        return View.OnClickListener { mute() }
    }

    /*
     * Accept an incoming Call
     */
    private fun answer() {
        SoundPoolManager.getInstance(this)?.stopRinging()
        activeCallInvite!!.accept(this, callListener)
        notificationManager!!.cancel(activeCallNotificationId)
        setCallUI()
        if (alertDialog != null && alertDialog!!.isShowing) {
            alertDialog!!.dismiss()
        }
    }

    /*
     * Disconnect from Call
     */
    private fun disconnect() {
        if (activeCall != null) {
            activeCall!!.disconnect()
            activeCall = null
        }
    }

    private fun hold() {
        if (activeCall != null) {
            val hold = !activeCall!!.isOnHold
            activeCall!!.hold(hold)
            applyFabState(holdActionFab, hold)
        }
    }

    private fun mute() {
        if (activeCall != null) {
            val mute = !activeCall!!.isMuted
            activeCall!!.mute(mute)
            applyFabState(muteActionFab, mute)
        }
    }

    private fun applyFabState(button: FloatingActionButton?, enabled: Boolean) {
        // Set fab as pressed when call is on hold
        val colorStateList = if (enabled) ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                R.color.colorPrimaryDark
            )
        ) else ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                R.color.colorAccent
            )
        )
        button!!.backgroundTintList = colorStateList
    }

    private fun checkPermissionForMicrophone(): Boolean {
        val resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return resultMic == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionForMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.RECORD_AUDIO
            )
        ) {
            Snackbar.make(
                coordinatorLayout!!,
                "Microphone permissions needed. Please allow in your application settings.",
                Snackbar.LENGTH_LONG
            ).show()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO),
                MIC_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        /*
         * Check if microphone permissions is granted
         */
        if (requestCode == MIC_PERMISSION_REQUEST_CODE && permissions.isNotEmpty()) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(
                    coordinatorLayout!!,
                    "Microphone permissions needed. Please allow in your application settings.",
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                registerForCallInvites()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        audioDeviceMenuItem = menu.findItem(R.id.menu_audio_device)

        /*
         * Start the audio device selector after the menu is created and update the icon when the
         * selected audio device changes.
         */audioSwitch!!.start { _: List<AudioDevice?>?, audioDevice: AudioDevice? ->
            updateAudioDeviceIcon(audioDevice)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_audio_device) {
            showAudioDevices()
            return true
        }
        return false
    }

    /*
     * Show the current available audio devices.
     */
    private fun showAudioDevices() {
        val selectedDevice = audioSwitch!!.selectedAudioDevice
        val availableAudioDevices = audioSwitch!!.availableAudioDevices
        if (selectedDevice != null) {
            val selectedDeviceIndex = availableAudioDevices.indexOf(selectedDevice)
            val audioDeviceNames = ArrayList<String>()
            for (a in availableAudioDevices) {
                audioDeviceNames.add(a.name)
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.select_device)
                .setSingleChoiceItems(
                    audioDeviceNames.toTypedArray<CharSequence>(),
                    selectedDeviceIndex
                ) { dialog: DialogInterface, index: Int ->
                    dialog.dismiss()
                    val selectedAudioDevice = availableAudioDevices[index]
                    updateAudioDeviceIcon(selectedAudioDevice)
                    audioSwitch!!.selectDevice(selectedAudioDevice)
                }.create().show()
        }
    }

    /*
     * Update the menu icon based on the currently selected audio device.
     */
    private fun updateAudioDeviceIcon(selectedAudioDevice: AudioDevice?) {
        var audioDeviceMenuIcon = R.drawable.ic_phonelink_ring_white_24dp
        if (selectedAudioDevice is AudioDevice.BluetoothHeadset) {
            audioDeviceMenuIcon = R.drawable.ic_bluetooth_white_24dp
        } else if (selectedAudioDevice is AudioDevice.WiredHeadset) {
            audioDeviceMenuIcon = R.drawable.ic_headset_mic_white_24dp
        } else if (selectedAudioDevice is AudioDevice.Earpiece) {
            audioDeviceMenuIcon = R.drawable.ic_phonelink_ring_white_24dp
        } else if (selectedAudioDevice is AudioDevice.Speakerphone) {
            audioDeviceMenuIcon = R.drawable.ic_volume_up_white_24dp
        }
        audioDeviceMenuItem!!.setIcon(audioDeviceMenuIcon)
    }

    private fun showIncomingCallDialog() {
        SoundPoolManager.getInstance(this)?.playRinging()
        if (activeCallInvite != null) {
            alertDialog = createIncomingCallDialog(
                this@VoiceActivity,
                activeCallInvite!!,
                answerCallClickListener(),
                cancelCallClickListener()
            )
            alertDialog!!.show()
        }
    }

    private val isAppVisible: Boolean
        get() = ProcessLifecycleOwner
            .get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)

    companion object {
        private const val TAG = "VoiceActivity"
        private const val MIC_PERMISSION_REQUEST_CODE = 1
        fun createIncomingCallDialog(
            context: Context?,
            callInvite: CallInvite,
            answerCallClickListener: DialogInterface.OnClickListener?,
            cancelClickListener: DialogInterface.OnClickListener?
        ): AlertDialog {
            val alertDialogBuilder = AlertDialog.Builder(context)
            alertDialogBuilder.setIcon(R.drawable.ic_call_black_24dp)
            alertDialogBuilder.setTitle("Incoming Call")
            alertDialogBuilder.setPositiveButton("Accept", answerCallClickListener)
            alertDialogBuilder.setNegativeButton("Reject", cancelClickListener)
            alertDialogBuilder.setMessage(callInvite.from + " is calling with " + callInvite.callerInfo.isVerified + " status")
            return alertDialogBuilder.create()
        }

        private fun createCallDialog(
            callClickListener: DialogInterface.OnClickListener,
            cancelClickListener: DialogInterface.OnClickListener,
            activity: Activity
        ): AlertDialog {
            val alertDialogBuilder = AlertDialog.Builder(activity)
            alertDialogBuilder.setIcon(R.drawable.ic_call_black_24dp)
            alertDialogBuilder.setTitle("Call")
            alertDialogBuilder.setPositiveButton("Call", callClickListener)
            alertDialogBuilder.setNegativeButton("Cancel", cancelClickListener)
            alertDialogBuilder.setCancelable(false)
            val li = LayoutInflater.from(activity)
            val dialogView = li.inflate(
                R.layout.dialog_call,
                activity.findViewById(android.R.id.content),
                false
            )
            val contact = dialogView.findViewById<EditText>(R.id.contact)
            contact.setHint(R.string.callee)
            alertDialogBuilder.setView(dialogView)
            return alertDialogBuilder.create()
        }
    }
}