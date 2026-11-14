package com.cardrop.drop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardrop.core.model.Car
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropTopic
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.theme.CdColors
import kotlinx.coroutines.delay

/** How long another car's 한마디 stays at the top before it goes by itself. */
const val SOCIAL_BANNER_MILLIS = 8_000L

/** A voice message stays longer: time to tap ▶ and hear it (at most 20 s) before it goes. */
const val VOICE_BANNER_MILLIS = 40_000L

/**
 * Owner: Member A (2026-11-15). Another car's SOCIAL drop (a 한마디, "…에 도착했어요"; not a song, which has board 13)
 * as a banner at the top of the CarDrop side: who sent it and what it says, with 확인. It goes by itself after
 * [SOCIAL_BANNER_MILLIS] or on 확인 ([onDismiss]); a newer drop replaces it and starts the time again. Never spoken,
 * like every social drop. The design has no receiving screen for 한마디; it used to be a small black caption.
 */
@Composable
fun SocialBanner(drop: Drop?, sender: Car?, onDismiss: () -> Unit) {
    LaunchedEffect(drop?.id) {
        if (drop != null) {
            delay(if (drop.topic == DropTopic.VOICE) VOICE_BANNER_MILLIS else SOCIAL_BANNER_MILLIS)
            onDismiss()
        }
    }
    Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = drop != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            if (drop != null) BannerCard(drop, sender, onDismiss)
        }
    }
}

@Composable
private fun BannerCard(drop: Drop, sender: Car?, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .width(640.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(CdColors.White)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (sender != null) CarBadge(sender, size = 48, bob = false)
        val voice = drop.topic == DropTopic.VOICE
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${if (voice) "음성 메시지" else "소셜 드랍"} · ${sender?.name ?: "일행"}",
                color = CdColors.VioletDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                if (voice) drop.title else "“${drop.title}”",
                color = CdColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // A voice message (Member A, 2026-11-15): ▶ 재생 plays it; never by itself.
        if (voice) {
            val context = LocalContext.current
            SecondaryButton("▶ 재생", onClick = { VoicePlayer.play(context, drop) }, height = 48.dp, radius = 14.dp, fontSize = 15.sp, horizontalPadding = 18.dp)
        }
        PrimaryButton("확인", onClick = { VoicePlayer.stop(); onDismiss() }, containerColor = CdColors.Violet, height = 48.dp, radius = 14.dp, fontSize = 15.sp, horizontalPadding = 22.dp)
    }
}
