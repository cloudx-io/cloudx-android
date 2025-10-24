package io.cloudx.demo.demoapp.loglistview

import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import io.cloudx.demo.demoapp.INIT_SUCCESS
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.util.utcNowEpochMillis
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun setupLogListView(
    recyclerView: RecyclerView,
    // null - do not include tag into log list view; otherwise include with a name returned as a func result.
    logTagMappingRule: (forTag: String) -> String?
) {
    val lifecycleOwner = recyclerView.findViewTreeLifecycleOwner()
        ?: throw IllegalStateException("couldn't find view tree lifecycle owner")

    val adapter = RecyclerViewLogAdapter(recyclerView)
    recyclerView.adapter = adapter

    CXLogger.logFlow.map { logItem ->
        logTagMappingRule(logItem.tag)?.let { tag ->
            logItem.copy(tag = tag)
        }
    }.onEach { logItem ->
        logItem?.let {
            adapter.addLog(logItem.toLogListItem())
        }
    }.launchIn(lifecycleOwner.lifecycleScope)
}

class LogListItem(
    val time: String,
    // Special highlighting for such logs (initialization success, for example)
    val isSuccessLog: Boolean,
    val logEntry: CXLogger.LogEntry
)

private fun CXLogger.LogEntry.toLogListItem(): LogListItem {
    return LogListItem(
        time = now(),
        // TODO. As ugly as it gets.
        isSuccessLog = message == INIT_SUCCESS,
        logEntry = this
    )
}

private fun now() = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(
    Date(utcNowEpochMillis())
)