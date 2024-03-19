/*
 * Copyright 2023 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import backend.Backend
import com.celzero.bravedns.R
import com.celzero.bravedns.database.WgConfigFiles
import com.celzero.bravedns.databinding.ListItemWgOneInterfaceBinding
import com.celzero.bravedns.service.ProxyManager
import com.celzero.bravedns.service.VpnController
import com.celzero.bravedns.service.WireguardManager
import com.celzero.bravedns.ui.activity.WgConfigDetailActivity
import com.celzero.bravedns.ui.activity.WgConfigDetailActivity.Companion.INTENT_EXTRA_WG_TYPE
import com.celzero.bravedns.ui.activity.WgConfigEditorActivity.Companion.INTENT_EXTRA_WG_ID
import com.celzero.bravedns.util.UIUtils
import com.celzero.bravedns.util.UIUtils.fetchColor
import com.celzero.bravedns.util.Utilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OneWgConfigAdapter(private val context: Context, private val listener: DnsStatusListener) :
    PagingDataAdapter<WgConfigFiles, OneWgConfigAdapter.WgInterfaceViewHolder>(DIFF_CALLBACK) {

    private var statusCheckJob: Job? = Job()
    private var lifecycleOwner: LifecycleOwner? = null

    interface DnsStatusListener {
        fun onDnsStatusChanged()
    }

    companion object {
        private const val DELAY = 1000L

        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<WgConfigFiles>() {

                override fun areItemsTheSame(
                    oldConnection: WgConfigFiles,
                    newConnection: WgConfigFiles
                ): Boolean {
                    return (oldConnection == newConnection)
                }

                override fun areContentsTheSame(
                    oldConnection: WgConfigFiles,
                    newConnection: WgConfigFiles
                ): Boolean {
                    return (oldConnection.id == newConnection.id &&
                        oldConnection.name == newConnection.name &&
                        oldConnection.isActive == newConnection.isActive &&
                        oldConnection.oneWireGuard == newConnection.oneWireGuard)
                }
            }
    }

    override fun onBindViewHolder(holder: WgInterfaceViewHolder, position: Int) {
        val wgConfigFiles: WgConfigFiles = getItem(position) ?: return
        holder.update(wgConfigFiles)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WgInterfaceViewHolder {
        val itemBinding =
            ListItemWgOneInterfaceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        lifecycleOwner = parent.findViewTreeLifecycleOwner()
        return WgInterfaceViewHolder(itemBinding)
    }

    inner class WgInterfaceViewHolder(private val b: ListItemWgOneInterfaceBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun update(config: WgConfigFiles) {
            b.interfaceNameText.text = config.name
            b.oneWgCheck.isChecked = config.isActive
            updateStatus(config)
            setupClickListeners(config)
            if (config.oneWireGuard) {
                keepStatusUpdated(config)
            } else {
                statusCheckJob?.cancel()
                b.interfaceDetailCard.strokeWidth = 0
                b.interfaceAppsCount.visibility = View.GONE
                b.oneWgCheck.isChecked = false
                b.interfaceStatus.text =
                    context.getString(R.string.lbl_disabled).replaceFirstChar(Char::titlecase)
            }
        }

        private fun keepStatusUpdated(config: WgConfigFiles) {
            statusCheckJob = ui {
                while (true) {
                    updateStatus(config)
                    delay(DELAY)
                }
            }
        }

        private fun updateStatus(config: WgConfigFiles) {
            val id = ProxyManager.ID_WG_BASE + config.id
            val apps = ProxyManager.getAppCountForProxy(id).toString()
            val statusId = VpnController.getProxyStatusById(id)
            updateStatusUi(config, statusId, apps)
        }

        private fun updateStatusUi(config: WgConfigFiles, statusId: Long?, apps: String) {
            // if the view is not active then cancel the job
            if (
                lifecycleOwner != null &&
                    lifecycleOwner
                        ?.lifecycle
                        ?.currentState
                        ?.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) == false
            ) {
                statusCheckJob?.cancel()
                return
            }

            val appsCount = context.getString(R.string.firewall_card_status_active, apps)
            if (config.isActive) {
                b.interfaceDetailCard.strokeColor = fetchColor(context, R.color.accentGood)
                b.interfaceDetailCard.strokeWidth = 2
                b.oneWgCheck.isChecked = true
                b.interfaceAppsCount.visibility = View.VISIBLE
                b.interfaceAppsCount.text = context.getString(R.string.one_wg_apps_added)
                if (statusId != null) {
                    val resId = UIUtils.getProxyStatusStringRes(statusId)
                    // change the color based on the status
                    if (statusId == Backend.TOK) {
                        b.interfaceDetailCard.strokeColor =
                            fetchColor(context, R.attr.chipTextPositive)
                        // cancel the job, as the status is connected
                        statusCheckJob?.cancel()
                    } else if (statusId == Backend.TUP || statusId == Backend.TZZ) {
                        b.interfaceDetailCard.strokeColor =
                            fetchColor(context, R.attr.chipTextNeutral)
                    } else {
                        b.interfaceDetailCard.strokeColor =
                            fetchColor(context, R.attr.chipTextNegative)
                    }
                    b.interfaceStatus.text =
                        context.getString(resId).replaceFirstChar(Char::titlecase)
                } else {
                    b.interfaceStatus.text =
                        context.getString(
                            R.string.about_version_install_source,
                            context
                                .getString(R.string.status_waiting)
                                .replaceFirstChar(Char::titlecase),
                            appsCount
                        )
                }
            } else {
                b.interfaceDetailCard.strokeWidth = 0
                b.interfaceAppsCount.visibility = View.GONE
                b.oneWgCheck.isChecked = false
                b.interfaceStatus.text =
                    context.getString(R.string.lbl_disabled).replaceFirstChar(Char::titlecase)
            }
        }

        fun setupClickListeners(config: WgConfigFiles) {
            b.interfaceDetailCard.setOnClickListener { launchConfigDetail(config.id) }

            b.oneWgCheck.setOnClickListener {
                val isChecked = b.oneWgCheck.isChecked
                io {
                    if (isChecked) {
                        if (WireguardManager.canEnableConfig(config)) {
                            config.oneWireGuard = true
                            WireguardManager.updateOneWireGuardConfig(config.id, owg = true)
                            WireguardManager.enableConfig(config)
                            uiCtx { listener.onDnsStatusChanged() }
                        } else {
                            uiCtx {
                                b.oneWgCheck.isChecked = false
                                Utilities.showToastUiCentered(
                                    context,
                                    context.getString(R.string.wireguard_enabled_failure),
                                    Toast.LENGTH_LONG
                                )
                            }
                        }
                    } else {
                        config.oneWireGuard = false
                        b.oneWgCheck.isChecked = false
                        WireguardManager.updateOneWireGuardConfig(config.id, owg = false)
                        WireguardManager.disableConfig(config)
                        uiCtx { listener.onDnsStatusChanged() }
                    }
                }
            }
        }

        private fun launchConfigDetail(id: Int) {
            val intent = Intent(context, WgConfigDetailActivity::class.java)
            intent.putExtra(INTENT_EXTRA_WG_ID, id)
            intent.putExtra(INTENT_EXTRA_WG_TYPE, WgConfigDetailActivity.WgType.ONE_WG.value)
            context.startActivity(intent)
        }
    }

    private suspend fun uiCtx(f: suspend () -> Unit) {
        withContext(Dispatchers.Main) { f() }
    }

    private fun ui(f: suspend () -> Unit): Job? {
        if (lifecycleOwner == null) {
            return null
        }
        return lifecycleOwner?.lifecycleScope?.launch(Dispatchers.Main) { f() }
    }

    private fun io(f: suspend () -> Unit) {
        lifecycleOwner?.lifecycleScope?.launch(Dispatchers.IO) { f() }
    }
}
