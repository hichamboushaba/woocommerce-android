package com.woocommerce.android.ui.mystore

import com.woocommerce.android.AppPrefs
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.di.ActivityScope
import com.woocommerce.android.network.ConnectionChangeReceiver
import com.woocommerce.android.network.ConnectionChangeReceiver.ConnectionChangeEvent
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.mystore.MyStoreContract.Presenter
import com.woocommerce.android.ui.mystore.MyStoreContract.View
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.WooLog.T
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.WCOrderAction.FETCH_HAS_ORDERS
import org.wordpress.android.fluxc.action.WCStatsAction.FETCH_NEW_VISITOR_STATS
import org.wordpress.android.fluxc.action.WCStatsAction.FETCH_REVENUE_STATS
import org.wordpress.android.fluxc.generated.WCOrderActionBuilder
import org.wordpress.android.fluxc.generated.WCStatsActionBuilder
import org.wordpress.android.fluxc.store.WCOrderStore
import org.wordpress.android.fluxc.store.WCOrderStore.FetchHasOrdersPayload
import org.wordpress.android.fluxc.store.WCOrderStore.OnOrderChanged
import org.wordpress.android.fluxc.store.WCStatsStore
import org.wordpress.android.fluxc.store.WCStatsStore.FetchNewVisitorStatsPayload
import org.wordpress.android.fluxc.store.WCStatsStore.FetchRevenueStatsPayload
import org.wordpress.android.fluxc.store.WCStatsStore.FetchTopEarnersStatsPayload
import org.wordpress.android.fluxc.store.WCStatsStore.OnWCRevenueStatsChanged
import org.wordpress.android.fluxc.store.WCStatsStore.OnWCStatsChanged
import org.wordpress.android.fluxc.store.WCStatsStore.OnWCTopEarnersChanged
import org.wordpress.android.fluxc.store.WCStatsStore.OrderStatsErrorType.PLUGIN_NOT_ACTIVE
import org.wordpress.android.fluxc.store.WCStatsStore.StatsGranularity
import org.wordpress.android.fluxc.store.WooCommerceStore
import javax.inject.Inject

@ActivityScope
class MyStorePresenter @Inject constructor(
    private val dispatcher: Dispatcher,
    private val wooCommerceStore: WooCommerceStore, // Required to ensure the WooCommerceStore is initialized!
    private val wcStatsStore: WCStatsStore,
    private val wcOrderStore: WCOrderStore, // Required to ensure the WCOrderStore is initialized!
    private val selectedSite: SelectedSite,
    private val networkStatus: NetworkStatus
) : Presenter {
    companion object {
        private val TAG = MyStorePresenter::class.java
        private const val NUM_TOP_EARNERS = 3
        private val statsForceRefresh = BooleanArray(StatsGranularity.values().size)
        private val topEarnersForceRefresh = BooleanArray(StatsGranularity.values().size)

        init {
            resetForceRefresh()
        }

        /**
         * this tells the presenter to force a refresh for all granularities on the next request - this is
         * used after a swipe-to-refresh on the dashboard to ensure we don't get cached data
         */
        fun resetForceRefresh() {
            for (i in 0 until statsForceRefresh.size) {
                statsForceRefresh[i] = true
            }
            for (i in 0 until topEarnersForceRefresh.size) {
                topEarnersForceRefresh[i] = true
            }
        }
    }

    private var dashboardView: View? = null

    override fun takeView(view: View) {
        dashboardView = view
        dispatcher.register(this)
        ConnectionChangeReceiver.getEventBus().register(this)
    }

    override fun dropView() {
        dashboardView = null
        dispatcher.unregister(this)
        ConnectionChangeReceiver.getEventBus().unregister(this)
    }

    override fun loadStats(granularity: StatsGranularity, forced: Boolean) {
        if (!networkStatus.isConnected()) {
            dashboardView?.isRefreshPending = true
            return
        }

        val forceRefresh = forced || statsForceRefresh[granularity.ordinal]
        if (forceRefresh) {
            statsForceRefresh[granularity.ordinal] = false
            dashboardView?.showChartSkeleton(true)
        }

        // fetch revenue stats
        fetchRevenueStats(granularity, forceRefresh)

        // fetch visitor stats
        fetchVisitorStats(granularity, forceRefresh)
    }

    override fun loadTopEarnerStats(granularity: StatsGranularity, forced: Boolean) {
        if (!networkStatus.isConnected()) {
            dashboardView?.isRefreshPending = true
            return
        }

        val forceRefresh = forced || topEarnersForceRefresh[granularity.ordinal]
        if (forceRefresh) {
            topEarnersForceRefresh[granularity.ordinal] = false
            dashboardView?.showTopEarnersSkeleton(true)
        }

        fetchTopEarnerStats(granularity, forceRefresh)
    }

    override fun fetchRevenueStats(granularity: StatsGranularity, forced: Boolean) {
        val statsPayload = FetchRevenueStatsPayload(selectedSite.get(), granularity, forced = forced)
        dispatcher.dispatch(WCStatsActionBuilder.newFetchRevenueStatsAction(statsPayload))
    }

    override fun fetchVisitorStats(granularity: StatsGranularity, forced: Boolean) {
        val visitsPayload = FetchNewVisitorStatsPayload(selectedSite.get(), granularity, forced)
        dispatcher.dispatch(WCStatsActionBuilder.newFetchNewVisitorStatsAction(visitsPayload))
    }

    override fun fetchTopEarnerStats(granularity: StatsGranularity, forced: Boolean) {
        val payload = FetchTopEarnersStatsPayload(
                selectedSite.get(), granularity, NUM_TOP_EARNERS, forced = forced
        )
        dispatcher.dispatch(WCStatsActionBuilder.newFetchTopEarnersStatsAction(payload))
    }

    override fun getStatsCurrency() = wooCommerceStore.getSiteSettings(selectedSite.get())?.currencyCode

    /**
     * dispatches a FETCH_HAS_ORDERS action which tells us whether this store has *ever* had any orders
     */
    override fun fetchHasOrders() {
        val payload = FetchHasOrdersPayload(selectedSite.get())
        dispatcher.dispatch(WCOrderActionBuilder.newFetchHasOrdersAction(payload))
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onWCRevenueStatsChanged(event: OnWCRevenueStatsChanged) {
        when (event.causeOfChange) {
            FETCH_REVENUE_STATS -> {
                dashboardView?.showChartSkeleton(false)
                if (event.isError) {
                    WooLog.e(T.DASHBOARD, "$TAG - Error fetching stats: ${event.error.message}")
                    // display a different error snackbar if the error type is not "plugin not active", since
                    // this error is already being handled by the activity class
                    if (event.error.type == PLUGIN_NOT_ACTIVE) {
                        AppPrefs.setV4StatsSupported(false)
                        dashboardView?.updateStatsAvailabilityError()
                    } else {
                        dashboardView?.showStatsError(event.granularity)
                    }
                    return
                }

                // Track fresh data load
                AnalyticsTracker.track(Stat.DASHBOARD_MAIN_STATS_LOADED,
                        mapOf(AnalyticsTracker.KEY_RANGE to event.granularity.name.toLowerCase()))

                val revenueStatsModel = wcStatsStore.getRawRevenueStats(
                        selectedSite.get(), event.granularity, event.startDate!!, event.endDate!!
                )
                dashboardView?.showStats(revenueStatsModel, event.granularity)
            }
        }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onWCStatsChanged(event: OnWCStatsChanged) {
        when (event.causeOfChange) {
            FETCH_NEW_VISITOR_STATS -> {
                if (event.isError) {
                    WooLog.e(T.DASHBOARD, "$TAG - Error fetching visitor stats: ${event.error.message}")
                    dashboardView?.showVisitorStatsError(event.granularity)
                    return
                }

                val visitorStats = wcStatsStore.getNewVisitorStats(
                        selectedSite.get(), event.granularity, event.quantity, event.date, event.isCustomField
                )
                dashboardView?.showVisitorStats(visitorStats, event.granularity)
            }
        }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onWCTopEarnersChanged(event: OnWCTopEarnersChanged) {
        dashboardView?.showTopEarnersSkeleton(false)
        if (event.isError) {
            dashboardView?.showTopEarnersError(event.granularity)
        } else {
            // Track fresh data loaded
            AnalyticsTracker.track(
                    Stat.DASHBOARD_TOP_PERFORMERS_LOADED,
                    mapOf(AnalyticsTracker.KEY_RANGE to event.granularity.name.toLowerCase()))

            dashboardView?.showTopEarners(event.topEarners, event.granularity)
        }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onOrderChanged(event: OnOrderChanged) {
        when (event.causeOfChange) {
            FETCH_HAS_ORDERS -> {
                if (event.isError) {
                    WooLog.e(T.DASHBOARD,
                            "$TAG - Error fetching whether orders exist: ${event.error.message}")
                } else {
                    val hasNoOrders = event.rowsAffected == 0
                    dashboardView?.showEmptyView(hasNoOrders)
                }
            }
        }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: ConnectionChangeEvent) {
        if (event.isConnected) {
            // Refresh data if needed now that a connection is active
            dashboardView?.let { view ->
                if (view.isRefreshPending) {
                    view.refreshMyStoreStats(forced = false)
                }
            }
        }
    }
}
