/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.ui.note.LoadCityName
import com.vitorpamplona.amethyst.ui.screen.CodeName
import com.vitorpamplona.amethyst.ui.screen.CommunityName
import com.vitorpamplona.amethyst.ui.screen.GeoHashName
import com.vitorpamplona.amethyst.ui.screen.HashtagName
import com.vitorpamplona.amethyst.ui.screen.Name
import com.vitorpamplona.amethyst.ui.screen.PeopleListName
import com.vitorpamplona.amethyst.ui.screen.ResourceName
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SpinnerSelectionDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.PeopleListEvent
import kotlinx.collections.immutable.ImmutableList

@Composable
fun FeedFilterSpinner(
    placeholderCode: String,
    explainer: String,
    options: ImmutableList<CodeName>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var optionsShowing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val selectAnOption =
        stringRes(
            id = R.string.select_an_option,
        )

    var currentText by
        remember(placeholderCode, options) {
            mutableStateOf(
                options.firstOrNull { it.code == placeholderCode }?.name?.name(context) ?: selectAnOption,
            )
        }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Size20Modifier)
            Text(currentText)
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = explainer,
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        optionsShowing = true
                    },
        )
    }

    if (optionsShowing) {
        options.isNotEmpty().also {
            SpinnerSelectionDialog(
                options = options,
                onDismiss = { optionsShowing = false },
                onSelect = {
                    currentText = options[it].name.name(context)
                    optionsShowing = false
                    onSelect(it)
                },
            ) {
                RenderOption(it.name)
            }
        }
    }
}

@Composable
fun RenderOption(option: Name) {
    when (option) {
        is GeoHashName -> {
            LoadCityName(option.geoHashTag) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "/g/$it", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        is HashtagName -> {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = option.name(), color = MaterialTheme.colorScheme.onSurface)
            }
        }
        is ResourceName -> {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringRes(id = option.resourceId),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        is PeopleListName -> {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val noteState by
                    option.note
                        .live()
                        .metadata
                        .observeAsState()

                val name = (noteState?.note?.event as? PeopleListEvent)?.nameOrTitle() ?: option.note.dTag() ?: ""

                Text(text = name, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        is CommunityName -> {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val name by
                    option.note
                        .live()
                        .metadata
                        .map { "/n/" + ((it.note as? AddressableNote)?.dTag() ?: "") }
                        .observeAsState()

                Text(text = name ?: "", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}