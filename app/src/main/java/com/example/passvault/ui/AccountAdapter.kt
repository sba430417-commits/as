package com.example.passvault.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.passvault.data.Account
import com.example.passvault.databinding.ItemAccountBinding
import com.example.passvault.util.decryptedForUi

class AccountAdapter(
    private val onClick: (Account) -> Unit,
    private val onLongClick: (Account) -> Unit,
    private val isSelectionMode: () -> Boolean,
    private val isSelected: (Long) -> Boolean,
    private val onSelectionChanged: (Account) -> Unit
) : ListAdapter<Account, AccountAdapter.VH>(DIFF) {
    inner class VH(val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position).decryptedForUi()
        holder.binding.tvSiteName.text = item.siteName
        holder.binding.tvSubtitle.text = item.username ?: item.email ?: item.phone ?: ""
        holder.binding.tvBadge.text = if (item.source == "imported_csv") "مستورد" else "يدوي"
        holder.binding.checkSelect.visibility = if (isSelectionMode()) View.VISIBLE else View.GONE
        holder.binding.checkSelect.setOnCheckedChangeListener(null)
        holder.binding.checkSelect.isChecked = isSelected(item.id)
        holder.binding.checkSelect.setOnClickListener { onSelectionChanged(item) }
        holder.itemView.setOnClickListener { if (isSelectionMode()) onSelectionChanged(item) else onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    fun refreshSelection(vararg ids: Long) {
        ids.forEach { id -> currentList.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(::notifyItemChanged) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Account>() {
            override fun areItemsTheSame(a: Account, b: Account) = a.id == b.id
            override fun areContentsTheSame(a: Account, b: Account) = a == b
        }
    }
}
