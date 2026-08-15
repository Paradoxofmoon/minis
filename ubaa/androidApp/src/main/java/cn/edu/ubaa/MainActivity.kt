package cn.edu.ubaa

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cn.edu.ubaa.api.network.platformLogEnabled

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    // release 构建(不可调试)关闭调试日志，提升性能与隐私
    val isDebuggable =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    platformLogEnabled = isDebuggable

    setContent { App() }
  }
}

@Preview
@Composable
fun AppAndroidPreview() {
  App()
}
