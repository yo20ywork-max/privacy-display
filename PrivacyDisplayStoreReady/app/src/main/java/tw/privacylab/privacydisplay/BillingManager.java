package tw.privacylab.privacydisplay;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side Google Play Billing bridge for the paid image converter.
 *
 * Product to create in Play Console:
 * Product ID: privacy-image-filter-pro
 * Type: One-time product / non-consumable
 */
public final class BillingManager implements PurchasesUpdatedListener {
    public static final String IMAGE_PRIVACY_PRODUCT_ID = "privacy-image-filter-pro";

    public interface Listener {
        void onBillingStateChanged();
        void onBillingMessage(String message);
    }

    private static BillingManager instance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private BillingClient billingClient;
    private ProductDetails imagePrivacyProductDetails;
    private String formattedPrice = "";
    private boolean connecting;

    public static synchronized BillingManager getInstance(Context context) {
        if (instance == null) {
            instance = new BillingManager(context.getApplicationContext());
        }
        return instance;
    }

    private BillingManager(Context context) {
        this.appContext = context;
        rebuildClient();
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        if (listener != null) listeners.remove(listener);
    }

    public boolean isUnlocked() {
        return SettingsStore.isImagePrivacyUnlocked(appContext);
    }

    public String getFormattedPrice() {
        return formattedPrice == null || formattedPrice.trim().isEmpty()
                ? "尚未取得價格"
                : formattedPrice;
    }

    public void startConnection() {
        if (billingClient == null) rebuildClient();
        if (billingClient.isReady()) {
            queryProductDetails();
            queryExistingPurchases();
            return;
        }
        if (connecting) return;
        connecting = true;
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                connecting = false;
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails();
                    queryExistingPurchases();
                    notifyMessage("已連線 Google Play Billing。");
                } else {
                    notifyMessage("Google Play Billing 尚未可用：" + billingResult.getDebugMessage());
                    notifyStateChanged();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                connecting = false;
                notifyMessage("Google Play Billing 連線中斷，稍後會重新嘗試。");
                notifyStateChanged();
            }
        });
    }

    public void queryExistingPurchases() {
        if (billingClient == null || !billingClient.isReady()) {
            startConnection();
            return;
        }
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                processPurchases(purchases);
            } else {
                notifyMessage("無法恢復購買：" + billingResult.getDebugMessage());
                notifyStateChanged();
            }
        });
    }

    public void launchPurchase(Activity activity) {
        if (activity == null) return;
        if (isUnlocked()) {
            notifyMessage("圖片防窺 Pro 已解鎖。");
            return;
        }
        if (billingClient == null || !billingClient.isReady()) {
            startConnection();
            notifyMessage("正在連線 Google Play，請稍後再試。");
            return;
        }
        if (imagePrivacyProductDetails == null) {
            queryProductDetails();
            notifyMessage("尚未取得商品資訊，請確認 Play Console 已建立商品 ID：" + IMAGE_PRIVACY_PRODUCT_ID);
            return;
        }

        BillingFlowParams.ProductDetailsParams.Builder productParamsBuilder =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(imagePrivacyProductDetails);

        ProductDetails.OneTimePurchaseOfferDetails selectedOffer =
                selectedOneTimeOffer(imagePrivacyProductDetails);
        if (selectedOffer != null
                && selectedOffer.getOfferToken() != null
                && !selectedOffer.getOfferToken().isEmpty()) {
            productParamsBuilder.setOfferToken(selectedOffer.getOfferToken());
        }

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productParamsBuilder.build()))
                .build();

        BillingResult result = billingClient.launchBillingFlow(activity, flowParams);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            notifyMessage("無法開啟購買流程：" + result.getDebugMessage());
        }
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases);
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            notifyMessage("已取消購買。");
        } else {
            notifyMessage("購買失敗：" + billingResult.getDebugMessage());
        }
        notifyStateChanged();
    }

    private void rebuildClient() {
        PendingPurchasesParams pendingParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build();
        billingClient = BillingClient.newBuilder(appContext)
                .setListener(this)
                .enablePendingPurchases(pendingParams)
                .enableAutoServiceReconnection()
                .build();
    }

    private void queryProductDetails() {
        if (billingClient == null || !billingClient.isReady()) return;
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(IMAGE_PRIVACY_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();
        billingClient.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(BillingResult billingResult, QueryProductDetailsResult result) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && result != null) {
                    List<ProductDetails> products = result.getProductDetailsList();
                    imagePrivacyProductDetails = null;
                    if (products != null) {
                        for (ProductDetails details : products) {
                            if (IMAGE_PRIVACY_PRODUCT_ID.equals(details.getProductId())) {
                                imagePrivacyProductDetails = details;
                                ProductDetails.OneTimePurchaseOfferDetails offer =
                                        selectedOneTimeOffer(details);
                                if (offer != null && offer.getFormattedPrice() != null) {
                                    formattedPrice = offer.getFormattedPrice();
                                }
                                break;
                            }
                        }
                    }
                    if (imagePrivacyProductDetails == null) {
                        notifyMessage("找不到商品，請在 Play Console 建立 one-time product：" + IMAGE_PRIVACY_PRODUCT_ID);
                    }
                } else {
                    notifyMessage("無法取得商品資訊：" + billingResult.getDebugMessage());
                }
                notifyStateChanged();
            }
        });
    }

    private ProductDetails.OneTimePurchaseOfferDetails selectedOneTimeOffer(ProductDetails details) {
        if (details == null) return null;
        List<ProductDetails.OneTimePurchaseOfferDetails> offers =
                details.getOneTimePurchaseOfferDetailsList();
        if (offers != null && !offers.isEmpty()) return offers.get(0);
        return null;
    }

    private void processPurchases(List<Purchase> purchases) {
        if (purchases == null) return;
        List<Purchase> relevant = new ArrayList<>();
        for (Purchase purchase : purchases) {
            if (purchase == null || purchase.getProducts() == null) continue;
            if (purchase.getProducts().contains(IMAGE_PRIVACY_PRODUCT_ID)) {
                relevant.add(purchase);
            }
        }
        if (relevant.isEmpty()) {
            notifyStateChanged();
            return;
        }
        for (Purchase purchase : relevant) {
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                SettingsStore.setImagePrivacyUnlocked(appContext, true);
                if (!purchase.isAcknowledged()) {
                    AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build();
                    billingClient.acknowledgePurchase(params, billingResult -> {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            notifyMessage("圖片防窺 Pro 已解鎖。");
                        } else {
                            notifyMessage("已解鎖，但確認購買時發生問題：" + billingResult.getDebugMessage());
                        }
                        notifyStateChanged();
                    });
                } else {
                    notifyMessage("圖片防窺 Pro 已解鎖。");
                }
            } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                notifyMessage("購買仍在處理中，完成付款後會自動解鎖。");
            }
        }
        notifyStateChanged();
    }

    private void notifyStateChanged() {
        mainHandler.post(() -> {
            for (Listener listener : listeners) listener.onBillingStateChanged();
        });
    }

    private void notifyMessage(String message) {
        mainHandler.post(() -> {
            for (Listener listener : listeners) listener.onBillingMessage(message);
        });
    }
}

