package com.cardrop.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Place
import com.cardrop.core.ui.BackButton
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.StatusText
import com.cardrop.core.ui.softShadow
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.NotoSansKr
import com.cardrop.navi.KeyboardRoom
import com.cardrop.navi.PlaceCard
import com.cardrop.navi.PlaceSuggestionList

/**
 * Board 02 목적지 검색 · Owner: Member A (place search: lead).
 *
 * Like Kakao Map's search: typing shows the places that fit (Kakao's place search, nearby first);
 * tapping one, or enter for the top one, makes it the crew's destination. "말로 검색" puts what the
 * driver says into the box and searches it. Before typing, the app's destinations. Without a Kakao
 * key (or offline) only those can be found.
 */
@Composable
fun DestinationSearchScreen(vm: CrewViewModel, onBack: () -> Unit, onPicked: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val suggestions by vm.destinationSuggestions.collectAsStateWithLifecycle()
    val voice by vm.destinationVoice.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        vm.openDestinationSearch()
        if (query.isNotEmpty()) vm.onDestinationTyped(query) // back after a restart: search again
        focus.requestFocus() // this screen is for typing: the box starts focused, the keyboard comes up
    }
    DisposableEffect(Unit) { onDispose { vm.stopListening() } } // leaving the screen stops the mic
    val pick: (Place) -> Unit = {
        vm.pickDestination(it)
        onPicked()
    }

    // The title and box stay put; only the places scroll. Pleos's keyboard covers the lower third.
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClick = onBack)
            Text(
                "목적지를 검색해 주세요",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
                color = CdColors.Ink,
            )
        }
        SearchBox(
            query = query,
            onQueryChange = {
                query = it
                vm.onDestinationTyped(it)
            },
            focus = focus,
            onDone = { vm.pickTypedDestination(query, onPicked) },
            onClear = {
                query = ""
                vm.onDestinationTyped("")
            },
            listening = voice.listening,
            onListen = if (vm.canListen) {
                {
                    focusManager.clearFocus() // the keyboard goes away, so the results show
                    vm.listenForDestination { heard -> query = heard }
                }
            } else {
                null
            },
        )
        voice.status?.let { StatusText(it) }
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (suggestions.shown) {
                PlaceSuggestionList(suggestions, onPick = { pick(it.place) })
            } else {
                Text("추천 목적지", color = CdColors.Gray600, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                PlaceCard(vm.knownDestinations(), highlight = "", onPick = { pick(it.place) })
            }
            // Room to scroll the last places up above the keyboard.
            Spacer(Modifier.height(KeyboardRoom))
        }
    }
}

/**
 * Board 02's search box (design lines 155-160): a blue magnifier, the typed text in big letters, the ✕
 * that empties the box (an app extra) and 말로 검색 inside the box. Its edge stays blue, with a soft blue
 * glow under it (`box-shadow: 0 8px 20px rgba(49,130,246,0.15)`): this screen is for typing, so the box
 * is what to look at. Without a voice service ([onListen] = null) there is no 말로 검색.
 */
@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    focus: FocusRequester,
    onDone: () -> Unit,
    onClear: () -> Unit,
    listening: Boolean,
    onListen: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .softShadow(shape, CdColors.Blue.copy(alpha = 0.15f), blur = 20.dp, y = 8.dp)
            .clip(shape)
            .background(CdColors.White)
            .border(2.dp, CdColors.Blue, shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(CdIcons.Search, contentDescription = null, tint = CdColors.Blue, modifier = Modifier.size(26.dp))
        BasicTextField(
            value = query,
            onValueChange = { onQueryChange(it.take(PLACE_NAME_MAX_LENGTH)) },
            // As tall as the box, so a tap anywhere between the magnifier and the buttons starts typing.
            modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(focus),
            singleLine = true,
            textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink, fontFamily = NotoSansKr),
            cursorBrush = SolidColor(CdColors.Blue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onDone()
                defaultKeyboardAction(ImeAction.Done) // hides the keyboard
            }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "장소 · 주소 검색 (예: 강릉 경포해변)",
                            color = CdColors.Gray450,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            // The same round ✕ as CdTextField's.
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(CdColors.Gray200)
                    .clickable(role = Role.Button, onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CdIcons.Close, contentDescription = "지우기", tint = CdColors.Gray600, modifier = Modifier.size(14.dp))
            }
        }
        if (onListen != null) {
            CdChip(
                if (listening) "듣는 중…" else "말로 검색",
                onClick = onListen,
                style = ChipStyle.Blue,
                enabled = !listening,
                height = 48.dp,
                fontSize = 15.sp,
            )
        }
    }
}

/** Place names longer than this are cut off (the design's limit for place names). */
private const val PLACE_NAME_MAX_LENGTH = 48
