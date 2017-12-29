package me.proxer.app.ui.view.bbcode.tree

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import me.proxer.app.base.BaseActivity
import me.proxer.app.ui.view.bbcode.applyToViews
import me.proxer.app.ui.view.bbcode.prototype.UrlPrototype
import okhttp3.HttpUrl

/**
 * @author Ruben Gees
 */
class UrlTree(
        private val url: HttpUrl,
        parent: BBTree?,
        children: MutableList<BBTree> = mutableListOf()
) : BBTree(parent, children) {

    override val prototype = UrlPrototype

    override fun makeViews(context: Context): List<View> {
        val children = super.makeViewsWithoutMerging(context)

        return applyToViews(children, { view: TextView ->
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View?) {
                    (context as? BaseActivity)?.showPage(url)
                }
            }

            view.text = SpannableStringBuilder(view.text).apply {
                setSpan(clickableSpan, 0, view.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        })
    }
}
