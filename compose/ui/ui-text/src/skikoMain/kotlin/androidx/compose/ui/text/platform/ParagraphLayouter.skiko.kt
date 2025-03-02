/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui.text.platform

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import kotlin.math.abs
import org.jetbrains.skia.paragraph.LineMetrics
import org.jetbrains.skia.paragraph.Paragraph

/**
 * The purpose of this class is to store already built paragraph and pass it between
 * different internal entities (from SkiaParagraphIntrinsics to SkiaParagraph).
 *
 * An alternative to passing and reusing existed paragraph is to build it again, but it is 2.5x
 * slower.
 *
 * LayoutedParagraph should have only one owner to avoid concurrent usage.
 *
 * Tests:
 *
 * val text = (1..100000).joinToString(" ")
 * reusedParagraph.layout(300f): 116.848500ms
 * builder.build().layout(300f): 288.302300ms
 *
 * text = (1..10000).joinToString(" ")
 * reusedParagraph.layout(300f): 10.004400ms
 * builder.build().layout(300f): 23.421500ms
 */
internal class ParagraphLayouter(
    val text: String,
    textDirection: ResolvedTextDirection,
    style: TextStyle,
    annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
    placeholders: List<AnnotatedString.Range<Placeholder>>,
    density: Density,
    fontFamilyResolver: FontFamily.Resolver
) {
    private val builder = ParagraphBuilder(
        fontFamilyResolver = fontFamilyResolver,
        text = text,
        textStyle = style,
        annotations = annotations,
        placeholders = placeholders,
        density = density,
        textDirection = textDirection
    )
    private var paragraphCache: Paragraph? = null
    private var updateForeground = false
    private var width: Float = Float.NaN

    val defaultFont get() = builder.defaultFont
    val textStyle get() = builder.textStyle

    private fun invalidateParagraph(onlyForeground: Boolean = false) {
        // skia's updateForegroundPaint applies the same style to every span,
        // so if we have any, we need to rebuild the entire paragraph :'(
        if (onlyForeground && builder.annotations.isEmpty()) {
            updateForeground = true
        } else {
            paragraphCache = null
        }
    }

    internal fun emptyLineMetrics(paragraph: Paragraph): Array<LineMetrics> =
        builder.emptyLineMetrics(paragraph)

    fun setParagraphStyle(
        maxLines: Int,
        ellipsis: String
    ) {
        if (builder.maxLines != maxLines ||
            builder.ellipsis != ellipsis
        ) {
            builder.maxLines = maxLines
            builder.ellipsis = ellipsis
            invalidateParagraph()
        }
    }

    fun setColor(
        color: Color,
    ) {
        val actualColor = color.takeOrElse { builder.textStyle.color }
        if (builder.textStyle.color != actualColor) {
            builder.textStyle = builder.textStyle.copy(
                color = actualColor,
            )
            invalidateParagraph(onlyForeground = true)
        }
    }

    fun setBrush(
        brush: Brush?,
        brushSize: Size,
        alpha: Float,
    ) {
        val actualSize = builder.brushSize
        if (builder.textStyle.brush != brush ||
            actualSize.isUnspecified ||
            !actualSize.width.sameValueAs(brushSize.width) ||
            !actualSize.height.sameValueAs(brushSize.height) ||
            !builder.textStyle.alpha.sameValueAs(alpha)
        ) {
            builder.textStyle = builder.textStyle.copy(
                brush = brush,
                alpha = alpha,
            )
            builder.brushSize = brushSize
            invalidateParagraph(onlyForeground = true)
        }
    }

    fun setBrushSize(
        brushSize: Size,
    ) {
        if (builder.brushSize != brushSize) {
            builder.brushSize = brushSize

            // [brushSize] requires only for shader recreation and does not require re-layout,
            // but we have to invalidate it because it's backed into skia's paragraph.
            // Since it affects only [ShaderBrush] we can keep the cache if it's not used.
            if (builder.textStyle.brush is ShaderBrush ||
                builder.annotations.any {
                    it.item is SpanStyle && // TODO(ivan): Verify that we need only [SpanStyle] here
                    it.item.brush is ShaderBrush }) {
                invalidateParagraph(onlyForeground = true)
            }
        }
    }

    fun setTextStyle(
        shadow: Shadow?,
        textDecoration: TextDecoration?,
    ) {
        if (builder.textStyle.shadow != shadow ||
            builder.textStyle.textDecoration != textDecoration
        ) {
            builder.textStyle = builder.textStyle.copy(
                shadow = shadow,
                textDecoration = textDecoration,
            )
            invalidateParagraph()
        }
    }

    fun setDrawStyle(drawStyle: DrawStyle?) {
        if (builder.drawStyle != drawStyle) {
            builder.drawStyle = drawStyle
            invalidateParagraph(onlyForeground = true)
        }
    }

    fun setBlendMode(blendMode: BlendMode) {
        if (builder.blendMode != blendMode) {
            builder.blendMode = blendMode
            invalidateParagraph()
        }
    }

    fun layoutParagraph(width: Float): Paragraph {
        var paragraph = paragraphCache
        return if (paragraph != null) {
            var layoutRequired = false
            if (updateForeground) {
                builder.updateForegroundPaint(paragraph)
                updateForeground = false

                // Skia caches everything internally, so to actually apply it
                // markDirty + layout is required.
                paragraph.markDirty()
                layoutRequired = true
            }
            if (!this.width.sameValueAs(width)) {
                this.width = width
                layoutRequired = true
            }
            if (layoutRequired) {
                paragraph.layout(width)
            }
            paragraph
        } else {
            paragraph = builder.build()
            paragraph.layout(width)
            paragraphCache = paragraph
            updateForeground = false
            return paragraph
        }
    }
}

private fun Float.sameValueAs(other: Float) : Boolean {
    return abs(this - other) < 0.00001f
}