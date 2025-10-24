package io.cloudx.demo.demoapp

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.core.os.BundleCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.parcelize.Parcelize

class PlacementTypeSelectorFragment : Fragment(R.layout.fragment_placement_type_selector) {

    private lateinit var toggleGroup: MaterialButtonToggleGroup

    private lateinit var items: List<PlacementItem<*>>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        items = BundleCompat.getParcelableArrayList(
            requireArguments(),
            KEY_ITEMS,
            PlacementItem::class.java
        ) ?: listOf()

        toggleGroup = view.findViewById(R.id.toggle_group)

        val ctx = requireContext()

        // Button per placement generation.
        items.onEach {
            val btn = MaterialButton(
                ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            )
            btn.text = it.label
            btn.id = View.generateViewId()

            toggleGroup.addView(btn)
        }

        toggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                val idx = group.children.indexOfFirst { it.id == checkedId }
                val placementItem = items[idx]
                placementItem.showFragment()
            }
        }

        // Select first by default. Didn't find a proper selection way.
        toggleGroup.check(toggleGroup.children.first().id)
    }

    private fun PlacementItem<*>.showFragment() {
        val fm = childFragmentManager
        val tag = fragmentClass.simpleName + fragmentBundle.getPlacements()!!

        val isFragmentAlreadyOnScreen = fm.findFragmentByTag(tag) != null
        if (isFragmentAlreadyOnScreen) {
            return
        }

        fm.commit {
            setCustomAnimations(
                com.google.android.material.R.anim.abc_fade_in,
                com.google.android.material.R.anim.abc_fade_out
            )
            replace(R.id.fragment_container, fragmentClass, fragmentBundle, tag)
        }
    }

    companion object {

        fun <T : Fragment> bundleFrom(items: List<PlacementItem<T>>): Bundle = Bundle().apply {
            putParcelableArrayList(KEY_ITEMS, ArrayList(items))
        }

        private const val KEY_ITEMS = "KEY_ITEMS"

        @Parcelize
        class PlacementItem<T : Fragment>(
            val label: String,
            val fragmentClass: Class<out T>,
            val fragmentBundle: Bundle
        ) : Parcelable
    }
}