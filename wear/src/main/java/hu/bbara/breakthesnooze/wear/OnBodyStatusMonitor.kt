package hu.bbara.breakthesnooze.wear

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object OnBodyStatusMonitor : SensorEventListener {

    private const val TAG = "OnBodyStatusMonitor"
    private const val SENSOR_TIMEOUT_MS = 300L

    private val initialized = AtomicBoolean(false)
    private val lastKnownState = AtomicReference<Boolean?>(null)
    private var sensorManager: SensorManager? = null
    private var offBodySensor: Sensor? = null

    fun ensureInitialized(context: Context) {
        if (initialized.get()) {
            return
        }
        val manager = context.applicationContext.getSystemService(SensorManager::class.java)
        if (manager == null) {
            Log.w(TAG, "SensorManager unavailable; cannot monitor on-body state")
            return
        }
        val sensor = resolveSensor(manager)
        if (sensor == null) {
            Log.w(TAG, "Off-body detection sensor not available on this device")
            return
        }
        try {
            if (manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                sensorManager = manager
                offBodySensor = sensor
                initialized.set(true)
                Log.d(TAG, "Registered on-body listener with sensor type=${sensor.type}")
            } else {
                Log.w(TAG, "Failed to register on-body listener")
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Missing permission to access on-body sensor", error)
        }
    }

    fun getOnBodyState(): Boolean? = lastKnownState.get()

    fun tryReadImmediateState(context: Context): Boolean? {
        val manager = context.applicationContext.getSystemService(SensorManager::class.java) ?: return null
        val sensor = resolveSensor(manager) ?: return null
        val latch = CountDownLatch(1)
        val result = AtomicReference<Boolean?>(null)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                result.set(parseEvent(event))
                latch.countDown()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        return try {
            if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)) {
                Log.w(TAG, "Immediate sensor registration failed")
                null
            } else {
                if (!latch.await(SENSOR_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.d(TAG, "Immediate sensor read timed out")
                }
                result.get().also {
                    if (it != null) {
                        lastKnownState.set(it)
                    }
                }
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Missing permission for immediate on-body read", error)
            null
        } finally {
            manager.unregisterListener(listener, sensor)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val state = parseEvent(event)
        if (state != null) {
            lastKnownState.set(state)
            Log.d(TAG, "On-body sensor update: onBody=$state")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun parseEvent(event: SensorEvent): Boolean? {
        if (event.values.isEmpty()) {
            return null
        }
        val raw = event.values[0]
        return when (event.sensor.type) {
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> raw >= 0.5f
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_SIGNIFICANT_MOTION -> null
            else -> null
        }
    }

    private fun resolveSensor(manager: SensorManager): Sensor? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
        } else {
            null
        }
    }
}
