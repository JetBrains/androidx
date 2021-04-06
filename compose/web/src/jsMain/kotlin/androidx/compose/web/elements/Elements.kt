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

package androidx.compose.web.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.web.DomApplier
import androidx.compose.web.DomNodeWrapper
import androidx.compose.web.attributes.AttrsBuilder
import androidx.compose.web.attributes.InputType
import androidx.compose.web.attributes.Tag
import androidx.compose.web.attributes.action
import androidx.compose.web.attributes.alt
import androidx.compose.web.attributes.href
import androidx.compose.web.attributes.label
import androidx.compose.web.attributes.src
import androidx.compose.web.attributes.type
import androidx.compose.web.attributes.value
import androidx.compose.web.css.StyleBuilder
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLBRElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLHeadingElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLLIElement
import org.w3c.dom.HTMLOListElement
import org.w3c.dom.HTMLOptGroupElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLParagraphElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLSpanElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.HTMLUListElement
import org.w3c.dom.Text

@Composable
fun Text(value: String) {
    ComposeNode<DomNodeWrapper, DomApplier>(
        factory = { DomNodeWrapper(document.createTextNode("")) },
        update = {
            set(value) { value -> (node as Text).data = value }
        },
    )
}

@Composable
inline fun Div(
    crossinline attrs: (AttrsBuilder<Tag.Div>.() -> Unit) = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLDivElement>.() -> Unit
) {
    TagElement(
        tagName = "div",
        applyAttrs = attrs,
        applyStyle = style,
        content = content
    )
}

@Composable
inline fun A(
    href: String? = null,
    crossinline attrs: (AttrsBuilder<Tag.A>.() -> Unit) = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLAnchorElement>.() -> Unit
) {
    TagElement<Tag.A, HTMLAnchorElement>(
        tagName = "a",
        applyAttrs = {
            href(href)
            attrs()
        },
        applyStyle = style,
        content = content
    )
}

@Composable
inline fun Input(
    type: InputType = InputType.Text,
    value: String = "",
    crossinline attrs: (AttrsBuilder<Tag.Input>.() -> Unit) = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLInputElement>.() -> Unit = {}
) {
    TagElement<Tag.Input, HTMLInputElement>(
        tagName = "input",
        applyAttrs = {
            type(type)
            value(value)
            attrs()
        },
        applyStyle = style,
        content = content
    )
}

@Composable
inline fun Button(
    crossinline attrs: AttrsBuilder<Tag.Button>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLHeadingElement>.() -> Unit
) = TagElement("button", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun H1(
    crossinline attrs: AttrsBuilder<Tag.H>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLHeadingElement>.() -> Unit
) = TagElement("h1", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun H2(
    crossinline attrs: AttrsBuilder<Tag.H>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLHeadingElement>.() -> Unit
) = TagElement("h2", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun H3(
    crossinline attrs: AttrsBuilder<Tag.H>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLHeadingElement>.() -> Unit
) = TagElement("h3", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun H4(
    crossinline attrs: AttrsBuilder<Tag.H>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLHeadingElement>.() -> Unit
) = TagElement("h4", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun H5(
    crossinline attrs: AttrsBuilder<Tag.H>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLHeadingElement>.() -> Unit
) = TagElement("h5", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun H6(
    crossinline attrs: AttrsBuilder<Tag.H>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLHeadingElement>.() -> Unit
) = TagElement("h6", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun P(
    crossinline attrs: AttrsBuilder<Tag.P>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLParagraphElement>.() -> Unit
) = TagElement("p", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun Em(
    crossinline attrs: AttrsBuilder<Tag>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLElement>.() -> Unit
) = TagElement("em", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun I(
    crossinline attrs: AttrsBuilder<Tag>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLElement>.() -> Unit
) = TagElement("i", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun B(
    crossinline attrs: AttrsBuilder<Tag>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLElement>.() -> Unit
) = TagElement("b", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun Small(
    crossinline attrs: AttrsBuilder<Tag>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLElement>.() -> Unit
) = TagElement("small", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun Span(
    crossinline attrs: AttrsBuilder<Tag.Span>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLSpanElement>.() -> Unit
) = TagElement("span", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun Br(
    crossinline attrs: AttrsBuilder<Tag.Br>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLBRElement>.() -> Unit
) = TagElement("br", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun Ul(
    crossinline attrs: AttrsBuilder<Tag.Ul>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLUListElement>.() -> Unit,
) = TagElement("ul", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun Ol(
    crossinline attrs: AttrsBuilder<Tag.Ol>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLOListElement>.() -> Unit
) = TagElement("ol", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun DOMScope<HTMLOListElement>.Li(
    crossinline attrs: AttrsBuilder<Tag.Li>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLLIElement>.() -> Unit
) = TagElement("li", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun DOMScope<HTMLUListElement>.Li(
    crossinline attrs: AttrsBuilder<Tag.Li>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLLIElement>.() -> Unit
) = TagElement("li", applyAttrs = attrs, applyStyle = style, content = content)

@Composable
inline fun Img(
    src: String,
    alt: String = "",
    crossinline attrs: AttrsBuilder<Tag.Img>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLImageElement>.() -> Unit
) = TagElement<Tag.Img, HTMLImageElement>(
    tagName = "img",
    applyAttrs = {
        src(src).alt(alt)
        attrs()
    },
    applyStyle = style, content = content
)

@Composable
inline fun Form(
    action: String? = null,
    crossinline attrs: AttrsBuilder<Tag.Form>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLFormElement>.() -> Unit
) = TagElement<Tag.Form, HTMLFormElement>(
    tagName = "form",
    applyAttrs = {
        if (!action.isNullOrEmpty()) action(action)
        attrs()
    },
    applyStyle = style, content = content
)

@Composable
inline fun Select(
    crossinline attrs: AttrsBuilder<Tag.Select>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLSelectElement>.() -> Unit
) = TagElement(
    tagName = "select",
    applyAttrs = attrs,
    applyStyle = style,
    content = content
)

@Composable
inline fun DOMScope<HTMLUListElement>.Option(
    value: String,
    crossinline attrs: AttrsBuilder<Tag.Option>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLOptionElement>.() -> Unit
) = TagElement<Tag.Option, HTMLOptionElement>(
    tagName = "option",
    applyAttrs = {
        value(value)
        attrs()
    },
    applyStyle = style,
    content = content
)

@Composable
inline fun OptGroup(
    label: String,
    crossinline attrs: AttrsBuilder<Tag.OptGroup>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLOptGroupElement>.() -> Unit
) = TagElement<Tag.OptGroup, HTMLOptGroupElement>(
    tagName = "optgroup",
    applyAttrs = {
        label(label)
        attrs()
    },
    applyStyle = style,
    content = content
)

@Composable
inline fun Section(
    crossinline attrs: AttrsBuilder<Tag>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLElement>.() -> Unit
) = TagElement(
    tagName = "section",
    applyAttrs = attrs,
    applyStyle = style,
    content = content
)

@Composable
inline fun TextArea(
    crossinline attrs: AttrsBuilder<Tag.TextArea>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    value: String
) = TagElement<Tag.TextArea, HTMLTextAreaElement>(
    tagName = "textarea",
    applyAttrs = {
        value(value)
        attrs()
    },
    applyStyle = style
) {
    Text(value)
}

@Composable
inline fun Nav(
    crossinline attrs: AttrsBuilder<Tag.Nav>.() -> Unit = {},
    crossinline style: (StyleBuilder.() -> Unit) = {},
    content: @Composable ElementScope<HTMLElement>.() -> Unit
) = TagElement(
    tagName = "nav",
    applyAttrs = attrs,
    applyStyle = style,
    content = content
)
