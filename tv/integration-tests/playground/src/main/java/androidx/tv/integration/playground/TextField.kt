/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.tv.integration.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TextFieldContent() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(4) { SampleCardItem() }
            }
        }
        item {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item { SampleTextField(label = "Email") }
                item { SampleTextField(label = "Password") }
                item { SampleButton(text = "Submit") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(4) { SampleCardItem() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleTextField(label: String) {
    var text by remember { mutableStateOf("") }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = {
            Text(label)
        },
        singleLine = true,
        placeholder = {
            Text("$label...")
        },
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = Color.Cyan,
            focusedLabelColor = Color.Cyan,
            cursorColor = Color.White
        )
    )
}

@Composable
fun SampleButton(text: String) {
    Button(
        onClick = { }
    ) {
        Text(text)
    }
}

@Composable
private fun SampleCardItem() {
    Box(
        modifier = Modifier
            .background(Color.Magenta.copy(alpha = 0.3f))
            .width(50.dp)
            .height(50.dp)
            .drawBorderOnFocus()
            .focusable()
    )
}