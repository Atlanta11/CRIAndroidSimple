package com.example.criandroidsimple

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    // ============================================================
    // LOGGING
    // ============================================================

    private companion object {

        const val MAX_VISIBLE_LOG_LINES = 200

        const val LOG_TAG_STATUS = "CRI_STATUS"
        const val LOG_TAG_CONNECTION = "CRI_CONNECTION"
        const val LOG_TAG_ERROR = "CRI_ERROR"
        const val LOG_TAG_PROGRAM = "CRI_PROGRAM"
    }

    private var lastLoggedConnectionState: Boolean? = null
    private var lastLoggedProgramColorConnected: Boolean? = null

    // ============================================================
    // PROGRAMS
    // ============================================================

    private val programFiles = arrayOf(
        "program01.xml",
        "program02.xml",
        "program03.xml",
        "program04.xml",
        "program05.xml",
        "program06.xml",
        "program07.xml",
        "program08.xml",
        "program09.xml"
    )

    private val defaultProgramNames = arrayOf(
        "PROGRAM 1",
        "PROGRAM 2",
        "PROGRAM 3",
        "PROGRAM 4",
        "PROGRAM 5",
        "PROGRAM 6",
        "PROGRAM 7",
        "PROGRAM 8",
        "PROGRAM 9"
    )

    private val expertPin = "1234"

    private enum class AccessLevel {
        OPERATOR,
        EXPERT
    }

    private var accessLevel = AccessLevel.OPERATOR

    // ============================================================
    // OPERATOR PROGRAM COUNT
    // ============================================================

    /*
     * Aantal PROGRAM-knoppen die zichtbaar zijn in OPERATOR.
     *
     * Geldige waarden:
     *
     * 1 t/m 9
     *
     * Deze waarde wordt opgeslagen en blijft behouden na
     * het opnieuw starten van de app.
     *
     * Eerste keer:
     * standaard 2
     */

    private var operatorProgramCount = 2

    private val operatorProgramPreferences =
        "CRI_OPERATOR_PROGRAM_SETTINGS"

    // ============================================================
    // PROGRAM RUN CONTROL
    // ============================================================

    /*
     * activeProgramIndex:
     *
     * -1 = geen programma actief
     *  0 = program01.xml
     *  1 = program02.xml
     * ...
     *  8 = program09.xml
     */

    @Volatile
    private var activeProgramIndex = -1

    @Volatile
    private var programRunning = false

    @Volatile
    private var programStartInProgress = false

    @Volatile
    private var stopRequested = false

    /*
     * Beschermt het starten van programma's tegen zeer snelle
     * dubbele drukken.
     */
    private val programStartLock = Any()

    // ============================================================
    // CONNECTION
    // ============================================================

    private var socket: Socket? = null
    private var output: OutputStream? = null

    private val executor = Executors.newCachedThreadPool()

    private val reconnectExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor()

    private val outputLock = Any()

    private var sendCounter = 1

    private val reconnectInProgress = AtomicBoolean(false)

    private var autoReconnectEnabled = true

    private var reconnectMonitorStarted = false

    // ============================================================
    // JOG
    // ============================================================

    private val jogValues = DoubleArray(9)

    private var overrideValue = 40.0

    // ============================================================
    // UI
    // ============================================================

    private lateinit var ipEdit: TextInputEditText
    private lateinit var portEdit: TextInputEditText

    private lateinit var statusText: TextView
    private lateinit var connectionInfo: TextView
    private lateinit var accessLevelText: TextView
    private lateinit var logText: TextView
    private lateinit var logTitle: TextView
    private lateinit var programTitle: TextView

    private lateinit var expertConnectionLayout: LinearLayout
    private lateinit var expertControlLayout: LinearLayout
    private lateinit var expertJogLayout: LinearLayout
    private lateinit var expertLogLayout: LinearLayout

    private lateinit var enableButton: Button
    private lateinit var resetButton: Button
    private lateinit var out31Button: Button
    private lateinit var plcButton: Button
    private lateinit var referenceAllJointsButton: Button

    private lateinit var modeJointButton: Button
    private lateinit var modeCartBaseButton: Button
    private lateinit var modeCartToolButton: Button
    private lateinit var modePlatformButton: Button

    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button

    private lateinit var programButtons: Array<Button>
    private lateinit var programNames: Array<String>

    private val preferencesName = "CRI_PROGRAM_NAMES"

    // ============================================================
    // ROBOT STATES
    // ============================================================

    private var robotEnabled = false
    private var out31State = false
    private var plcEnabled = false

    private enum class MotionMode {
        JOINT,
        CART_BASE,
        CART_TOOL,
        PLATFORM
    }

    private var motionMode = MotionMode.JOINT

    // ============================================================
    // TTS
    // ============================================================

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    // ============================================================
    // ACTIVITY
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        hideSystemUI()
        supportActionBar?.hide()

        // --------------------------------------------------------
        // UI REFERENCES
        // --------------------------------------------------------

        ipEdit = findViewById(R.id.ipEdit)
        portEdit = findViewById(R.id.portEdit)

        statusText = findViewById(R.id.statusText)
        connectionInfo = findViewById(R.id.connectionInfo)
        accessLevelText = findViewById(R.id.accessLevelText)
        programTitle = findViewById(R.id.programTitle)
        logText = findViewById(R.id.logText)

        /*
         * LOG TITEL
         *
         * Deze TextView wordt gebruikt als BUTTON om het aantal
         * PROGRAM-knoppen in OPERATOR in te stellen.
         *
         * Het logText zelf wordt NIET gewijzigd.
         */
        logTitle = findViewById(R.id.logTitle)

        logTitle.setOnClickListener {

            /*
             * Alleen EXPERT mag het aantal PROGRAM-knoppen
             * aanpassen.
             */
            if (accessLevel == AccessLevel.EXPERT) {

                showOperatorProgramCountDialog()

            } else {

                speak(
                    "Expert toegang vereist"
                )

                appendLog(
                    "LOG settings blocked - Expert access required"
                )
            }
        }

        expertConnectionLayout =
            findViewById(R.id.expertConnectionLayout)

        expertControlLayout =
            findViewById(R.id.expertControlLayout)

        expertJogLayout =
            findViewById(R.id.expertJogLayout)

        expertLogLayout =
            findViewById(R.id.expertLogLayout)

        enableButton =
            findViewById(R.id.enableButton)

        resetButton =
            findViewById(R.id.resetButton)

        out31Button =
            findViewById(R.id.out31Button)

        plcButton =
            findViewById(R.id.plcButton)

        referenceAllJointsButton =
            findViewById(R.id.referenceAllJointsButton)

        modeJointButton =
            findViewById(R.id.modeJointButton)

        modeCartBaseButton =
            findViewById(R.id.modeCartBaseButton)

        modeCartToolButton =
            findViewById(R.id.modeCartToolButton)

        modePlatformButton =
            findViewById(R.id.modePlatformButton)

        startButton =
            findViewById(R.id.startButton)

        pauseButton =
            findViewById(R.id.pauseButton)

        stopButton =
            findViewById(R.id.stopButton)

        logText.movementMethod =
            ScrollingMovementMethod()

        // --------------------------------------------------------
        // PROGRAM BUTTONS
        // --------------------------------------------------------

        programButtons = arrayOf(
            findViewById(R.id.programButton1),
            findViewById(R.id.programButton2),
            findViewById(R.id.programButton3),
            findViewById(R.id.programButton4),
            findViewById(R.id.programButton5),
            findViewById(R.id.programButton6),
            findViewById(R.id.programButton7),
            findViewById(R.id.programButton8),
            findViewById(R.id.programButton9)
        )

        // --------------------------------------------------------
        // PROGRAM NAMES
        // --------------------------------------------------------

        loadProgramNames()

        /*
         * BELANGRIJK:
         *
         * De opgeslagen waarde wordt hier geladen.
         *
         * Daardoor blijft bijvoorbeeld 6 behouden na een
         * volledige app-herstart.
         */
        loadOperatorProgramCount()

        updateProgramButtons()

        // --------------------------------------------------------
        // TTS
        // --------------------------------------------------------

        initializeTextToSpeech()

        // --------------------------------------------------------
        // ACCESS
        // --------------------------------------------------------

        findViewById<Button>(R.id.accessButton).setOnClickListener {
            changeAccessLevel()
        }

        // --------------------------------------------------------
        // CONNECTION
        // --------------------------------------------------------

        findViewById<Button>(R.id.connectButton).setOnClickListener {

            autoReconnectEnabled = true

            logConnectionEvent(
                "Manual CONNECT requested"
            )

            connect()
        }

        findViewById<Button>(R.id.disconnectButton).setOnClickListener {

            autoReconnectEnabled = false

            logConnectionEvent(
                "Manual DISCONNECT requested"
            )

            disconnect()
        }

        // --------------------------------------------------------
        // PROGRAM CONTROL
        // --------------------------------------------------------

        startButton.setOnClickListener {
            startProgram()
        }

        pauseButton.setOnClickListener {
            pauseProgram()
        }

        stopButton.setOnClickListener {
            stopProgram()
        }

        // --------------------------------------------------------
        // EXPERT CONTROL
        // --------------------------------------------------------

        enableButton.setOnClickListener {
            toggleRobotEnable()
        }

        resetButton.setOnClickListener {
            resetRobot()
        }

        out31Button.setOnClickListener {
            toggleOut31()
        }

        plcButton.setOnClickListener {
            togglePlc()
        }

        referenceAllJointsButton.setOnClickListener {
            referenceAllJoints()
        }

        // --------------------------------------------------------
        // MOTION MODE
        // --------------------------------------------------------

        modeJointButton.setOnClickListener {
            setMotionMode(MotionMode.JOINT)
        }

        modeCartBaseButton.setOnClickListener {
            setMotionMode(MotionMode.CART_BASE)
        }

        modeCartToolButton.setOnClickListener {
            setMotionMode(MotionMode.CART_TOOL)
        }

        modePlatformButton.setOnClickListener {
            setMotionMode(MotionMode.PLATFORM)
        }

        // --------------------------------------------------------
        // JOG
        // --------------------------------------------------------

        setupJogButtons()

        // --------------------------------------------------------
        // INITIAL UI STATE
        // --------------------------------------------------------

        accessLevel = AccessLevel.OPERATOR

        updateMotionModeUI()
        updateAccessUI()
        updateControlButtons()
        updateProgramButtons()

        updateProgramConnectionColors()

        // --------------------------------------------------------
        // AUTOMATIC CONNECTION
        // --------------------------------------------------------

        startReconnectMonitor()

        reconnectRobot()
    }

    // ============================================================
    // OPERATOR PROGRAM COUNT
    // ============================================================

    /*
     * ============================================================
     * PROGRAM COUNT DIALOG
     * ============================================================
     *
     * Via LOG kan EXPERT hier een getal van 1 t/m 9 ingeven.
     *
     * Er wordt GEEN lijst gebruikt.
     *
     * Voorbeeld:
     *
     * 6
     *
     * betekent:
     *
     * PROGRAM 1 t/m PROGRAM 6 zichtbaar in OPERATOR.
     */

    private fun showOperatorProgramCountDialog() {

        val input =
            EditText(this)

        input.inputType =
            InputType.TYPE_CLASS_NUMBER

        input.setSingleLine(true)

        input.hint =
            "1 - 9"

        /*
         * Toon de huidige instelling.
         *
         * Als er bijvoorbeeld 6 opgeslagen is,
         * staat hier 6.
         */
        input.setText(
            operatorProgramCount.toString()
        )

        input.selectAll()

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    "PROGRAMMA'S IN OPERATOR"
                )
                .setMessage(
                    "Geef het aantal PROGRAM-knoppen in:\n\n" +
                            "Toegestaan: 1 t/m 9"
                )
                .setView(input)
                .setNegativeButton(
                    "CANCEL",
                    null
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val value =
                    input.text
                        .toString()
                        .trim()
                        .toIntOrNull()

                /*
                 * Alleen 1 t/m 9 toestaan.
                 */
                if (
                    value == null ||
                    value !in 1..9
                ) {

                    input.error =
                        "Geef een getal van 1 t/m 9 in"

                    return@setOnClickListener
                }

                /*
                 * Nieuwe waarde opslaan.
                 */
                operatorProgramCount =
                    value

                saveOperatorProgramCount()

                /*
                 * Meteen de PROGRAM-knoppen aanpassen.
                 */
                updateProgramVisibility()

                appendLog(
                    "Operator PROGRAM buttons set to: $operatorProgramCount"
                )

                Log.d(
                    LOG_TAG_PROGRAM,
                    "OPERATOR_PROGRAM_COUNT=$operatorProgramCount"
                )

                dialog.dismiss()
            }
        }

        dialog.show()

        /*
         * Automatisch toetsenbord openen.
         */
        input.requestFocus()

        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
    }

    // ============================================================
    // LOAD OPERATOR PROGRAM COUNT
    // ============================================================

    private fun loadOperatorProgramCount() {

        val preferences =
            getSharedPreferences(
                operatorProgramPreferences,
                Context.MODE_PRIVATE
            )

        /*
         * Eerste keer is 2.
         *
         * Daarna wordt de laatst opgeslagen waarde gebruikt.
         */
        operatorProgramCount =
            preferences.getInt(
                "operator_program_count",
                2
            ).coerceIn(1, 9)

        Log.d(
            LOG_TAG_PROGRAM,
            "Loaded OPERATOR_PROGRAM_COUNT=$operatorProgramCount"
        )
    }

    // ============================================================
    // SAVE OPERATOR PROGRAM COUNT
    // ============================================================

    private fun saveOperatorProgramCount() {

        val preferences =
            getSharedPreferences(
                operatorProgramPreferences,
                Context.MODE_PRIVATE
            )

        preferences.edit()
            .putInt(
                "operator_program_count",
                operatorProgramCount
            )
            .apply()

        Log.d(
            LOG_TAG_PROGRAM,
            "Saved OPERATOR_PROGRAM_COUNT=$operatorProgramCount"
        )
    }

    // ============================================================
    // APP RESUME
    // ============================================================

    override fun onResume() {
        super.onResume()

        hideSystemUI()

        if (autoReconnectEnabled && !isConnected()) {

            appendLog(
                "App resumed - checking robot connection"
            )

            logConnectionEvent(
                "APP RESUMED - connection check"
            )

            reconnectRobot()
        }

        updateProgramConnectionColors()
    }

    // ============================================================
    // AUTOMATIC RECONNECT
    // ============================================================

    private fun startReconnectMonitor() {

        if (reconnectMonitorStarted) {
            return
        }

        reconnectMonitorStarted = true

        reconnectExecutor.scheduleWithFixedDelay(
            {

                try {

                    if (!autoReconnectEnabled) {
                        return@scheduleWithFixedDelay
                    }

                    if (!isConnected()) {

                        runOnUiThread {

                            updateDisconnectedUI()

                            updateProgramConnectionColors()
                        }

                        reconnectRobot()
                    }

                } catch (e: Exception) {

                    runOnUiThread {

                        appendLog(
                            "Reconnect monitor error: ${e.message}"
                        )

                        Log.e(
                            LOG_TAG_ERROR,
                            "Reconnect monitor error",
                            e
                        )
                    }
                }

            },
            0,
            2,
            TimeUnit.SECONDS
        )
    }

    private fun reconnectRobot() {

        if (!autoReconnectEnabled) {
            return
        }

        if (isConnected()) {

            updateProgramConnectionColors()

            return
        }

        if (!reconnectInProgress.compareAndSet(false, true)) {
            return
        }

        val ip =
            ipEdit.text.toString().trim()

        val port =
            portEdit.text.toString().toIntOrNull()
                ?: 3920

        if (ip.isEmpty()) {

            reconnectInProgress.set(false)

            runOnUiThread {

                statusText.text =
                    "● NO IP"

                connectionInfo.text =
                    "IP address ontbreekt"

                updateProgramConnectionColors()
            }

            return
        }

        executor.execute {

            try {

                runOnUiThread {

                    statusText.text =
                        "● CONNECTING..."

                    connectionInfo.text =
                        "IP: $ip\n" +
                                "Port: $port\n" +
                                "Status: Connecting..."

                    updateProgramConnectionColors()
                }

                logConnectionEvent(
                    "CONNECTING to $ip:$port"
                )

                disconnectInternal()

                val newSocket =
                    Socket(ip, port)

                newSocket.tcpNoDelay = true

                socket = newSocket

                output =
                    newSocket.getOutputStream()

                synchronized(outputLock) {
                    sendCounter = 1
                }

                runOnUiThread {

                    statusText.text =
                        "● CONNECTED"

                    connectionInfo.text =
                        "IP: $ip\n" +
                                "Port: $port\n" +
                                "Status: Connected"

                    appendLog(
                        "Connected to $ip:$port"
                    )

                    updateProgramConnectionColors()
                }

                logConnectionEvent(
                    "CONNECTED to $ip:$port"
                )

                startReader(newSocket)

                startAliveJogLoopInternal()

                initializeRobotAfterReconnect()

            } catch (e: Exception) {

                disconnectInternal()

                runOnUiThread {

                    statusText.text =
                        "● DISCONNECTED"

                    connectionInfo.text =
                        "IP: $ip\n" +
                                "Port: $port\n" +
                                "Status: Waiting for connection..."

                    appendLog(
                        "Reconnect failed: ${e.message}"
                    )

                    updateProgramConnectionColors()
                }

                Log.e(
                    LOG_TAG_CONNECTION,
                    "Reconnect failed: ${e.message}"
                )

            } finally {

                reconnectInProgress.set(false)
            }
        }
    }

    private fun initializeRobotAfterReconnect() {

        Thread.sleep(300)

        if (!isConnected()) {
            return
        }

        appendLog(
            "Automatic robot initialization..."
        )

        logConnectionEvent(
            "Automatic robot initialization started"
        )

        sendCommandSync(
            "CMD Reset"
        )

        Thread.sleep(500)

        if (!isConnected()) {
            return
        }

        sendCommandSync(
            "CMD ReferenceAllJoints"
        )

        Thread.sleep(1000)

        if (!isConnected()) {
            return
        }

        sendCommandSync(
            "CMD Enable"
        )

        robotEnabled = true

        runOnUiThread {

            updateControlButtons()
            updateProgramConnectionColors()

            appendLog(
                "Robot initialized: RESET -> REFERENCE -> ENABLE"
            )
        }

        Log.d(
            LOG_TAG_STATUS,
            "Robot initialized: RESET -> REFERENCE -> ENABLE"
        )
    }

    // ============================================================
    // JOG
    // ============================================================

    private fun setupJogButtons() {

        findViewById<Button>(R.id.jogXPlusButton)
            .setOnClickListener {
                changeJog(0, +10.0)
            }

        findViewById<Button>(R.id.jogXMinusButton)
            .setOnClickListener {
                changeJog(0, -10.0)
            }

        findViewById<Button>(R.id.jogYPlusButton)
            .setOnClickListener {
                changeJog(1, +10.0)
            }

        findViewById<Button>(R.id.jogYMinusButton)
            .setOnClickListener {
                changeJog(1, -10.0)
            }

        findViewById<Button>(R.id.jogZPlusButton)
            .setOnClickListener {
                changeJog(2, +10.0)
            }

        findViewById<Button>(R.id.jogZMinusButton)
            .setOnClickListener {
                changeJog(2, -10.0)
            }

        findViewById<Button>(R.id.jogAPlusButton)
            .setOnClickListener {
                changeJog(3, +10.0)
            }

        findViewById<Button>(R.id.jogAMinusButton)
            .setOnClickListener {
                changeJog(3, -10.0)
            }

        findViewById<Button>(R.id.jogBPlusButton)
            .setOnClickListener {
                changeJog(4, +10.0)
            }

        findViewById<Button>(R.id.jogBMinusButton)
            .setOnClickListener {
                changeJog(4, -10.0)
            }

        findViewById<Button>(R.id.jogCPlusButton)
            .setOnClickListener {
                changeJog(5, +10.0)
            }

        findViewById<Button>(R.id.jogCMinusButton)
            .setOnClickListener {
                changeJog(5, -10.0)
            }

        findViewById<Button>(R.id.jogOverrideMinusButton)
            .setOnClickListener {

                if (!isConnected()) {

                    speak("Robot niet verbonden")

                    return@setOnClickListener
                }

                overrideValue =
                    (overrideValue - 10.0)
                        .coerceAtLeast(0.0)

                sendCommand(
                    "CMD Override %.1f"
                        .format(
                            Locale.US,
                            overrideValue
                        )
                )

                updateOverrideText()
            }

        findViewById<Button>(R.id.jogOverridePlusButton)
            .setOnClickListener {

                if (!isConnected()) {

                    speak("Robot niet verbonden")

                    return@setOnClickListener
                }

                overrideValue =
                    (overrideValue + 10.0)
                        .coerceAtMost(100.0)

                sendCommand(
                    "CMD Override %.1f"
                        .format(
                            Locale.US,
                            overrideValue
                        )
                )

                updateOverrideText()
            }

        findViewById<Button>(R.id.jogStopButton)
            .setOnClickListener {
                stopJog()
            }
    }

    private fun changeJog(
        index: Int,
        delta: Double
    ) {

        if (!isConnected()) {

            speak("Robot niet verbonden")

            appendLog(
                "Jog ignored: robot is not connected"
            )

            return
        }

        jogValues[index] =
            (jogValues[index] + delta)
                .coerceIn(-100.0, 100.0)

        appendLog(
            "JOG[$index] = %.1f"
                .format(
                    Locale.US,
                    jogValues[index]
                )
        )
    }

    private fun stopJog() {

        synchronized(jogValues) {

            for (i in jogValues.indices) {
                jogValues[i] = 0.0
            }
        }

        appendLog("JOG STOP")
    }

    // ============================================================
    // MOTION MODE
    // ============================================================

    private fun setMotionMode(
        mode: MotionMode
    ) {

        if (!isConnected()) {

            speak("Robot niet verbonden")

            appendLog(
                "Motion mode ignored: robot is not connected"
            )

            return
        }

        motionMode = mode

        when (mode) {

            MotionMode.JOINT ->
                sendCommand("CMD MotionTypeJoint")

            MotionMode.CART_BASE ->
                sendCommand("CMD MotionTypeCartBase")

            MotionMode.CART_TOOL ->
                sendCommand("CMD MotionTypeCartTool")

            MotionMode.PLATFORM ->
                sendCommand("CMD MotionTypePlatform")
        }

        updateMotionModeUI()
    }

    private fun updateMotionModeUI() {

        modeJointButton.isSelected =
            motionMode == MotionMode.JOINT

        modeCartBaseButton.isSelected =
            motionMode == MotionMode.CART_BASE

        modeCartToolButton.isSelected =
            motionMode == MotionMode.CART_TOOL

        modePlatformButton.isSelected =
            motionMode == MotionMode.PLATFORM

        when (motionMode) {

            MotionMode.JOINT -> {

                setJogButtonText(
                    "A1+", "A1-",
                    "A2+", "A2-",
                    "A3+", "A3-",
                    "A4+", "A4-",
                    "A5+", "A5-",
                    "A6+", "A6-"
                )
            }

            MotionMode.CART_BASE,
            MotionMode.CART_TOOL -> {

                setJogButtonText(
                    "X+", "X-",
                    "Y+", "Y-",
                    "Z+", "Z-",
                    "A+", "A-",
                    "B+", "B-",
                    "C+", "C-"
                )
            }

            MotionMode.PLATFORM -> {

                setJogButtonText(
                    "Forward", "Backward",
                    "Right-Lat", "Left-Lat",
                    "Right-Rot", "Left-Rot",
                    "NC", "NC",
                    "NC", "NC",
                    "NC", "NC"
                )
            }
        }
    }

    private fun setJogButtonText(
        xPlus: String,
        xMinus: String,
        yPlus: String,
        yMinus: String,
        zPlus: String,
        zMinus: String,
        aPlus: String,
        aMinus: String,
        bPlus: String,
        bMinus: String,
        cPlus: String,
        cMinus: String
    ) {

        findViewById<Button>(R.id.jogXPlusButton).text = xPlus
        findViewById<Button>(R.id.jogXMinusButton).text = xMinus
        findViewById<Button>(R.id.jogYPlusButton).text = yPlus
        findViewById<Button>(R.id.jogYMinusButton).text = yMinus
        findViewById<Button>(R.id.jogZPlusButton).text = zPlus
        findViewById<Button>(R.id.jogZMinusButton).text = zMinus
        findViewById<Button>(R.id.jogAPlusButton).text = aPlus
        findViewById<Button>(R.id.jogAMinusButton).text = aMinus
        findViewById<Button>(R.id.jogBPlusButton).text = bPlus
        findViewById<Button>(R.id.jogBMinusButton).text = bMinus
        findViewById<Button>(R.id.jogCPlusButton).text = cPlus
        findViewById<Button>(R.id.jogCMinusButton).text = cMinus
    }

    // ============================================================
    // ALIVE JOG
    // ============================================================

    private var aliveLoopStarted = false

    private fun startAliveJogLoopInternal() {

        if (aliveLoopStarted) {
            return
        }

        aliveLoopStarted = true

        executor.execute {

            while (!Thread.currentThread().isInterrupted) {

                try {

                    if (isConnected()) {
                        sendAliveJog()
                    }

                    Thread.sleep(100)

                } catch (_: InterruptedException) {

                    break

                } catch (_: Exception) {
                }
            }
        }
    }

    private fun sendAliveJog() {

        val values =
            synchronized(jogValues) {
                jogValues.copyOf()
            }

        val msg =
            buildString {

                append("ALIVEJOG")

                for (v in values) {

                    append(' ')

                    append(
                        "%.1f".format(
                            Locale.US,
                            v
                        )
                    )
                }
            }

        sendFrame(
            msg,
            logMessage = false
        )
    }

    // ============================================================
    // SEND COMMAND
    // ============================================================

    private fun sendCommand(
        command: String
    ) {

        sendFrame(
            command,
            logMessage = true
        )
    }

    private fun sendCommandSync(
        command: String
    ) {

        try {

            val currentOutput =
                output ?: return

            synchronized(outputLock) {

                val counter =
                    sendCounter

                sendCounter++

                if (sendCounter >= 10000) {
                    sendCounter = 1
                }

                val data =
                    "CRISTART $counter $command CRIEND"

                currentOutput.write(
                    data.toByteArray(
                        Charsets.US_ASCII
                    )
                )

                currentOutput.flush()
            }

            runOnUiThread {
                appendLog("TX: $command")
            }

            Log.d(
                LOG_TAG_STATUS,
                "TX: $command"
            )

        } catch (e: Exception) {

            runOnUiThread {

                appendLog(
                    "Send error: ${e.message}"
                )

                handleConnectionLost()
            }

            Log.e(
                LOG_TAG_ERROR,
                "Send error: ${e.message}"
            )
        }
    }

    private fun sendFrame(
        command: String,
        logMessage: Boolean
    ) {

        executor.execute {

            try {

                val currentOutput =
                    output ?: return@execute

                synchronized(outputLock) {

                    val counter =
                        sendCounter

                    sendCounter++

                    if (sendCounter >= 10000) {
                        sendCounter = 1
                    }

                    val data =
                        "CRISTART $counter $command CRIEND"

                    currentOutput.write(
                        data.toByteArray(
                            Charsets.US_ASCII
                        )
                    )

                    currentOutput.flush()

                    if (logMessage) {

                        runOnUiThread {
                            appendLog("TX: $data")
                        }

                        Log.d(
                            LOG_TAG_STATUS,
                            "TX: $command"
                        )
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    appendLog(
                        "Send error: ${e.message}"
                    )

                    handleConnectionLost()
                }

                Log.e(
                    LOG_TAG_ERROR,
                    "Send error",
                    e
                )
            }
        }
    }

    // ============================================================
    // PROGRAMS
    // ============================================================

    private fun updateProgramButtons() {

        for (i in programButtons.indices) {

            val button =
                programButtons[i]

            button.text =
                programNames[i]

            button.setOnClickListener {

                val name =
                    programNames[i]

                programTitle.text =
                    name

                speak(name)

                loadProgramAndStart(i)
            }

            button.setOnLongClickListener {

                if (
                    accessLevel ==
                    AccessLevel.EXPERT
                ) {

                    editProgramName(i)

                    true

                } else {

                    false
                }
            }
        }

        updateProgramVisibility()

        updateProgramConnectionColors()
    }

    // ============================================================
    // PROGRAM VISIBILITY
    // ============================================================

    private fun updateProgramVisibility() {

        /*
         * OPERATOR:
         *
         * Alleen de eerste operatorProgramCount programma's
         * worden zichtbaar.
         *
         * Bijvoorbeeld:
         *
         * operatorProgramCount = 4
         *
         * PROGRAM 1 = zichtbaar
         * PROGRAM 2 = zichtbaar
         * PROGRAM 3 = zichtbaar
         * PROGRAM 4 = zichtbaar
         * PROGRAM 5 = onzichtbaar
         * ...
         * PROGRAM 9 = onzichtbaar
         *
         * EXPERT:
         *
         * Alle 9 zichtbaar.
         */

        for (i in programButtons.indices) {

            if (accessLevel == AccessLevel.OPERATOR) {

                programButtons[i].visibility =
                    if (i < operatorProgramCount) {
                        View.VISIBLE
                    } else {
                        View.INVISIBLE
                    }

            } else {

                programButtons[i].visibility =
                    View.VISIBLE
            }
        }
    }

    private fun updateProgramRunningUI() {

        runOnUiThread {

            for (button in programButtons) {

                button.isEnabled =
                    !programRunning &&
                            !programStartInProgress
            }

            if (programRunning) {

                val activeName =
                    if (
                        activeProgramIndex in
                        programNames.indices
                    ) {
                        programNames[
                            activeProgramIndex
                        ]
                    } else {
                        "ONBEKEND PROGRAMMA"
                    }

                programTitle.text =
                    "$activeName - ACTIEF"

            } else if (!programStartInProgress) {

                if (
                    activeProgramIndex !in
                    programNames.indices
                ) {
                    programTitle.text =
                        "GEEN PROGRAMMA ACTIEF"
                }
            }
        }
    }

    private fun updateProgramConnectionColors() {

        runOnUiThread {

            val connected =
                isConnected()

            for (i in programButtons.indices) {

                val button =
                    programButtons[i]

                if (connected) {

                    button.setBackgroundColor(
                        Color.GREEN
                    )

                    button.setTextColor(
                        Color.BLACK
                    )

                } else {

                    button.setBackgroundColor(
                        Color.WHITE
                    )

                    button.setTextColor(
                        Color.BLACK
                    )
                }
            }

            if (
                lastLoggedProgramColorConnected !=
                connected
            ) {

                lastLoggedProgramColorConnected =
                    connected

                val color =
                    if (connected)
                        "GREEN"
                    else
                        "WHITE"

                Log.d(
                    LOG_TAG_STATUS,
                    "PROGRAM1_COLOR=$color"
                )

                Log.d(
                    LOG_TAG_STATUS,
                    "PROGRAM2_COLOR=$color"
                )
            }

            updateProgramRunningUI()
        }
    }

    private fun loadProgram(
        index: Int
    ) {

        if (
            index !in
            programFiles.indices
        ) {
            return
        }

        if (
            programRunning ||
            programStartInProgress
        ) {

            val activeName =
                if (
                    activeProgramIndex in
                    programNames.indices
                ) {
                    programNames[
                        activeProgramIndex
                    ]
                } else {
                    "ander programma"
                }

            appendLog(
                "PROGRAM BLOCKED: ${programNames[index]} requested, " +
                        "$activeName is still active"
            )

            Log.d(
                LOG_TAG_PROGRAM,
                "PROGRAM_BLOCKED index=$index active=$activeProgramIndex"
            )

            speak(
                "$activeName is nog actief"
            )

            return
        }

        val file =
            programFiles[index]

        val name =
            programNames[index]

        if (!isConnected()) {

            appendLog(
                "Robot is not connected"
            )

            speak(
                "Robot niet verbonden"
            )

            return
        }

        appendLog(
            "Loading: $name -> $file"
        )

        sendCommand(
            "CMD LoadProgram $file"
        )
    }

    private fun loadProgramAndStart(
        index: Int
    ) {

        if (
            index !in
            programFiles.indices
        ) {
            return
        }

        synchronized(programStartLock) {

            if (
                programRunning ||
                programStartInProgress
            ) {

                val activeName =
                    if (
                        activeProgramIndex in
                        programNames.indices
                    ) {
                        programNames[
                            activeProgramIndex
                        ]
                    } else {
                        "een ander programma"
                    }

                appendLog(
                    "PROGRAM BLOCKED: " +
                            "${programNames[index]} " +
                            "requested while " +
                            "$activeName is active"
                )

                Log.d(
                    LOG_TAG_PROGRAM,
                    "BLOCKED REQUEST: ${programNames[index]}"
                )

                speak(
                    "$activeName is nog actief"
                )

                return
            }

            if (!isConnected()) {

                speak(
                    "Robot niet verbonden"
                )

                appendLog(
                    "Program start ignored: robot is not connected"
                )

                return
            }

            programStartInProgress = true
            activeProgramIndex = index

            updateProgramRunningUI()

            val file =
                programFiles[index]

            val name =
                programNames[index]

            programTitle.text =
                name

            appendLog(
                "PROGRAM REQUEST: $name -> $file"
            )

            Log.d(
                LOG_TAG_PROGRAM,
                "START REQUEST index=$index file=$file"
            )

            executor.execute {

                try {

                    val currentOutput =
                        output ?: run {

                            runOnUiThread {

                                programStartInProgress =
                                    false

                                activeProgramIndex =
                                    -1

                                updateProgramRunningUI()
                            }

                            return@execute
                        }

                    synchronized(outputLock) {

                        var counter =
                            sendCounter

                        sendCounter++

                        if (sendCounter >= 10000) {
                            sendCounter = 1
                        }

                        val loadData =
                            "CRISTART $counter CMD LoadProgram $file CRIEND"

                        currentOutput.write(
                            loadData.toByteArray(
                                Charsets.US_ASCII
                            )
                        )

                        currentOutput.flush()

                        runOnUiThread {

                            appendLog(
                                "TX: $loadData"
                            )
                        }

                        Log.d(
                            LOG_TAG_PROGRAM,
                            "TX LoadProgram $file"
                        )

                        Thread.sleep(250)

                        if (!isConnected()) {

                            runOnUiThread {

                                programStartInProgress =
                                    false

                                activeProgramIndex =
                                    -1

                                updateProgramRunningUI()
                            }

                            return@synchronized
                        }

                        counter =
                            sendCounter

                        sendCounter++

                        if (sendCounter >= 10000) {
                            sendCounter = 1
                        }

                        val startData =
                            "CRISTART $counter CMD StartProgram CRIEND"

                        currentOutput.write(
                            startData.toByteArray(
                                Charsets.US_ASCII
                            )
                        )

                        currentOutput.flush()

                        programRunning = true
                        programStartInProgress = false
                        stopRequested = false

                        runOnUiThread {

                            appendLog(
                                "TX: $startData"
                            )

                            appendLog(
                                "PROGRAM ACTIVE: $name"
                            )

                            speak("Start")

                            updateProgramRunningUI()
                        }

                        Log.d(
                            LOG_TAG_PROGRAM,
                            "TX: CMD StartProgram"
                        )

                        Log.d(
                            LOG_TAG_PROGRAM,
                            "PROGRAM ACTIVE: $name"
                        )
                    }

                } catch (e: Exception) {

                    runOnUiThread {

                        programRunning =
                            false

                        programStartInProgress =
                            false

                        activeProgramIndex =
                            -1

                        appendLog(
                            "Program start error: ${e.message}"
                        )

                        updateProgramRunningUI()

                        handleConnectionLost()
                    }

                    Log.e(
                        LOG_TAG_ERROR,
                        "Program start error",
                        e
                    )
                }
            }
        }
    }

    private fun startProgram1() {

        loadProgramAndStart(0)
    }

    // ============================================================
    // PROGRAM NAME EDIT
    // ============================================================

    private fun editProgramName(
        index: Int
    ) {

        val input =
            EditText(this)

        input.setText(
            programNames[index]
        )

        input.selectAll()

        input.inputType =
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    "Rename program"
                )
                .setMessage(
                    "File: ${programFiles[index]}"
                )
                .setView(input)
                .setNegativeButton(
                    "CANCEL",
                    null
                )
                .setPositiveButton(
                    "SAVE",
                    null
                )
                .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val newName =
                    input.text
                        .toString()
                        .trim()

                if (newName.isEmpty()) {

                    input.error =
                        "Name cannot be empty"

                    return@setOnClickListener
                }

                programNames[index] =
                    newName

                saveProgramNames()

                updateProgramButtons()

                appendLog(
                    "Program ${index + 1} renamed to: $newName"
                )

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun loadProgramNames() {

        val preferences =
            getSharedPreferences(
                preferencesName,
                Context.MODE_PRIVATE
            )

        programNames =
            Array(programFiles.size) { index ->

                preferences.getString(
                    "program_$index",
                    defaultProgramNames[index]
                ) ?: defaultProgramNames[index]
            }
    }

    private fun saveProgramNames() {

        val preferences =
            getSharedPreferences(
                preferencesName,
                Context.MODE_PRIVATE
            )

        val editor =
            preferences.edit()

        for (i in programNames.indices) {

            editor.putString(
                "program_$i",
                programNames[i]
            )
        }

        editor.apply()
    }

    // ============================================================
    // ACCESS
    // ============================================================

    private fun changeAccessLevel() {

        if (
            accessLevel ==
            AccessLevel.OPERATOR
        ) {

            showExpertPinDialog()

        } else {

            stopJog()

            accessLevel =
                AccessLevel.OPERATOR

            updateAccessUI()

            appendLog(
                "Access level changed to OPERATOR"
            )
        }
    }

    private fun showExpertPinDialog() {

        val input =
            EditText(this)

        input.inputType =
            InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_VARIATION_PASSWORD

        input.hint =
            "Expert PIN"

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    "Robot Expert Access:"
                )
                .setMessage(
                    "Enter expert PIN:\n\n" +
                            "Created by: Gino Van De Velde\n" +
                            "Info: +32 476 50 42 17\n\n" +
                            "YouTube:\n" +
                            "https://www.youtube.com/@Univers314\n\n"
                )
                .setView(input)
                .setNegativeButton(
                    "CANCEL",
                    null
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                if (
                    input.text.toString()
                    == expertPin
                ) {

                    accessLevel =
                        AccessLevel.EXPERT

                    updateAccessUI()

                    appendLog(
                        "Expert access enabled"
                    )

                    dialog.dismiss()

                } else {

                    input.text.clear()

                    input.error =
                        "Invalid PIN"

                    appendLog(
                        "Invalid expert PIN"
                    )
                }
            }
        }

        dialog.show()
    }

    private fun updateAccessUI() {

        if (
            accessLevel ==
            AccessLevel.OPERATOR
        ) {

            accessLevelText.text =
                "OPERATOR"

            findViewById<Button>(
                R.id.accessButton
            ).text =
                "EXPERT"

            expertConnectionLayout.visibility =
                View.GONE

            expertControlLayout.visibility =
                View.GONE

            expertJogLayout.visibility =
                View.GONE

            expertLogLayout.visibility =
                View.GONE

            startButton.visibility =
                View.INVISIBLE

            pauseButton.visibility =
                View.INVISIBLE

            stopButton.visibility =
                View.INVISIBLE

        } else {

            accessLevelText.text =
                "EXPERT"

            findViewById<Button>(
                R.id.accessButton
            ).text =
                "OPERATOR"

            expertConnectionLayout.visibility =
                View.VISIBLE

            expertControlLayout.visibility =
                View.VISIBLE

            expertJogLayout.visibility =
                View.VISIBLE

            expertLogLayout.visibility =
                View.VISIBLE

            startButton.visibility =
                View.VISIBLE

            pauseButton.visibility =
                View.VISIBLE

            stopButton.visibility =
                View.VISIBLE
        }

        /*
         * OPERATOR:
         * aantal volgens operatorProgramCount.
         *
         * EXPERT:
         * alle 9.
         */
        updateProgramVisibility()

        updateControlButtons()
    }

    // ============================================================
    // CONTROL UI
    // ============================================================

    private fun updateControlButtons() {

        enableButton.text =
            if (robotEnabled)
                "DISABLE"
            else
                "ENABLE"

        out31Button.text =
            if (out31State)
                "OUT31 ON"
            else
                "OUT31 OFF"

        plcButton.text =
            if (plcEnabled)
                "PLC DISABLE"
            else
                "PLC ENABLE"

        updateOverrideText()
    }

    private fun updateOverrideText() {

        findViewById<TextView>(
            R.id.overrideValueText
        ).text =
            "Override: %.1f %%"
                .format(
                    Locale.US,
                    overrideValue
                )
    }

    // ============================================================
    // ENABLE
    // ============================================================

    private fun toggleRobotEnable() {

        if (!isConnected()) {

            speak(
                "Robot niet verbonden"
            )

            appendLog(
                "Robot is not connected"
            )

            return
        }

        if (robotEnabled) {

            sendCommand(
                "CMD Disable"
            )

            robotEnabled = false

            speak(
                "Robot uitgeschakeld"
            )

            appendLog(
                "ENABLE/DISABLE: DISABLE"
            )

            Log.d(
                LOG_TAG_STATUS,
                "ROBOT_ENABLE=false"
            )

        } else {

            sendCommand(
                "CMD Enable"
            )

            robotEnabled = true

            speak(
                "Robot ingeschakeld"
            )

            appendLog(
                "ENABLE/DISABLE: ENABLE"
            )

            Log.d(
                LOG_TAG_STATUS,
                "ROBOT_ENABLE=true"
            )
        }

        updateControlButtons()
    }

    // ============================================================
    // RESET
    // ============================================================

    private fun resetRobot() {

        if (!isConnected()) {

            speak(
                "Robot niet verbonden"
            )

            appendLog(
                "Robot is not connected"
            )

            return
        }

        sendCommand(
            "CMD Reset"
        )

        speak(
            "Reset"
        )

        appendLog(
            "RESET command sent"
        )
    }

    // ============================================================
    // OUT31
    // ============================================================

    private fun toggleOut31() {

        if (!isConnected()) {

            speak(
                "Robot niet verbonden"
            )

            appendLog(
                "Robot is not connected"
            )

            return
        }

        out31State =
            !out31State

        if (out31State) {
            setOut31High()
        } else {
            setOut31Low()
        }

        updateControlButtons()
    }

    private fun setOut31High() {

        appendLog(
            "OUT31 -> ON"
        )

        speak(
            "Output eenendertig aan"
        )
    }

    private fun setOut31Low() {

        appendLog(
            "OUT31 -> OFF"
        )

        speak(
            "Output eenendertig uit"
        )
    }

    // ============================================================
    // PLC
    // ============================================================

    private fun togglePlc() {

        if (!isConnected()) {

            speak(
                "Robot niet verbonden"
            )

            appendLog(
                "Robot is not connected"
            )

            return
        }

        plcEnabled =
            !plcEnabled

        if (plcEnabled) {
            setPlcEnabled()
        } else {
            setPlcDisabled()
        }

        updateControlButtons()
    }

    private fun setPlcEnabled() {

        appendLog(
            "PLC -> ENABLE"
        )

        speak(
            "PLC ingeschakeld"
        )
    }

    private fun setPlcDisabled() {

        appendLog(
            "PLC -> DISABLE"
        )

        speak(
            "PLC uitgeschakeld"
        )
    }

    // ============================================================
    // REFERENCE
    // ============================================================

    private fun referenceAllJoints() {

        if (!isConnected()) {

            speak(
                "Robot niet verbonden"
            )

            appendLog(
                "Reference all joints ignored: robot is not connected"
            )

            return
        }

        stopJog()

        appendLog(
            "REFERENCE ALL JOINTS"
        )

        speak(
            "Reference all joints"
        )

        sendCommand(
            "CMD ReferenceAllJoints"
        )
    }

    // ============================================================
    // MANUAL CONNECT
    // ============================================================

    private fun connect() {

        if (
            accessLevel !=
            AccessLevel.EXPERT
        ) {
            return
        }

        autoReconnectEnabled = true

        reconnectRobot()
    }

    // ============================================================
    // READER
    // ============================================================

    private fun startReader(
        s: Socket
    ) {

        executor.execute {

            try {

                val reader =
                    BufferedReader(
                        InputStreamReader(
                            s.getInputStream()
                        )
                    )

                val buffer =
                    StringBuilder()

                while (
                    !s.isClosed &&
                    s == socket
                ) {

                    val c =
                        reader.read()

                    if (c < 0) {
                        break
                    }

                    buffer.append(
                        c.toChar()
                    )

                    if (
                        buffer.contains(
                            "CRIEND"
                        )
                    ) {

                        val message =
                            buffer.toString()

                        buffer.clear()

                        parseStatusMessage(
                            message
                        )

                        logReceivedMessageToLogcat(
                            message
                        )
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    appendLog(
                        "Reader stopped: ${e.message}"
                    )
                }

                Log.e(
                    LOG_TAG_CONNECTION,
                    "Reader stopped",
                    e
                )

            } finally {

                if (s == socket) {

                    runOnUiThread {
                        handleConnectionLost()
                    }
                }
            }
        }
    }

    private fun logReceivedMessageToLogcat(
        message: String
    ) {

        val clean =
            message
                .replace("\r", " ")
                .replace("\n", " ")
                .trim()

        if (clean.isEmpty()) {
            return
        }

        if (
            clean.contains("RUNSTATE", true) ||
            clean.contains("EXECACK", true) ||
            clean.contains("EXECEND", true) ||
            clean.contains("EXECPAUSE", true) ||
            clean.contains("Program done", true) ||
            clean.contains("Starting Program", true) ||
            clean.contains("Stopping Program", true) ||
            clean.contains("Pausing Program", true) ||
            clean.contains("Program loaded", true) ||
            clean.contains("CMDACK", true)
        ) {

            Log.d(
                LOG_TAG_PROGRAM,
                "RX: $clean"
            )
        }

        if (
            clean.contains(
                "STATUS",
                ignoreCase = true
            )
        ) {

            Log.d(
                LOG_TAG_STATUS,
                "RX STATUS: $clean"
            )
        }
    }

    // ============================================================
    // STATUS / PROGRAM RESPONSE
    // ============================================================

    private fun parseStatusMessage(
        message: String
    ) {

        try {

            val clean =
                message
                    .replace("\r", " ")
                    .replace("\n", " ")
                    .trim()

            if (
                clean.contains(
                    "MESSAGE Starting Program",
                    ignoreCase = true
                )
            ) {

                programRunning = true
                programStartInProgress = false
                stopRequested = false

                runOnUiThread {

                    val activeName =
                        if (
                            activeProgramIndex in
                            programNames.indices
                        ) {
                            programNames[
                                activeProgramIndex
                            ]
                        } else {
                            "ONBEKEND PROGRAMMA"
                        }

                    appendLog(
                        "ROBOT CONFIRMED: $activeName STARTING"
                    )

                    Log.d(
                        LOG_TAG_PROGRAM,
                        "ROBOT PROGRAM START CONFIRMED: $activeName"
                    )

                    updateProgramRunningUI()
                }

                return
            }

            if (
                clean.contains(
                    "MESSAGE Program done",
                    ignoreCase = true
                )
            ) {

                val finishedIndex =
                    activeProgramIndex

                val finishedName =
                    if (
                        finishedIndex in
                        programNames.indices
                    ) {
                        programNames[
                            finishedIndex
                        ]
                    } else {
                        "ONBEKEND PROGRAMMA"
                    }

                programRunning = false
                programStartInProgress = false
                stopRequested = false
                activeProgramIndex = -1

                runOnUiThread {

                    appendLog(
                        "PROGRAM DONE: $finishedName"
                    )

                    appendLog(
                        "PROGRAMS UNLOCKED"
                    )

                    Log.d(
                        LOG_TAG_PROGRAM,
                        "PROGRAM_DONE: $finishedName"
                    )

                    updateProgramRunningUI()
                }

                return
            }

            if (
                clean.contains(
                    "EXECPAUSE",
                    ignoreCase = true
                )
            ) {

                Log.d(
                    LOG_TAG_PROGRAM,
                    "RX EXECPAUSE"
                )

                if (stopRequested) {

                    val stoppedIndex =
                        activeProgramIndex

                    val stoppedName =
                        if (
                            stoppedIndex in
                            programNames.indices
                        ) {
                            programNames[
                                stoppedIndex
                            ]
                        } else {
                            "ONBEKEND PROGRAMMA"
                        }

                    programRunning = false
                    programStartInProgress = false
                    stopRequested = false
                    activeProgramIndex = -1

                    runOnUiThread {

                        appendLog(
                            "PROGRAM STOPPED: $stoppedName"
                        )

                        appendLog(
                            "PROGRAMS UNLOCKED"
                        )

                        updateProgramRunningUI()
                    }
                }

                return
            }

            if (
                clean.contains(
                    "MESSAGE Stopping Program",
                    ignoreCase = true
                )
            ) {

                Log.d(
                    LOG_TAG_PROGRAM,
                    "ROBOT: Stopping Program"
                )

                return
            }

            if (
                clean.contains(
                    "MESSAGE Pausing Program",
                    ignoreCase = true
                )
            ) {

                Log.d(
                    LOG_TAG_PROGRAM,
                    "ROBOT: Pausing Program"
                )

                return
            }

            if (
                clean.contains(
                    "EXECEND",
                    ignoreCase = true
                )
            ) {

                Log.d(
                    LOG_TAG_PROGRAM,
                    "ROBOT EXECEND: $clean"
                )
            }

            val parts =
                clean.split(
                    Regex("\\s+")
                )

            if (
                parts.size >= 3 &&
                parts[0] == "CRISTART" &&
                parts[2] == "STATUS"
            ) {

                Log.d(
                    LOG_TAG_STATUS,
                    "STATUS PARSED: $clean"
                )

                val overrideIndex =
                    parts.indexOf(
                        "OVERRIDE"
                    )

                if (
                    overrideIndex >= 0 &&
                    overrideIndex + 1 <
                    parts.size
                ) {

                    overrideValue =
                        parts[
                            overrideIndex + 1
                        ].toDoubleOrNull()
                            ?: overrideValue

                    runOnUiThread {
                        updateOverrideText()
                    }

                    Log.d(
                        LOG_TAG_STATUS,
                        "OVERRIDE=$overrideValue"
                    )
                }
            }

        } catch (e: Exception) {

            Log.e(
                LOG_TAG_ERROR,
                "Status parsing error",
                e
            )
        }
    }

    // ============================================================
    // CONNECTION LOST
    // ============================================================

    private fun handleConnectionLost() {

        if (!isConnected()) {

            updateDisconnectedUI()

            updateProgramConnectionColors()

            return
        }

        appendLog(
            "Robot connection lost"
        )

        logConnectionEvent(
            "ROBOT CONNECTION LOST"
        )

        disconnectInternal()

        updateDisconnectedUI()

        updateProgramConnectionColors()
    }

    private fun updateDisconnectedUI() {

        statusText.text =
            "● DISCONNECTED"

        connectionInfo.text =
            "IP: ${ipEdit.text}\n" +
                    "Port: ${portEdit.text}\n" +
                    "Status: Waiting for reconnect..."

        robotEnabled = false

        updateControlButtons()

        updateProgramConnectionColors()

        logConnectionEvent(
            "UI STATUS = DISCONNECTED"
        )
    }

    // ============================================================
    // START / PAUSE / STOP
    // ============================================================

    private fun startProgram() {

        startProgram1()
    }

    private fun pauseProgram() {

        if (!isConnected()) {

            speak(
                "Robot niet verbonden"
            )

            appendLog(
                "Robot is not connected"
            )

            return
        }

        if (!programRunning) {

            appendLog(
                "PAUSE ignored: no program is running"
            )

            return
        }

        speak(
            "Pauze"
        )

        appendLog(
            "PAUSE requested for " +
                    programNames[
                        activeProgramIndex
                            .coerceIn(
                                0,
                                programNames.lastIndex
                            )
                    ]
        )

        sendCommand(
            "CMD PauseProgram"
        )
    }

    private fun stopProgram() {

        if (!isConnected()) {

            speak(
                "Robot niet verbonden"
            )

            appendLog(
                "Robot is not connected"
            )

            return
        }

        if (!programRunning) {

            appendLog(
                "STOP ignored: no program is running"
            )

            return
        }

        stopJog()

        speak(
            "Stop"
        )

        stopRequested = true

        appendLog(
            "STOP requested"
        )

        sendCommand(
            "CMD StopProgram"
        )
    }

    // ============================================================
    // CONNECTION STATE
    // ============================================================

    private fun isConnected(): Boolean {

        return socket != null &&
                socket?.isConnected == true &&
                socket?.isClosed == false &&
                output != null
    }

    private fun disconnect() {

        stopJog()

        disconnectInternal()

        statusText.text =
            "● DISCONNECTED"

        connectionInfo.text =
            "IP: ${ipEdit.text}\n" +
                    "Port: ${portEdit.text}\n" +
                    "Status: Disconnected"

        appendLog(
            "Disconnected"
        )

        logConnectionEvent(
            "DISCONNECTED"
        )

        updateControlButtons()

        updateProgramConnectionColors()
    }

    private fun disconnectInternal() {

        try {
            socket?.close()
        } catch (_: Exception) {
        }

        socket = null

        output = null

        synchronized(jogValues) {

            for (i in jogValues.indices) {
                jogValues[i] = 0.0
            }
        }

        robotEnabled = false
        out31State = false
        plcEnabled = false

        runOnUiThread {

            updateControlButtons()
            updateProgramConnectionColors()
        }
    }

    // ============================================================
    // LOG CONNECTION EVENTS
    // ============================================================

    private fun logConnectionEvent(
        text: String
    ) {

        if (
            text.contains(
                "CONNECTED",
                ignoreCase = true
            )
        ) {

            if (lastLoggedConnectionState != true) {

                lastLoggedConnectionState =
                    true

                Log.d(
                    LOG_TAG_CONNECTION,
                    text
                )
            }

            return
        }

        if (
            text.contains(
                "DISCONNECTED",
                ignoreCase = true
            ) ||
            text.contains(
                "LOST",
                ignoreCase = true
            )
        ) {

            if (lastLoggedConnectionState != false) {

                lastLoggedConnectionState =
                    false

                Log.d(
                    LOG_TAG_CONNECTION,
                    text
                )
            }

            return
        }

        Log.d(
            LOG_TAG_CONNECTION,
            text
        )
    }

    // ============================================================
    // TTS
    // ============================================================

    private fun initializeTextToSpeech() {

        textToSpeech =
            TextToSpeech(this) { status ->

                if (
                    status ==
                    TextToSpeech.SUCCESS
                ) {

                    var result =
                        textToSpeech?.setLanguage(
                            Locale("nl", "BE")
                        )

                    if (
                        result ==
                        TextToSpeech.LANG_MISSING_DATA ||
                        result ==
                        TextToSpeech.LANG_NOT_SUPPORTED
                    ) {

                        result =
                            textToSpeech?.setLanguage(
                                Locale("nl", "NL")
                            )
                    }

                    ttsReady =
                        result !=
                                TextToSpeech.LANG_MISSING_DATA &&
                                result !=
                                TextToSpeech.LANG_NOT_SUPPORTED

                    if (ttsReady) {

                        textToSpeech
                            ?.setSpeechRate(0.65f)

                        textToSpeech
                            ?.setPitch(1.0f)
                    }
                }
            }
    }

    private fun speak(
        text: String
    ) {

        if (!ttsReady) {

            Log.d(
                LOG_TAG_STATUS,
                "TTS not available: $text"
            )

            return
        }

        textToSpeech?.stop()

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "CRI_${System.currentTimeMillis()}"
        )
    }

    // ============================================================
    // SYSTEM UI
    // ============================================================

    private fun hideSystemUI() {

        window.insetsController?.let { controller ->

            controller.hide(
                WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars()
            )

            controller.systemBarsBehavior =
                WindowInsetsController
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(
        hasFocus: Boolean
    ) {

        super.onWindowFocusChanged(
            hasFocus
        )

        if (hasFocus) {
            hideSystemUI()
        }
    }

    // ============================================================
    // BACK BUTTON
    // ============================================================

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {

        if (
            accessLevel ==
            AccessLevel.EXPERT
        ) {

            showExitDialog()

        } else {

            appendLog(
                "Back blocked - Expert PIN required"
            )

            speak(
                "Expert toegang vereist"
            )
        }
    }

    private fun showExitDialog() {

        val input =
            EditText(this)

        input.inputType =
            InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_VARIATION_PASSWORD

        input.hint =
            "Expert PIN"

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    "EXIT APPLICATION"
                )
                .setMessage(
                    "Enter expert PIN to exit"
                )
                .setView(input)
                .setNegativeButton(
                    "CANCEL",
                    null
                )
                .setPositiveButton(
                    "EXIT",
                    null
                )
                .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                if (
                    input.text.toString()
                    == expertPin
                ) {

                    dialog.dismiss()

                    stopJog()

                    autoReconnectEnabled =
                        false

                    disconnectInternal()

                    finishAndRemoveTask()

                } else {

                    input.text.clear()

                    input.error =
                        "Invalid PIN"

                    appendLog(
                        "Invalid exit PIN"
                    )
                }
            }
        }

        dialog.show()
    }

    // ============================================================
    // VISIBLE LOG
    // ============================================================

    private fun appendLog(
        text: String
    ) {

        runOnUiThread {

            logText.append(
                text + "\n"
            )

            val lineCount =
                logText.lineCount

            if (
                lineCount >
                MAX_VISIBLE_LOG_LINES
            ) {

                val textValue =
                    logText.text.toString()

                val lines =
                    textValue.split(
                        "\n"
                    )

                val startIndex =
                    (
                            lines.size -
                                    MAX_VISIBLE_LOG_LINES -
                                    1
                            )
                        .coerceAtLeast(0)

                val newText =
                    lines
                        .drop(startIndex)
                        .takeLast(
                            MAX_VISIBLE_LOG_LINES
                        )
                        .joinToString("\n")

                logText.text =
                    newText
            }

            val layout =
                logText.layout

            if (layout != null) {

                val scrollAmount =
                    layout.getLineTop(
                        logText.lineCount
                    ) - logText.height

                if (scrollAmount > 0) {

                    logText.scrollTo(
                        0,
                        scrollAmount
                    )
                }
            }
        }
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        autoReconnectEnabled =
            false

        stopJog()

        disconnectInternal()

        try {
            reconnectExecutor.shutdownNow()
        } catch (_: Exception) {
        }

        textToSpeech?.stop()

        textToSpeech?.shutdown()

        textToSpeech = null

        try {
            executor.shutdownNow()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}