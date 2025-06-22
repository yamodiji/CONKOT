/**
 * Load apps using Android 15 compatibility methods
 */
fun loadAppsWithAndroid15Compatibility() {
    viewModelScope.launch {
        try {
            repository.loadAppsWithAndroid15Compatibility()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading apps with Android 15 compatibility", e)
        }
    }
} 