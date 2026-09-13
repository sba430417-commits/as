package com.example.passvault.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.passvault.data.Account
import com.example.passvault.databinding.ItemAccountBinding

class AccountAdapter(
    private val onClick: (Account) -> Unit
) : ListAdapter<Account, AccountAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvSiteName.text = item.siteName
        holder.binding.tvSubtitle.text = item.username ?: item.email ?: ""
        holder.binding.tvBadge.text = if (item.source == "imported_csv") "مستورد" else "يدوي"
        holder.itemView.setOnClickListener { onClick(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Account>() {
            override fun areItemsTheSame(oldItem: Account, newItem: Account) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Account, newItem: Account) = oldItem == newItem
        }
    }
}
