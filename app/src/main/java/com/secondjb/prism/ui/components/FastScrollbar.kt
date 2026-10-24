package com.secondjb.prism.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Unified scroll state abstraction supporting both [LazyListState] and [LazyGridState].
 */
interface FastScrollableState {
    val isScrollInProgress: Boolean
    val firstVisibleItemIndex: Int
    val firstVisibleItemScrollOffset: Int
    val totalItemsCount: Int
    val visibleItemsCount: Int
    val firstItemSize: Int
    suspend fun scrollToItem(index: Int)
}

/**
 * Converts a [LazyListState] into a [FastScrollableState].
 */
@Composable
fun rememberFastScrollableState(lazyListState: LazyListState): FastScrollableState {
    return remember(lazyListState) {
        object : FastScrollableState {
            override val isScrollInProgress: Boolean
                get() = lazyListState.isScrollInProgress

            override val firstVisibleItemIndex: Int
                get() = lazyListState.firstVisibleItemIndex

            override val firstVisibleItemScrollOffset: Int
                get() = lazyListState.firstVisibleItemScrollOffset

            override val totalItemsCount: Int
                get() = lazyListState.layoutInfo.totalItemsCount

            override val visibleItemsCount: Int
                get() = lazyListState.layoutInfo.visibleItemsInfo.size

            override val firstItemSize: Int
                get() = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0

            override suspend fun scrollToItem(index: Int) {
                lazyListState.scrollToItem(index)
            }
        }
    }
}

/**
 * Converts a [LazyGridState] into a [FastScrollableState].
 */
@Composable
fun rememberFastScrollableState(lazyGridState: LazyGridState): FastScrollableState {
    return remember(lazyGridState) {
        object : FastScrollableState {
            override val isScrollInProgress: Boolean
                get() = lazyGridState.isScrollInProgress

            override val firstVisibleItemIndex: Int
                get() = lazyGridState.firstVisibleItemIndex

            override val firstVisibleItemScrollOffset: Int
                get() = lazyGridState.firstVisibleItemScrollOffset

            override val totalItemsCount: Int
                get() = lazyGridState.layoutInfo.totalItemsCount

            override val visibleItemsCount: Int
                get() = lazyGridState.layoutInfo.visibleItemsInfo.size

            override val firstItemSize: Int
                get() = lazyGridState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0

            override suspend fun scrollToItem(index: Int) {
                lazyGridState.scrollToItem(index)
            }
        }
    }
}

/**
 * Extracts a clean single section character/label for fast scroller indicators.
 * Skips leading non-alphanumeric punctuation; numbers group into '#'.
 */
fun getSectionHeader(text: String?): String {
    if (text.isNullOrBlank()) return "#"
    val trimmed = text.trim()
    val firstAlphaNumeric = trimmed.firstOrNull { it.isLetterOrDigit() } ?: return "#"
    return if (firstAlphaNumeric.isLetter()) firstAlphaNumeric.uppercaseChar().toString() else "#"
}

/**
 * Maps scroll position to a 0f..1f progress ratio.
 */
fun calculateScrollProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstItemSize: Int,
    totalItemsCount: Int,
    visibleItemsCount: Int
): Float {
    if (totalItemsCount <= 1 || visibleItemsCount <= 0) return 0f
    val maxScrollIndex = (totalItemsCount - visibleItemsCount).coerceAtLeast(1)
    val itemFraction = if (firstItemSize > 0) {
        (firstVisibleItemScrollOffset.toFloat() / firstItemSize).coerceIn(0f, 1f)
    } else {
        0f
    }
    return ((firstVisibleItemIndex + itemFraction) / maxScrollIndex).coerceIn(0f, 1f)
}

/**
 * Computes target item index from a drag progress fraction.
 */
fun calculateTargetIndex(fraction: Float, totalItems: Int): Int {
    if (totalItems <= 1) return 0
    return (fraction.coerceIn(0f, 1f) * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
}

/**
 * A themed fast-scroller scrollbar for Jetpack Compose lists and grids.
 *
 * - Becomes visible when scrolling starts or when dragging.
 * - Draggable pill handle to rapidly navigate large libraries.
 * - Shows an optional dynamic section indicator bubble (e.g. current letter).
 * - Disappears smoothly after 3 seconds of inactivity.
 */
@Composable
fun FastScrollbar(
    scrollableState: FastScrollableState,
    modifier: Modifier = Modifier,
    itemCount: Int = scrollableState.totalItemsCount,
    sectionLabel: ((Int) -> String?)? = null,
    inactivityTimeoutMs: Long = 3000L
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    // Visibility & inactivity timer
    LaunchedEffect(scrollableState.isScrollInProgress, isDragging) {
        if (scrollableState.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(inactivityTimeoutMs)
            isVisible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "FastScrollbarAlpha"
    )

    // Skip layout when completely hidden
    if (alpha <= 0.001f && !isVisible) {
        return
    }

    val density = LocalDensity.current
    val thumbHeightDp = 52.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }

    val totalItems = itemCount.coerceAtLeast(1)
    val visibleItems = scrollableState.visibleItemsCount.coerceAtLeast(1)

    // If all items fit without scrolling, do not show
    if (scrollableState.visibleItemsCount > 0 && totalItems <= visibleItems) {
        return
    }

    val smoothProgress = remember(
        scrollableState.firstVisibleItemIndex,
        scrollableState.firstVisibleItemScrollOffset,
        scrollableState.firstItemSize,
        totalItems,
        visibleItems
    ) {
        calculateScrollProgress(
            firstVisibleItemIndex = scrollableState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = scrollableState.firstVisibleItemScrollOffset,
            firstItemSize = scrollableState.firstItemSize,
            totalItemsCount = totalItems,
            visibleItemsCount = visibleItems
        )
    }

    val currentSmoothProgress by rememberUpdatedState(smoothProgress)
    val currentScrollableState by rememberUpdatedState(scrollableState)
    val currentTotalItems by rememberUpdatedState(totalItems)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(36.dp)
            .alpha(alpha)
    ) {
        val containerHeightPx = constraints.maxHeight.toFloat()
        val maxThumbOffset = (containerHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val currentMaxOffset by rememberUpdatedState(maxThumbOffset)

        val currentThumbOffset = if (isDragging) {
            dragOffsetPx.coerceIn(0f, maxThumbOffset)
        } else {
            (smoothProgress * maxThumbOffset).coerceIn(0f, maxThumbOffset)
        }

        val touchModifier = if (isVisible || alpha > 0.01f) {
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true

                    val maxOffset = currentMaxOffset
                    val totalCount = currentTotalItems
                    val initialOffset = currentSmoothProgress * maxOffset
                    val thumbTop = initialOffset
                    val thumbBottom = initialOffset + thumbHeightPx

                    var currentY = if (down.position.y in (thumbTop - 16f)..(thumbBottom + 16f)) {
                        initialOffset
                    } else {
                        (down.position.y - thumbHeightPx / 2f).coerceIn(0f, maxOffset)
                    }
                    dragOffsetPx = currentY
                    val initialFraction = if (maxOffset > 0f) currentY / maxOffset else 0f
                    val initialTargetIndex = calculateTargetIndex(initialFraction, totalCount)
                    scrollJob?.cancel()
                    scrollJob = coroutineScope.launch {
                        currentScrollableState.scrollToItem(initialTargetIndex)
                    }
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            break
                        }
                        val delta = change.positionChange().y
                        if (delta != 0f) {
                            currentY = (currentY + delta).coerceIn(0f, currentMaxOffset)
                            dragOffsetPx = currentY
                            val fraction = if (currentMaxOffset > 0f) currentY / currentMaxOffset else 0f
                            val targetIndex = calculateTargetIndex(fraction, currentTotalItems)
                            scrollJob?.cancel()
                            scrollJob = coroutineScope.launch {
                                currentScrollableState.scrollToItem(targetIndex)
                            }
                            change.consume()
                        }
                    }
                    isDragging = false
                }
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(touchModifier)
        ) {
            val thumbWidth by animateDpAsState(
                targetValue = if (isDragging) 8.dp else 5.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "FastScrollbarThumbWidth"
            )

            // Track & Thumb aligned column container
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .width(10.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter
            ) {
                // Background Track
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )

                // Draggable Thumb Handle
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, currentThumbOffset.roundToInt()) }
                        .width(thumbWidth)
                        .height(thumbHeightDp)
                        .shadow(
                            elevation = if (isDragging) 6.dp else 2.dp,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            // Section indicator bubble while dragging
            val currentTargetIndex = if (isDragging) {
                val fraction = if (maxThumbOffset > 0f) dragOffsetPx / maxThumbOffset else 0f
                calculateTargetIndex(fraction, totalItems)
            } else {
                scrollableState.firstVisibleItemIndex
            }
            val label = sectionLabel?.invoke(currentTargetIndex)

            AnimatedVisibility(
                visible = isDragging && !label.isNullOrBlank(),
                enter = fadeIn(tween(120)) + scaleIn(tween(120)),
                exit = fadeOut(tween(120)) + scaleOut(tween(120)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = -(44.dp.roundToPx()),
                            y = (currentThumbOffset + thumbHeightPx / 2f - 23.dp.toPx())
                                .roundToInt()
                                .coerceIn(
                                    0,
                                    (containerHeightPx - 46.dp.toPx()).roundToInt().coerceAtLeast(0)
                                )
                        )
                    }
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label?.take(2)?.uppercase() ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Convenience overload for [LazyListState].
 */
@Composable
fun FastScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    itemCount: Int = state.layoutInfo.totalItemsCount,
    sectionLabel: ((Int) -> String?)? = null,
    inactivityTimeoutMs: Long = 3000L
) {
    val scrollableState = rememberFastScrollableState(state)
    FastScrollbar(
        scrollableState = scrollableState,
        modifier = modifier,
        itemCount = itemCount,
        sectionLabel = sectionLabel,
        inactivityTimeoutMs = inactivityTimeoutMs
    )
}

/**
 * Convenience overload for [LazyGridState].
 */
@Composable
fun FastScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    itemCount: Int = state.layoutInfo.totalItemsCount,
    sectionLabel: ((Int) -> String?)? = null,
    inactivityTimeoutMs: Long = 3000L
) {
    val scrollableState = rememberFastScrollableState(state)
    FastScrollbar(
        scrollableState = scrollableState,
        modifier = modifier,
        itemCount = itemCount,
        sectionLabel = sectionLabel,
        inactivityTimeoutMs = inactivityTimeoutMs
    )
}
