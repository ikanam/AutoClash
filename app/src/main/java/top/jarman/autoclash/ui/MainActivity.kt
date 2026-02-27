package top.jarman.autoclash.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import top.jarman.autoclash.ui.navigation.AppNavigation
import top.jarman.autoclash.ui.screen.PermissionRationaleScreen
import top.jarman.autoclash.ui.theme.AutoClashTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AutoClashTheme {
                var permissionsGranted by remember { mutableStateOf(checkAllPermissions()) }
                var showRationale by remember { mutableStateOf(!permissionsGranted) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showRationale && !permissionsGranted) {
                        PermissionRationaleScreen(
                            onRequestPermissions = {
                                requestNeededPermissions()
                                showRationale = false
                            },
                            onSkip = {
                                showRationale = false
                                permissionsGranted = true // let user in anyway
                            }
                        )
                    } else {
                        val navController = rememberNavController()
                        AppNavigation(navController = navController)
                    }
                }
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Recreate to refresh permission state
            recreate()
        }
    }

    private fun checkAllPermissions(): Boolean {
        val needed = getNeededPermissions()
        return needed.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getNeededPermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms
    }

    private fun requestNeededPermissions() {
        val perms = getNeededPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
}
