package org.polisan.crescendo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@ExperimentalMaterial3Api
fun SelectionBottomSheet(
    elements: List<Pair<String, String>>,
    onSelected: (Int) -> Unit,
    onDismissClick: () -> Unit,
    state: SheetState,
    chosenIndex: Int,
    selectText: String
) {
    var selectedElement by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedIndex by remember { mutableStateOf(chosenIndex) }

    ModalBottomSheet(
        sheetState = state,
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = selectText,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Column {
                    elements.forEachIndexed { index, (playerName, _) ->
                        val sinkID: Int = try {
                            elements[index].second.toInt()
                        } catch (ex: NumberFormatException) {
                            -1 //means indexing players, not devices
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .selectable(
                                    index == selectedIndex || selectedIndex == sinkID
                                ) {
                                    selectedElement = elements[index]
                                    selectedIndex = index
                                }
                        ) {
                            RadioButton(
                                selected = index == selectedIndex || selectedIndex == sinkID,
                                onClick = {
                                    selectedElement = elements[index]
                                    selectedIndex = index
                                },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            Text(
                                text = playerName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .align(Alignment.CenterVertically)
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (selectText == "Choose Player")
                                selectedElement?.let { onSelected(selectedIndex) }
                            else selectedElement?.let { onSelected(elements[selectedIndex].second.toInt()) }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(text = "Confirm")
                    }
                }
            }
        },
        onDismissRequest = {
            onDismissClick()
        },
    )
}