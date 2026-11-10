package ai.bozer.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image

private val FutureInk = Color(0xFF172538)
private val FutureMuted = Color(0xFF7D8A9B)
private val FutureBlue = Color(0xFF66C9F4)
private val FutureCyan = Color(0xFF9AEAFF)
private val FutureGlass = Color(0xBFFFFFFF)

@Composable
fun FutureBackdrop(content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(-34f, 34f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    val pulse by transition.animateFloat(0.20f, 0.48f, infiniteRepeatable(tween(4200), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.fillMaxSize().background(Color(0xFFF8FCFF))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFF0F9FF), Color(0xFFFBFDFF), Color(0xFFF6FAFF))))
            drawCircle(Brush.radialGradient(listOf(Color(0x6697E4FF), Color.Transparent)), size.minDimension * .47f, Offset(size.width * .12f + drift, size.height * .20f), alpha = pulse)
            drawCircle(Brush.radialGradient(listOf(Color(0x559DABFF), Color.Transparent)), size.minDimension * .54f, Offset(size.width * .94f - drift, size.height * .70f), alpha = pulse * .72f)
            drawCircle(Brush.radialGradient(listOf(Color(0x33FFFFFF), Color.Transparent)), size.minDimension * .42f, Offset(size.width * .54f, size.height * .48f))
        }
        content()
    }
}

@Composable
fun FutureHeader(onMenu: () -> Unit, onProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        FutureIconButton("menu", onMenu, 46.dp)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("B O Z E R", color = FutureInk, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.6.sp)
            Text("INTELLIGENCE • ACTIVE", color = Color(0xFF8A9CAD), fontSize = 8.sp, letterSpacing = 1.8.sp)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(46.dp).clip(CircleShape).background(FutureGlass).border(1.dp, Color.White, CircleShape).clickable(onClick = onProfile), contentAlignment = Alignment.Center) {
            FutureOrb(34.dp)
        }
    }
}

@Composable
fun FutureIconButton(kind: String, onClick: () -> Unit, size: Dp = 42.dp) {
    Box(Modifier.size(size).shadow(8.dp, CircleShape, ambientColor = Color(0x223D9FCB), spotColor = Color(0x223D9FCB))
        .clip(CircleShape).background(Brush.linearGradient(listOf(Color.White.copy(.94f), Color(0xFFEAF7FF).copy(.82f))))
        .border(1.dp, Color.White, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        when (kind) {
            "menu" -> Canvas(Modifier.size(20.dp)) {
                val w = size.width
                for (i in 0..2) drawLine(FutureInk, Offset(1f, 5f + i * 6f), Offset(w - 1f, 5f + i * 6f), 1.8f, cap = StrokeCap.Round)
            }
            "mic" -> Canvas(Modifier.size(20.dp)) {
                drawRoundRect(FutureInk, Offset(size.width*.36f, 1f), androidx.compose.ui.geometry.Size(size.width*.28f, size.height*.57f), androidx.compose.ui.geometry.CornerRadius(8f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.8f))
                drawArc(FutureBlue, 0f, 180f, false, Offset(size.width*.20f, size.height*.22f), androidx.compose.ui.geometry.Size(size.width*.60f, size.height*.62f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.8f))
                drawLine(FutureInk, Offset(size.width*.5f, size.height*.82f), Offset(size.width*.5f, size.height*.98f), 1.8f)
                drawLine(FutureInk, Offset(size.width*.34f, size.height*.98f), Offset(size.width*.66f, size.height*.98f), 1.8f)
            }
            "send" -> Canvas(Modifier.size(21.dp)) {
                val p = Path().apply { moveTo(size.width*.08f, size.height*.48f); lineTo(size.width*.92f, size.height*.08f); lineTo(size.width*.63f, size.height*.92f); lineTo(size.width*.47f, size.height*.57f); close() }
                drawPath(p, Color.White)
                drawLine(Color(0xFFB9F1FF), Offset(size.width*.47f, size.height*.57f), Offset(size.width*.92f, size.height*.08f), 1.2f)
            }
            else -> Text("+", color = FutureInk, fontSize = 25.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
fun FutureOrb(size: Dp = 54.dp) {
    val transition = rememberInfiniteTransition(label = "orb")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "rotation")
    val glow by transition.animateFloat(.35f, .78f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "glow")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFBDF3FF).copy(glow), Color(0x5587C9FF), Color.Transparent)))
            drawCircle(Color(0xFFB6EFFF).copy(alpha = .65f), radius = size.minDimension * .43f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.1.dp.toPx()))
            rotate(rotation) {
                drawArc(Brush.sweepGradient(listOf(Color.Transparent, Color(0xFF56C7F4), Color(0xFFB5A8FF), Color.Transparent)), 20f, 115f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
            drawCircle(Color(0xFF193D60), radius = size.minDimension * .27f)
            drawCircle(Color(0xFF77DEFF), radius = size.minDimension * .055f, center = Offset(size.width*.42f, size.height*.47f))
            drawCircle(Color(0xFF77DEFF), radius = size.minDimension * .055f, center = Offset(size.width*.58f, size.height*.47f))
            drawArc(Color(0xFFB5F3FF), 20f, 140f, false, Offset(size.width*.40f, size.height*.48f), androidx.compose.ui.geometry.Size(size.width*.20f, size.height*.20f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun FutureMessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val appear by animateFloatAsState(1f, animationSpec = tween(420), label = "message-in")
    if (isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = Color(0xDDE8F6FF),
                shape = RoundedCornerShape(23.dp, 23.dp, 7.dp, 23.dp),
                modifier = Modifier.widthIn(max = 310.dp).border(1.dp, Color.White.copy(.95f), RoundedCornerShape(23.dp, 23.dp, 7.dp, 23.dp))
            ) {
                Text(message.content, Modifier.padding(horizontal = 18.dp, vertical = 13.dp), color = FutureInk, fontSize = 15.sp, lineHeight = 23.sp)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 1.dp).size(38.dp).clip(CircleShape).background(Color.White.copy(.72f)).border(1.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) { FutureOrb(34.dp) }
            Spacer(Modifier.width(12.dp))
            Text(message.content, Modifier.weight(1f).padding(top = 5.dp, bottom = 8.dp), color = FutureInk.copy(alpha = appear), fontSize = 15.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
fun FutureComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onAttach: () -> Unit,
    enabled: Boolean,
    sending: Boolean
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 16.dp)
            .shadow(18.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x334F9EC7), spotColor = Color(0x224F9EC7))
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(Color(0xF7FFFFFF), Color(0xDDF0F9FF))))
            .border(1.dp, Color.White, RoundedCornerShape(32.dp))
            .padding(start = 9.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FutureIconButton("add", onAttach, 38.dp)
        BasicTextField(
            value = draft, onValueChange = onDraftChange,
            modifier = Modifier.weight(1f).padding(horizontal = 11.dp, vertical = 12.dp),
            textStyle = TextStyle(color = FutureInk, fontSize = 15.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(Color(0xFF48BCEB)),
            decorationBox = { inner -> if (draft.isEmpty()) Text("Message à BOZER…", color = FutureMuted, fontSize = 15.sp); inner() },
            singleLine = false
        )
        if (draft.isBlank()) FutureIconButton("mic", onVoice, 40.dp)
        else Box(Modifier.size(44.dp).shadow(8.dp, CircleShape).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF55C7F2), Color(0xFF6589E8))))
            .clickable(enabled = enabled && !sending, onClick = onSend), contentAlignment = Alignment.Center) {
            FutureIconButton("send", onSend, 42.dp)
        }
    }
}

@Composable
fun FutureWelcome(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        FutureOrb(148.dp)
        Spacer(Modifier.height(18.dp))
        Text("L’avenir commence ici.", color = FutureInk, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.4).sp)
        Spacer(Modifier.height(7.dp))
        Text("Une idée, une question, une envie ?\nJe suis prêt à avancer avec toi.", color = FutureMuted, fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(25.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FutureSuggestion("Organiser ma journée", "Aide-moi à organiser ma journée.", Modifier.weight(1f), onSuggestion)
            FutureSuggestion("Faire avancer un projet", "Aide-moi à faire avancer mon projet.", Modifier.weight(1f), onSuggestion)
        }
    }
}

@Composable
private fun FutureSuggestion(title: String, prompt: String, modifier: Modifier, onSuggestion: (String) -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(.58f))
        .border(1.dp, Color.White, RoundedCornerShape(20.dp)).clickable { onSuggestion(prompt) }.padding(14.dp)) {
        Text("✧", color = Color(0xFF5DBDE9), fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = FutureInk, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
    }
}
