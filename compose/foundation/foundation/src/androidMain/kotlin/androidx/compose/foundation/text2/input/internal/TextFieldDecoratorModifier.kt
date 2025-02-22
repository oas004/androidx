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

package androidx.compose.foundation.text2.input.internal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text2.input.TextEditFilter
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.deselect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.imeAction
import androidx.compose.ui.semantics.insertTextAtCursor
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.setSelection
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAny

/**
 * Modifier element for most of the functionality of [BasicTextField2] that is attached to the
 * decoration box. This is only half the actual modifiers for the field, the other half are only
 * attached to the internal text field.
 *
 * This modifier handles input events (both key and pointer), semantics, and focus.
 */
@OptIn(ExperimentalFoundationApi::class)
internal data class TextFieldDecoratorModifier(
    private val textFieldState: TextFieldState,
    private val textLayoutState: TextLayoutState,
    private val textInputAdapter: AndroidTextInputAdapter?,
    private val filter: TextEditFilter?,
    private val enabled: Boolean,
    private val readOnly: Boolean,
    private val keyboardOptions: KeyboardOptions,
    private val singleLine: Boolean
) : ModifierNodeElement<TextFieldDecoratorModifierNode>() {
    override fun create(): TextFieldDecoratorModifierNode = TextFieldDecoratorModifierNode(
        textFieldState = textFieldState,
        textLayoutState = textLayoutState,
        textInputAdapter = textInputAdapter,
        filter = filter,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine
    )

    override fun update(node: TextFieldDecoratorModifierNode): TextFieldDecoratorModifierNode {
        node.updateNode(
            textFieldState = textFieldState,
            textLayoutState = textLayoutState,
            textInputAdapter = textInputAdapter,
            filter = filter,
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            singleLine = singleLine
        )
        return node
    }

    override fun InspectorInfo.inspectableProperties() {
        // Show nothing in the inspector.
    }
}

/** Modifier node for [TextFieldDecoratorModifier]. */
@OptIn(ExperimentalFoundationApi::class)
internal class TextFieldDecoratorModifierNode(
    var textFieldState: TextFieldState,
    var textLayoutState: TextLayoutState,
    var textInputAdapter: AndroidTextInputAdapter?,
    var filter: TextEditFilter?,
    var enabled: Boolean,
    var readOnly: Boolean,
    var keyboardOptions: KeyboardOptions,
    var singleLine: Boolean
) : Modifier.Node(),
    SemanticsModifierNode,
    FocusRequesterModifierNode,
    FocusEventModifierNode,
    GlobalPositionAwareModifierNode,
    PointerInputModifierNode,
    KeyInputModifierNode {

    // semantics properties that require semantics invalidation
    private var lastText: AnnotatedString? = null
    private var lastSelection: TextRange? = null
    private var lastEnabled: Boolean = enabled

    private var isFocused: Boolean = false
    private var semanticsConfigurationCache: SemanticsConfiguration? = null
    private var textInputSession: TextInputSession? = null

    /**
     * Manages key events. These events often are sourced by a hardware keyboard but it's also
     * possible that IME or some other platform system simulates a KeyEvent.
     */
    private val textFieldKeyEventHandler = TextFieldKeyEventHandler().also {
        it.setFilter(filter)
    }

    /**
     * Updates all the related properties and invalidates internal state based on the changes.
     */
    fun updateNode(
        textFieldState: TextFieldState,
        textLayoutState: TextLayoutState,
        textInputAdapter: AndroidTextInputAdapter?,
        filter: TextEditFilter?,
        enabled: Boolean,
        readOnly: Boolean,
        keyboardOptions: KeyboardOptions,
        singleLine: Boolean
    ) {
        // Find the diff: current previous and new values before updating current.
        val previousWriteable = this.enabled && !this.readOnly
        val writeable = enabled && !readOnly
        val previousTextFieldState = this.textFieldState

        // Apply the diff.
        this.textFieldState = textFieldState
        this.textLayoutState = textLayoutState
        this.textInputAdapter = textInputAdapter
        this.filter = filter
        this.enabled = enabled
        this.readOnly = readOnly
        this.keyboardOptions = keyboardOptions
        this.singleLine = singleLine

        // React to diff.
        // If made writable while focused, or we got a completely new state instance,
        // start a new input session.
        if (previousWriteable != writeable || textFieldState != previousTextFieldState) {
            if (writeable && isFocused) {
                // The old session will be implicitly disposed.
                textInputSession = textInputAdapter?.startInputSession(
                    textFieldState,
                    keyboardOptions.toImeOptions(singleLine),
                    filter,
                )
            } else if (!writeable) {
                // We were made read-only or disabled, hide the keyboard.
                disposeInputSession()
            }
        }
        textInputSession?.setFilter(filter)
        textFieldKeyEventHandler.setFilter(filter)
    }

    /**
     * The current semantics for this node. The first time this is read after an update a new
     * configuration is created by calling [generateSemantics] and then cached.
     */
    override val semanticsConfiguration: SemanticsConfiguration
        get() {
            var localSemantics = semanticsConfigurationCache
            val value = textFieldState.value
            // Cache invalidation is done here instead of only in updateNode because the text or
            // selection might change without triggering a modifier update.
            if (localSemantics == null ||
                lastText != value.annotatedString ||
                lastSelection != value.selection ||
                lastEnabled != enabled
            ) {
                localSemantics = generateSemantics(value.annotatedString, value.selection)
            }
            return localSemantics
        }

    override fun onFocusEvent(focusState: FocusState) {
        if (isFocused == focusState.isFocused) {
            return
        }
        isFocused = focusState.isFocused

        if (focusState.isFocused) {
            textInputSession = textInputAdapter?.startInputSession(
                textFieldState,
                keyboardOptions.toImeOptions(singleLine),
                filter,
            )
            // TODO(halilibo): bringIntoView
        } else {
            textFieldState.deselect()
        }
    }

    override fun onDetach() {
        if (isFocused) {
            disposeInputSession()
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        textLayoutState.proxy?.decorationBoxCoordinates = coordinates
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) {
        if (pass == PointerEventPass.Main && pointerEvent.changes.fastAny { it.changedToDown() }) {
            requestFocus()
        }
    }

    override fun onCancelPointerInput() {
        // Nothing to do yet, since onPointerEvent isn't handling any gestures.
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        // TextField does not handle pre key events.
        return false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return textFieldKeyEventHandler.onKeyEvent(
            event = event,
            state = textFieldState,
            textLayoutState = textLayoutState,
            editable = enabled && !readOnly,
            singleLine = singleLine,
            onSubmit = {
                // TODO(halilibo): finalize the wiring when KeyboardActions or equivalent is added.
            }
        )
    }

    private fun generateSemantics(
        text: AnnotatedString,
        selection: TextRange
    ): SemanticsConfiguration {
        lastText = text
        lastSelection = selection
        lastEnabled = enabled
        return SemanticsConfiguration().apply {
            this.isMergingSemanticsOfDescendants = true
            getTextLayoutResult {
                textLayoutState.layoutResult?.let { result -> it.add(result) } ?: false
            }
            editableText = text
            textSelectionRange = selection
            imeAction = keyboardOptions.imeAction
            if (!enabled) disabled()

            setText { text ->
                textFieldState.editProcessor.update(
                    listOf(
                        DeleteAllCommand,
                        CommitTextCommand(text, 1)
                    ),
                    filter
                )
                true
            }
            setSelection { start, end, _ ->
                // BasicTextField2 doesn't have VisualTransformation for the time being and
                // probably won't have something that uses offsetMapping design. We can safely
                // skip relativeToOriginalText flag. Assume it's always true.

                if (!enabled) {
                    false
                } else if (start == selection.start && end == selection.end) {
                    false
                } else if (start.coerceAtMost(end) >= 0 &&
                    start.coerceAtLeast(end) <= text.length
                ) {
                    // reset is required to make sure IME gets the update.
                    textFieldState.editProcessor.reset(
                        TextFieldValue(
                            annotatedString = text,
                            selection = TextRange(start, end)
                        )
                    )
                    true
                } else {
                    false
                }
            }
            insertTextAtCursor { text ->
                textFieldState.editProcessor.update(
                    listOf(
                        // Finish composing text first because when the field is focused the IME
                        // might set composition.
                        FinishComposingTextCommand,
                        CommitTextCommand(text, 1)
                    ),
                    filter
                )
                true
            }
            onClick {
                // according to the documentation, we still need to provide proper semantics actions
                // even if the state is 'disabled'
                if (!isFocused) {
                    requestFocus()
                }
                true
            }
            semanticsConfigurationCache = this
        }
    }

    private fun disposeInputSession() {
        textInputSession?.dispose()
        textInputSession = null
    }
}