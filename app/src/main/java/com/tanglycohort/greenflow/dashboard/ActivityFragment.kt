package com.tanglycohort.greenflow.dashboard

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tanglycohort.greenflow.R
import com.tanglycohort.greenflow.SmsActivityEntry
import com.tanglycohort.greenflow.SmsActivityLog
import com.tanglycohort.greenflow.data.repository.AuthRepository
import com.tanglycohort.greenflow.databinding.FragmentActivityBinding
import com.tanglycohort.greenflow.databinding.ItemSmsActivityBinding
import io.github.jan.supabase.gotrue.auth

class ActivityFragment : Fragment() {

    private var _binding: FragmentActivityBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = com.tanglycohort.greenflow.supabase.SupabaseProvider.client.auth.currentUserOrNull()?.id
            ?: AuthRepository().getUserIdFromSession()
        if (userId == null) {
            findNavController().navigate(R.id.LoginFragment)
            return
        }

        val list = SmsActivityLog.getAll(requireContext())
        binding.activityEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.activityRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.activityRecycler.adapter = ActivityAdapter(list, requireContext().getString(R.string.activity_status_success),
            requireContext().getString(R.string.activity_status_filtered),
            requireContext().getString(R.string.activity_status_error))
    }

    override fun onResume() {
        super.onResume()
        val list = SmsActivityLog.getAll(requireContext())
        (binding.activityRecycler.adapter as? ActivityAdapter)?.updateList(list)
        binding.activityEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ActivityAdapter(
        private var list: List<SmsActivityEntry>,
        private val labelSuccess: String,
        private val labelFiltered: String,
        private val labelError: String
    ) : RecyclerView.Adapter<ActivityAdapter.ViewHolder>() {

        fun updateList(newList: List<SmsActivityEntry>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemSmsActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        inner class ViewHolder(private val b: ItemSmsActivityBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(entry: SmsActivityEntry) {
                b.itemActivityTime.text = entry.formattedTime()
                b.itemActivityFrom.text = entry.from
                b.itemActivityBody.text = entry.bodyPreview
                b.itemActivityResponse.text = entry.responseDetail
                val (statusText, colorRes) = when (entry.responseStatus) {
                    "success" -> labelSuccess to android.R.color.holo_green_dark
                    "filtered" -> labelFiltered to android.R.color.holo_orange_dark
                    else -> labelError to android.R.color.holo_red_dark
                }
                b.itemActivityStatus.text = statusText
                b.itemActivityStatus.setTextColor(ContextCompat.getColor(b.root.context, colorRes))
                b.itemActivityStatus.setTypeface(null, Typeface.BOLD)
            }
        }
    }
}
