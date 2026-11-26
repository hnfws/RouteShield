package id.ac.stmi.api.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import id.ac.stmi.api.R
import id.ac.stmi.api.data.model.Hazard

class HazardAdapter(
    private val context: Context,
    private var items: List<Hazard> = emptyList()
) : BaseAdapter() {

    fun updateData(newItems: List<Hazard>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Hazard = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.activity_item_hazard, parent, false)
            holder = ViewHolder(
                tvLocation = view.findViewById(R.id.tvItemLocation),
                tvNoise = view.findViewById(R.id.tvItemNoise),
                tvCategory = view.findViewById(R.id.tvItemCategory),
                tvCoords = view.findViewById(R.id.tvItemCoords)
            )
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val item = getItem(position)
        holder.tvLocation.text = item.location_name
        holder.tvNoise.text = item.noise_level
        holder.tvCategory.text = "Kategori: ${item.category}"
        holder.tvCoords.text = "GPS: ${item.latitude}, ${item.longitude}"

        return view
    }

    private data class ViewHolder(
        val tvLocation: TextView,
        val tvNoise: TextView,
        val tvCategory: TextView,
        val tvCoords: TextView
    )
}