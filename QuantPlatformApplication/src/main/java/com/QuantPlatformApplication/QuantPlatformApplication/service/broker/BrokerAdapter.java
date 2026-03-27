package com.QuantPlatformApplication.QuantPlatformApplication.service.broker;

import java.util.List;
import java.util.Map;

/**
 * Abstract broker integration interface.
 * Implementations: PaperBrokerAdapter (internal), AlpacaBrokerAdapter (Alpaca API)
 */
public interface BrokerAdapter {

    String getName();

    boolean isConnected();

    Map<String, Object> placeOrder(String symbol, String side, String type, double quantity, Double price);

    Map<String, Object> cancelOrder(String orderId);

    Map<String, Object> getOrder(String orderId);

    List<Map<String, Object>> getOpenOrders();

    List<Map<String, Object>> getPositions();

    Map<String, Object> getAccount();

    Map<String, Object> reconcilePositions();
}
