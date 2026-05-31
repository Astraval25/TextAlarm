package com.astraval.brightalarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.astraval.brightalarm.data.Alarm
import com.astraval.brightalarm.databinding.ActivityMainBinding
import com.astraval.brightalarm.ui.edit.EditAlarmActivity
import com.astraval.brightalarm.ui.main.AlarmAdapter
import com.astraval.brightalarm.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private enum class HomeTab { ALARMS, TASKS }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: AlarmAdapter
    private var selectedTab: HomeTab = HomeTab.ALARMS
    private var latestAlarms: List<Alarm> = emptyList()

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore; user may deny */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val rootStartPadding = binding.root.paddingLeft
        val rootTopPadding = binding.root.paddingTop
        val rootEndPadding = binding.root.paddingRight
        val rootBottomPadding = binding.root.paddingBottom
        val headerTopPadding = binding.headerContainer.paddingTop
        val recyclerBottomPadding = binding.alarmsRecycler.paddingBottom
        val emptyStateBottomPadding = binding.emptyState.paddingBottom
        val bottomNavBottomPadding = binding.bottomNav.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val horizontalInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val topInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val bottomInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            v.setPadding(
                rootStartPadding + horizontalInsets.left,
                rootTopPadding,
                rootEndPadding + horizontalInsets.right,
                rootBottomPadding
            )
            binding.headerContainer.updatePadding(top = headerTopPadding + topInsets.top)
            binding.alarmsRecycler.updatePadding(
                bottom = recyclerBottomPadding
            )
            binding.emptyState.updatePadding(
                bottom = emptyStateBottomPadding
            )
            binding.bottomNav.updatePadding(bottom = bottomNavBottomPadding + bottomInsets.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        adapter = AlarmAdapter(
            onToggle = viewModel::toggle,
            onClick = { alarm ->
                startActivity(
                    Intent(this, EditAlarmActivity::class.java)
                        .putExtra(EditAlarmActivity.EXTRA_ALARM_ID, alarm.id)
                        .putExtra(
                            EditAlarmActivity.EXTRA_CREATE_MODE,
                            if (alarm.triggerAtMillis != null) {
                                EditAlarmActivity.MODE_TASK
                            } else {
                                EditAlarmActivity.MODE_ALARM
                            }
                        )
                )
            },
            onLongClick = { alarm ->
                AlertDialog.Builder(this)
                    .setMessage("Delete this alarm?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.delete(alarm) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.alarmsRecycler.layoutManager = LinearLayoutManager(this)
        binding.alarmsRecycler.adapter = adapter
        binding.alarmsRecycler.setHasFixedSize(true)

        binding.addAlarmFab.setOnClickListener {
            startActivity(
                Intent(this, EditAlarmActivity::class.java)
                    .putExtra(
                        EditAlarmActivity.EXTRA_CREATE_MODE,
                        if (selectedTab == HomeTab.TASKS) {
                            EditAlarmActivity.MODE_TASK
                        } else {
                            EditAlarmActivity.MODE_ALARM
                        }
                    )
            )
        }
        binding.bottomNav.setOnItemSelectedListener { item ->
            selectedTab = when (item.itemId) {
                R.id.nav_tasks -> HomeTab.TASKS
                else -> HomeTab.ALARMS
            }
            renderList()
            true
        }
        binding.bottomNav.selectedItemId = R.id.nav_alarms

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.alarms.collect { list ->
                    latestAlarms = list
                    renderList()
                }
            }
        }

        requestRuntimePermissions()
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Exact alarms required")
                    .setMessage("BrightAlarm needs permission to schedule exact alarms so alarms fire on time.")
                    .setPositiveButton("Grant") { _, _ ->
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:$packageName"))
                        )
                    }
                    .setNegativeButton("Later", null)
                    .show()
            }
        }
    }

    private fun buildSummary(alarms: List<Alarm>): String {
        val enabledCount = alarms.count { it.isEnabled }
        return when {
            alarms.isEmpty() -> "No alarms scheduled"
            enabledCount == 0 -> "All alarms are paused"
            enabledCount == 1 -> "1 alarm is ready"
            else -> "$enabledCount alarms are ready"
        }
    }

    private fun renderList() {
        val filtered = when (selectedTab) {
            HomeTab.ALARMS -> latestAlarms.filter { it.triggerAtMillis == null }
            HomeTab.TASKS -> latestAlarms.filter { it.triggerAtMillis != null }
        }
        adapter.submitList(filtered)
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.summaryText.text = when (selectedTab) {
            HomeTab.ALARMS -> buildSummary(filtered)
            HomeTab.TASKS -> if (filtered.isEmpty()) "No tasks scheduled" else "${filtered.size} tasks scheduled"
        }
        binding.emptyText.text = when (selectedTab) {
            HomeTab.ALARMS -> "Tap Create alarm to add a spoken alarm reminder."
            HomeTab.TASKS -> "Tap Create task to add a task with date-time alarm sound."
        }
        binding.addAlarmFab.text = when (selectedTab) {
            HomeTab.ALARMS -> "Create alarm"
            HomeTab.TASKS -> "Create task"
        }
        binding.addAlarmFab.contentDescription = when (selectedTab) {
            HomeTab.ALARMS -> "Create alarm"
            HomeTab.TASKS -> "Create task"
        }
    }
}
