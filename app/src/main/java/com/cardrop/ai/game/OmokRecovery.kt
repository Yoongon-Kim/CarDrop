package com.cardrop.ai.game

import android.content.Context
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Place
import com.cardrop.crew.CrewRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class OmokResume(val code: String, val trip: Long, val car: String, val real: Boolean, val savedAt: Long)

interface OmokRecoveryStore {
    fun read(car: String, real: Boolean, now: Long = System.currentTimeMillis()): OmokResume?
    fun save(crew: Crew, car: String, real: Boolean)
    fun clear()
}

/** Only an omok session opts a crew into process-restart recovery. Other crew creation keeps its behavior. */
class OmokResumeStore(context: Context) : OmokRecoveryStore {
    private val prefs = context.getSharedPreferences("cardrop_omok_resume", Context.MODE_PRIVATE)
    override fun read(car: String, real: Boolean, now: Long): OmokResume? = runCatching {
        val json = JSONObject(prefs.getString("crew", null) ?: return null)
        OmokResume(json.getString("code"), json.getLong("trip"), json.getString("car"), json.getBoolean("real"), json.getLong("at"))
            .takeIf { it.car == car && it.real == real && now - it.savedAt in 0..86_400_000L }
    }.getOrNull()
    override fun save(crew: Crew, car: String, real: Boolean) {
        val previous = read(car, real)
        // Heartbeats must not turn into disk writes every second.
        if (previous?.code == crew.code && previous.trip == crew.startedAtMillis) return
        prefs.edit().putString("crew", JSONObject().put("code", crew.code).put("trip", crew.startedAtMillis ?: return)
            .put("car", car).put("real", real).put("at", System.currentTimeMillis()).toString()).commit()
    }
    override fun clear() { prefs.edit().remove("crew").commit() }
}

/** Serializes auto-rejoin with board 01's ensureCrew, so a host cannot create a new crew while restoring. */
class OmokRecoveringCrew(
    private val delegate: CrewRepository,
    private val scope: CoroutineScope,
    private val store: OmokRecoveryStore,
    private val real: Boolean,
) : CrewRepository by delegate {
    val finished = MutableStateFlow<Boolean?>(null)
    val recoveryError = MutableStateFlow<String?>(null)
    private var recovery: Job? = null
    init { retry() }

    fun retry() {
        if (recovery?.isActive == true) return
        recovery = scope.launch {
            finished.value = null
            recoveryError.value = null
            recover()
        }
    }
    private suspend fun recover() {
        val saved = store.read(delegate.myCarId, real)
        if (saved == null) { finished.value = false; return }
        try {
            val restored = withTimeout(8_000) { delegate.joinCrew(saved.code) }
            if (restored == null || (real && restored.startedAtMillis != saved.trip)) {
                delegate.leaveCrew(); store.clear(); finished.value = false
            } else finished.value = true
        } catch (_: TimeoutCancellationException) {
            recoveryError.value = "이전 대국에 연결하지 못했어요. 연결을 확인하고 다시 시도해 주세요"
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            // Keep the old crew and suspend ensureCrew on transient errors, instead of creating a new crew.
            recoveryError.value = "이전 대국에 연결하지 못했어요. 연결을 확인하고 다시 시도해 주세요"
        }
    }
    suspend fun restored(): Boolean = finished.filterNotNull().first()
    override suspend fun createCrew(name: String, destination: Place): Crew {
        restored()
        return delegate.crew.value ?: delegate.createCrew(name, destination)
    }
    override suspend fun joinCrew(code: String): Crew? {
        recovery?.cancel()
        store.clear()
        finished.value = false
        recoveryError.value = null
        return delegate.joinCrew(code)
    }
    override fun leaveCrew() { recovery?.cancel(); store.clear(); finished.value = false; delegate.leaveCrew() }
}
