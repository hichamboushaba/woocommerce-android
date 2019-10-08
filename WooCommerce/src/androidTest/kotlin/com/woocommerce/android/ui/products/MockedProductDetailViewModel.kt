package com.woocommerce.android.ui.products

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import com.woocommerce.android.di.UI_THREAD
import com.woocommerce.android.model.Product
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.util.CurrencyFormatter
import kotlinx.coroutines.CoroutineDispatcher
import org.wordpress.android.fluxc.store.WooCommerceStore
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.roundToInt

class MockedProductDetailViewModel @Inject constructor(
    @Named(UI_THREAD) mainDispatcher: CoroutineDispatcher,
    wooCommerceStore: WooCommerceStore,
    selectedSite: SelectedSite,
    productRepository: ProductDetailRepository,
    networkStatus: NetworkStatus,
    private val currencyFormatter: CurrencyFormatter
) : ProductDetailViewModel(
        mainDispatcher,
        wooCommerceStore,
        selectedSite,
        productRepository,
        networkStatus,
        currencyFormatter
) {
    override val productData: LiveData<ProductWithParameters>
        get() = Transformations.map(super.productData) {
            combineData(it.product, Parameters("$", "oz", "in"))
        }

    private fun combineData(product: Product, parameters: Parameters): ProductWithParameters {
        val weight = if (product.weight > 0) "${product.weight.roundToInt()}${parameters.weightUnit ?: ""}" else ""

        val hasLength = product.length > 0
        val hasWidth = product.width > 0
        val hasHeight = product.height > 0
        val unit = parameters.dimensionUnit ?: ""
        val size = if (hasLength && hasWidth && hasHeight) {
            "${product.length.roundToInt()} x ${product.width.roundToInt()} x ${product.height.roundToInt()} $unit"
        } else if (hasWidth && hasHeight) {
            "${product.width.roundToInt()} x ${product.height.roundToInt()} $unit"
        } else {
            ""
        }.trim()

        return ProductWithParameters(
                product,
                weight,
                size,
                formatCurrency(product.price, parameters.currencyCode),
                formatCurrency(product.salePrice, parameters.currencyCode),
                formatCurrency(product.regularPrice, parameters.currencyCode)
        )
    }

    private fun formatCurrency(amount: BigDecimal?, currencyCode: String?): String {
        return currencyCode?.let {
            currencyFormatter.formatCurrency(amount ?: BigDecimal.ZERO, it)
        } ?: amount.toString()
    }
}