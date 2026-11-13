package com.cardrop.navi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardrop.core.model.Place
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.theme.CdColors
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Owner: lead. Room to put under a suggestion list, so its last lines can scroll up above Pleos's
 * on-screen keyboard. The keyboard covers about the lower third of the screen and doesn't shrink
 * our window. Sized from the emulator's keyboard, in the screens' units.
 */
val KeyboardRoom = 320.dp

/**
 * The places that fit what the driver is typing (boards 02 and 10): "‘경포’ 검색 결과", then one
 * white card of lines. Put it in a scrolling column. [inList] marks places already chosen
 * ("후보에 있어요"); [bordered] adds a grey edge for white pop-ups.
 */
@Composable
fun PlaceSuggestionList(
    suggestions: PlaceSuggestions,
    onPick: (PlaceSuggestion) -> Unit,
    bordered: Boolean = false,
    inList: (Place) -> Boolean = { false },
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("‘${suggestions.query}’ 검색 결과", color = CdColors.Gray600, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (suggestions.searching) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
    }
    suggestions.note?.let { Text(it, color = CdColors.OrangeDark, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    if (suggestions.places.isEmpty()) {
        if (!suggestions.searching) {
            Text("‘${suggestions.query}’에 맞는 장소가 없어요", color = CdColors.Ink2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        return
    }
    PlaceCard(suggestions.places, suggestions.query, onPick, bordered, inList)
}

/**
 * Places as one white card of lines (design board 02: pin, name, address, distance). [highlight]
 * is what the driver typed: those letters show in blue, as Kakao Map marks what matched.
 */
@Composable
fun PlaceCard(
    places: List<PlaceSuggestion>,
    highlight: String,
    onPick: (PlaceSuggestion) -> Unit,
    bordered: Boolean = false,
    inList: (Place) -> Boolean = { false },
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CdColors.White)
            .then(if (bordered) Modifier.border(2.dp, CdColors.Gray100, shape) else Modifier)
            .padding(vertical = 6.dp),
    ) {
        places.forEachIndexed { index, place ->
            if (index > 0) {
                Spacer(Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(1.dp).background(CdColors.Gray100))
            }
            PlaceLine(place, highlight, inList(place.place), onClick = { onPick(place) })
        }
    }
}

/** One line: pin, name (typed letters in blue), address · category, distance from the car. */
@Composable
private fun PlaceLine(suggestion: PlaceSuggestion, highlight: String, inList: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(CdColors.BlueLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CdIcons.Pin, contentDescription = null, tint = CdColors.BlueDark, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                highlighted(suggestion.place.name, highlight),
                color = CdColors.Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Address first, as on board 02: "강원 강릉시 강문동 · 해수욕장".
            val meta = listOf(suggestion.address, suggestion.category).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, color = CdColors.Gray600, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (inList) Text("후보에 있어요", color = CdColors.BlueDark, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        suggestion.distanceKm?.let {
            Text(distanceText(it), color = CdColors.Gray600, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** [name] with each typed word in blue. */
private fun highlighted(name: String, typed: String): AnnotatedString = buildAnnotatedString {
    append(name)
    typed.split(' ').filter { it.isNotBlank() }.forEach { word ->
        var from = 0
        while (true) {
            val at = name.indexOf(word, from, ignoreCase = true)
            if (at < 0) break
            addStyle(SpanStyle(color = CdColors.Blue), at, at + word.length)
            from = at + word.length
        }
    }
}

/** "850m", "3.2km", "230km". */
private fun distanceText(km: Double): String = when {
    km < 1 -> "${(km * 1000).roundToInt()}m"
    km < 10 -> String.format(Locale.US, "%.1fkm", km)
    else -> "${km.roundToInt()}km"
}
