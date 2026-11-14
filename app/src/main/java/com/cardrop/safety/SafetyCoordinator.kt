package com.cardrop.safety

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.SystemClock
import android.util.Log
import com.cardrop.ai.SafetyEncouragement
import com.cardrop.ai.EncouragementContext
import com.cardrop.ai.validatedEncouragement
import com.cardrop.core.model.Gear
import com.cardrop.crew.CrewRepository
import com.cardrop.navi.InAppNavigation
import com.cardrop.vehicle.VehicleRepository
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Lives with AppContainer, never with the record screen. All detector mutations run on appScope's main dispatcher. */
class SafetyCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val crew: CrewRepository,
    private val vehicle: VehicleRepository,
    private val navigation: InAppNavigation,
    private val encouragement: SafetyEncouragement,
    private val voice: VoiceService,
    val demo: Boolean,
    realCrew: Boolean,
) {
    private val appContext = context.applicationContext
    private var appVisible = false
    private var serviceStarted = false
    val current = MutableStateFlow<SafetyRecord?>(null)
    val history = MutableStateFlow<List<SafetyRecord>>(emptyList())
    val peers = MutableStateFlow<Map<String, SafetyPeer>>(emptyMap())
    val speedKmh = MutableStateFlow<Double?>(null)
    val sourceState = MutableStateFlow(SafetySourceState.WAITING)
    val canFinish = MutableStateFlow(false)
    val cheerText = MutableStateFlow("기록 슬롯을 열면 짧은 응원을 들려드려요")
    val cheerBusy = MutableStateFlow(false)
    val completion = MutableStateFlow<String?>(null)
    val clock = MutableStateFlow(System.currentTimeMillis())
    val groupClosed = MutableStateFlow(false)
    val canCloseGroup: Boolean get() = crew.crew.value?.hostCarId == crew.myCarId
    private val prefs = context.getSharedPreferences("safety-settings", Context.MODE_PRIVATE)
    val cheerEnabled = MutableStateFlow(prefs.getBoolean("cheer", true))
    private val store = SafetyStore(context)
    private val writes = Channel<SafetyRecord>(Channel.UNLIMITED)
    private val native = if (demo) null else runCatching { AaosSafetyTelemetry(context, scope) }.getOrNull()
    val demoTelemetry = if (demo) DemoSafetyTelemetry(scope) { vehicle.snapshot.value.gear } else null
    private val sync = if (realCrew) SafetySync() else null
    private var fleet: DemoSafetyFleet? = null
    private var engine: SafetyDetector? = null
    private var key: String? = null
    private var lastReceivedNanos = 0L
    private var sourceStartedNanos = 0L
    private var stationarySince: Long? = null
    private var lastPublished = 0L
    private var lastSave = 0L
    private var savedRevision = -1L
    private var lastShare = 0L
    private var sharedRevision = -1L
    private val entryGate = SafetyEntryGate()
    private var entry: Long? = null
    private var cheerJob: Job? = null
    private var displayedRevision: Long? = null

    init {
        scope.launch(Dispatchers.IO) {
            for (record in writes) try { store.save(record) } catch (e: Exception) { Log.e("CarDropSafety", "Could not save safety record", e) }
        }
        scope.launch {
            val stored = withContext(Dispatchers.IO) { store.load() }
            // The DEMO car: three finished sample trips of this week, so the 시연 has something to show (SafetySamples).
            // No crew yet at this point, so the car's name comes from its id (SafetySamples.carName).
            history.value = if (demo) {
                SafetySamples.withSamples(stored, crew.myCarId, SafetySamples.carName(crew.myCarId), System.currentTimeMillis())
            } else stored
            crew.crew.collectLatest { group ->
                val started = group?.startedAtMillis
                val nextKey = if (group != null && started != null) "${group.code}-$started" else null
                if (nextKey == key) return@collectLatest
                engine?.let { old ->
                    if (old.record.status == SafetyTripStatus.RECORDING) old.finish(System.currentTimeMillis(), false)
                    save(old.record)
                }
                stopSources(); engine = null; current.value = null; peers.value = emptyMap(); key = nextKey
                if (group == null || started == null || nextKey == null) return@collectLatest
                val me = group.cars.firstOrNull { it.id == crew.myCarId } ?: return@collectLatest
                val now = System.currentTimeMillis()
                val old = history.value.firstOrNull { it.tripId == nextKey && it.carId == me.id && it.demo == demo && it.rulesVersion == SafetyRules.VERSION }
                val record = old?.let { if (it.status == SafetyTripStatus.RECORDING) it.copy(
                    eligibleSeconds = it.eligibleSeconds + ((now - it.updatedAtMillis).coerceAtLeast(0) / 1000.0), updatedAtMillis = now,
                ) else it } ?: SafetyRecord(nextKey, me.id, me.name, group.destination.name, started, now, demo,
                    eligibleSeconds = if (demo) 0.0 else (now - started).coerceAtLeast(0) / 1000.0)
                sourceStartedNanos = SystemClock.elapsedRealtimeNanos()
                engine = SafetyDetector(record, sourceStartedNanos)
                fleet = if (demo && sync == null) DemoSafetyFleet(group.cars.filterNot { it.id == me.id }, nextKey, now, group.destination.name) else null
                sync?.follow(nextKey, group.cars, group.hostCarId == me.id)
                savedRevision = -1; sharedRevision = -1
                publish(true)
                if (record.status == SafetyTripStatus.RECORDING) startSources()
            }
        }
        scope.launch {
            val stream = demoTelemetry?.readings ?: native?.readings ?: emptyFlow()
            stream.collect { reading ->
                val detector = engine ?: return@collect
                if (reading.measuredNanos < sourceStartedNanos) return@collect
                val now = System.currentTimeMillis()
                val paused = vehicle.snapshot.value.gear.let { it == Gear.PARK || it == Gear.REVERSE }
                lastReceivedNanos = reading.receivedNanos
                speedKmh.value = reading.metresPerSecond.takeIf { it.isFinite() && it >= 0 }?.times(3.6)
                if (reading.metresPerSecond in 0.0..(1.0 / 3.6)) {
                    if (stationarySince == null) stationarySince = reading.receivedNanos
                } else stationarySince = null
                detector.ingest(reading, now, paused)
                publish()
            }
        }
        scope.launch {
            while (true) {
                delay(1000)
                val nowNanos = SystemClock.elapsedRealtimeNanos()
                val now = System.currentTimeMillis()
                clock.value = now
                val gear = vehicle.snapshot.value.gear
                val paused = gear == Gear.PARK || gear == Gear.REVERSE
                engine?.tick(nowNanos, now, paused)
                val fresh = nowNanos - lastReceivedNanos in 0..1_000_000_000L
                if (!fresh) { speedKmh.value = null; stationarySince = null }
                canFinish.value = gear == Gear.PARK || (fresh && stationarySince?.let { nowNanos - it >= 5_000_000_000L } == true)
                sourceState.value = if (demo) if (demoTelemetry?.paused == true) SafetySourceState.WAITING else SafetySourceState.READY else if (!fresh && native?.state?.value == SafetySourceState.READY) SafetySourceState.WAITING else native?.state?.value ?: SafetySourceState.UNAVAILABLE
                if (navigation.state.value.arrived && canFinish.value) finish(true)
                publish(true)
                updatePeers(now)
            }
        }
    }

    private fun startSources() { if (demo) demoTelemetry?.start() else native?.start(); updateService() }
    private fun stopSources() { demoTelemetry?.stop(); native?.stop(); sync?.stop(); stopService(); lastReceivedNanos = 0; stationarySince = null; speedKmh.value = null; groupClosed.value = false }
    fun onSpeedPermissionGranted() { if (!demo && engine?.record?.status == SafetyTripStatus.RECORDING) native?.start() }
    fun retrySpeed(context: Context) {
        if (appContext.checkSelfPermission(AaosSafetyTelemetry.PERMISSION) == PackageManager.PERMISSION_GRANTED) { native?.stop(); onSpeedPermissionGranted() }
        else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${appContext.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun onAppVisible(visible: Boolean) {
        appVisible = visible
        if (visible) updateService() else { engine?.record?.let(::save); leaveRecordScreen() }
    }
    private fun updateService() {
        if (serviceStarted || !appVisible || current.value?.status != SafetyTripStatus.RECORDING ||
            appContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        runCatching { appContext.startForegroundService(Intent(appContext, SafetyRecordingService::class.java)); serviceStarted = true }
            .onFailure { Log.w("CarDropSafety", "Foreground recording could not start; unavailable measurements will remain gaps", it) }
    }
    private fun stopService() { if (serviceStarted) appContext.stopService(Intent(appContext, SafetyRecordingService::class.java)); serviceStarted = false }

    private fun publish(force: Boolean = false) {
        val record = engine?.record ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && record.revision == current.value?.revision && now - lastPublished < 250) return
        val changed = record.revision != current.value?.revision
        current.value = record; lastPublished = now
        if (changed) updatePeers(System.currentTimeMillis())
        if (record.revision != savedRevision || now - lastSave >= 5000) {
            save(record); lastSave = now; savedRevision = record.revision
        }
        if (sync != null && ((record.revision != sharedRevision && now - lastShare >= 1000) || now - lastShare >= 5000)) {
            sync.share(record.peer()); sharedRevision = record.revision; lastShare = now
        }
        if (displayedRevision != null && displayedRevision != record.revision) cancelCheer()
    }

    private fun save(record: SafetyRecord) {
        writes.trySend(record)
        history.value = (listOf(record) + history.value.filterNot { it.id == record.id }).sortedByDescending { it.updatedAtMillis }.take(100)
    }

    private fun updatePeers(now: Long) {
        val mine = current.value ?: return
        groupClosed.value = sync?.closed?.value ?: groupClosed.value
        val others = sync?.peers?.value ?: fleet?.advance(now).orEmpty()
        peers.value = if (sync?.closed?.value == true) others else others + (mine.carId to mine.peer())
    }

    fun finish(completed: Boolean = false) {
        if (!isStationary() || engine?.record?.status != SafetyTripStatus.RECORDING) return
        engine?.finish(System.currentTimeMillis(), completed)
        publish(true); updatePeers(System.currentTimeMillis())
        completion.value = current.value?.id
        demoTelemetry?.stop(); native?.stop(); stopService()
    }
    fun consumeCompletion(id: String) { if (completion.value == id) completion.value = null }
    fun closeGroup() {
        if (!isStationary() || !canCloseGroup) return
        current.value?.peer()?.let { mine ->
            if (sync != null) sync.closeGroup(mine)
            else { fleet?.finish(clock.value, completed = false); groupClosed.value = true; updatePeers(clock.value) }
        }
    }
    private fun isStationary(): Boolean = vehicle.snapshot.value.gear == Gear.PARK ||
        (SystemClock.elapsedRealtimeNanos() - lastReceivedNanos in 0..1_000_000_000L && stationarySince?.let { SystemClock.elapsedRealtimeNanos() - it >= 5_000_000_000L } == true)
    /** Simulator-only final destination signal; never inferred from a rest stop or a button in real mode. */
    fun demoArrival() { if (demo && isStationary()) { fleet?.finish(System.currentTimeMillis(), true); finish(true) } }
    fun record(id: String): SafetyRecord? = current.value?.takeIf { it.id == id } ?: history.value.firstOrNull { it.id == id }

    fun newEntry(): Long = entryGate.newEntry()
    fun showRecordEntry(id: Long?) {
        if (entry == id) return
        cancelCheer(); entry = id
        val record = current.value
        val enteredAt = SystemClock.elapsedRealtime()
        if (!entryGate.enter(id, cheerEnabled.value, record?.revision, enteredAt) || id == null) return
        val deadline = enteredAt + 5000
        val context = EncouragementContext(record?.score, record?.movingSeconds?.toInt()?.div(60) ?: 0,
            record?.count(SafetyEventType.ACCELERATION) ?: 0, record?.count(SafetyEventType.BRAKING) ?: 0,
            record?.rankEligible == true)
        displayedRevision = record?.revision
        cheerBusy.value = true
        cheerJob = scope.launch {
            try {
                val answer = withTimeoutOrNull(3000) { encouragement.create(context) }
                val message = validatedEncouragement(answer, context)
                if (!cheerEnabled.value || !entryGate.valid(id, current.value?.revision, SystemClock.elapsedRealtime())) return@launch
                cheerText.value = message
                if (!voice.listening.value) voice.trySpeakEncouragement(message, id, deadline)
            } finally { cheerBusy.value = false }
        }
    }
    fun leaveRecordScreen() { cancelCheer(); entry = null }
    private fun cancelCheer() { entryGate.leave(); cheerJob?.cancel(); cheerJob = null; entry?.let(voice::cancelEncouragement); displayedRevision = null; cheerBusy.value = false }
    fun setCheerEnabled(enabled: Boolean) { cheerEnabled.value = enabled; prefs.edit().putBoolean("cheer", enabled).apply(); if (!enabled) cancelCheer() }
}
