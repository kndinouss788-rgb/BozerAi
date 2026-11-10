package ai.bozer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

@androidx.compose.runtime.Composable
fun FutureSettingLine(title: String, subtitle: String, glyph: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
            .background(Color(0xFFF7FAFC)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFE8F7FD)),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = Color(0xFF319BC7), fontSize = 19.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, color = Color(0xFF172538), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF7D8A9B), fontSize = 11.sp)
        }
    }
}
