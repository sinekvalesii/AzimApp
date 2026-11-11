package com.selcuk.azim.auth

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

data class Session(val email: String?, val guest: Boolean)

/**
 * Firebase Authentication (e-posta + şifre).
 * app/google-services.json eklenmemişse Firebase başlatılmaz; bu durumda [available] false olur
 * ve uygulama yalnızca misafir moduyla (veriler cihazda) kullanılabilir.
 */
class AuthRepo(ctx: Context) {
    val available = FirebaseApp.getApps(ctx).isNotEmpty()
    private val prefs = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val fb get() = FirebaseAuth.getInstance()
    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session

    private fun load(): Session? {
        val user = if (available) fb.currentUser else null
        return when {
            user != null -> Session(user.email, false)
            prefs.getBoolean("guest", false) -> Session(null, true)
            else -> null
        }
    }

    suspend fun signIn(email: String, pass: String) {
        fb.signInWithEmailAndPassword(email, pass).await(); _session.value = load()
    }

    suspend fun signUp(email: String, pass: String) {
        fb.createUserWithEmailAndPassword(email, pass).await(); _session.value = load()
    }

    suspend fun reset(email: String) {
        fb.sendPasswordResetEmail(email).await()
    }

    fun guest() {
        prefs.edit { putBoolean("guest", true) }; _session.value = load()
    }

    fun signOut() {
        if (available) fb.signOut()
        prefs.edit { remove("guest") }
        _session.value = null
    }

    /** Firebase hesabını kalıcı olarak siler (Play hesap silme şartı). */
    suspend fun delete() {
        if (available) fb.currentUser?.delete()?.await()
        signOut()
    }
}
