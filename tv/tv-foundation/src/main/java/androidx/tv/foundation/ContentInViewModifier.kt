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

package androidx.tv.foundation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.Orientation.Horizontal
import androidx.compose.foundation.gestures.Orientation.Vertical
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.OnPlacedModifier
import androidx.compose.ui.layout.OnRemeasuredModifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A [Modifier] to be placed on a scrollable container (i.e. [Modifier.scrollableWithPivot]) that
 * animates the [ScrollableState] to handle [BringIntoViewRequester] requests and keep the
 * currently-focused child in view when the viewport shrinks.
 *
 * Instances of this class should not be directly added to the modifier chain, instead use the
 * [modifier] property since this class relies on some modifiers that must be specified as modifier
 * factory functions and can't be implemented as interfaces.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class ContentInViewModifier(
    private val scope: CoroutineScope,
    private val orientation: Orientation,
    private val scrollState: ScrollableState,
    private val reverseDirection: Boolean,
    private val pivotOffsets: PivotOffsets
) : BringIntoViewResponder,
    OnRemeasuredModifier,
    OnPlacedModifier {

    /**
     * Ongoing requests from [bringChildIntoView], with the invariant that it is always sorted by
     * overlapping order: each item's [Rect] completely overlaps the next item.
     *
     * May contain requests whose bounds are too big to fit in the current viewport. This is for
     * a few reasons:
     *  1. The viewport may shrink after a request was enqueued, causing a request that fit at the
     *     time it was enqueued to no longer fit.
     *  2. The size of the bounds of a request may change after it's added, causing it to grow
     *     larger than the viewport.
     *  3. Having complete information about too-big requests allows us to make the right decision
     *     about what part of the request to bring into view when smaller requests are also present.
     */
    private val bringIntoViewRequests = BringIntoViewRequestPriorityQueue()

    /** The [LayoutCoordinates] of this modifier (i.e. the scrollable container). */
    private var coordinates: LayoutCoordinates? = null
    private var focusedChild: LayoutCoordinates? = null

    /**
     * The previous bounds of the [focusedChild] used by [onRemeasured] to calculate when the
     * focused child is first clipped when scrolling is reversed.
     */
    private var focusedChildBoundsFromPreviousRemeasure: Rect? = null

    /**
     * Set to true when this class is actively animating the scroll to keep the focused child in
     * view.
     */
    private var trackingFocusedChild = false

    /** The size of the scrollable container. */
    private var viewportSize = IntSize.Zero
    private var isAnimationRunning = false
    private val animationState = UpdatableAnimationState()

    val modifier: Modifier = this
        .onFocusedBoundsChanged { focusedChild = it }
        .bringIntoViewResponder(this)

    override fun calculateRectForParent(localRect: Rect): Rect {
        check(viewportSize != IntSize.Zero) {
            "Expected BringIntoViewRequester to not be used before parents are placed."
        }
        // size will only be zero before the initial measurement.
        return computeDestination(localRect, viewportSize)
    }

    override suspend fun bringChildIntoView(localRect: () -> Rect?) {
        // Avoid creating no-op requests and no-op animations if the request does not require
        // scrolling or returns null.
        if (localRect()?.isMaxVisible() != false) return

        suspendCancellableCoroutine { continuation ->
            val request = Request(currentBounds = localRect, continuation = continuation)
            // Once the request is enqueued, even if it returns false, the queue will take care of
            // handling continuation cancellation so we don't need to do that here.
            if (bringIntoViewRequests.enqueue(request) && !isAnimationRunning) {
                launchAnimation()
            }
        }
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun onRemeasured(size: IntSize) {
        val oldSize = viewportSize
        viewportSize = size

        // Don't care if the viewport grew.
        if (size >= oldSize) return

        getFocusedChildBounds()?.let { focusedChild ->
            val previousFocusedChildBounds = focusedChildBoundsFromPreviousRemeasure ?: focusedChild
            if (!isAnimationRunning && !trackingFocusedChild &&
                // Resize caused it to go from being fully visible to at least partially
                // clipped. Need to use the lastFocusedChildBounds to compare with the old size
                // only to handle the case where scrolling direction is reversed: in that case, when
                // the child first goes out-of-bounds, it will be out of bounds regardless of which
                // size we pass in, so the only way to detect the change is to use the previous
                // bounds.
                previousFocusedChildBounds.isMaxVisible(oldSize) && !focusedChild.isMaxVisible(size)
            ) {
                trackingFocusedChild = true
                launchAnimation()
            }

            this.focusedChildBoundsFromPreviousRemeasure = focusedChild
        }
    }

    private fun getFocusedChildBounds(): Rect? {
        val coordinates = this.coordinates?.takeIf { it.isAttached } ?: return null
        val focusedChild = this.focusedChild?.takeIf { it.isAttached } ?: return null
        return coordinates.localBoundingBoxOf(focusedChild, clipBounds = false)
    }

    private fun launchAnimation() {
        check(!isAnimationRunning)

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var cancellationException: CancellationException? = null
            val animationJob = coroutineContext.job

            try {
                isAnimationRunning = true
                scrollState.scroll {
                    animationState.value = calculateScrollDelta()
                    animationState.animateToZero(
                        // This lambda will be invoked on every frame, during the choreographer
                        // callback.
                        beforeFrame = { delta ->
                            // reverseDirection is actually opposite of what's passed in through the
                            // (vertical|horizontal)Scroll modifiers.
                            val scrollMultiplier = if (reverseDirection) 1f else -1f
                            val adjustedDelta = scrollMultiplier * delta
                            val consumedScroll = scrollMultiplier * scrollBy(adjustedDelta)
                            if (abs(consumedScroll) < abs(delta)) {
                                // If the scroll state didn't consume all the scroll on this frame,
                                // it probably won't consume any more later either (we might have
                                // hit the scroll bounds). This is a terminal condition for the
                                // animation: If we don't cancel it, it could loop forever asking
                                // for a scroll that will never be consumed.
                                // Note this will cancel all pending BIV jobs.
                                // TODO(b/239671493) Should this trigger nested scrolling?
                                animationJob.cancel(
                                    "Scroll animation cancelled because scroll was not consumed " +
                                        "($consumedScroll < $delta)"
                                )
                            }
                        },
                        // This lambda will be invoked on every frame, but will be dispatched to run
                        // after the choreographer callback, and after any composition and layout
                        // passes for the frame. This means that the scroll performed in the above
                        // lambda will have been applied to the layout nodes.
                        afterFrame = {
                            // Complete any BIV requests that were satisfied by this scroll
                            // adjustment.
                            bringIntoViewRequests.resumeAndRemoveWhile { bounds ->
                                // If a request is no longer attached, remove it.
                                if (bounds == null) return@resumeAndRemoveWhile true
                                bounds.isMaxVisible()
                            }

                            // Stop tracking any KIV requests that were satisfied by this scroll
                            // adjustment.
                            if (trackingFocusedChild &&
                                getFocusedChildBounds()?.isMaxVisible() == true
                            ) {
                                trackingFocusedChild = false
                            }

                            // Compute a new scroll target taking into account any resizes,
                            // replacements, or added/removed requests since the last frame.
                            animationState.value = calculateScrollDelta()
                        }
                    )
                }

                // Complete any BIV requests if the animation didn't need to run, or if there were
                // requests that were too large to satisfy. Note that if the animation was
                // cancelled, this won't run, and the requests will be cancelled instead.
                bringIntoViewRequests.resumeAndRemoveAll()
            } catch (e: CancellationException) {
                cancellationException = e
                throw e
            } finally {
                isAnimationRunning = false
                // Any BIV requests that were not completed should be considered cancelled.
                bringIntoViewRequests.cancelAndRemoveAll(cancellationException)
                trackingFocusedChild = false
            }
        }
    }

    /**
     * Calculates how far we need to scroll to satisfy all existing BringIntoView requests and the
     * focused child tracking.
     */
    private fun calculateScrollDelta(): Float {
        if (viewportSize == IntSize.Zero) return 0f

        val rectangleToMakeVisible: Rect = findBringIntoViewRequest()
            ?: (if (trackingFocusedChild) getFocusedChildBounds() else null)
            ?: return 0f

        val size = viewportSize.toSize()
        return when (orientation) {
            Vertical -> relocationDistance(
                rectangleToMakeVisible.top,
                rectangleToMakeVisible.bottom,
                size.height
            )

            Horizontal -> relocationDistance(
                rectangleToMakeVisible.left,
                rectangleToMakeVisible.right,
                size.width
            )
        }
    }

    /**
     * Find the largest BIV request that can completely fit inside the viewport.
     */
    private fun findBringIntoViewRequest(): Rect? {
        var rectangleToMakeVisible: Rect? = null
        bringIntoViewRequests.forEachFromSmallest { bounds ->
            // Ignore detached requests for now. They'll be removed later.
            if (bounds == null) return@forEachFromSmallest
            if (bounds.size <= viewportSize.toSize()) {
                rectangleToMakeVisible = bounds
            } else {
                // Found a request that doesn't fit, use the next-smallest one.
                // TODO(klippenstein) if there is a request that's too big to fit in the current
                //  bounds, we should try to fit the largest part of it that contains the
                //  next-smallest request.
                return rectangleToMakeVisible ?: bounds
            }
        }
        return rectangleToMakeVisible
    }

    /**
     * Compute the destination given the source rectangle and current bounds.
     *
     * @param childBounds The bounding box of the item that sent the request to be brought into view.
     * @return the destination rectangle.
     */
    private fun computeDestination(childBounds: Rect, containerSize: IntSize): Rect {
        return childBounds.translate(-relocationOffset(childBounds, containerSize))
    }

    // According to a comment in LazyListState.onScroll()
    // "inside measuring we do scrollToBeConsumed.roundToInt() so there will be no scroll if
    // we have less than 0.5 pixels"
    private val scrollableThreshold = 0.5f
    /**
     * Returns true if this [Rect] is as visible as it can be given the [size] of the viewport.
     * This means either it's fully visible or too big to fit in the viewport all at once and
     * already filling the whole viewport.
     */
    private fun Rect.isMaxVisible(size: IntSize = viewportSize): Boolean {
        // According to a comment in LazyListState.onScroll()
        // "inside measuring we do scrollToBeConsumed.roundToInt() so there will be no scroll if
        // we have less than 0.5 pixels". So, if any of the offsets are less than 0.5, it will be
        // considered to be max-visible
        val relocationOffset = relocationOffset(this, size)
        return abs(relocationOffset.x) <= scrollableThreshold &&
            abs(relocationOffset.y) <= scrollableThreshold
    }

    private fun relocationOffset(childBounds: Rect, containerSize: IntSize): Offset {
        val size = containerSize.toSize()
        return when (orientation) {
            Vertical -> Offset(
                x = 0f,
                y = relocationDistance(
                    childBounds.top,
                    childBounds.bottom,
                    size.height
                )
            )

            Horizontal -> Offset(
                x = relocationDistance(
                    childBounds.left,
                    childBounds.right,
                    size.width
                ),
                y = 0f
            )
        }
    }

    /**
     * Calculate the offset needed to bring one of the edges into view. The leadingEdge is the side
     * closest to the origin (For the x-axis this is 'left', for the y-axis this is 'top').
     * The trailing edge is the other side (For the x-axis this is 'right', for the y-axis this is
     * 'bottom').
     */
    private fun relocationDistance(
        leadingEdgeOfItemRequestingFocus: Float,
        trailingEdgeOfItemRequestingFocus: Float,
        containerSize: Float
    ): Float {
        val sizeOfItemRequestingFocus =
            abs(trailingEdgeOfItemRequestingFocus - leadingEdgeOfItemRequestingFocus)
        val childSmallerThanParent = sizeOfItemRequestingFocus <= containerSize
        val initialTargetForLeadingEdge =
            pivotOffsets.parentFraction * containerSize -
                (pivotOffsets.childFraction * sizeOfItemRequestingFocus)
        val spaceAvailableToShowItem = containerSize - initialTargetForLeadingEdge

        val targetForLeadingEdge =
            if (childSmallerThanParent && spaceAvailableToShowItem < sizeOfItemRequestingFocus) {
                containerSize - sizeOfItemRequestingFocus
            } else {
                initialTargetForLeadingEdge
            }

        return leadingEdgeOfItemRequestingFocus - targetForLeadingEdge
    }

    private operator fun IntSize.compareTo(other: IntSize): Int = when (orientation) {
        Horizontal -> width.compareTo(other.width)
        Vertical -> height.compareTo(other.height)
    }

    private operator fun Size.compareTo(other: Size): Int = when (orientation) {
        Horizontal -> width.compareTo(other.width)
        Vertical -> height.compareTo(other.height)
    }

    /**
     * A request to bring some [Rect] in the scrollable viewport.
     *
     * @param currentBounds A function that returns the current bounds that the request wants to
     * make visible.
     * @param continuation The [CancellableContinuation] from the suspend function used to make the
     * request.
     */
    internal class Request(
        val currentBounds: () -> Rect?,
        val continuation: CancellableContinuation<Unit>,
    )
}