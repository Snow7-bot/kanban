package com.kangban.client;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

/** Keeps provider calls independent from stale desktop proxy settings. */
public final class DirectProxySelector extends ProxySelector {

    public static final DirectProxySelector INSTANCE = new DirectProxySelector();

    private DirectProxySelector() {
    }

    @Override
    public List<Proxy> select(URI uri) {
        return List.of(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress address, IOException error) {
        // HttpClient reports the connection failure to its caller.
    }
}
