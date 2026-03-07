package com.tanglycohort.greenflow.bugreport

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.tanglycohort.greenflow.R

class ReportBugDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.main_report_bug_description_hint)
            setPadding(48, 32, 48, 32)
            minLines = 3
        }
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.main_report_bug_dialog_title)
            .setView(editText)
            .setPositiveButton(R.string.main_report_bug_send) { _, _ ->
                val description = editText.text?.toString()?.trim()
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
