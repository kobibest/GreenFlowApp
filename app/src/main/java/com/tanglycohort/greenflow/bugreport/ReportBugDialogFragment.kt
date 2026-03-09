package com.tanglycohort.greenflow.bugreport

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tanglycohort.greenflow.R

class ReportBugDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_report_bug, null)
        val descriptionEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.report_bug_description)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.main_report_bug_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.main_report_bug_send) { _, _ ->
                val description = descriptionEdit.text?.toString()?.trim()
                sendBugReport(description)
            }
            .setNegativeButton(R.string.main_report_bug_cancel) { _, _ -> dismiss() }
            .create()
    }

    private fun sendBugReport(description: String?) {
        val payload = BugReportCollector.collect(
            context = requireContext(),
            description = description
        )
        BugReportSender.send(payload) { result ->
            requireActivity().runOnUiThread {
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), R.string.main_report_bug_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.main_report_bug_error, Toast.LENGTH_LONG).show()
                }
                dismiss()
            }
        }
    }
}
