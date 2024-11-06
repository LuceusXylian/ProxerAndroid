package me.proxer.app.util

import me.proxer.app.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

/**
 * A simple to use adapter for the RecyclerView. It decorates an existing adapter with the ability
 * to set any View as header, footer or both.
 *
 * @author Ruben Gees
 */
class EasyHeaderFooterAdapter(private val innerAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var header: View? = null
    private var footer: View? = null

    private var layoutManager: RecyclerView.LayoutManager? = null

    companion object {
        private const val TYPE_HEADER = Int.MIN_VALUE
        private const val TYPE_FOOTER = Int.MIN_VALUE + 1
        private const val ID_HEADER = Long.MIN_VALUE
        private const val ID_FOOTER = Long.MIN_VALUE + 1
    }

    init {
        innerAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                notifyDataSetChanged()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                notifyItemRangeChanged(getDelegatedPosition(positionStart), itemCount)
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                notifyItemRangeChanged(getDelegatedPosition(positionStart), itemCount, payload)
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                notifyItemRangeInserted(getDelegatedPosition(positionStart), itemCount)
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                notifyItemRangeRemoved(getDelegatedPosition(positionStart), itemCount)
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                notifyItemRangeChanged(getDelegatedPosition(fromPosition), getDelegatedPosition(toPosition) + itemCount)
            }
        })

        setHasStableIds(innerAdapter.hasStableIds())
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        initLayoutManager(recyclerView.layoutManager)
        innerAdapter.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        innerAdapter.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if (holder !is HeaderFooterViewHolder) {
            innerAdapter.onViewAttachedToWindow(holder)
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder !is HeaderFooterViewHolder) {
            innerAdapter.onViewDetachedFromWindow(holder)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder !is HeaderFooterViewHolder) {
            innerAdapter.onViewRecycled(holder)
        }
    }

    override fun onFailedToRecycleView(holder: RecyclerView.ViewHolder): Boolean {
        return if (holder !is HeaderFooterViewHolder) {
            innerAdapter.onFailedToRecycleView(holder)
        } else super.onFailedToRecycleView(holder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER || viewType == TYPE_FOOTER) {
            HeaderFooterViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.easy_header_footer_adapter_item, parent, false))
        } else {
            innerAdapter.onCreateViewHolder(parent, viewType)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (holder is HeaderFooterViewHolder) {
            bind(holder, position)
        } else {
            innerAdapter.onBindViewHolder(holder, getRealPosition(position), payloads)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderFooterViewHolder) {
            bind(holder, position)
        } else {
            innerAdapter.onBindViewHolder(holder, getRealPosition(position))
        }
    }

    override fun getItemCount(): Int {
        return innerAdapter.itemCount + if (header != null) 1 else 0 + if (footer != null) 1 else 0
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            isHeader(position) -> TYPE_HEADER
            isFooter(position) -> TYPE_FOOTER
            else -> innerAdapter.getItemViewType(getRealPosition(position))
        }
    }

    override fun getItemId(position: Int): Long {
        return when {
            isHeader(position) -> ID_HEADER
            isFooter(position) -> ID_FOOTER
            else -> innerAdapter.getItemId(getRealPosition(position))
        }
    }

    fun isHeader(position: Int): Boolean {
        return header != null && position == 0
    }

    fun isFooter(position: Int): Boolean {
        return footer != null && position == getFooterPosition()
    }

    fun getHeader(): View? {
        return header
    }

    fun setHeader(header: View?) {
        if (this.header === header) return

        val hadHeader = this.header != null
        this.header = header

        if (header == null) {
            notifyItemRemoved(0)
        } else {
            detachFromParent(header)
            if (hadHeader) {
                notifyItemChanged(0)
            } else {
                notifyItemInserted(0)
            }
        }
    }

    fun getFooter(): View? {
        return footer
    }

    fun setFooter(footer: View?) {
        if (this.footer === footer) return

        val hadFooter = this.footer != null
        this.footer = footer

        if (footer == null) {
            notifyItemRemoved(getFooterPosition())
        } else {
            if (hadFooter) {
                notifyItemChanged(getFooterPosition())
            } else {
                notifyItemInserted(getFooterPosition())
            }
        }
    }

    fun getInnerAdapter(): RecyclerView.Adapter<*> {
        return innerAdapter
    }

    private fun getRealPosition(position: Int): Int {
        return position - if (header != null) 1 else 0
    }

    private fun getDelegatedPosition(position: Int): Int {
        return position + if (header != null) 1 else 0
    }

    private fun getFooterPosition(): Int {
        return innerAdapter.itemCount + if (header != null) 1 else 0
    }

    private fun initLayoutManager(layoutManager: RecyclerView.LayoutManager?) {
        this.layoutManager = layoutManager

        if (layoutManager is GridLayoutManager) {
            val castedLayoutManager = layoutManager
            val existingLookup = castedLayoutManager.spanSizeLookup

            castedLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (isHeader(position) || isFooter(position)) {
                        castedLayoutManager.spanCount
                    } else {
                        existingLookup.getSpanSize(getRealPosition(position))
                    }
                }
            }
        }
    }

    private fun bind(holder: HeaderFooterViewHolder, position: Int) {
        val holderItemView = holder.itemView as ViewGroup
        val layoutParams: ViewGroup.LayoutParams
        val viewToAdd: View?

        viewToAdd = when {
            isHeader(position) -> header
            isFooter(position) -> footer
            else -> return
        }

        detachFromParent(viewToAdd)
        holderItemView.removeAllViews()
        holderItemView.addView(viewToAdd)

        layoutParams = if (layoutManager is StaggeredGridLayoutManager) {
            val staggeredLayoutParams = if (viewToAdd?.layoutParams == null) {
                StaggeredGridLayoutManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            } else {
                StaggeredGridLayoutManager.LayoutParams(
                    viewToAdd.layoutParams.width,
                    viewToAdd.layoutParams.height
                )
            }
            staggeredLayoutParams.isFullSpan = true
            staggeredLayoutParams
        } else {
            if (viewToAdd?.layoutParams == null) {
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            } else {
                ViewGroup.LayoutParams(
                    viewToAdd.layoutParams.width,
                    viewToAdd.layoutParams.height
                )
            }
        }

        holder.itemView.layoutParams = layoutParams
    }

    private fun detachFromParent(view: View?) {
        val parent = view?.parent as? ViewGroup
        parent?.removeView(view)
    }

    private class HeaderFooterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
