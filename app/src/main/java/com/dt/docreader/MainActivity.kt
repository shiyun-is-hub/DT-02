package com.dt.docreader

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dt.docreader.ui.home.HomeScreen
import com.dt.docreader.ui.reader.DocumentViewModel
import com.dt.docreader.ui.reader.ReaderScreen
import com.dt.docreader.ui.theme.DocReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 锁定暗色系统栏（终端风）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent { DocReaderTheme { AppNav() } }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(onFilePicked = { uri ->
                nav.currentBackStackEntry?.savedStateHandle?.set("uri", uri.toString())
                nav.navigate("reader")
            })
        }
        composable("reader") {
            val vm: DocumentViewModel = viewModel()
            val entry = nav.currentBackStackEntry
            LaunchedEffect(entry) {
                val uriStr = nav.previousBackStackEntry
                    ?.savedStateHandle?.get<String>("uri")
                if (!uriStr.isNullOrEmpty()) {
                    vm.load(android.net.Uri.parse(uriStr))
                }
            }
            ReaderScreen(viewModel = vm, onBack = { nav.popBackStack() })
        }
    }
}