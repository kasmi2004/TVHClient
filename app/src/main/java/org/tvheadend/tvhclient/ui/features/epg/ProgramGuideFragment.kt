package org.tvheadend.tvhclient.ui.features.epg

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.util.SparseArray
import android.view.*
import android.widget.Filter
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
import androidx.viewpager.widget.ViewPager
import kotlinx.android.synthetic.main.epg_main_fragment.*
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.domain.entity.ChannelTag
import org.tvheadend.tvhclient.domain.entity.EpgProgram
import org.tvheadend.tvhclient.ui.base.BaseFragment
import org.tvheadend.tvhclient.ui.common.*
import org.tvheadend.tvhclient.ui.common.callbacks.RecyclerViewClickCallback
import org.tvheadend.tvhclient.ui.features.channels.ChannelDisplayOptionListener
import org.tvheadend.tvhclient.ui.features.dialogs.showChannelTagSelectionDialog
import org.tvheadend.tvhclient.ui.features.dialogs.showGenreColorDialog
import org.tvheadend.tvhclient.ui.features.dvr.RecordingAddEditActivity
import org.tvheadend.tvhclient.ui.features.notification.addNotification
import org.tvheadend.tvhclient.ui.features.search.SearchActivity
import org.tvheadend.tvhclient.ui.features.search.SearchRequestInterface
import timber.log.Timber

class ProgramGuideFragment : BaseFragment(), EpgScrollInterface, RecyclerViewClickCallback, ChannelDisplayOptionListener, Filter.FilterListener, ViewPager.OnPageChangeListener, SearchRequestInterface {

    private lateinit var epgViewModel: EpgViewModel
    private lateinit var channelListRecyclerViewAdapter: EpgChannelListRecyclerViewAdapter

    private lateinit var viewPagerAdapter: EpgViewPagerAdapter
    private var enableScrolling = true
    private lateinit var channelListRecyclerViewLayoutManager: LinearLayoutManager
    private var programIdToBeEditedWhenBeingRecorded = 0
    private var channelTags: List<ChannelTag> = ArrayList()
    private var channelCount: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.epg_main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        epgViewModel = ViewModelProviders.of(activity!!).get(EpgViewModel::class.java)

        forceSingleScreenLayout()

        if (savedInstanceState == null) {
            epgViewModel.searchQuery = arguments?.getString(SearchManager.QUERY) ?: ""
        }

        channelListRecyclerViewAdapter = EpgChannelListRecyclerViewAdapter(epgViewModel, this)
        channelListRecyclerViewLayoutManager = LinearLayoutManager(activity)
        channel_list_recycler_view.layoutManager = channelListRecyclerViewLayoutManager
        channel_list_recycler_view.addItemDecoration(DividerItemDecoration(activity, LinearLayoutManager.VERTICAL))
        channel_list_recycler_view.itemAnimator = DefaultItemAnimator()
        channel_list_recycler_view.adapter = channelListRecyclerViewAdapter
        channel_list_recycler_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState != SCROLL_STATE_IDLE) {
                    enableScrolling = true
                } else if (enableScrolling) {
                    enableScrolling = false
                    this@ProgramGuideFragment.onScrollStateChanged()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (enableScrolling) {
                    val position = channelListRecyclerViewLayoutManager.findFirstVisibleItemPosition()
                    val v = channelListRecyclerViewLayoutManager.getChildAt(0)
                    val offset = if (v == null) 0 else v.top - recyclerView.paddingTop
                    onScroll(position, offset)
                }
            }
        })

        viewPagerAdapter = EpgViewPagerAdapter(childFragmentManager, epgViewModel)
        program_list_viewpager.adapter = viewPagerAdapter
        program_list_viewpager.offscreenPageLimit = 2
        program_list_viewpager.addOnPageChangeListener(this)

        Timber.d("Observing channel tags")
        epgViewModel.channelTags.observe(viewLifecycleOwner, Observer { tags ->
            if (tags != null) {
                Timber.d("View model returned ${tags.size} channel tags")
                channelTags = tags
            }
        })

        Timber.d("Observing epg channels")
        epgViewModel.epgChannels.observe(viewLifecycleOwner, Observer { channels ->

            progress_bar?.gone()
            channel_list_recycler_view?.visible()
            program_list_viewpager?.visible()

            if (channels != null) {
                Timber.d("View model returned ${channels.size} epg channels")
                channelListRecyclerViewAdapter.addItems(channels)
            }
            // Show either all channels or the name of the selected
            // channel tag and the channel count in the toolbar
            context?.let {
                val toolbarTitle = epgViewModel.getSelectedChannelTagName(it)
                toolbarInterface.setTitle(toolbarTitle)
                toolbarInterface.setSubtitle(resources.getQuantityString(R.plurals.items,
                        channelListRecyclerViewAdapter.itemCount, channelListRecyclerViewAdapter.itemCount))
            }
        })

        Timber.d("Observing trigger to reload epg data")
        epgViewModel.reloadEpgData.observe(viewLifecycleOwner, Observer { reload ->
            Timber.d("Trigger to reload epg data has changed to $reload")
            if (reload) {
                epgViewModel.loadEpgData()
            }
        })

        Timber.d("Observing epg data")
        epgViewModel.epgData.observe(viewLifecycleOwner, Observer { data ->
            data?.forEach {
                Timber.d("Loaded ${it.value.size} programs for channel ${it.key}")
            }
        })

        // Observe all recordings here in case a recording shall be edited right after it was added.
        // This needs to be done in this fragment because the popup menu handling is also done here.
        Timber.d("Observing recordings")
        epgViewModel.recordings.observe(viewLifecycleOwner, Observer { recordings ->
            if (recordings != null) {
                Timber.d("View model returned ${recordings.size} recordings")
                for (recording in recordings) {
                    // Show the edit recording screen of the scheduled recording
                    // in case the user has selected the record and edit menu item.
                    if (recording.eventId == programIdToBeEditedWhenBeingRecorded && programIdToBeEditedWhenBeingRecorded > 0) {
                        programIdToBeEditedWhenBeingRecorded = 0
                        val intent = Intent(activity, RecordingAddEditActivity::class.java)
                        intent.putExtra("id", recording.id)
                        intent.putExtra("type", "recording")
                        activity?.startActivity(intent)
                        break
                    }
                }
            }
        })

        epgViewModel.channelCount.observe(viewLifecycleOwner, Observer { count ->
            channelCount = count
        })
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.channel_list_options_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val showGenreColors = sharedPreferences.getBoolean("genre_colors_for_channels_enabled", resources.getBoolean(R.bool.pref_default_genre_colors_for_channels_enabled))
        val showChannelTagMenu = sharedPreferences.getBoolean("channel_tag_menu_enabled", resources.getBoolean(R.bool.pref_default_channel_tag_menu_enabled))

        menu.findItem(R.id.menu_genre_color_info_channels)?.isVisible = showGenreColors

        // Prevent the channel tag menu item from going into the overlay menu
        if (showChannelTagMenu) {
            menu.findItem(R.id.menu_tags)?.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val ctx = context ?: return super.onOptionsItemSelected(item)
        return when (item.itemId) {
            R.id.menu_tags ->
                showChannelTagSelectionDialog(ctx, channelTags.toMutableList(), channelCount, this)
            R.id.menu_timeframe ->
                menuUtils.handleMenuTimeSelection(epgViewModel.selectedTimeOffset, epgViewModel.hoursToShow, epgViewModel.hoursToShow * epgViewModel.daysToShow, this)
            R.id.menu_genre_color_info_channels ->
                showGenreColorDialog(ctx)
            R.id.menu_sort_order ->
                menuUtils.handleMenuChannelSortOrderSelection(this)
            else ->
                super.onOptionsItemSelected(item)
        }
    }

    override fun onTimeSelected(which: Int) {
        epgViewModel.selectedTimeOffset = which
        program_list_viewpager?.currentItem = which

        // TODO check if this is required
        // Add the selected list index as extra hours to the current time.
        // If the first index was selected then use the current time.
        var timeInMillis = System.currentTimeMillis()
        timeInMillis += (1000 * 60 * 60 * which * epgViewModel.hoursToShow).toLong()
        epgViewModel.setSelectedTime(timeInMillis)
    }

    override fun onChannelTagIdsSelected(ids: Set<Int>) {
        channel_list_recycler_view?.gone()
        program_list_viewpager?.gone()
        progress_bar?.visible()
        epgViewModel.setSelectedChannelTagIds(ids)
    }

    override fun onChannelSortOrderSelected(id: Int) {
        epgViewModel.setChannelSortOrder(id)
    }

    override fun onClick(view: View, position: Int) {
        if (view.id == R.id.icon || view.id == R.id.icon_text) {
            if (isNetworkAvailable) {
                channelListRecyclerViewAdapter.getItem(position)?.let {
                    menuUtils.handleMenuPlayChannelIcon(it.id)
                }
            }
        }
    }

    override fun onLongClick(view: View, position: Int): Boolean {
        // NOP
        return true
    }

    internal fun showPopupMenu(view: View, program: EpgProgram?) {
        val ctx = context ?: return
        if (program == null) {
            return
        }

        val recording = epgViewModel.getRecordingById(program.eventId)

        val popupMenu = PopupMenu(ctx, view)
        popupMenu.menuInflater.inflate(R.menu.program_popup_and_toolbar_menu, popupMenu.menu)
        popupMenu.menuInflater.inflate(R.menu.external_search_options_menu, popupMenu.menu)

        prepareMenu(ctx, popupMenu.menu, program, program.recording, isNetworkAvailable, htspVersion, isUnlocked)
        prepareSearchMenu(popupMenu.menu, program.title, isNetworkAvailable)

        popupMenu.setOnMenuItemClickListener { item ->
            if (onMenuSelected(ctx, item.itemId, program.title)) {
                return@setOnMenuItemClickListener true
            }
            when (item.itemId) {
                R.id.menu_record_stop -> {
                    return@setOnMenuItemClickListener menuUtils.handleMenuStopRecordingSelection(recording, null)
                }
                R.id.menu_record_cancel ->
                    return@setOnMenuItemClickListener menuUtils.handleMenuCancelRecordingSelection(recording, null)
                R.id.menu_record_remove ->
                    return@setOnMenuItemClickListener menuUtils.handleMenuRemoveRecordingSelection(recording, null)
                R.id.menu_record_once ->
                    return@setOnMenuItemClickListener menuUtils.handleMenuRecordSelection(program.eventId)
                R.id.menu_record_once_and_edit -> {
                    programIdToBeEditedWhenBeingRecorded = program.eventId
                    return@setOnMenuItemClickListener menuUtils.handleMenuRecordSelection(program.eventId)
                }
                R.id.menu_record_once_custom_profile ->
                    return@setOnMenuItemClickListener menuUtils.handleMenuCustomRecordSelection(program.eventId, program.channelId)
                R.id.menu_record_series ->
                    return@setOnMenuItemClickListener menuUtils.handleMenuSeriesRecordSelection(program.title)
                R.id.menu_play ->
                    return@setOnMenuItemClickListener menuUtils.handleMenuPlayChannel(program.channelId)
                R.id.menu_cast ->
                    return@setOnMenuItemClickListener menuUtils.handleMenuCast("channelId", program.channelId)
                R.id.menu_add_notification -> {
                    activity?.let {
                        addNotification(it, program, epgViewModel.getRecordingProfile())
                    }
                    return@setOnMenuItemClickListener true
                }
                else ->
                    return@setOnMenuItemClickListener false
            }
        }
        popupMenu.show()
    }

    override fun onFilterComplete(i: Int) {
        // Show either all channels or the name of the selected
        // channel tag and the channel count in the toolbar
        context?.let {
            val toolbarTitle = epgViewModel.getSelectedChannelTagName(it)
            toolbarInterface.setTitle(toolbarTitle)
            toolbarInterface.setSubtitle(it.resources.getQuantityString(R.plurals.results,
                    channelListRecyclerViewAdapter.itemCount, channelListRecyclerViewAdapter.itemCount))
        }
    }

    override fun onScroll(position: Int, offset: Int) {
        epgViewModel.verticalScrollPosition = position
        epgViewModel.verticalScrollOffset = offset
        startScrolling()
    }

    override fun onScrollStateChanged() {
        startScrolling()
    }

    /**
     * The channel list fragment and all program list fragment in the viewpager fragments
     * will be scrolled to the saved position and offset from the view model.
     * Only already existing fragments in the view pager will be scrolled
     */
    private fun startScrolling() {
        val position = epgViewModel.verticalScrollPosition
        val offset = epgViewModel.verticalScrollOffset

        channelListRecyclerViewLayoutManager.scrollToPositionWithOffset(position, offset)

        for (i in 0 until viewPagerAdapter.registeredFragmentCount) {
            val fragment = viewPagerAdapter.getRegisteredFragment(i)
            if (fragment is EpgScrollInterface) {
                fragment.onScroll(position, offset)
            }
        }
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        // NOP
    }

    /**
     * When the user has selected a new fragment the view pager will create the
     * required neighbour fragments. Inform all those fragments except the current
     * one to scroll to the required position.
     */
    override fun onPageSelected(position: Int) {
        for (i in position - 1..position + 1) {
            val fragment = viewPagerAdapter.getRegisteredFragment(i)
            if (i != position && fragment is EpgScrollInterface) {
                fragment.onScroll(epgViewModel.verticalScrollPosition, epgViewModel.verticalScrollOffset)
            }
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
        // TOP
    }

    override fun onSearchRequested(query: String) {
        // Start searching for programs on all channels
        val searchIntent = Intent(activity, SearchActivity::class.java)
        searchIntent.putExtra(SearchManager.QUERY, query)
        searchIntent.action = Intent.ACTION_SEARCH
        searchIntent.putExtra("type", "program_guide")
        startActivity(searchIntent)
    }

    override fun onSearchResultsCleared(): Boolean {
        return false
    }

    override fun getQueryHint(): String {
        return getString(R.string.search_program_guide)
    }

    private class EpgViewPagerAdapter internal constructor(fragmentManager: FragmentManager, private val viewModel: EpgViewModel) : FragmentStatePagerAdapter(fragmentManager) {

        private val registeredFragments = SparseArray<Fragment>()

        internal val registeredFragmentCount: Int
            get() = registeredFragments.size()

        override fun getItem(position: Int): Fragment {
            return EpgViewPagerFragment.newInstance(position)
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val fragment = super.instantiateItem(container, position) as Fragment
            registeredFragments.put(position, fragment)
            return fragment
        }

        override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
            registeredFragments.remove(position)
            super.destroyItem(container, position, item)
        }

        internal fun getRegisteredFragment(position: Int): Fragment? {
            return registeredFragments.get(position)
        }

        override fun getCount(): Int {
            return viewModel.fragmentCount
        }
    }
}
