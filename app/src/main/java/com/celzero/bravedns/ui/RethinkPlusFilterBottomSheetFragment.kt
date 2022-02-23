/*
 * Copyright 2022 RethinkDNS and its authors
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
package com.celzero.bravedns.ui

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import com.celzero.bravedns.R
import com.celzero.bravedns.data.FileTag
import com.celzero.bravedns.databinding.BottomSheetRethinkPlusFilterBinding
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities.Companion.isDarkSystemTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import org.koin.android.ext.android.inject

class RethinkPlusFilterBottomSheetFragment(val activity: ConfigureRethinkPlusActivity,
                                           private val fileTags: List<FileTag>) :
        BottomSheetDialogFragment() {

    private var _binding: BottomSheetRethinkPlusFilterBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val b get() = _binding!!

    private val persistentState by inject<PersistentState>()

    private var filters : ConfigureRethinkPlusActivity.Filters? = null

    override fun getTheme(): Int = Themes.getBottomsheetCurrentTheme(isDarkSystemTheme(activity),
                                                                     persistentState.theme)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = BottomSheetRethinkPlusFilterBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initClickListeners()
    }

    private fun initView() {
        filters = activity.filterObserver().value
        makeChipGroup(fileTags)
        makeChipSubGroup(fileTags)
        // checkFilterIfNeeded()
    }

    private fun makeChipGroup(ft: List<FileTag>) {
        b.rpfFilterChipGroup.removeAllViews()
        ft.distinctBy { it.group }.forEach {
            val checked = filters?.groups?.contains(it.group) == true
            b.rpfFilterChipGroup.addView(remakeChipGroup(it.group, checked))
        }
    }

    private fun makeChipSubGroup(ft: List<FileTag>) {
        //val f = activity.filterObserver().value
        b.rpfFilterChipSubGroup.removeAllViews()
        ft.distinctBy { it.subg }.forEach {
            if (it.subg.isBlank()) return@forEach

            val checked = filters?.subGroups?.contains(it.subg) == true
            b.rpfFilterChipSubGroup.addView(remakeChipSubgroup(it.subg, checked))
        }

        // add an extra chip as "others" only to handle all the empty sub-group in the list
        val checked = filters?.subGroups?.contains("others") == true
        b.rpfFilterChipSubGroup.addView(remakeChipSubgroup("others", checked))
        // TODO: Remove the above code for others after the changes in server for filetag.json
    }

    private fun initClickListeners() {
        b.rpfApply.setOnClickListener {
            this.dismiss()
            if (filters == null) return@setOnClickListener

            activity.filterObserver().postValue(filters)
        }

        b.rpfClear.setOnClickListener {
            activity.filterObserver().postValue(ConfigureRethinkPlusActivity.Filters())
            this.dismiss()
        }
    }

    private fun remakeChipGroup(label: String, checked: Boolean): Chip {
        val chip = this.layoutInflater.inflate(R.layout.item_chip_filter, b.root, false) as Chip
        chip.tag = label
        chip.text = label
        chip.isChecked = checked

        chip.setOnCheckedChangeListener { button: CompoundButton, isSelected: Boolean ->
            if (isSelected) {
                applyGroupFilter(button.tag as String)
                colorUpChipIcon(chip)
            } else {
                removeGroupFilter(button.tag as String)
            }
        }

        return chip
    }

    private fun remakeChipSubgroup(label: String, checked: Boolean): Chip {
        val chip = this.layoutInflater.inflate(R.layout.item_chip_filter, b.root, false) as Chip
        chip.tag = label
        chip.text = label
        chip.isChecked = checked

        chip.setOnCheckedChangeListener { button: CompoundButton, isSelected: Boolean ->
            if (isSelected) {
                applySubgroupFilter(button.tag as String)
                colorUpChipIcon(chip)
            } else {
                removeSubgroupFilter(button.tag as String)
            }
        }

        return chip
    }

    private fun colorUpChipIcon(chip: Chip) {
        val colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(requireContext(), R.color.primaryText), PorterDuff.Mode.SRC_IN)
        chip.checkedIcon?.colorFilter = colorFilter
        chip.chipIcon?.colorFilter = colorFilter
    }

    private fun applyGroupFilter(tag: String) {
        if (filters == null) {
            filters = ConfigureRethinkPlusActivity.Filters()
        }
        // asserting the filters object with above check
        filters!!.groups.add(tag)
    }

    private fun removeGroupFilter(tag: String) {
        if (filters == null) return

        filters!!.groups.remove(tag)
    }

    private fun applySubgroupFilter(tag: String) {
        val ft = fileTags.first { it.subg == tag }
        if (filters == null) {
            filters = ConfigureRethinkPlusActivity.Filters()
        }
        // asserting the filters object with above check
        filters!!.groups.add(ft.group)
        filters!!.subGroups.add(tag)
    }

    private fun removeSubgroupFilter(tag: String) {
        if (filters == null) return

        filters!!.subGroups.remove(tag)
    }
}
