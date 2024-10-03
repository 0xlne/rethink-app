/*
 * Copyright 2024 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import by.kirich1409.viewbindingdelegate.viewBinding
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.DomainConnectionsAdapter
import com.celzero.bravedns.databinding.ActivityDomainConnectionsBinding
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.util.CustomLinearLayoutManager
import com.celzero.bravedns.util.Themes.Companion.getCurrentTheme
import com.celzero.bravedns.util.UIUtils.getCountryNameFromFlag
import com.celzero.bravedns.viewmodel.DomainConnectionsViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class DomainConnectionsActivity : AppCompatActivity(R.layout.activity_domain_connections){
    private val b by viewBinding(ActivityDomainConnectionsBinding::bind)
    private val persistentState by inject<PersistentState>()
    private val viewModel by viewModel<DomainConnectionsViewModel>()

    private var type: InputType = InputType.DOMAIN

    private fun Context.isDarkThemeOn(): Boolean {
            return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                UI_MODE_NIGHT_YES
        }

    companion object {
        const val INTENT_TYPE = "TYPE"
        const val INTENT_FLAG = "FLAG"
        const val INTENT_DOMAIN = "DOMAIN"
        const val INTENT_TIME_CATEGORY = "TIME_CATEGORY"
    }

    enum class InputType(val type: Int) {
        DOMAIN(0), FLAG(1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getCurrentTheme(isDarkThemeOn(), persistentState.theme))
        super.onCreate(savedInstanceState)
        val t = intent.getIntExtra(INTENT_TYPE, 0)
        type = InputType.entries.toTypedArray()[t]
        if (type == InputType.DOMAIN) {
            val domain = intent.getStringExtra(INTENT_DOMAIN) ?: ""
            viewModel.setDomain(domain)
            b.dcTitle.visibility = View.VISIBLE
            b.dcTitle.text = domain
            b.dcFlag.visibility = View.GONE
        } else {
            val flag = intent.getStringExtra(INTENT_FLAG) ?: ""
            viewModel.setFlag(flag)
            b.dcFlag.visibility = View.VISIBLE
            b.dcFlag.text = getString(R.string.two_argument_space, flag, getCountryNameFromFlag(flag))
            b.dcTitle.visibility = View.GONE
        }
        val tc = intent.getIntExtra(INTENT_TIME_CATEGORY, 0)
        val timeCategory =
            DomainConnectionsViewModel.TimeCategory.fromValue(tc)
                ?: DomainConnectionsViewModel.TimeCategory.ONE_HOUR
        setSubTitle(timeCategory)

        viewModel.timeCategoryChanged(timeCategory)
        setRecyclerView()
    }

    private fun setSubTitle(timeCategory: DomainConnectionsViewModel.TimeCategory) {
        b.dcSubtitle.text =
            when (timeCategory) {
                DomainConnectionsViewModel.TimeCategory.ONE_HOUR -> {
                    getString(
                        R.string.three_argument,
                        getString(R.string.lbl_last),
                        getString(R.string.numeric_one),
                        getString(R.string.lbl_hour)
                    )
                }

                DomainConnectionsViewModel.TimeCategory.TWENTY_FOUR_HOUR -> {
                    getString(
                        R.string.three_argument,
                        getString(R.string.lbl_last),
                        getString(R.string.numeric_twenty_four),
                        getString(R.string.lbl_hour)
                    )
                }

                DomainConnectionsViewModel.TimeCategory.SEVEN_DAYS -> {
                    getString(
                        R.string.three_argument,
                        getString(R.string.lbl_last),
                        getString(R.string.numeric_seven),
                        getString(R.string.lbl_day)
                    )
                }
            }
    }

    private fun setRecyclerView() {
        b.dcRecycler.setHasFixedSize(true)
        val layoutManager = CustomLinearLayoutManager(this)
        b.dcRecycler.layoutManager = layoutManager

        val recyclerAdapter = DomainConnectionsAdapter(this)

        if (type == InputType.DOMAIN) {
            viewModel.domainConnectionList.observe(this) { recyclerAdapter.submitData(this.lifecycle, it) }
        } else {
            viewModel.flagConnectionList.observe(this) { recyclerAdapter.submitData(this.lifecycle, it) }
        }

        // remove the view if there is no data
        recyclerAdapter.addLoadStateListener {
            if (it.append.endOfPaginationReached) {
                if (recyclerAdapter.itemCount < 1) {
                    b.dcRecycler.visibility = View.GONE
                    b.dcRecycler.visibility = View.VISIBLE
                } else {
                    b.dcRecycler.visibility = View.VISIBLE
                    b.dcRecycler.visibility = View.GONE
                }
            } else {
                b.dcRecycler.visibility = View.VISIBLE
                b.dcRecycler.visibility = View.GONE
            }
        }
        b.dcRecycler.adapter = recyclerAdapter
    }
}