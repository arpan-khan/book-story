/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.book_info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.R
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.presentation.book_info.BookInfoEvent
import ua.acclorite.book_story.ui.common.components.common.LazyColumnWithScrollbar
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.components.modal_bottom_sheet.ModalBottomSheet
import ua.acclorite.book_story.ui.settings.components.SettingsSubcategoryTitle

@Composable
fun BookInfoEditMetadataBottomSheet(
    book: Book,
    onSave: (BookInfoEvent.OnSaveMetadata) -> Unit,
    showChangeCoverBottomSheet: (BookInfoEvent.OnShowChangeCoverBottomSheet) -> Unit,
    dismissBottomSheet: (BookInfoEvent.OnDismissBottomSheet) -> Unit
) {
    val initialAuthor = book.author.asString()
    val title = remember { mutableStateOf(book.title) }
    val subtitle = remember { mutableStateOf(book.subtitle ?: "") }
    val author = remember { mutableStateOf(initialAuthor) }
    val description = remember { mutableStateOf(book.description ?: "") }

    ModalBottomSheet(
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = {
            dismissBottomSheet(BookInfoEvent.OnDismissBottomSheet)
        },
        sheetGesturesEnabled = true
    ) {
        LazyColumnWithScrollbar(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                SettingsSubcategoryTitle(
                    title = stringResource(id = R.string.edit),
                    padding = 16.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = title.value,
                        onValueChange = { title.value = it },
                        label = { StyledText(stringResource(R.string.title)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = subtitle.value,
                        onValueChange = { subtitle.value = it },
                        label = { StyledText(stringResource(R.string.subtitle)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = author.value,
                        onValueChange = { author.value = it },
                        label = { StyledText(stringResource(R.string.author)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = description.value,
                        onValueChange = { description.value = it },
                        label = { StyledText(stringResource(R.string.description)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Spacer(Modifier.height(12.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        onClick = {
                            showChangeCoverBottomSheet(BookInfoEvent.OnShowChangeCoverBottomSheet)
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        StyledText(stringResource(R.string.change_cover))
                    }
                    
                    Spacer(Modifier.height(12.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        onClick = {
                            onSave(
                                BookInfoEvent.OnSaveMetadata(
                                    title = title.value,
                                    subtitle = subtitle.value.ifBlank { null },
                                    author = author.value,
                                    description = description.value.ifBlank { null }
                                )
                            )
                            dismissBottomSheet(BookInfoEvent.OnDismissBottomSheet)
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        StyledText(stringResource(R.string.done))
                    }
                }
            }
        }
    }
}
