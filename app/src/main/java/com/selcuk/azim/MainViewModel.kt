package com.selcuk.azim

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.selcuk.azim.data.Kv
import com.selcuk.azim.health.HcState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/**
 * Uygulamanın ViewModel'i. Veri katmanı (Room), sağlık (Health Connect) ve kimlik (Firebase) buradan yönetilir.
 * Arayüz WebView içindedir; sonuçlar [js] akışıyla arayüze iletilir, Activity bu akışı toplayıp çalıştırır.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as AzimApp
    private val dao = a.db.dao()
    val js = MutableSharedFlow<String>(extraBufferCapacity = 32)

    /** app/google-services.json eklendiyse true. Yoksa uygulama cihaz içi hesapla çalışır. */
    val fbAvailable = FirebaseApp.getApps(app).isNotEmpty()

    // ---------- Depolama (Room) ----------
    @Volatile private var migrated = false

    /** 2.x sürümündeki SharedPreferences verisini bir kez Room'a taşır. */
    private fun ensureMigrated() {
        if (migrated) return
        synchronized(this) {
            if (migrated) return
            val old = getApplication<Application>().getSharedPreferences("azim_store", Context.MODE_PRIVATE)
            if (old.all.isNotEmpty() && dao.count() == 0) {
                dao.putAll(old.all.mapNotNull { (k, v) -> (v as? String)?.let { Kv(k, it) } })
                old.edit { clear() }
            }
            migrated = true
        }
    }

    fun get(k: String): String? { ensureMigrated(); return dao.get(k) }
    fun set(k: String, v: String) { ensureMigrated(); dao.put(Kv(k, v)) }
    fun remove(k: String) { ensureMigrated(); dao.remove(k) }

    // ---------- Sağlık (Health Connect) ----------
    @Volatile var hcState: HcState = HcState.UNAVAILABLE
        private set

    fun refreshHealth() = viewModelScope.launch(Dispatchers.IO) {
        hcState = a.health.state()
        if (hcState == HcState.READY) a.health.lastDays(14).takeIf { it.isNotEmpty() }?.let { dao.upsertHealth(it) }
        js.emit("window.azimOnHealth&&azimOnHealth()")
    }

    fun healthJson(): String = JSONArray(dao.health().takeLast(30).map {
        JSONObject().put("date", it.date).put("steps", it.steps).put("hr", it.avgHr ?: JSONObject.NULL)
    }).toString()

    // ---------- Kimlik (Firebase Authentication) ----------
    private val fb get() = FirebaseAuth.getInstance()

    fun currentEmail(): String = if (fbAvailable) fb.currentUser?.email.orEmpty() else ""

    fun signOut() { if (fbAvailable) fb.signOut() }

    /** op: signIn, signUp, reset, delete. Sonuç: azimAuth(op, başarılı, hataKodu) */
    fun auth(op: String, email: String, pass: String) = viewModelScope.launch {
        val r = runCatching {
            check(fbAvailable)
            when (op) {
                "signIn" -> fb.signInWithEmailAndPassword(email, pass).await()
                "signUp" -> fb.createUserWithEmailAndPassword(email, pass).await()
                "reset" -> fb.sendPasswordResetEmail(email).await()
                "delete" -> fb.currentUser?.delete()?.await()
                else -> error("op")
            }
        }
        val code = when (val e = r.exceptionOrNull()) {
            null -> ""
            is FirebaseAuthException -> e.errorCode
            is FirebaseNetworkException -> "NETWORK"
            else -> "UNKNOWN"
        }
        js.emit("window.azimAuth&&azimAuth(${JSONObject.quote(op)},${r.isSuccess},${JSONObject.quote(code)})")
    }
}
