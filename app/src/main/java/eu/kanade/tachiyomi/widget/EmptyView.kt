package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.getResourceColor
import kotlinx.android.synthetic.main.common_view_empty.view.*

class EmptyView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        RelativeLayout(context, attrs) {

    init {
        inflate(context, R.layout.common_view_empty, this)
    }

    /**
     * Hide the information view
     */
    fun hide() {
        this.visibility = View.GONE
    }

    /**
     * Show the information view
     * @param drawable icon of information view
     * @param textResource text of information view
     */
    fun show(icon: IIcon, textResource: Int) {
        image_view.setImageDrawable(IconicsDrawable(context)
                .icon(icon).sizeDp(96)
                .colorInt(context.getResourceColor(android.R.attr.textColorHint)))
        text_label.text = context.getString(textResource)
        this.visibility = View.VISIBLE
    }
}
