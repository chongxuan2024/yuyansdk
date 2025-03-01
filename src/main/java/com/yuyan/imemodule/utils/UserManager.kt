import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object UserManager {
    private const val PREF_NAME = "user_info"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email" 
    private const val KEY_TOKEN = "token"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    private var currentUser: User? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IS_LOGGED_IN, false)) {
            currentUser = User(
                username = prefs.getString(KEY_USERNAME, "")!!,
                email = prefs.getString(KEY_EMAIL, null),
                token = prefs.getString(KEY_TOKEN, "")!!
            )
        }
    }

    fun isLoggedIn() = currentUser != null

    fun getCurrentUser() = currentUser

    fun saveUser(context: Context, user: User) {
        currentUser = user
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_USERNAME, user.username)
            putString(KEY_EMAIL, user.email)
            putString(KEY_TOKEN, user.token)
            putBoolean(KEY_IS_LOGGED_IN, true)
        }
    }

    fun logout(context: Context) {
        currentUser = null
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
} 